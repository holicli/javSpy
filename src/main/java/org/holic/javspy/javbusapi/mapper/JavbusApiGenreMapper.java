package org.holic.javspy.javbusapi.mapper;

import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.JavbusApiGenreName;
import org.holic.javspy.javbusapi.model.JavbusApiStar;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javbus_genre 表及影片-类别关联 mapper。
 */
@Repository
public interface JavbusApiGenreMapper {

    /** 插入或更新类别（按 id 去重）。 */
    int upsert(@Param("id") String id, @Param("name") String name);

    /** 插入影片-类别关联。 */
    int insertMovieGenres(@Param("movieId") Long movieId,
                          @Param("list") List<JavbusApiStar> genres);

    /** 按影片 code 查询类别名称列表。 */
    List<String> findByMovieCode(@Param("code") String code);

    /** 按多个影片 code 批量查询类别名称。 */
    List<JavbusApiGenreName> findByCodes(@Param("codes") List<String> codes);
}
