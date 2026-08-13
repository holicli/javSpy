package org.holic.javspy.javbusapi.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * javbus 影片预览图，对应 javbus_movie_sample 表。
 */
@TableName("javbus_movie_sample")
@Data
public class JavbusApiMovieSample implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 影片主键（javbus_movie.id） */
    @TableField("movie_id")
    private Long movieId;

    /** 预览图 ID（API 返回的 id） */
    private String sampleId;

    /** alt */
    private String alt;

    /** 大图地址 */
    private String src;

    /** 缩略图地址 */
    private String thumbnail;

    /** 创建时间 */
    private Date createdAt;
}
