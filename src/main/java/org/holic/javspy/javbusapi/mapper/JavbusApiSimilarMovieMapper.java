package org.holic.javspy.javbusapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.JavbusApiSimilarMovie;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javbus_movie_similar 表 mapper。
 */
@Repository
public interface JavbusApiSimilarMovieMapper extends BaseMapper<JavbusApiSimilarMovie> {

    /** 批量插入影片-相似影片关联。 */
    int insertBatch(@Param("movieId") Long movieId,
                    @Param("list") List<JavbusApiSimilarMovie> similars);

    /** 按影片 code 查询相似影片列表。 */
    List<JavbusApiSimilarMovie> findByCode(@Param("code") String code);
}
