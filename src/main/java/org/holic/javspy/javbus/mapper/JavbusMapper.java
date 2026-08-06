package org.holic.javspy.javbus.mapper;

import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbus.model.JavbusMagnet;
import org.holic.javspy.javbus.model.JavbusMovie;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javbus 数据访问接口（javbus_movie / javbus_magnet 两张新表）。
 */
@Repository
public interface JavbusMapper {

    /** 按番号查询影片，不存在返回 null。 */
    JavbusMovie findByCode(@Param("code") String code);

    /** 批量按番号查询已存在的影片。 */
    List<JavbusMovie> findByCodes(@Param("codes") List<String> codes);

    /** 插入影片；已存在（番号冲突）时更新可更新字段。 */
    int insertMovie(JavbusMovie movie);

    /** 批量插入磁力链接（重复行自动忽略）。 */
    int insertMagnets(@Param("list") List<JavbusMagnet> magnets);

    /** 按条件分页查询影片列表。 */
    List<JavbusMovie> searchMovies(@Param("code") String code,
                                   @Param("keyword") String keyword,
                                   @Param("releaseDate") String releaseDate);
}
