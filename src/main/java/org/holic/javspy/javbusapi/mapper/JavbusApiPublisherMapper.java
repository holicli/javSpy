package org.holic.javspy.javbusapi.mapper;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * javbus_publisher 表 mapper。
 */
@Repository
public interface JavbusApiPublisherMapper {

    /** 插入或更新发行商（按 id 去重）。 */
    int upsert(@Param("id") String id, @Param("name") String name);
}
