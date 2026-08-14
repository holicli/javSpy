package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.holic.javspy.javbusapi.mapper.JavbusApiSimilarMovieMapper;
import org.holic.javspy.javbusapi.model.JavbusApiSimilarMovie;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * javbus_movie_similar 表服务。
 */
@Service
public class JavbusApiSimilarMovieService extends ServiceImpl<JavbusApiSimilarMovieMapper, JavbusApiSimilarMovie> {

    /** 批量保存相似影片。 */
    public boolean saveSimilars(Long movieId, List<JavbusApiSimilarMovie> similars) {
        if (similars == null || similars.isEmpty()) {
            return true;
        }
        return baseMapper.insertBatch(movieId, similars) > 0;
    }

    /** 按影片 code 查询相似影片列表。 */
    public List<JavbusApiSimilarMovie> listByMovieCode(String code) {
        return baseMapper.findByCode(code);
    }
}
