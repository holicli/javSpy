package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.holic.javspy.javbusapi.model.JavbusApiMovieSample;

import java.util.List;

/**
 * javbus_movie_sample 表服务。
 */
public interface JavbusApiMovieSampleService extends IService<JavbusApiMovieSample> {

    /** 批量保存影片预览图。 */
    boolean saveSamples(Long movieId, List<JavbusApiMovieSample> samples);

    /** 按影片 code 查询预览图列表。 */
    List<JavbusApiMovieSample> listByMovieCode(String code);

    /** 删除某部影片的全部预览图。 */
    boolean removeByMovieId(Long movieId);
}
