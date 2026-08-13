package org.holic.javspy.javbusapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.JavbusApiStar;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javbus_star 表 mapper。
 */
@Repository
public interface JavbusApiStarMapper extends BaseMapper<JavbusApiStar> {

    /** 插入或更新演员（按 id 去重）。 */
    int upsert(JavbusApiStar star);

    /** 批量插入或更新演员。 */
    int upsertBatch(@Param("list") List<JavbusApiStar> stars);

    /** 按影片 code 查询演员列表（关联 javbus_movie_star / javbus_star）。 */
    List<JavbusApiStar> findStarsByCode(@Param("code") String code);
}
