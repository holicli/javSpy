package org.holic.javspy.javbusapi.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * javbus 演员，对应 javbus_star 表。
 */
@Data
public class JavbusApiStar implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 演员 ID */
    private String id;

    /** 演员名称 */
    private String name;

    /** 头像地址 */
    private String avatar;

    /** 生日 yyyy-MM-dd */
    private String birthday;

    /** 年龄 */
    private String age;

    /** 身高 */
    private String height;

    /** 胸围 */
    private String bust;

    /** 腰围 */
    private String waistline;

    /** 臀围 */
    private String hipline;

    /** 出生地 */
    private String birthplace;

    /** 爱好 */
    private String hobby;

    /** 创建时间 */
    private Date createdAt;

    /** 更新时间 */
    private Date updatedAt;
}
