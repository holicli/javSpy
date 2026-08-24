package org.holic.javspy.javbusapi.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Emby 影片番号缓存，对应 emby_movie 表。
 */
@TableName("emby_movie")
@Data
public class EmbyMovie implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 影片番号（统一大写） */
    private String code;

    /** 首次同步时间 */
    private Date createdAt;

    /** 最近同步时间 */
    private Date updatedAt;
}
