package org.holic.javspy.javdb.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * javdb 关注演员，对应 javdb_follow_actor 表。
 */
@Data
public class JavdbFollowActor implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 演员名称 */
    private String actorName;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createdAt;
}
