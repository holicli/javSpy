package org.holic.javspy.javbusapi.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.holic.javspy.javbusapi.client.JavbusApiClient;
import org.holic.javspy.javbusapi.mapper.JavbusApiDirectorMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiGenreMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiMagnetMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiMovieMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiMovieSampleMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiPublisherMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiSeriesMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiSimilarMovieMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiStarMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiStudioMapper;
import org.holic.javspy.javbusapi.mapper.JavbusFollowActorMapper;
import org.holic.javspy.javbusapi.model.JavbusApiGenreName;
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;
import org.holic.javspy.javbusapi.model.JavbusApiMagnetCount;
import org.holic.javspy.javbusapi.model.JavbusApiMovie;
import org.holic.javspy.javbusapi.model.JavbusApiMovieDetail;
import org.holic.javspy.javbusapi.model.JavbusApiMovieDisplay;
import org.holic.javspy.javbusapi.model.JavbusApiMovieSample;
import org.holic.javspy.javbusapi.model.JavbusApiScrapeItem;
import org.holic.javspy.javbusapi.model.JavbusApiScrapeResult;
import org.holic.javspy.javbusapi.model.JavbusApiScrapeStatus;
import org.holic.javspy.javbusapi.model.JavbusApiStar;
import org.holic.javspy.javbusapi.model.JavbusApiStarDetail;
import org.holic.javspy.javbusapi.model.JavbusApiStarName;
import org.holic.javspy.javbusapi.model.JavbusApiVideoItem;
import org.holic.javspy.javbusapi.model.JavbusFollowActor;
import org.holic.javspy.misc.EmbyMovieService;
import org.holic.javspy.misc.ImageDownloadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * javbus API 刮削业务：调用 JSON 接口，按规范化表结构入库并提供展示查询。
 */
@Slf4j
@Service
public class JavbusApiService {

    private final JavbusApiClient apiClient;
    private final JavbusApiMovieMapper movieMapper;
    private final JavbusApiMagnetMapper magnetMapper;
    private final JavbusApiStarMapper starMapper;
    private final JavbusApiSimilarMovieMapper similarMovieMapper;
    private final JavbusApiMovieSampleMapper movieSampleMapper;
    private final JavbusFollowActorMapper followActorMapper;
    private final JavbusApiDirectorMapper directorMapper;
    private final JavbusApiStudioMapper studioMapper;
    private final JavbusApiPublisherMapper publisherMapper;
    private final JavbusApiSeriesMapper seriesMapper;
    private final JavbusApiGenreMapper genreMapper;
    private final ImageDownloadService imageDownloadService;
    private final EmbyMovieService embyMovieService;

    /** 后台一键刮削任务线程池（单线程串行执行）。 */
    private final ExecutorService scrapeExecutor = Executors.newSingleThreadExecutor();

    private volatile boolean scraping = false;
    private volatile int scrapePage = 0;
    private volatile int scrapeCount = 0;
    private volatile String scrapeMessage = "未开始";
    private volatile String scrapeStopReason = null;
    private volatile String scrapeStopCode = null;

    public JavbusApiService(JavbusApiClient apiClient,
                            JavbusApiMovieMapper movieMapper,
                            JavbusApiMagnetMapper magnetMapper,
                            JavbusApiStarMapper starMapper,
                            JavbusApiSimilarMovieMapper similarMovieMapper,
                            JavbusApiMovieSampleMapper movieSampleMapper,
                            JavbusFollowActorMapper followActorMapper,
                            JavbusApiDirectorMapper directorMapper,
                            JavbusApiStudioMapper studioMapper,
                            JavbusApiPublisherMapper publisherMapper,
                            JavbusApiSeriesMapper seriesMapper,
                            JavbusApiGenreMapper genreMapper,
                            ImageDownloadService imageDownloadService,
                            EmbyMovieService embyMovieService) {
        this.apiClient = apiClient;
        this.movieMapper = movieMapper;
        this.magnetMapper = magnetMapper;
        this.starMapper = starMapper;
        this.similarMovieMapper = similarMovieMapper;
        this.movieSampleMapper = movieSampleMapper;
        this.followActorMapper = followActorMapper;
        this.directorMapper = directorMapper;
        this.studioMapper = studioMapper;
        this.publisherMapper = publisherMapper;
        this.seriesMapper = seriesMapper;
        this.genreMapper = genreMapper;
        this.imageDownloadService = imageDownloadService;
        this.embyMovieService = embyMovieService;
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
    public List<JavbusApiScrapeItem> scrapeByKeyword(String keyword, int pages, String magnet) {
        if (StringUtils.isBlank(keyword)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        List<JavbusApiScrapeItem> summary = new ArrayList<>();
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
            JavbusApiScrapeItem row = new JavbusApiScrapeItem();
            row.setStatus("FAILED");
            row.setMessage(e.getMessage());
            summary.add(row);
        }
        return summary;
    }

    /**
     * javbus API 关键字搜索并逐部完整入库（详情 + 磁力 + 封面下载），
     * 返回每部的入库结果行。
     */
    public List<JavbusApiScrapeItem> searchFromApi(String keyword, int page, String magnet) {
        if (StringUtils.isBlank(keyword)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        List<JavbusApiScrapeItem> summary = new ArrayList<>();
        try {
            List<JavbusApiVideoItem> items = apiClient.searchMovies(keyword.trim(), Math.max(1, page), magnet, null);
            for (JavbusApiVideoItem item : items) {
                summary.add(scrapeOne(item));
            }
            log.info("javbus-api 搜索并入库完成, keyword={}, page={}, total={}", keyword, page, summary.size());
        } catch (Exception e) {
            log.error("javbus-api 搜索失败, keyword={}", keyword, e);
            throw new RuntimeException("javbus-api 搜索失败: " + e.getMessage(), e);
        }
        return summary;
    }

    /**
     * 按页抓取列表并逐部入库。
     */
    public List<JavbusApiScrapeItem> scrapeByPage(int page, String magnet, boolean withDetail) {
        List<JavbusApiScrapeItem> summary = new ArrayList<>();
        try {
            List<JavbusApiVideoItem> items = apiClient.listMovies(page, magnet, null, null, null);
            log.info("javbus-api 第 {} 页获取到 {} 部影片", page, items.size());
            // 优化：本页第一部影片已入库 -> 整页直接读库展示，不再逐部调 API
            if (!items.isEmpty()
                    && StringUtils.isNotBlank(items.get(0).getCode())
                    && movieMapper.findByCode(items.get(0).getCode()) != null) {
                log.info("javbus-api 第 {} 页第一部影片已入库，整页直接读库展示, code={}",
                        page, items.get(0).getCode());
                List<String> codes = new ArrayList<>();
                for (JavbusApiVideoItem item : items) {
                    if (item != null && StringUtils.isNotBlank(item.getCode())) {
                        codes.add(item.getCode());
                    }
                }
                Map<String, JavbusApiMovie> byCode = new java.util.HashMap<>();
                for (JavbusApiMovie movie : movieMapper.findByCodes(codes)) {
                    if (movie != null && StringUtils.isNotBlank(movie.getCode())) {
                        byCode.put(movie.getCode(), movie);
                    }
                }
                Set<String> embyCodes = embyMovieService.getCodes();
                Map<String, MovieDisplayData> displayByCode = loadDisplayData(codes);
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
                    JavbusApiScrapeItem row = JavbusApiScrapeItem.fromDisplay(
                            toDisplayRow(movie, embyCodes, displayByCode));
                    row.setStatus("DB");
                    summary.add(row);
                }
                return summary;
            }
            for (JavbusApiVideoItem item : items) {
                JavbusApiScrapeItem row = new JavbusApiScrapeItem();
                row.setCode(item.getCode());
                row.setTitle(item.getTitle());
                row.setCover(item.getCover());
                row.setDate(item.getDate());
                if (withDetail) {
                    row.setStatus("LIST");
                    summary.add(row);
                    continue;
                }
                summary.add(scrapeOne(item));
            }
        } catch (Exception e) {
            log.error("javbus-api 第 {} 页获取失败", page, e);
            JavbusApiScrapeItem row = new JavbusApiScrapeItem();
            row.setStatus("FAILED");
            row.setMessage(e.getMessage());
            summary.add(row);
        }
        return summary;
    }

    /** 抓取单部影片详情+磁力并入库，返回结果行。 */
    private JavbusApiScrapeItem scrapeOne(JavbusApiVideoItem item) {
        JavbusApiScrapeItem row = new JavbusApiScrapeItem();
        row.setCode(item.getCode());
        row.setTitle(item.getTitle());

        try {
            JavbusApiScrapeResult result = apiClient.scrapeMovie(item.getCode());
            if (result.getMovie() == null) {
                row.setStatus("FAILED");
                row.setMessage("详情获取失败");
            } else {
                result.getMovie().setCoverUrl(item.getCover());
                saveResult(result);
                row.setMovie(result.getMovie());
                row.setActors(result.getMovie().getActors());
                row.setDuration(result.getMovie().getDuration());
                row.setGenres(result.getMovie().getGenres());
                row.setDirector(result.getMovie().getDirector());
                row.setStudio(result.getMovie().getStudio());
                row.setSeries(result.getMovie().getSeries());
                row.setStart(result.getMovie().getStars());
                row.setCoverUrl(ImageDownloadService.normalizeAccessUrl(
                        result.getMovie().getCoverLocal() != null
                                ? result.getMovie().getCoverLocal()
                                : result.getMovie().getCoverUrl()));
                row.setHDUrl(result.getMovie().getCoverHd());
                row.setReleaseDate(result.getMovie().getReleaseDate());
                row.setStatus("INSERTED");
                row.setMagnetCount(result.getMagnets() == null ? 0 : result.getMagnets().size());
            }
        } catch (Exception e) {
            log.error("javbus-api 抓取失败, code={}", item.getCode(), e);
            row.setStatus("FAILED");
            row.setMessage(e.getMessage());
        }
        return row;
    }

    /**
     * 分页查询已入库的影片（展示用：带演员/类别/导演/片商名称 + 磁力数量 + Emby 状态）。
     */
    public PageInfo<JavbusApiMovieDisplay> searchMovies(String code, String keyword,
                                                        String releaseDate, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        PageHelper.startPage(safePage, safeSize);
        List<JavbusApiMovie> list = movieMapper.searchMovies(
                StringUtils.trimToNull(code),
                StringUtils.trimToNull(keyword),
                StringUtils.trimToNull(releaseDate));
        PageInfo<JavbusApiMovie> pageInfo = new PageInfo<>(list);

        List<String> codes = list.stream()
                .map(JavbusApiMovie::getCode)
                .collect(Collectors.toList());
        Set<String> embyCodes = embyMovieService.getCodes();
        Map<String, MovieDisplayData> displayByCode = loadDisplayData(codes);
        List<JavbusApiMovieDisplay> rows = new ArrayList<>();
        for (JavbusApiMovie movie : list) {
            ensureCoverLocal(movie);
            rows.add(toDisplayRow(movie, embyCodes, displayByCode));
        }
        PageInfo<JavbusApiMovieDisplay> result = new PageInfo<>(rows);
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());
        result.setPages(pageInfo.getPages());
        return result;
    }

    /**
     * 分页查询最新入库的影片（按 created_at 倒序，每页最多 30 部）。
     */
    public PageInfo<JavbusApiMovieDisplay> newestMovies(int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(Math.max(1, pageSize), 30);
        PageHelper.startPage(safePage, safeSize);
        List<JavbusApiMovie> list = movieMapper.searchNewest();
        PageInfo<JavbusApiMovie> pageInfo = new PageInfo<>(list);

        List<String> codes = list.stream()
                .map(JavbusApiMovie::getCode)
                .collect(Collectors.toList());
        Set<String> embyCodes = embyMovieService.getCodes();
        Map<String, MovieDisplayData> displayByCode = loadDisplayData(codes);
        List<JavbusApiMovieDisplay> rows = new ArrayList<>();
        for (JavbusApiMovie movie : list) {
            ensureCoverLocal(movie);
            rows.add(toDisplayRow(movie, embyCodes, displayByCode));
        }
        PageInfo<JavbusApiMovieDisplay> result = new PageInfo<>(rows);
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());
        result.setPages(pageInfo.getPages());
        return result;
    }

    /** 影片 -> 展示行：单部影片查询演员/类别/磁力数量。 */
    private JavbusApiMovieDisplay toDisplayRow(JavbusApiMovie movie, Set<String> embyCodes) {
        String code = movie.getCode();
        String stars = starMapper.findStarsByCode(code).stream()
                .map(JavbusApiStar::getName)
                .collect(Collectors.joining(","));
        String genres = String.join(",", genreMapper.findByMovieCode(code));
        int magnetCount = magnetMapper.countByCode(code);
        return buildDisplayRow(movie, embyCodes, stars, genres, magnetCount);
    }

    /** 影片 -> 展示行：使用整页预查询结果，不在循环内执行 SQL。 */
    private JavbusApiMovieDisplay toDisplayRow(JavbusApiMovie movie, Set<String> embyCodes,
                                               Map<String, MovieDisplayData> displayByCode) {
        MovieDisplayData data = displayByCode.get(movie.getCode());
        String stars = data == null ? "" : data.stars;
        String genres = data == null ? "" : data.genres;
        int magnetCount = data == null ? 0 : data.magnetCount;
        return buildDisplayRow(movie, embyCodes, stars, genres, magnetCount);
    }

    private JavbusApiMovieDisplay buildDisplayRow(JavbusApiMovie movie, Set<String> embyCodes,
                                                  String stars, String genres, int magnetCount) {
        JavbusApiMovieDisplay display = new JavbusApiMovieDisplay();
        display.setCode(movie.getCode());
        display.setTitle(movie.getTitle());
        display.setCoverUrl(ImageDownloadService.normalizeAccessUrl(movie.getCoverLocal()));
        display.setCoverHd(movie.getCoverHd());
        display.setReleaseDate(movie.getReleaseDate());
        display.setDuration(movie.getDuration());
        display.setDirector(movie.getDirector());
        display.setStudio(movie.getStudio());
        display.setPublisher(movie.getPublisher());
        display.setSeries(movie.getSeries());
        display.setGenres(genres);
        display.setActors(stars);
        display.setGid(movie.getGid());
        display.setUc(movie.getUc());
        display.setMagnetCount(magnetCount);
        display.setEmbyExists(embyCodes != null
                && embyCodes.contains(movie.getCode().trim().toUpperCase()));
        return display;
    }

    /** 整页预查询展示数据：演员/类别/磁力数量一次查齐，按番号聚合。 */
    private Map<String, MovieDisplayData> loadDisplayData(List<String> codes) {
        Map<String, MovieDisplayData> result = new HashMap<>();
        if (codes == null || codes.isEmpty()) {
            return result;
        }
        for (JavbusApiStarName row : starMapper.findStarsByCodes(codes)) {
            MovieDisplayData data = result.computeIfAbsent(row.getCode(), k -> new MovieDisplayData());
            if (StringUtils.isNotBlank(row.getName())) {
                data.stars = data.stars.isEmpty() ? row.getName() : data.stars + "," + row.getName();
            }
        }
        for (JavbusApiGenreName row : genreMapper.findByCodes(codes)) {
            MovieDisplayData data = result.computeIfAbsent(row.getCode(), k -> new MovieDisplayData());
            if (StringUtils.isNotBlank(row.getName())) {
                data.genres = data.genres.isEmpty() ? row.getName() : data.genres + "," + row.getName();
            }
        }
        for (JavbusApiMagnetCount row : magnetMapper.countByCodes(codes)) {
            MovieDisplayData data = result.computeIfAbsent(row.getCode(), k -> new MovieDisplayData());
            data.magnetCount = row.getCnt() == null ? 0 : row.getCnt().intValue();
        }
        return result;
    }

    /** 整页展示数据的聚合实体。 */
    private static class MovieDisplayData {
        private String stars = "";
        private String genres = "";
        private int magnetCount;
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
            directorMapper.upsert(movie.getDirectorId(), movie.getDirector());
        }
        if (StringUtils.isNotBlank(movie.getStudioId()) && StringUtils.isNotBlank(movie.getStudio())) {
            studioMapper.upsert(movie.getStudioId(), movie.getStudio());
        }
        if (StringUtils.isNotBlank(movie.getPublisherId()) && StringUtils.isNotBlank(movie.getPublisher())) {
            publisherMapper.upsert(movie.getPublisherId(), movie.getPublisher());
        }
        if (StringUtils.isNotBlank(movie.getSeriesId()) && StringUtils.isNotBlank(movie.getSeries())) {
            seriesMapper.upsert(movie.getSeriesId(), movie.getSeries());
        }
        if (movie.getStars() != null && !movie.getStars().isEmpty()) {
            starMapper.upsertBatch(movie.getStars());
        }
        if (movie.getGenresList() != null && !movie.getGenresList().isEmpty()) {
            for (JavbusApiStar genre : movie.getGenresList()) {
                genreMapper.upsert(genre.getId(), genre.getName());
            }
        }

        // 2. 影片（拿到自增 id）
        Date now = new Date();
        movie.setCreatedAt(now);
        movie.setUpdatedAt(now);
        movieMapper.insertMovie(movie);

        // 2.1 下载封面到本地并回写 cover_local（带 javbus Referer 绕过防盗链）
        downloadCoverToLocal(movie);

        // 3. 磁力（回填 movie_id）
        if (result.getMagnets() != null && !result.getMagnets().isEmpty()) {
            for (JavbusApiMagnet magnet : result.getMagnets()) {
                magnet.setMovieId(movie.getId());
                magnet.setCode(movie.getCode());
            }
            magnetMapper.insertBatch(result.getMagnets());
        }

        // 4. 关联表
        if (movie.getId() != null) {
            if (movie.getStars() != null && !movie.getStars().isEmpty()) {
                starMapper.insertMovieStars(movie.getId(), movie.getStars());
            }
            if (movie.getGenresList() != null && !movie.getGenresList().isEmpty()) {
                genreMapper.insertMovieGenres(movie.getId(), movie.getGenresList());
            }
            if (movie.getSamples() != null && !movie.getSamples().isEmpty()) {
                movieSampleMapper.insertBatch(movie.getId(), movie.getSamples());
            }
            if (movie.getSimilarMovies() != null && !movie.getSimilarMovies().isEmpty()) {
                similarMovieMapper.insertBatch(movie.getId(), movie.getSimilarMovies());
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
                movieMapper.updateCoverLocal(movie.getCode(), localUrl);
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
     * 影片详情展示：基本信息 + 演员 + 预览图 + Emby 状态。
     */
    public JavbusApiMovieDetail movieDetail(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("番号不能为空");
        }
        String c = code.trim().toUpperCase();
        JavbusApiMovie movie = movieMapper.findByCode(c);
        JavbusApiMovieDetail detail = new JavbusApiMovieDetail();
        detail.setCode(c);
        if (movie == null) {
            detail.setFound(false);
            return detail;
        }
        detail.setFound(true);
        detail.setMovie(toDisplayRow(movie, embyMovieService.getCodes()));
        detail.setStars(starMapper.findStarsByCode(c));
        detail.setSamples(movieSampleMapper.findByCode(c));
        return detail;
    }

    /**
     * 演员详情：先查本地表，查不到时尝试从 javbus API 拉取并入库。
     */
    public JavbusApiStarDetail starDetail(String starId, String type) {
        if (StringUtils.isBlank(starId)) {
            throw new IllegalArgumentException("演员 ID 不能为空");
        }
        JavbusApiStar star = starMapper.findById(starId.trim());
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
                        starMapper.upsert(star);
                        star = starMapper.findById(star.getId());
                    }
                }
            } catch (Exception e) {
                log.warn("javbus-api 演员详情拉取失败, starId={}", starId, e);
            }
        }
        JavbusApiStarDetail result = new JavbusApiStarDetail();
        result.setStar(star);
        result.setFound(star != null);
        return result;
    }

    /**
     * 查询全部关注演员。
     */
    public List<JavbusFollowActor> listFollowActors() {
        return followActorMapper.list();
    }

    /**
     * 新增关注演员（重名忽略）。
     */
    public boolean addFollowActor(String actorName, String remark) {
        if (StringUtils.isBlank(actorName)) {
            throw new IllegalArgumentException("演员名称不能为空");
        }
        int rows = followActorMapper.insertIgnore(
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
        int rows = followActorMapper.deleteByName(actorName.trim());
        return rows > 0;
    }

    /**
     * 查询某部影片的全部磁力链接（含相关信息）。
     */
    public List<JavbusApiMagnet> magnetsByCode(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("番号不能为空");
        }
        return magnetMapper.findByCode(code.trim().toUpperCase());
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
        int rows = magnetMapper.insertSave(
                code.trim().toUpperCase(), magnet.trim());
        return rows > 0;
    }

    /**
     * 启动后台一键刮削：按页持续抓取 javbus API 详情+磁力+封面入库，
     * 直到刮到的影片已存在于 Emby 或数据库中才停止。
     */
    public boolean startScrapeUntilEmby() {
        synchronized (this) {
            if (scraping) {
                return false;
            }
            scraping = true;
            scrapePage = 0;
            scrapeCount = 0;
            scrapeMessage = "正在启动...";
            scrapeStopReason = null;
            scrapeStopCode = null;
        }
        scrapeExecutor.submit(this::scrapeUntilEmbyLoop);
        return true;
    }

    /** 后台一键刮削任务状态。 */
    public JavbusApiScrapeStatus scrapeUntilEmbyStatus() {
        JavbusApiScrapeStatus status = new JavbusApiScrapeStatus();
        status.setRunning(scraping);
        status.setPage(scrapePage);
        status.setCount(scrapeCount);
        status.setMessage(scrapeMessage);
        status.setStopReason(scrapeStopReason);
        status.setStopCode(scrapeStopCode);
        return status;
    }

    /** 后台刮削循环：从第 1 页开始抓取，命中 Emby 或数据库已有影片时停止。 */
    private void scrapeUntilEmbyLoop() {
        final int maxPages = 500;
        try {
            for (int page = 1; page <= maxPages; page++) {
                scrapePage = page;
                scrapeMessage = "正在抓取第 " + page + " 页...";
                // 每页刷新一次 Emby 影片集合，确保用最新数据判断
                embyMovieService.refresh();
                List<JavbusApiVideoItem> items = apiClient.listMovies(page, "exist", null, null, null);
                if (items == null || items.isEmpty()) {
                    scrapeMessage = "第 " + page + " 页无数据，任务结束";
                    scrapeStopReason = "EMPTY";
                    return;
                }
                for (JavbusApiVideoItem item : items) {
                    if (item == null || StringUtils.isBlank(item.getCode())) {
                        continue;
                    }
                    String code = item.getCode().trim().toUpperCase();
                    // 停止条件：Emby 已有 或 数据库已有（本库已刮过），命中即停，避免重复入库
                    if (embyMovieService.exists(code)) {
                        scrapeMessage = "命中 Emby 已有影片 " + code + "，任务结束";
                        scrapeStopReason = "EMBY_MATCH";
                        scrapeStopCode = code;
                        return;
                    }
                    if (movieMapper.findByCode(code) != null) {
                        scrapeMessage = "命中数据库已有影片 " + code + "，任务结束";
                        scrapeStopReason = "DB_MATCH";
                        scrapeStopCode = code;
                        return;
                    }
                    scrapeMessage = "正在入库 " + code + " ...";
                    try {
                        JavbusApiScrapeResult result = apiClient.scrapeMovie(code);
                        if (result != null && result.getMovie() != null) {
                            result.getMovie().setCoverUrl(item.getCover());
                            saveResult(result);
                            scrapeCount++;
                        }
                    } catch (Exception e) {
                        log.warn("后台刮削单部失败, code={}", code, e);
                        scrapeMessage = code + " 抓取失败：" + e.getMessage();
                    }
                }
            }
            scrapeMessage = "已抓取 " + maxPages + " 页仍未命中 Emby/数据库，任务结束";
            scrapeStopReason = "MAX_PAGES";
        } catch (Exception e) {
            log.error("后台刮削任务失败", e);
            scrapeMessage = "任务异常：" + e.getMessage();
            scrapeStopReason = "ERROR";
        } finally {
            scraping = false;
        }
    }
}
