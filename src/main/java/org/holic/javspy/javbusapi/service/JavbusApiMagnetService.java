package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.holic.javspy.javbusapi.mapper.JavbusApiMagnetMapper;
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * javbus_magnet 表服务。
 */
@Service
public class JavbusApiMagnetService extends ServiceImpl<JavbusApiMagnetMapper, JavbusApiMagnet> {

    /** 批量保存磁力链接。 */
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

    /** 查询某部影片已入库的磁力数量。 */
    public int countByCode(String code) {
        return baseMapper.countByCode(code);
    }

    /** 查询某部影片的全部磁力链接。 */
    public List<JavbusApiMagnet> listByCode(String code) {
        return baseMapper.findByCode(code);
    }

    /** 保存磁力链接到单独表 javbus_magnet_save。 */
    public boolean saveMagnet(String code, String magnet) {
        return baseMapper.insertSave(code, magnet) > 0;
    }
}
