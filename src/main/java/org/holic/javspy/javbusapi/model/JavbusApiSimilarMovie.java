package org.holic.javspy.javbusapi.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * javbus 相似影片，对应 javbus_movie_similar 表。
 */
@TableName("javbus_movie_similar")
@Data
public class JavbusApiSimilarMovie implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 相似影片番号 */
    @TableId(type = IdType.INPUT)
    @TableField("similar_code")
    private String code;

    /** 相似影片标题 */
    @TableField("similar_title")
    private String title;

    /** 相似影片封面 */
    @TableField("similar_img")
    private String img;

    /** 影片主键（javbus_movie.id） */
    @TableField("movie_id")
    private Long movieId;

    /** 创建时间 */
    private Date createdAt;
}
