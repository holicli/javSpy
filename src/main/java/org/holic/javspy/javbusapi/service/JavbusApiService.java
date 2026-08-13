package org.holic.javspy.javbusapi.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.holic.javspy.javbusapi.client.JavbusApiClient;
import org.holic.javspy.javbusapi.mapper.JavbusApiMapper;
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;
import org.holic.javspy.javbusapi.model.JavbusApiMovie;
import org.holic.javspy.javbusapi.model.JavbusApiMovieSample;
import org.holic.javspy.javbusapi.model.JavbusApiScrapeResult;
import org.holic.javspy.javbusapi.model.JavbusApiStar;
import org.holic.javspy.javbusapi.model.JavbusApiVideoItem;
import org.holic.javspy.javbusapi.model.JavbusFollowActor;
import org.holic.javspy.misc.ImageDownloadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * javbus API 刮削业务：调用 JSON 接口，按规范化表结构入库并提供展示查询。
 */
@Slf4j
@Service
public class JavbusApiService {

    private final JavbusApiClient apiClient;
    private final JavbusApiMapper javbusApiMapper;
    private final ImageDownloadService imageDownloadService;

    public JavbusApiService(JavbusApiClient apiClient, JavbusApiMapper javbusApiMapper,
                            ImageDownloadService imageDownloadService) {
        this.apiClient = apiClient;
        this.javbusApiMapper = javbusApiMapper;
        this.imageDownloadService = imageDownloadService;
    }

    /**
     * 按番号抓取详情 + 磁力并入库。
     */
    public JavbusApiScrapeResult scrapeByCode(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("番号不能为空");
        }
        try {
            JavbusApiScrapeResult result = apiClient.scrapeMovie(code.trim());
            if (result.getMovie() == null) {
                log.warn("javbus-api 未找到番号: {}", code);
                return result;
            }
            saveResult(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("javbus-api 抓取失败, code={}", code, e);
            throw new RuntimeException("javbus-api 抓取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按关键词搜索并逐部抓取入库。
     */
    public List<Map<String, Object>> scrapeByKeyword(String keyword, int pages, String magnet) {
        if (StringUtils.isBlank(keyword)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        List<Map<String, Object>> summary = new ArrayList<>();
        try {
            for (int page = 1; page <= Math.max(1, pages); page++) {
                List<JavbusApiVideoItem> items = apiClient.searchMovies(keyword.trim(), page, magnet, null);
                if (items.isEmpty()) {
                    break;
                }
                for (JavbusApiVideoItem item : items) {
                    summary.add(scrapeOne(item));
                }
            }
            log.info("javbus-api 搜索抓取完成, keyword={}, total={}", keyword, summary.size());
        } catch (Exception e) {
            log.error("javbus-api 搜索失败, keyword={}", keyword, e);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", "FAILED");
            row.put("message", e.getMessage());
            summary.add(row);
        }
        return summary;
    }

    /**
     * 按页抓取列表并逐部入库。
     */
    public List<Map<String, Object>> scrapeByPage(int page, String magnet, boolean withDetail) {
        List<Map<String, Object>> summary = new ArrayList<>();
        try {
            List<JavbusApiVideoItem> items = apiClient.listMovies(page, magnet, null, null, null);
            log.info("javbus-api 第 {} 页获取到 {} 部影片", page, items.size());
            // 优化：本页第一部影片已入库 -> 整页直接读库展示，不再逐部调 API
            if (!items.isEmpty()
                    && StringUtils.isNotBlank(items.get(0).getCode())
                    && javbusApiMapper.findByCode(items.get(0).getCode()) != null) {
                log.info("javbus-api 第 {} 页第一部影片已入库，整页直接读库展示, code={}",
                        page, items.get(0).getCode());
                List<String> codes = new ArrayList<>();
                for (JavbusApiVideoItem item : items) {
                    if (item != null && StringUtils.isNotBlank(item.getCode())) {
                        codes.add(item.getCode());
                    }
                }
                Map<String, JavbusApiMovie> byCode = new java.util.HashMap<>();
                for (JavbusApiMovie movie : javbusApiMapper.findByCodes(codes)) {
                    if (movie != null && StringUtils.isNotBlank(movie.getCode())) {
                        byCode.put(movie.getCode(), movie);
                    }
                }
                for (JavbusApiVideoItem item : items) {
                    if (item == null || StringUtils.isBlank(item.getCode())) {
                        continue;
                    }
                    JavbusApiMovie movie = byCode.get(item.getCode());

                    if (movie == null) {
                        log.warn("javbus-api 读库时缺少影片, code={}", item.getCode());
                        continue;
                    }
                    // 封面本地缺失时先下载（cover_url 优先，其次 cover_hd）
                    ensureCoverLocal(movie);
                    Map<String, Object> row = toDisplayRow(movie);
                    row.put("status", "DB");
                    summary.add(row);
                }
                return summary;
            }
            for (JavbusApiVideoItem item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", item.getCode());
                row.put("title", item.getTitle());
                row.put("cover", item.getCover());
                row.put("date", item.getDate());
                if (withDetail) {
                    row.put("status", "LIST");
                    summary.add(row);
                    continue;
                }
                summary.add(scrapeOne(item));
            }
        } catch (Exception e) {
            log.error("javbus-api 第 {} 页获取失败", page, e);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", "FAILED");
            row.put("message", e.getMessage());
            summary.add(row);
        }
        return summary;
    }

    /** 抓取单部影片详情+磁力并入库，返回结果行。 */
    private Map<String, Object> scrapeOne(JavbusApiVideoItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", item.getCode());
        row.put("title", item.getTitle());

        try {
            JavbusApiScrapeResult result = apiClient.scrapeMovie(item.getCode());
            if (result.getMovie() == null) {
                row.put("status", "FAILED");
                row.put("message", "详情获取失败");
            } else {
                result.getMovie().setCoverUrl(item.getCover());
                saveResult(result);
                row.put("movie", result.getMovie());
                row.put("actors", result.getMovie().getActors());
                row.put("duration", result.getMovie().getDuration());
                row.put("genres", result.getMovie().getGenres());
                row.put("director", result.getMovie().getDirector());
                row.put("studio", result.getMovie().getStudio());
                row.put("series", result.getMovie().getSeries());
                row.put("start", result.getMovie().getStars());
                row.put("coverUrl", result.getMovie().getCoverLocal() != null
                        ? result.getMovie().getCoverLocal()
                        : result.getMovie().getCoverUrl());
                row.put("HDUrl", result.getMovie().getCoverHd());
                row.put("releaseDate", result.getMovie().getReleaseDate());
                row.put("status", "INSERTED");
                row.put("magnetCount",
                        result.getMagnets() == null ? 0 : result.getMagnets().size());
            }
        } catch (Exception e) {
            log.error("javbus-api 抓取失败, code={}", item.getCode(), e);
            row.put("status", "FAILED");
            row.put("message", e.getMessage());
        }
        return row;
    }

    /**
     * 分页查询已入库的影片（展示用：带演员/类别/导演/片商名称 + 磁力数量）。
     */
    public PageInfo<Map<String, Object>> searchMovies(String code, String keyword,
                                                      String releaseDate, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        PageHelper.startPage(safePage, safeSize);
        List<JavbusApiMovie> list = javbusApiMapper.searchMovies(
                StringUtils.trimToNull(code),
                StringUtils.trimToNull(keyword),
                StringUtils.trimToNull(releaseDate));
        PageInfo<JavbusApiMovie> pageInfo = new PageInfo<>(list);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (JavbusApiMovie movie : list) {
            rows.add(toDisplayRow(movie));
        }
        PageInfo<Map<String, Object>> result = new PageInfo<>(rows);
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());
        result.setPages(pageInfo.getPages());
        return result;
    }

    /** 影片 -> 展示行：join 出导演/制作商/发行商/系列名称 + 演员/类别 + 磁力数量。 */
    private Map<String, Object> toDisplayRow(JavbusApiMovie movie) {
        List<JavbusApiStar> starList = javbusApiMapper.findStarsByCode(movie.getCode());
        String stars = starList.stream()
                .map(JavbusApiStar::getName)
                .collect(Collectors.joining(","));

        List<String> genreByCode = javbusApiMapper.findGenreByCode(movie.getCode());
        String genres = String.join(",", genreByCode);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", movie.getCode());
        row.put("title", movie.getTitle());
        row.put("coverUrl", movie.getCoverLocal());
        row.put("coverHd", movie.getCoverHd());
        row.put("releaseDate", movie.getReleaseDate());
        row.put("duration", movie.getDuration());
        row.put("director", movie.getDirector());
        row.put("studio", movie.getStudio());
        row.put("publisher", movie.getPublisher());
        row.put("series", movie.getSeries());
        row.put("genres",genres);
        row.put("actors", stars);
        row.put("gid", movie.getGid());
        row.put("uc", movie.getUc());
        row.put("magnetCount", javbusApiMapper.countMagnetsByCode(movie.getCode()));
        return row;
    }

    /**
     * 保存抓取结果：实体表 -> 影片 -> 磁力 -> 关联表，同事务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveResult(JavbusApiScrapeResult result) {
        if (result == null || result.getMovie() == null) {
            return;
        }
        JavbusApiMovie movie = result.getMovie();
        if (StringUtils.isBlank(movie.getCode())) {
            throw new IllegalArgumentException("影片缺少番号，无法入库");
        }
        movie.setCode(movie.getCode().trim().toUpperCase());

        // 1. 实体表
        if (StringUtils.isNotBlank(movie.getDirectorId()) && StringUtils.isNotBlank(movie.getDirector())) {
            javbusApiMapper.upsertDirector(movie.getDirectorId(), movie.getDirector());
        }
        if (StringUtils.isNotBlank(movie.getStudioId()) && StringUtils.isNotBlank(movie.getStudio())) {
            javbusApiMapper.upsertStudio(movie.getStudioId(), movie.getStudio());
        }
        if (StringUtils.isNotBlank(movie.getPublisherId()) && StringUtils.isNotBlank(movie.getPublisher())) {
            javbusApiMapper.upsertPublisher(movie.getPublisherId(), movie.getPublisher());
        }
        if (StringUtils.isNotBlank(movie.getSeriesId()) && StringUtils.isNotBlank(movie.getSeries())) {
            javbusApiMapper.upsertSeries(movie.getSeriesId(), movie.getSeries());
        }
        if (movie.getStars() != null && !movie.getStars().isEmpty()) {
            javbusApiMapper.upsertStars(movie.getStars());
        }
        if (movie.getGenresList() != null && !movie.getGenresList().isEmpty()) {
            for (JavbusApiStar genre : movie.getGenresList()) {
                javbusApiMapper.upsertGenre(genre.getId(), genre.getName());
            }
        }

        // 2. 影片（拿到自增 id）
        Date now = new Date();
        movie.setCreatedAt(now);
        movie.setUpdatedAt(now);
        javbusApiMapper.insertMovie(movie);

        // 2.1 下载封面到本地并回写 cover_local（带 javbus Referer 绕过防盗链）
        downloadCoverToLocal(movie);

        // 3. 磁力（回填 movie_id）
        if (result.getMagnets() != null && !result.getMagnets().isEmpty()) {
            for (JavbusApiMagnet magnet : result.getMagnets()) {
                magnet.setMovieId(movie.getId());
                magnet.setCode(movie.getCode());
            }
            javbusApiMapper.insertMagnets(result.getMagnets());
        }

        // 4. 关联表
        if (movie.getId() != null) {
            if (movie.getStars() != null && !movie.getStars().isEmpty()) {
                javbusApiMapper.insertMovieStars(movie.getId(), movie.getStars());
            }
            if (movie.getGenresList() != null && !movie.getGenresList().isEmpty()) {
                javbusApiMapper.insertMovieGenres(movie.getId(), movie.getGenresList());
            }
            if (movie.getSamples() != null && !movie.getSamples().isEmpty()) {
                javbusApiMapper.insertMovieSamples(movie.getId(), movie.getSamples());
            }
            if (movie.getSimilarMovies() != null && !movie.getSimilarMovies().isEmpty()) {
                javbusApiMapper.insertMovieSimilars(movie.getId(), movie.getSimilarMovies());
            }
        }
    }

    /** 连通性自检。 */
    public String ping() {
        return apiClient.ping();
    }

    /** 下载封面到本地：优先 cover_url（列表缩略图），其次 cover_hd（详情大图）。 */
    private void downloadCoverToLocal(JavbusApiMovie movie) {
        String remote = StringUtils.defaultIfBlank(movie.getCoverUrl(), movie.getCoverHd());
        if (StringUtils.isBlank(remote)) {
            return;
        }
        try {
            String fileName = ImageDownloadService.extractFileName(remote);
            String localUrl = imageDownloadService.getImageUrl(
                    remote, fileName, "https://www.javbus.com/");
            if (StringUtils.isNotBlank(localUrl)) {
                movie.setCoverLocal(localUrl);
                javbusApiMapper.updateCoverLocal(movie.getCode(), localUrl);
                log.info("javbus-api 封面已下载到本地, code={}, local={}", movie.getCode(), localUrl);
            }
        } catch (Exception e) {
            log.warn("javbus-api 封面下载失败, code={}, url={}", movie.getCode(), remote, e);
        }
    }

    /** 检查本地封面是否存在；缺失则下载。 */
    private void ensureCoverLocal(JavbusApiMovie movie) {
        if (movie == null || StringUtils.isBlank(movie.getCode())) {
            return;
        }
        if (StringUtils.isNotBlank(movie.getCoverLocal())) {
            String fileName = ImageDownloadService.extractFileName(movie.getCoverLocal());
            if (StringUtils.isNotBlank(fileName) && imageDownloadService.checkImageExists(fileName)) {
                return;
            }
        }
        downloadCoverToLocal(movie);
    }

    /**
     * 影片详情展示：基本信息 + 演员 + 预览图。
     */
    public Map<String, Object> movieDetail(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("番号不能为空");
        }
        String c = code.trim().toUpperCase();
        JavbusApiMovie movie = javbusApiMapper.findByCode(c);
        Map<String, Object> detail = new LinkedHashMap<>();
        if (movie == null) {
            detail.put("code", c);
            detail.put("found", false);
            return detail;
        }
        detail.put("found", true);
        detail.put("movie", toDisplayRow(movie));
        detail.put("stars", javbusApiMapper.findStarsByCode(c));
        detail.put("samples", javbusApiMapper.findSamplesByCode(c));
        return detail;
    }

    /**
     * 演员详情：先查本地表，查不到时尝试从 javbus API 拉取并入库。
     */
    public Map<String, Object> starDetail(String starId, String type) {
        if (StringUtils.isBlank(starId)) {
            throw new IllegalArgumentException("演员 ID 不能为空");
        }
        JavbusApiStar star = javbusApiMapper.findStarById(starId.trim());
        if (star == null) {
            try {
                com.alibaba.fastjson.JSONObject remote = apiClient.getStar(starId.trim(), type);
                if (remote != null) {
                    star = new JavbusApiStar();
                    star.setId(remote.getString("id"));
                    star.setName(remote.getString("name"));
                    star.setAvatar(remote.getString("avatar"));
                    star.setBirthday(remote.getString("birthday"));
                    star.setAge(remote.getString("age"));
                    star.setHeight(remote.getString("height"));
                    star.setBust(remote.getString("bust"));
                    star.setWaistline(remote.getString("waistline"));
                    star.setHipline(remote.getString("hipline"));
                    star.setBirthplace(remote.getString("birthplace"));
                    star.setHobby(remote.getString("hobby"));
                    if (StringUtils.isNotBlank(star.getId()) && StringUtils.isNotBlank(star.getName())) {
                        javbusApiMapper.upsertStar(star);
                        star = javbusApiMapper.findStarById(star.getId());
                    }
                }
            } catch (Exception e) {
                log.warn("javbus-api 演员详情拉取失败, starId={}", starId, e);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("star", star);
        result.put("found", star != null);
        return result;
    }

    /**
     * 查询全部关注演员。
     */
    public List<JavbusFollowActor> listFollowActors() {
        return javbusApiMapper.listFollowActors();
    }

    /**
     * 新增关注演员（重名忽略）。
     */
    public boolean addFollowActor(String actorName, String remark) {
        if (StringUtils.isBlank(actorName)) {
            throw new IllegalArgumentException("演员名称不能为空");
        }
        int rows = javbusApiMapper.insertFollowActor(
                actorName.trim(), StringUtils.trimToNull(remark));
        return rows > 0;
    }

    /**
     * 删除关注演员。
     */
    public boolean removeFollowActor(String actorName) {
        if (StringUtils.isBlank(actorName)) {
            throw new IllegalArgumentException("演员名称不能为空");
        }
        int rows = javbusApiMapper.deleteFollowActor(actorName.trim());
        return rows > 0;
    }

    /**
     * 查询某部影片的全部磁力链接（含相关信息）。
     */
    public List<JavbusApiMagnet> magnetsByCode(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("番号不能为空");
        }
        return javbusApiMapper.findMagnetsByCode(code.trim().toUpperCase());
    }

    /**
     * 保存磁力链接到单独表 javbus_magnet_save（code + magnet + 插入日期）。
     */
    public boolean saveMagnet(String code, String magnet) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("番号不能为空");
        }
        if (StringUtils.isBlank(magnet)) {
            throw new IllegalArgumentException("磁力链接不能为空");
        }
        int rows = javbusApiMapper.insertMagnetSave(
                code.trim().toUpperCase(), magnet.trim());
        return rows > 0;
    }
}
