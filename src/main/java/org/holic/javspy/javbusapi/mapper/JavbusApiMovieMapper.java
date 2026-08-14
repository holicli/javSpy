package org.holic.javspy.javbusapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.JavbusApiMovie;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javbus_movie 表 mapper。
 */
@Repository
public interface JavbusApiMovieMapper extends BaseMapper<JavbusApiMovie> {

    /** 按番号查询影片，不存在返回 null。 */
    JavbusApiMovie findByCode(@Param("code") String code);

    /** 批量按番号查询已存在的影片。 */
    List<JavbusApiMovie> findByCodes(@Param("codes") List<String> codes);

    /** 插入影片；已存在（番号冲突）时更新可更新字段。 */
    int insertMovie(JavbusApiMovie movie);

    /** 按条件分页查询影片列表。 */
    List<JavbusApiMovie> searchMovies(@Param("code") String code,
                                      @Param("keyword") String keyword,
                                      @Param("releaseDate") String releaseDate);

    /** 按入库时间倒序查询影片列表。 */
    List<JavbusApiMovie> searchNewest();

    /** 回写本地封面地址。 */
    int updateCoverLocal(@Param("code") String code, @Param("coverLocal") String coverLocal);
}
