package org.holic.javspy.javbusapi.mapper;

import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.JavbusApiStar;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javbus_genre 表及影片-类别关联 mapper。
 */
@Repository
public interface JavbusApiGenreMapper {

    /** 批量插入或更新类别（按 id 去重，一条 SQL 完成）。 */
    int upsertBatch(@Param("list") List<JavbusApiStar> genres);

    /** 插入影片-类别关联。 */
    int insertMovieGenres(@Param("movieId") Long movieId,
                          @Param("list") List<JavbusApiStar> genres);
}
