package org.holic.javspy.javbusapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.JavbusApiMovie;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javbus_movie 表 mapper。
 */
@Repository
public interface JavbusApiMovieMapper extends BaseMapper<JavbusApiMovie> {

    /** 按条件分页查询影片列表。 */
    List<JavbusApiMovie> searchMovies(@Param("code") String code,
                                      @Param("keyword") String keyword,
                                      @Param("releaseDate") String releaseDate);

    /** 回写本地封面地址。 */
    int updateCoverLocal(@Param("code") String code, @Param("coverLocal") String coverLocal);
}
