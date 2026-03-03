package org.holic.javspy;

import org.holic.javspy.model.JavTableConstants;
import org.holic.javspy.model.MovieResponse;
import org.holic.javspy.service.JavService;
import org.holic.javspy.service.MovieApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.holic.javspy.model.JavTableConstants.TABLE_DIRECTOR;

@SpringBootTest
class JavspyApplicationTests {
    @Autowired
    private JavService javService;

    @Autowired
    private MovieApiService movieApiService;

    @Test
    void contextLoads() {
    }

    @Test
    void insertinfo2db(){
        javService.getMovie();
    }
    @Test
    void testNextPage(){
        MovieResponse nextPageMovies = movieApiService.getNextPageMovies(2);
        System.out.println(nextPageMovies);
    }
}
