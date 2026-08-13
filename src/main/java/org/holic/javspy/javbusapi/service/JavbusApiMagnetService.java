package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;

import java.util.List;

/**
 * javbus_magnet 表服务。
 */
public interface JavbusApiMagnetService extends IService<JavbusApiMagnet> {

    /** 批量保存磁力链接。 */
    boolean saveMagnets(List<JavbusApiMagnet> magnets);

    /** 查询某部影片已入库的磁力数量。 */
    int countByCode(String code);

    /** 查询某部影片的全部磁力链接。 */
    List<JavbusApiMagnet> listByCode(String code);

    /** 保存磁力链接到单独表 javbus_magnet_save。 */
    boolean saveMagnet(String code, String magnet);
}
