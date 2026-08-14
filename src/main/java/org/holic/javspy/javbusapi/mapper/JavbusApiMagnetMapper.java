package org.holic.javspy.javbusapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;
import org.holic.javspy.javbusapi.model.JavbusApiMagnetCount;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javbus_magnet 表 mapper。
 */
@Repository
public interface JavbusApiMagnetMapper extends BaseMapper<JavbusApiMagnet> {

    /** 批量插入磁力链接（按 link 去重）。 */
    int insertBatch(@Param("list") List<JavbusApiMagnet> magnets);

    /** 查询某部影片已入库的磁力链接数量。 */
    int countByCode(@Param("code") String code);

    /** 查询某部影片的全部磁力链接。 */
    List<JavbusApiMagnet> findByCode(@Param("code") String code);

    /** 按多个影片 code 批量统计磁力数量。 */
    List<JavbusApiMagnetCount> countByCodes(@Param("codes") List<String> codes);

    /** 保存磁力链接到单独表（code + magnet + 插入日期）。 */
    int insertSave(@Param("code") String code, @Param("magnet") String magnet);
}
