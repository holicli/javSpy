package org.holic.javspy.service;

import org.holic.javspy.model.MovieDetail;
import org.holic.javspy.model.MovieResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;

@Service
public class MovieApiService {

    private static final String API_URL = "http://192.168.0.120:33000/api/movies";
    private static final String API_URL_PAGES = "http://192.168.0.120:33000/api/movies?page=";
    private static final String API_URL_DETAILS = "http://192.168.0.120:33000/api/movies/";
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
            System.err.println("获取电影数据失败: " + e.getMessage());
            return null;
        }
    }
    //获取指定番号下的影片详情
    public MovieDetail getMovieDetail(String id) {
        try {
            ResponseEntity<MovieDetail> response = restTemplate.getForEntity(API_URL_DETAILS+id, MovieDetail.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("获取电影数据失败: " + e.getMessage());
            return null;
        }
    }



}

