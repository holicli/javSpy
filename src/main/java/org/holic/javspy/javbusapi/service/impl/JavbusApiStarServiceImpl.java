package org.holic.javspy.javbusapi.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.holic.javspy.javbusapi.mapper.JavbusApiStarMapper;
import org.holic.javspy.javbusapi.model.JavbusApiStar;
import org.holic.javspy.javbusapi.service.JavbusApiStarService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * javbus_star 表服务实现。
 */
@Service
public class JavbusApiStarServiceImpl extends ServiceImpl<JavbusApiStarMapper, JavbusApiStar>
        implements JavbusApiStarService {

    @Override
    public boolean upsertStar(JavbusApiStar star) {
        return star != null && baseMapper.upsert(star) > 0;
    }

    @Override
    public boolean upsertStars(List<JavbusApiStar> stars) {
        if (stars == null || stars.isEmpty()) {
            return true;
        }
        return baseMapper.upsertBatch(stars) > 0;
    }

    @Override
    public List<JavbusApiStar> listByMovieCode(String code) {
        return baseMapper.findStarsByCode(code);
    }
}
