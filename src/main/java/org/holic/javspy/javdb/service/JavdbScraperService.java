package org.holic.javspy.javdb.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.holic.javspy.javdb.client.JavdbApiClient;
import org.holic.javspy.javdb.mapper.JavdbMapper;
import org.holic.javspy.javdb.model.JavdbMagnet;
import org.holic.javspy.javdb.model.JavdbMagnetExport;
import org.holic.javspy.javdb.model.JavdbMovie;
import org.holic.javspy.javdb.model.JavdbMovieVo;
import org.holic.javspy.javdb.model.JavdbScrapeResult;
import org.holic.javspy.javdb.model.JavdbVideoItem;
import org.holic.javspy.misc.ImageDownloadService;
import org.holic.javspy.service.MovieIsExsitInSystem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * javdb 刮削业务：抓取 javdb.com 并把影片/磁力信息写入数据库。
 */
@Slf4j
@Service
public class JavdbScraperService {

    private final JavdbApiClient apiClient;
    private final JavdbMapper javdbMapper;
    private final ImageDownloadService imageDownloadService;
    private final MovieIsExsitInSystem movieIsExsitInSystem;

    public JavdbScraperService(JavdbApiClient apiClient, JavdbMapper javdbMapper,
                               ImageDownloadService imageDownloadService,
                               MovieIsExsitInSystem movieIsExsitInSystem) {
        this.apiClient = apiClient;
        this.javdbMapper = javdbMapper;
        this.imageDownloadService = imageDownloadService;
        this.movieIsExsitInSystem = movieIsExsitInSystem;
    }

    /** 近 7 天内发布的磁链视为新磁链。 */
    private static final int RECENT_MAGNET_DAYS = 7;
    /** 7 天内有多个新磁链时优先选择的最小大小：1.5GB。 */
    private static final long MIN_RECOMMENDED_SIZE_BYTES = (long) (1.5 * 1024 * 1024 * 1024);

    /**
     * 将选中影片的磁链按规则写入 javdb_magnet_export 表；
     * 影片没有磁链时写入一行无磁链记录（status=NO_MAGNET）。
     * 每次保存会先清空导出表，再写入本次选中的结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> exportSelectedMagnets(List<String> codes) {
        List<String> normalizedCodes = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        if (codes != null) {
            for (String code : codes) {
                if (StringUtils.isBlank(code)) {
                    continue;
                }
                String c = code.trim().toUpperCase();
                if (seenCodes.add(c)) {
                    normalizedCodes.add(c);
                }
            }
        }
        if (normalizedCodes.isEmpty()) {
            throw new IllegalArgumentException("请先选择影片");
        }

        Map<String, List<JavdbMagnet>> magnetsByCode = new HashMap<>();
        for (JavdbMagnet magnet : javdbMapper.findMagnetsByCodes(normalizedCodes)) {
            if (magnet == null || StringUtils.isBlank(magnet.getCode())
                    || StringUtils.isBlank(magnet.getMagnet())) {
                continue;
            }
            magnetsByCode.computeIfAbsent(magnet.getCode(), k -> new ArrayList<>()).add(magnet);
        }

        List<JavdbMagnetExport> exportRows = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        int magnetCount = 0;
        int noMagnetCount = 0;
        for (String code : normalizedCodes) {
            List<JavdbMagnet> magnets = magnetsByCode.getOrDefault(code, Collections.emptyList());
            JavdbMagnet selected = selectBestMagnet(magnets);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code);

            JavdbMagnetExport row = new JavdbMagnetExport();
            row.setCode(code);
            row.setCreatedAt(new Date());
            if (selected == null) {
                item.put("status", "NO_MAGNET");
                row.setStatus("NO_MAGNET");
                noMagnetCount++;
            } else {
                item.put("status", "OK");
                item.put("magnet", selected.getMagnet());
                item.put("name", selected.getName());
                item.put("sizeText", selected.getSizeText());
                item.put("shareDate", selected.getShareDate());
                row.setStatus("OK");
                row.setMagnet(selected.getMagnet());
                row.setName(selected.getName());
                row.setSizeText(selected.getSizeText());
                row.setShareDate(selected.getShareDate());
                magnetCount++;
            }
            exportRows.add(row);
            items.add(item);
        }

        javdbMapper.clearMagnetExports();
        if (!exportRows.isEmpty()) {
            javdbMapper.insertMagnetExports(exportRows);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", "javdb_magnet_export");
        result.put("total", normalizedCodes.size());
        result.put("magnetCount", magnetCount);
        result.put("noMagnetCount", noMagnetCount);
        result.put("items", items);
        return result;
    }

    /** 按时间优先规则选择一条磁链。 */
    private JavdbMagnet selectBestMagnet(List<JavdbMagnet> magnets) {
        if (magnets == null || magnets.isEmpty()) {
            return null;
        }
        List<JavdbMagnet> sorted = new ArrayList<>(magnets);
        sorted.sort((a, b) -> {
            LocalDate da = magnetDate(a);
            LocalDate db = magnetDate(b);
            if (da != null && db != null) {
                int c = db.compareTo(da);
                if (c != 0) {
                    return c;
                }
            } else if (da != null) {
                return -1;
            } else if (db != null) {
                return 1;
            }
            return Long.compare(idOf(b), idOf(a));
        });

        LocalDate today = LocalDate.now();
        LocalDate threshold = today.minusDays(RECENT_MAGNET_DAYS);
        JavdbMagnet newest = sorted.get(0);
        LocalDate newestDate = magnetDate(newest);
        if (newestDate == null || newestDate.isBefore(threshold)) {
            return newest;
        }

        List<JavdbMagnet> recent = new ArrayList<>();
        for (JavdbMagnet magnet : sorted) {
            LocalDate date = magnetDate(magnet);
            if (date != null && !date.isBefore(threshold)) {
                recent.add(magnet);
            } else {
                break;
            }
        }
        if (recent.size() <= 1) {
            return recent.isEmpty() ? newest : recent.get(0);
        }

        JavdbMagnet best = smallestMagnetAtLeast(recent, MIN_RECOMMENDED_SIZE_BYTES);
        if (best != null) {
            return best;
        }
        JavdbMagnet fallback = smallestMagnetAtLeast(recent, 0L);
        return fallback != null ? fallback : newest;
    }

    /** 从列表中选出大小 >= minSizeBytes 的最小一条。 */
    private JavdbMagnet smallestMagnetAtLeast(List<JavdbMagnet> magnets, long minSizeBytes) {
        JavdbMagnet best = null;
        for (JavdbMagnet magnet : magnets) {
            Long size = magnetSizeBytes(magnet);
            if (size == null || size < minSizeBytes) {
                continue;
            }
            if (best == null || size < magnetSizeBytes(best)) {
                best = magnet;
            }
        }
        return best;
    }

    private LocalDate magnetDate(JavdbMagnet magnet) {
        if (magnet == null) {
            return null;
        }
        if (StringUtils.isNotBlank(magnet.getShareDate())) {
            try {
                return LocalDate.parse(magnet.getShareDate().trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // 继续尝试用入库时间兜底
            }
        }
        if (magnet.getCreatedAt() != null) {
            return magnet.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private Long magnetSizeBytes(JavdbMagnet magnet) {
        if (magnet == null) {
            return null;
        }
        if (magnet.getSizeBytes() != null) {
            return magnet.getSizeBytes();
        }
        return parseSizeBytes(magnet.getSizeText());
    }

    private Long parseSizeBytes(String sizeText) {
        if (StringUtils.isBlank(sizeText)) {
            return null;
        }
        try {
            Matcher m = Pattern.compile("([\\d.]+)\\s*([A-Za-z]+)").matcher(sizeText.trim());
            if (!m.find()) {
                return null;
            }
            double value = Double.parseDouble(m.group(1));
            String unit = m.group(2).toUpperCase();
            long factor;
            switch (unit) {
                case "TB":
                    factor = 1024L * 1024L * 1024L * 1024L;
                    break;
                case "GB":
                    factor = 1024L * 1024L * 1024L;
                    break;
                case "MB":
                    factor = 1024L * 1024L;
                    break;
                case "KB":
                    factor = 1024L;
                    break;
                default:
                    factor = 1L;
            }
            return (long) (value * factor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long idOf(JavdbMagnet magnet) {
        return magnet == null || magnet.getId() == null ? 0L : magnet.getId();
    }

    /**
     * 按番号刮削单个影片并入库。
     *
     * @return 入库结果；抓不到时 movie 为 null
     */
    public JavdbScrapeResult scrapeByCode(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("番号不能为空");
        }
        try {
            JavdbScrapeResult result = apiClient.scrapeByCode(code.trim());
            if (result.getMovie() == null) {
                log.warn("javdb 未找到番号: {}", code);
                return result;
            }
            saveResult(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("javdb 刮削失败, code={}", code, e);
            throw new RuntimeException("javdb 刮削失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按详情页 URL 刮削并入库。
     */
    public JavdbScrapeResult scrapeByUrl(String detailUrl) {
        if (StringUtils.isBlank(detailUrl)) {
            throw new IllegalArgumentException("详情页地址不能为空");
        }
        try {
            JavdbScrapeResult result = apiClient.scrapeByUrl(detailUrl.trim());
            if (result.getMovie() == null) {
                log.warn("javdb 详情页解析失败: {}", detailUrl);
                return result;
            }
            saveResult(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("javdb 刮削失败, url={}", detailUrl, e);
            throw new RuntimeException("javdb 刮削失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按关键词搜索 javdb 前 pages 页，逐部刮削入库。
     *
     * @return 汇总：每个番号的处理结果
     */
    public List<Map<String, Object>> scrapeByKeyword(String keyword, int pages) {
        if (StringUtils.isBlank(keyword)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        List<Map<String, Object>> summary = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        try {
            List<String> urls = apiClient.searchDetailUrls(keyword.trim(), pages);
            log.info("javdb 搜索到 {} 个详情页, keyword={}", urls.size(), keyword);
            for (String url : urls) {
                if (!visited.add(url)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("detailUrl", url);
                try {
                    JavdbScrapeResult result = apiClient.scrapeByUrl(url);
                    if (result.getMovie() == null) {
                        item.put("status", "SKIP");
                        item.put("message", "详情页解析失败");
                    } else {
                        String code = result.getMovie().getCode();
                        item.put("code", code);
                        item.put("title", result.getMovie().getTitle());
                        JavdbMovie before = javdbMapper.findByCode(code);
                        saveResult(result);
                        item.put("status", before == null ? "INSERTED" : "UPDATED");
                        item.put("magnetCount", result.getMagnets() == null ? 0 : result.getMagnets().size());
                    }
                } catch (Exception e) {
                    log.error("javdb 刮削失败, url={}", url, e);
                    item.put("status", "FAILED");
                    item.put("message", e.getMessage());
                }
                summary.add(item);
            }
        } catch (Exception e) {
            log.error("javdb 搜索失败, keyword={}", keyword, e);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", "FAILED");
            item.put("message", e.getMessage());
            summary.add(item);
        }
        return summary;
    }

    /**
     * 按番号/关键词/日期分页查询已入库的影片。
     */
    public PageInfo<JavdbMovie> searchMovies(String code, String keyword,
                                             String releaseDate, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        PageHelper.startPage(safePage, safeSize);
        List<JavdbMovie> list = javdbMapper.searchMovies(
                StringUtils.trimToNull(code),
                StringUtils.trimToNull(keyword),
                StringUtils.trimToNull(releaseDate));
        return new PageInfo<>(list);
    }

    /**
     * 保存刮削结果：影片 upsert + 磁力批量插入，同事务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveResult(JavdbScrapeResult result) {
        if (result == null || result.getMovie() == null) {
            return;
        }
        JavdbMovie movie = result.getMovie();
        if (StringUtils.isBlank(movie.getCode())) {
            throw new IllegalArgumentException("影片缺少番号，无法入库");
        }
        movie.setCode(movie.getCode().trim().toUpperCase());
        Date now = new Date();
        movie.setCreatedAt(now);
        movie.setUpdatedAt(now);
        movie.setCoverUrl(null);
        javdbMapper.insertMovie(movie);

        if (result.getMagnets() != null && !result.getMagnets().isEmpty()) {
            List<JavdbMagnet> magnetsToInsert = filterNewMagnets(result.getMagnets());
            if (!magnetsToInsert.isEmpty()) {
                javdbMapper.insertMagnets(magnetsToInsert);
            }
        }
    }

    /**
     * 过滤掉库里已存在的磁力链接，避免重复抓取同一页时重复入库。
     */
    private List<JavdbMagnet> filterNewMagnets(List<JavdbMagnet> magnets) {
        List<JavdbMagnet> result = new ArrayList<>();
        if (magnets == null || magnets.isEmpty()) {
            return result;
        }
        String detailId = magnets.get(0).getDetailId();
        Set<String> existing = new HashSet<>();
        if (StringUtils.isNotBlank(detailId)) {
            existing.addAll(javdbMapper.findMagnetLinksByDetailId(detailId));
        }
        Set<String> seen = new HashSet<>();
        for (JavdbMagnet magnet : magnets) {
            if (magnet == null || StringUtils.isBlank(magnet.getMagnet())) {
                continue;
            }
            String link = magnet.getMagnet();
            if (existing.contains(link) || !seen.add(link)) {
                continue;
            }
            result.add(magnet);
        }
        return result;
    }

    /**
     * 连通性自检：确认代理/cookie 配置后能访问 javdb。
     */
    public String ping() {
        return apiClient.ping();
    }

    /**
     * 抓取 javdb 首页影片列表（纯解析，不入库）。
     */
    public List<JavdbVideoItem> homeMovies() {
        try {
            return apiClient.homeMovies();
        } catch (Exception e) {
            log.error("javdb 首页抓取失败", e);
            throw new RuntimeException("javdb 首页抓取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按 javdb 首页分页同步影片：库中已存在时直接读库，
     * 不存在时刮削详情并入库，最后返回列表展示数据。
     */
    public List<JavdbMovieVo> homeMoviesWithSync(int page) throws Exception {
        if (page == 1) {
            movieIsExsitInSystem.refreshEmbyListIfAvailable();
        }
        List<JavdbMovieVo> result = new ArrayList<>();
        List<JavdbVideoItem> items = apiClient.pageMovies(page);
        log.info("javdb 首页第 {} 页抓取到 {} 部影片", page, items.size());
        if (!items.isEmpty() && StringUtils.isNotBlank(items.get(0).getCode())) {
            JavdbMovie first = javdbMapper.findByCode(items.get(0).getCode());
            if (first != null) {
                log.info("javdb 首页第 {} 页第一部影片已入库，直接读库展示整页, code={}",
                        page, items.get(0).getCode());
                return moviesFromDb(items);
            }
        }
        for (JavdbVideoItem item : items) {
            if (item == null || StringUtils.isBlank(item.getCode())) {
                continue;
            }
            try {
                JavdbMovie movie = javdbMapper.findByCode(item.getCode());
                String source = "DB";
                if (movie == null) {
                    JavdbScrapeResult scrapeResult = apiClient.scrapeByUrl(item.getUrl());
                    if (scrapeResult == null || scrapeResult.getMovie() == null
                            || StringUtils.isBlank(scrapeResult.getMovie().getCode())) {
                        log.warn("javdb 首页详情解析失败，跳过 code={}", item.getCode());
                        continue;
                    }
                    saveResult(scrapeResult);
                    movie = scrapeResult.getMovie();
                    source = "JAVDB";
                }
                result.add(toVo(movie, resolveLocalCover(movie, item.getCover()), source));
            } catch (Exception e) {
                log.error("javdb 首页同步失败, code={}, url={}", item.getCode(), item.getUrl(), e);
            }
        }
        return result;
    }

    /** 第一部影片已入库时，整页直接按番号批量读库展示，不再逐部检查/刮削。 */
    private List<JavdbMovieVo> moviesFromDb(List<JavdbVideoItem> items) {
        List<JavdbMovieVo> result = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (JavdbVideoItem item : items) {
            if (item != null && StringUtils.isNotBlank(item.getCode())) {
                codes.add(item.getCode());
            }
        }
        Map<String, JavdbMovie> moviesByCode = new HashMap<>();
        for (JavdbMovie movie : javdbMapper.findByCodes(codes)) {
            if (movie != null && StringUtils.isNotBlank(movie.getCode())) {
                moviesByCode.put(movie.getCode(), movie);
            }
        }
        for (JavdbVideoItem item : items) {
            if (item == null || StringUtils.isBlank(item.getCode())) {
                continue;
            }
            JavdbMovie movie = moviesByCode.get(item.getCode());
            if (movie == null) {
                log.warn("javdb 首页读库时缺少影片, code={}", item.getCode());
                continue;
            }
            String cover = StringUtils.defaultIfBlank(movie.getCoverLocal(), movie.getCoverUrl());
            result.add(toVo(movie, cover, "DB"));
        }
        return result;
    }

    /**
     * 封面优先用本地：数据库已有本地地址且文件存在时直接返回，
     * 否则从远程下载到本地并回写 cover_local。
     */
    private String resolveLocalCover(JavdbMovie movie, String remoteCover) {
        if (StringUtils.isNotBlank(movie.getCoverLocal())) {
            String fileName = ImageDownloadService.extractFileName(movie.getCoverLocal());
            if (StringUtils.isNotBlank(fileName) && imageDownloadService.checkImageExists(fileName)) {
                return movie.getCoverLocal();
            }
        }
        String remote = StringUtils.defaultIfBlank(remoteCover, movie.getCoverUrl());
        if (StringUtils.isBlank(remote)) {
            return movie.getCoverLocal();
        }
        try {
            String fileName = ImageDownloadService.extractFileName(remote);
            String localUrl = imageDownloadService.getImageUrl(remote, fileName, apiClient.getBaseUrl() + "/");
            if (StringUtils.isNotBlank(localUrl)) {
                movie.setCoverLocal(localUrl);
                javdbMapper.updateCoverLocal(movie.getCode(), localUrl);
                return localUrl;
            }
        } catch (Exception e) {
            log.warn("javdb 封面下载失败, code={}, url={}", movie.getCode(), remote, e);
        }
        return remote;
    }

    private JavdbMovieVo toVo(JavdbMovie movie, String cover, String source) {
        JavdbMovieVo vo = new JavdbMovieVo();
        vo.setCode(movie.getCode());
        vo.setTitle(movie.getTitle());
        vo.setCoverUrl(cover);
        vo.setReleaseDate(movie.getReleaseDate());
        vo.setDuration(movie.getDuration());
        vo.setDirector(movie.getDirector());
        vo.setStudio(movie.getStudio());
        vo.setSeries(movie.getSeries());
        vo.setActors(movie.getActors());
        vo.setGenres(movie.getGenres());
        vo.setDetailUrl(movie.getDetailUrl());
        vo.setMagnetCount(javdbMapper.countMagnetsByCode(movie.getCode()));
        vo.setEmbyExists(movieIsExsitInSystem.isInEmby(movie.getCode()));
        vo.setSource(source);
        return vo;
    }

    /**
     * 按关键词搜索 javdb 影片列表（纯解析，不入库）。
     */
    public List<JavdbVideoItem> searchVideoItems(String keyword, int pages) {
        if (StringUtils.isBlank(keyword)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        try {
            return apiClient.searchMovies(keyword.trim(), pages);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("javdb 搜索失败, keyword={}", keyword, e);
            throw new RuntimeException("javdb 搜索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按页刮削 javdb 列表（默认第 1 页，即首页）。
     *
     * @param page       页码，默认 1
     * @param withDetail 是否进一步刮削每部影片详情并入库；false 时只返回列表
     * @return 每部影片的处理结果摘要
     */
    public List<Map<String, Object>> scrapeByPage(int page, boolean withDetail) {
        List<Map<String, Object>> summary = new ArrayList<>();
        try {
            List<JavdbVideoItem> items = apiClient.pageMovies(page);
            log.info("javdb 第 {} 页抓取到 {} 部影片", page, items.size());
            for (JavdbVideoItem item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", item.getCode());
                row.put("title", item.getTitle());
                row.put("url", item.getUrl());
                row.put("cover", item.getCover());
                if (!withDetail) {
                    row.put("status", "LIST");
                    summary.add(row);
                    continue;
                }
                try {
                    JavdbScrapeResult result = apiClient.scrapeByUrl(item.getUrl());
                    if (result.getMovie() == null) {
                        row.put("status", "FAILED");
                        row.put("message", "详情页解析失败");
                    } else {
                        JavdbMovie before = javdbMapper.findByCode(result.getMovie().getCode());
                        saveResult(result);
                        row.put("status", before == null ? "INSERTED" : "UPDATED");
                        row.put("magnetCount",
                                result.getMagnets() == null ? 0 : result.getMagnets().size());
                    }
                } catch (Exception e) {
                    log.error("javdb 刮削失败, url={}", item.getUrl(), e);
                    row.put("status", "FAILED");
                    row.put("message", e.getMessage());
                }
                summary.add(row);
            }
        } catch (Exception e) {
            log.error("javdb 第 {} 页抓取失败", page, e);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", "FAILED");
            row.put("message", e.getMessage());
            summary.add(row);
        }
        return summary;
    }
}
