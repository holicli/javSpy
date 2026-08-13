package org.holic.javspy.javbusapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.JavbusApiMovieSample;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javbus_movie_sample 表 mapper。
 */
@Repository
public interface JavbusApiMovieSampleMapper extends BaseMapper<JavbusApiMovieSample> {

    /** 批量插入影片预览图。 */
    int insertBatch(@Param("movieId") Long movieId,
                    @Param("list") List<JavbusApiMovieSample> samples);

    /** 按影片 code 查询预览图列表。 */
    List<JavbusApiMovieSample> findByCode(@Param("code") String code);

    /** 删除某部影片的全部预览图。 */
    int deleteByMovieId(@Param("movieId") Long movieId);
}
