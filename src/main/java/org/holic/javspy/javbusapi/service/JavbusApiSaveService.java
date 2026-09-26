package org.holic.javspy.javbusapi.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;
import org.holic.javspy.javbusapi.model.JavbusApiMovie;
import org.holic.javspy.javbusapi.model.JavbusApiScrapeResult;
import org.holic.javspy.misc.ImageDownloadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * javbus 抓取结果落库服务。
 * <p>
 * 单独抽成一个 Bean 并给 {@link #persist} 加事务，是为了让 Spring 代理生效：
 * 之前在 {@code JavbusApiService} 内部自调用 {@code saveResult} 属于同类自调用，
 * {@code @Transactional} 不会生效，导致每句 SQL 各自自动提交（一部影片 20~40 句 SQL）。
 * 并发抓取入库时，每部影片在自己的独立事务中原子落库。
 * </p>
 */
@Slf4j
@Service
public class JavbusApiSaveService {

    private final JavbusApiMovieMapper movieMapper;
    private final JavbusApiMagnetMapper magnetMapper;
    private final JavbusApiStarMapper starMapper;
    private final JavbusApiSimilarMovieMapper similarMovieMapper;
    private final JavbusApiMovieSampleMapper movieSampleMapper;
    private final JavbusApiDirectorMapper directorMapper;
    private final JavbusApiStudioMapper studioMapper;
    private final JavbusApiPublisherMapper publisherMapper;
    private final JavbusApiSeriesMapper seriesMapper;
    private final JavbusApiGenreMapper genreMapper;
    private final ImageDownloadService imageDownloadService;

    public JavbusApiSaveService(JavbusApiMovieMapper movieMapper,
                                JavbusApiMagnetMapper magnetMapper,
                                JavbusApiStarMapper starMapper,
                                JavbusApiSimilarMovieMapper similarMovieMapper,
                                JavbusApiMovieSampleMapper movieSampleMapper,
                                JavbusApiDirectorMapper directorMapper,
                                JavbusApiStudioMapper studioMapper,
                                JavbusApiPublisherMapper publisherMapper,
                                JavbusApiSeriesMapper seriesMapper,
                                JavbusApiGenreMapper genreMapper,
                                ImageDownloadService imageDownloadService) {
        this.movieMapper = movieMapper;
        this.magnetMapper = magnetMapper;
        this.starMapper = starMapper;
        this.similarMovieMapper = similarMovieMapper;
        this.movieSampleMapper = movieSampleMapper;
        this.directorMapper = directorMapper;
        this.studioMapper = studioMapper;
        this.publisherMapper = publisherMapper;
        this.seriesMapper = seriesMapper;
        this.genreMapper = genreMapper;
        this.imageDownloadService = imageDownloadService;
    }

    /**
     * 保存抓取结果：实体表 -> 影片 -> 磁力 -> 关联表，同一事务内完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void persist(JavbusApiScrapeResult result) {
        if (result == null || result.getMovie() == null) {
            return;
        }
        JavbusApiMovie movie = result.getMovie();
        if (StringUtils.isBlank(movie.getCode())) {
            throw new IllegalArgumentException("影片缺少番号，无法入库");
        }
        movie.setCode(movie.getCode().trim().toUpperCase());

        // 1. 实体表（导演/制作商/发行商/系列/演员/类别，尽量批量）
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
        // 类别数量多（常 10~30 个），必须批量 upsert，避免逐条 SQL
        if (movie.getGenresList() != null && !movie.getGenresList().isEmpty()) {
            genreMapper.upsertBatch(movie.getGenresList());
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

    /** 下载封面到本地：优先 cover_url（列表缩略图），其次 cover_hd（详情大图）。 */
    public void downloadCoverToLocal(JavbusApiMovie movie) {
        if (movie == null || StringUtils.isBlank(movie.getCode())) {
            return;
        }
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
}
