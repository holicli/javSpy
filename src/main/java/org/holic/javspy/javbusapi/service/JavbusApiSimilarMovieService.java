package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.holic.javspy.javbusapi.model.JavbusApiSimilarMovie;

import java.util.List;

/**
 * javbus_movie_similar 表服务。
 */
public interface JavbusApiSimilarMovieService extends IService<JavbusApiSimilarMovie> {

    /** 批量保存相似影片。 */
    boolean saveSimilars(Long movieId, List<JavbusApiSimilarMovie> similars);

    /** 按影片 code 查询相似影片列表。 */
    List<JavbusApiSimilarMovie> listByMovieCode(String code);
}
