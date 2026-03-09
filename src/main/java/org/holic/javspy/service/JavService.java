package org.holic.javspy.service;

import lombok.extern.slf4j.Slf4j;
import org.holic.javspy.mapper.JavMapper;
import org.holic.javspy.misc.ImageDownloadService;
import org.holic.javspy.misc.QBittorrentAutoDownloader;
import org.holic.javspy.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.holic.javspy.model.JavTableConstants.TABLE_DIRECTOR;
import static org.holic.javspy.model.JavTableConstants.TABLE_STAR;

@Service
@Slf4j
public class JavService {

    @Autowired
    private JavMapper javMapper;
    @Autowired
    private MovieApiService movieApiService;
    @Autowired
    private MagnetFilterService magnetFilterService;
    @Autowired
    private ImageDownloadService imageDownloadService;
    @Autowired
    private MovieIsExsitInSystem movieIsExsitInSystem;


    /**
     * 获取并保存所有电影数据
     *
     * @return 成功保存的电影总数
     */
    public Integer getMovie() {
        Integer totalSaved = 0;

        try {
            // 获取首页信息（第一页）
            MovieResponse movies = movieApiService.getMovies();
            saveMovieWithDetails(movies);
            totalSaved = movies.getMovies().size();

            Pagination pagination = movies.getPagination();

            // 循环获取并保存后续页面的数据
            while (pagination != null && pagination.isHasNextPage()) {
                MovieResponse nextPageMovies = movieApiService.getNextPageMovies(pagination.getNextPage());
                saveMovieWithDetails(nextPageMovies);
                totalSaved += nextPageMovies.getMovies().size();
                pagination = nextPageMovies.getPagination();

                // 添加短暂延迟，避免请求过于频繁
                Thread.sleep(100);
            }
            log.info("成功保存 {} 部电影数据", totalSaved);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("电影数据获取过程被中断", e);
            throw new RuntimeException("数据获取过程被中断", e);
        } catch (Exception e) {
            log.error("获取电影数据时发生异常", e);
            throw new RuntimeException("获取电影数据失败", e);
        }

        return totalSaved;
    }
    //根据番号检查影片是否存在
    public Boolean existsByTitle(String id) {
        return javMapper.existsByTitle(id);
    }
    //插入影片信息
    public Integer insertMovie(MovieDetail movie) {
        return javMapper.insertMovie(movie);
    }
    //检查信息是否存在
    public Boolean existsInfo(String table, String id){
        return javMapper.existsInfo(table,id);
    }
    //当信息不存在时插入信息
    public Boolean insertInfo2Tabel(String table,String id,String value){
        if(Objects.isNull(value) || Objects.isNull(id) || value.isEmpty() || id.isEmpty()){
            return false;
        }
        if (existsInfo(table,id)){
            return true;
        }
        return javMapper.insertInfo2Tabel(table,id,JavTableConstants.getTableValueName().get(table),value);
    }
    //当信息不存在时插入信息
    public Boolean insertStar2Tabel(List<Star> list) throws InterruptedException {
        if (list.isEmpty()){
            return false;
        }
        List<Star> filteredList = new ArrayList<>();
        for (Star star : list) {
            if (!existsInfo(TABLE_STAR, star.getId())) {
                Thread.sleep(100);
                Star starDetail = movieApiService.getStarDetail(star.getId());
                filteredList.add(starDetail);
            }
        }
        list = filteredList;
        log.info(list.toString());
        if (list.isEmpty()){
            return true;
        }
        return javMapper.insertStar2DB(list);
    }

    public Boolean saveMovieWithDetails(MovieResponse movies) throws InterruptedException {
        for (Movie movie : movies.getMovies()) {
            //由于影片的排序是按照发行时间排序，所以判断番号是否已经存在，如果存在就直接结束
            if (existsByTitle(movie.getId())){
                continue;
            }
            //todo 处理首页上的信息
            //根据影片番号获取相关信息
            MovieDetail movieDetail = movieApiService.getMovieDetail(movie.getId());
            //保存影片相关信息
            Integer i = insertMovie(movieDetail);
            //保存导演信息
            if (Objects.nonNull(movieDetail.getDirector())){
                if (Objects.nonNull(movieDetail.getId())){
                    insertInfo2Tabel(TABLE_DIRECTOR,movieDetail.getDirector().getId(),movieDetail.getDirector().getName());
                }
            }else {
                continue;
            }

            //保存女优信息
            if (Objects.nonNull(movieDetail.getStars())){
                insertStar2Tabel(movieDetail.getStars());
            }

            //todo 还有很多类似系类 工作室信息没有校验是否有新的并保存

        }
        return false;
    }

    public List<NewMovie> getNewMovie(Integer page) throws IOException {
        List<NewMovie> list = new ArrayList<>();
        MovieResponse movieResponse = new MovieResponse();
        if (Objects.equals(page,0) || Objects.isNull(page)){
            // 获取首页信息（第一页）
            movieResponse = movieApiService.getMovies();
        }else {
            movieResponse = movieApiService.getNextPageMovies(page);
        }
        if (Objects.nonNull(movieResponse)){
            List<Movie> movies = movieResponse.getMovies();
            for (Movie movie : movies) {
                MovieDetail movieDetail = new MovieDetail();
                List<Magnet> movieMagnets = new ArrayList<>();
                if (existsByTitle(movie.getId())){
                    movieDetail = javMapper.getMovieDetail(movie.getId());
                    String gid = movieDetail.getGid();
                    movieMagnets = javMapper.getMovieMagnets(gid);
                }else {
                    movieDetail = movieApiService.getMovieDetail(movie.getId());
                    if (Objects.isNull(movieDetail)){
                        continue;
                    }
                    if (Objects.nonNull(movieDetail.getStarstr())){
                        movieDetail.setActors(movieDetail.getStarstr());
                    }

                    if (Objects.isNull(movie.getId())){
                        continue;
                    }
                    insertMovie(movieDetail);
                    String gid = movieDetail.getGid();
                    String uc = movieDetail.getUc();
                    movieMagnets = movieApiService.getMovieMagnets(movie.getId(), gid, uc);
                    if (movieMagnets.size() > 0){
                        insertMagnets(movieMagnets, gid);
                    }
                }
                NewMovie newMovie = new NewMovie();
                movie.setImg(processMovieCover(movie).getImg());
                Magnet suggestionMagnet = getSuggestionMagnet(movieMagnets);
                newMovie.fullMovie(movie,movieDetail,suggestionMagnet);
                newMovie.setExists(movieIsExsitInSystem.isInEmby(movie.getId()));
                newMovie.setIsDownload(movieIsExsitInSystem.isInQbit(movie.getId()));
                list.add(newMovie);
            }
        }
        return list;
    }

    private Integer insertMagnets(List<Magnet> movieMagnets,String gid) {
        return javMapper.insertMangnets(movieMagnets,gid);
    }

    public Magnet getSuggestionMagnet(List<Magnet> movie){
        if (movie.isEmpty()){
            return null;
        }
        return magnetFilterService.findOptimalMagnet(movie);
    }

    /**
     * 处理电影封面（带Referer策略）
     */
    private Movie processMovieCover(Movie movie) throws IOException {
        if (movie != null && movie.getImg() != null && !movie.getImg().isEmpty()) {

//            String picurl = "http://192.168.0.108:18080/v1/images/primary/JavBus/"+movie.getId()+"?url="+movie.getImg()+"&ratio=-1&pos=1&auto=True&quality=90";
//            String picurl2 = "http://192.168.0.108:18080/v1/images/primary/JavBus/"+movie.getId()+"_1?url="+movie.getImg()+"&ratio=-1&pos=1&auto=True&quality=90";
            String picurl = "http://192.168.0.108:18080/v1/images/primary/JavBus/"+movie.getId()+"?ratio=-1&pos=-1&auto=False&quality=90";
            String imageUrl = imageDownloadService.getImageUrl(picurl, getImageName(movie.getImg()));
//            String imageUrl2 = imageDownloadService.getImageUrl(picurl2, getImageName(movie.getImg()));
            movie.setImg(imageUrl);
        }
        return movie;
    }
    public static String getImageName(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        // 正则表达式匹配最后一个/之后的内容
        Pattern pattern = Pattern.compile("([^/]+)$");
        Matcher matcher = pattern.matcher(imageUrl);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    public String startDownload(NewMovie newMovie) {
        if (Objects.isNull(newMovie)){
            return null;
        }
        // 配置信息
        final String qbtUrl = "http://192.168.0.108:8085";

        final String username= "admin";

        final String password= "qwer1234";
        // 创建下载器
        QBittorrentAutoDownloader downloader = new QBittorrentAutoDownloader(qbtUrl);

        // 1. 登录
        if (downloader.login(username, password)) {
            System.out.println("登录成功！");

            // 5. 创建自动下载管理器
            QBittorrentAutoDownloader.AutoDownloadManager manager = new QBittorrentAutoDownloader.AutoDownloadManager(downloader);

            // 添加一些磁力链接到队列
            if (Objects.nonNull(newMovie.getLinkUrl())) {
                String magnetLink = newMovie.getLinkUrl();
                downloader.addMagnet(magnetLink, "", "");
            }
            return "ok";

        } else {
            System.err.println("登录失败，请检查配置！");
            return "fail";
        }
    }
}