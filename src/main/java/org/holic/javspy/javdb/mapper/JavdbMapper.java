package org.holic.javspy.javdb.mapper;

import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javdb.model.JavdbMagnet;
import org.holic.javspy.javdb.model.JavdbMovie;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javdb 数据访问接口（javdb_movie / javdb_magnet 两张新表）。
 */
@Repository
public interface JavdbMapper {

    /** 按番号查询影片，不存在返回 null。 */
    JavdbMovie findByCode(@Param("code") String code);

    /** 批量按番号查询已存在的影片。 */
    List<JavdbMovie> findByCodes(@Param("codes") List<String> codes);

    /** 插入影片；已存在（番号冲突）时更新可更新字段。 */
    int insertMovie(JavdbMovie movie);

    /** 批量插入磁力链接（重复行自动忽略）。 */
    int insertMagnets(@Param("list") List<JavdbMagnet> magnets);

    /** 按条件分页查询影片列表。 */
    List<JavdbMovie> searchMovies(@Param("code") String code,
                                  @Param("keyword") String keyword,
                                  @Param("releaseDate") String releaseDate);
}
