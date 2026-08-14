package org.holic.javspy.javbusapi.mapper;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * javbus_series 表 mapper。
 */
@Repository
public interface JavbusApiSeriesMapper {

    /** 插入或更新系列（按 id 去重）。 */
    int upsert(@Param("id") String id, @Param("name") String name);
}
