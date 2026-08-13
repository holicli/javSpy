package org.holic.javspy.javbusapi.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.holic.javspy.javbusapi.mapper.JavbusFollowActorMapper;
import org.holic.javspy.javbusapi.model.JavbusFollowActor;
import org.holic.javspy.javbusapi.service.JavbusFollowActorService;
import org.springframework.stereotype.Service;

/**
 * javbus_follow_actor 表服务实现。
 */
@Service
public class JavbusFollowActorServiceImpl extends ServiceImpl<JavbusFollowActorMapper, JavbusFollowActor>
        implements JavbusFollowActorService {

    @Override
    public boolean addActor(String actorName, String remark) {
        return baseMapper.insertIgnore(actorName, remark) > 0;
    }

    @Override
    public boolean removeActor(String actorName) {
        return baseMapper.deleteByName(actorName) > 0;
    }
}
