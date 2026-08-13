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
}
