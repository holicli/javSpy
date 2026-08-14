package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.holic.javspy.javbusapi.mapper.JavbusFollowActorMapper;
import org.holic.javspy.javbusapi.model.JavbusFollowActor;
import org.springframework.stereotype.Service;

/**
 * javbus_follow_actor 表服务。
 */
@Service
public class JavbusFollowActorService extends ServiceImpl<JavbusFollowActorMapper, JavbusFollowActor> {

    /** 新增关注演员（重名忽略）。 */
    public boolean addActor(String actorName, String remark) {
        return baseMapper.insertIgnore(actorName, remark) > 0;
    }

    /** 删除关注演员。 */
    public boolean removeActor(String actorName) {
        return baseMapper.deleteByName(actorName) > 0;
    }
}
