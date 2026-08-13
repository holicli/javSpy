package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.holic.javspy.javbusapi.model.JavbusFollowActor;

/**
 * javbus_follow_actor 表服务。
 */
public interface JavbusFollowActorService extends IService<JavbusFollowActor> {

    /** 新增关注演员（重名忽略）。 */
    boolean addActor(String actorName, String remark);

    /** 删除关注演员。 */
    boolean removeActor(String actorName);
}
