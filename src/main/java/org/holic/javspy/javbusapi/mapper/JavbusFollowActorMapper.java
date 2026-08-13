package org.holic.javspy.javbusapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.JavbusFollowActor;
import org.springframework.stereotype.Repository;

/**
 * javbus_follow_actor 表 mapper。
 */
@Repository
public interface JavbusFollowActorMapper extends BaseMapper<JavbusFollowActor> {

    /** 新增关注演员（重名时忽略），返回受影响行数。 */
    int insertIgnore(@Param("actorName") String actorName,
                     @Param("remark") String remark);

    /** 删除关注演员，返回受影响行数。 */
    int deleteByName(@Param("actorName") String actorName);
}
