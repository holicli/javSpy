package org.holic.javspy.mapper;

import org.holic.javspy.model.MovieDetail;
import org.holic.javspy.model.MovieResponse;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JavMapper {
    List<MovieResponse> getMovie4Db();
    Integer getMovieCount();

    Boolean existsByTitle(String id);

    Integer insertMovie(MovieDetail movie);
}
