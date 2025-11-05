package org.holic.javspy.service;

import org.holic.javspy.mapper.JavMapper;
import org.holic.javspy.model.Movie;
import org.holic.javspy.model.MovieDetail;
import org.holic.javspy.model.MovieResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class JavService {

    @Autowired
    private JavMapper javMapper;
    @Autowired
    private MovieApiService movieApiService;

    public Integer getmovie(){
        MovieResponse movies = movieApiService.getMovies();
        for (Movie movie : movies.getMovies()) {
            MovieDetail movieDetail = movieApiService.getMovieDetail(movie.getId());
            System.out.println(movieDetail);
        }
        return movies.getMovies().size();
    }
    public Boolean existsByTitle(String id) {
        return javMapper.existsByTitle(id);
    }
    public Integer insertMovie(MovieDetail movie) {
        return javMapper.insertMovie(movie);
    }
}