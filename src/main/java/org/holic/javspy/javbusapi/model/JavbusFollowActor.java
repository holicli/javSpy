package org.holic.javspy.javbusapi.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * javbus 关注演员，对应 javbus_follow_actor 表。
 */
@TableName("javbus_follow_actor")
@Data
public class JavbusFollowActor implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 演员名称 */
    private String actorName;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createdAt;

    /** 关联演员 ID（javbus_star.id，按名字左连接得到，非本表字段） */
    private String starId;

    /** 演员头像远程地址（javbus_star.avatar，非本表字段） */
    private String avatar;

    /** 演员头像本地地址（javbus_star.avatar_local，非本表字段） */
    private String avatarLocal;
}
