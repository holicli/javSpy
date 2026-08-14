package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.holic.javspy.javbusapi.mapper.JavbusApiMovieSampleMapper;
import org.holic.javspy.javbusapi.model.JavbusApiMovieSample;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * javbus_movie_sample 表服务。
 */
@Service
public class JavbusApiMovieSampleService extends ServiceImpl<JavbusApiMovieSampleMapper, JavbusApiMovieSample> {

    /** 批量保存影片预览图。 */
    public boolean saveSamples(Long movieId, List<JavbusApiMovieSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return true;
        }
        return baseMapper.insertBatch(movieId, samples) > 0;
    }

    /** 按影片 code 查询预览图列表。 */
    public List<JavbusApiMovieSample> listByMovieCode(String code) {
        return baseMapper.findByCode(code);
    }

    /** 删除某部影片的全部预览图。 */
    public boolean removeByMovieId(Long movieId) {
        return movieId != null && baseMapper.deleteByMovieId(movieId) >= 0;
    }
}
