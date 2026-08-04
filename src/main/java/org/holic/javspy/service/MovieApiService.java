package org.holic.javspy.service;

import org.holic.javspy.model.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MovieApiService {

    private static final String BASE_URL = "http://192.168.0.108:33000";
    private static final String API_URL = BASE_URL+"/api/movies";
    private static final String API_URL_PAGES = BASE_URL+"/api/movies?page=";
    private static final String API_URL_DETAILS = BASE_URL+"/api/movies/";
    private static final String API_URL_STAR = BASE_URL+"/api/stars/";
    private static final String API_URL_MAGNETS = BASE_URL+"/api/magnets/";
    private final RestTemplate restTemplate;

    public MovieApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // 获取默认情况（首页）下的页面
    public MovieResponse getMovies() {
        try {
            ResponseEntity<MovieResponse> response = restTemplate.getForEntity(API_URL, MovieResponse.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("获取电影数据失败1: " + e.getMessage());
            return null;
        }
    }

    // 获取默认情况（首页）下的页面
    public MovieResponse getNextPageMovies(int page) {
        try {
            ResponseEntity<MovieResponse> response = restTemplate.getForEntity(API_URL_PAGES+page, MovieResponse.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("获取电影数据失败2: " + e.getMessage());
            return null;
        }
    }



    //获取指定番号下的影片详情
    public MovieDetail getMovieDetail(String id) {
        try {
            ResponseEntity<MovieDetail> response = restTemplate.getForEntity(API_URL_DETAILS+id, MovieDetail.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("获取电影数据失败3: " + e.getMessage());
            return null;
        }
    }
    //获取指定的女优出演的
    public Star getStarDetail(String id) {
        try {
            ResponseEntity<Star> response = restTemplate.getForEntity(API_URL_STAR+id, Star.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("获取电影数据失败4: " + e.getMessage());
            return null;
        }
    }

    //获取指定影片的番号list
    public List<Magnet> getMovieMagnets(String id,String gid,String uc) {
        try {
            String url = API_URL_MAGNETS+id+"?gid="+gid+"&uc="+uc;
            ResponseEntity<List<Magnet>> response = restTemplate.exchange(
                    API_URL_MAGNETS + id + "?gid=" + gid + "&uc=" + uc,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Magnet>>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            System.err.println("获取电影数据失败5: " + e.getMessage());
            return null;
        }
    }

}

