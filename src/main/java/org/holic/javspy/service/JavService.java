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
import java.util.stream.Collectors;

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


    //根据番号检查影片是否存在
    public Boolean existsByTitle(String id) {
        return javMapper.existsByTitle(id);
    }
    //插入影片信息
    public Integer insertMovie(MovieDetail movie) {
        if (movie == null) {
            return null;
        }
        if (movie.getId() == null) {
            return null;
        }
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
    //当女优信息不存在时插入信息
    public Boolean insertStar2Tabel(List<Star> list) throws InterruptedException {
        if (list.isEmpty()){
            return false;
        }
        List<Star> filteredList = new ArrayList<>();
        for (Star star : list) {
            if (!existsInfo(TABLE_STAR, star.getId())) {
                Star starDetail = movieApiService.getStarDetail(star.getId());
                filteredList.add(starDetail);
            }
        }
        list = filteredList;
        log.info(list.toString());
        if (list.isEmpty()){
            return true;
        }
        List<Star> distinctList = list.stream()
                .collect(Collectors.toMap(
                        Star::getId,  // key: star.id
                        star -> star, // value: star本身
                        (existing, replacement) -> existing // 冲突时保留已存在的
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
        return javMapper.insertStar2DB(distinctList);
    }

    public Boolean saveMovieWithDetails(MovieDetail movieDetail) throws InterruptedException {
            //由于影片的排序是按照发行时间排序，所以判断番号是否已经存在，如果存在就直接结束
            if (existsByTitle(movieDetail.getId())){

            }
            //todo 处理首页上的信息
            //根据影片番号获取相关信息
            //保存影片相关信息
            Integer i = insertMovie(movieDetail);
            //保存导演信息
            if (Objects.nonNull(movieDetail.getDirector())){
                if (Objects.nonNull(movieDetail.getId())){
                    insertInfo2Tabel(TABLE_DIRECTOR,movieDetail.getDirector().getId(),movieDetail.getDirector().getName());
                }
            }

            //保存女优信息
            if (Objects.nonNull(movieDetail.getStars())){
                insertStar2Tabel(movieDetail.getStars());
            }

            //todo 还有很多类似系类 工作室信息没有校验是否有新的并保存
        return false;
    }

    public List<NewMovie> getNewMovie(Integer page,String keyword) throws IOException, InterruptedException {
        if (keyword != null && !keyword.isEmpty()){

        }
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
                    List<Star> stars = movieDetail.getStars();
                }else {
                    movieDetail = movieApiService.getMovieDetail(movie.getId());

//                    saveMovieWithDetails(movieDetail);
                    if (Objects.nonNull(movieDetail)){
                        insertMovie(movieDetail);
                        String gid = movieDetail.getGid();
                        String uc = movieDetail.getUc();
                        movieMagnets = movieApiService.getMovieMagnets(movie.getId(), gid, uc);
                        if (Objects.nonNull(movieMagnets)){
                            insertMagnets(movieMagnets, gid);
                        }
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