package org.holic.javspy.javbusapi.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.holic.javspy.javbusapi.mapper.JavbusApiMagnetMapper;
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;
import org.holic.javspy.javbusapi.service.JavbusApiMagnetService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * javbus_magnet 表服务实现。
 */
@Service
public class JavbusApiMagnetServiceImpl extends ServiceImpl<JavbusApiMagnetMapper, JavbusApiMagnet>
        implements JavbusApiMagnetService {

    @Override
    public boolean saveMagnets(List<JavbusApiMagnet> magnets) {
        if (magnets == null || magnets.isEmpty()) {
            return true;
        }
        Date now = new Date();
        for (JavbusApiMagnet magnet : magnets) {
            if (magnet != null && magnet.getCreatedAt() == null) {
                magnet.setCreatedAt(now);
            }
        }
        return baseMapper.insertBatch(magnets) > 0;
    }

    @Override
    public int countByCode(String code) {
        return baseMapper.countByCode(code);
    }

    @Override
    public List<JavbusApiMagnet> listByCode(String code) {
        return baseMapper.findByCode(code);
    }

    @Override
    public boolean saveMagnet(String code, String magnet) {
        return baseMapper.insertSave(code, magnet) > 0;
    }
}
