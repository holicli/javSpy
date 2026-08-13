package org.holic.javspy.javbusapi.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.holic.javspy.javbusapi.mapper.JavbusApiSimilarMovieMapper;
import org.holic.javspy.javbusapi.model.JavbusApiSimilarMovie;
import org.holic.javspy.javbusapi.service.JavbusApiSimilarMovieService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * javbus_movie_similar 表服务实现。
 */
@Service
public class JavbusApiSimilarMovieServiceImpl extends ServiceImpl<JavbusApiSimilarMovieMapper, JavbusApiSimilarMovie>
        implements JavbusApiSimilarMovieService {

    @Override
    public boolean saveSimilars(Long movieId, List<JavbusApiSimilarMovie> similars) {
        if (similars == null || similars.isEmpty()) {
            return true;
        }
        return baseMapper.insertBatch(movieId, similars) > 0;
    }

    @Override
    public List<JavbusApiSimilarMovie> listByMovieCode(String code) {
        return baseMapper.findByCode(code);
    }
}
