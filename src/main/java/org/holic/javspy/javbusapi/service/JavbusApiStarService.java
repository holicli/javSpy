package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.holic.javspy.javbusapi.mapper.JavbusApiStarMapper;
import org.holic.javspy.javbusapi.model.JavbusApiStar;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * javbus_star 表服务。
 */
@Service
public class JavbusApiStarService extends ServiceImpl<JavbusApiStarMapper, JavbusApiStar> {

    /** 插入或更新演员。 */
    public boolean upsertStar(JavbusApiStar star) {
        return star != null && baseMapper.upsert(star) > 0;
    }

    /** 批量插入或更新演员。 */
    public boolean upsertStars(List<JavbusApiStar> stars) {
        if (stars == null || stars.isEmpty()) {
            return true;
        }
        return baseMapper.upsertBatch(stars) > 0;
    }

    /** 按影片 code 查询演员列表。 */
    public List<JavbusApiStar> listByMovieCode(String code) {
        return baseMapper.findStarsByCode(code);
    }
}
