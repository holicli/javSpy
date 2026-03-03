package org.holic.javspy.mapper;

import org.apache.ibatis.annotations.Param;
import org.holic.javspy.model.Magnet;
import org.holic.javspy.model.MovieDetail;
import org.holic.javspy.model.MovieResponse;
import org.holic.javspy.model.Star;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JavMapper {
    Integer getMovieCount();

    Boolean existsByTitle(String id);

    Integer insertMovie(MovieDetail movie);

    Boolean insertStar2DB(List<Star> list);
    Boolean existsInfo(@Param("table") String table,@Param("id") String id);
    Boolean insertInfo2Tabel(@Param("table") String table,@Param("id") String id,@Param("valuename") String valuename,@Param("value") String value);

    MovieDetail getMovieDetail(String id);

    Integer insertMangnets(@Param("list")List<Magnet> movieMagnets,@Param("gid")String gid);
}
