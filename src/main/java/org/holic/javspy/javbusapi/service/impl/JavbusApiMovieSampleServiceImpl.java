package org.holic.javspy.javbusapi.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.holic.javspy.javbusapi.mapper.JavbusApiMovieSampleMapper;
import org.holic.javspy.javbusapi.model.JavbusApiMovieSample;
import org.holic.javspy.javbusapi.service.JavbusApiMovieSampleService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * javbus_movie_sample 表服务实现。
 */
@Service
public class JavbusApiMovieSampleServiceImpl extends ServiceImpl<JavbusApiMovieSampleMapper, JavbusApiMovieSample>
        implements JavbusApiMovieSampleService {

    @Override
    public boolean saveSamples(Long movieId, List<JavbusApiMovieSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return true;
        }
        return baseMapper.insertBatch(movieId, samples) > 0;
    }

    @Override
    public List<JavbusApiMovieSample> listByMovieCode(String code) {
        return baseMapper.findByCode(code);
    }

    @Override
    public boolean removeByMovieId(Long movieId) {
        return movieId != null && baseMapper.deleteByMovieId(movieId) >= 0;
    }
}
