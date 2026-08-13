package org.holic.javspy.javbusapi.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * javbus API 影片信息，对应 javbus_movie 表。
 */
@TableName("javbus_movie")
@Data
public class JavbusApiMovie implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 番号 */
    private String code;

    /** 标题 */
    private String title;

    /** 封面图（列表缩略图） */
    private String coverUrl;

    /** 高清封面图（详情大图） */
    private String coverHd;

    /** 本地封面图地址 */
    private String coverLocal;

    /** 封面大图宽度 */
    private Integer coverWidth;

    /** 封面大图高度 */
    private Integer coverHeight;

    /** 发售日期 yyyy-MM-dd */
    private String releaseDate;

    /** 时长（分钟） */
    private Integer duration;

    /** 导演 */
    @TableField(exist = false)
    private String director;

    /** 导演 ID */
    private String directorId;

    /** 制作商 */
    @TableField(exist = false)
    private String studio;

    /** 制作商 ID */
    private String studioId;

    /** 发行商 */
    @TableField(exist = false)
    private String publisher;

    /** 发行商 ID */
    private String publisherId;

    /** 系列 */
    @TableField(exist = false)
    private String series;

    /** 系列 ID */
    private String seriesId;

    /** 演员，多个用逗号分隔 */
    @TableField(exist = false)
    private String actors;

    /** 类型/标签，多个用逗号分隔 */
    @TableField(exist = false)
    private String genres;

    /** 演员列表（含 ID，供关联表入库） */
    @TableField(exist = false)
    private List<JavbusApiStar> stars = new ArrayList<>();

    /** 类别列表（含 ID，供关联表入库） */
    @TableField(exist = false)
    private List<JavbusApiStar> genresList = new ArrayList<>();

    /** 预览图列表 */
    @TableField(exist = false)
    private List<JavbusApiMovieSample> samples = new ArrayList<>();

    /** 相似影片列表 */
    @TableField(exist = false)
    private List<JavbusApiSimilarMovie> similarMovies = new ArrayList<>();

    /** javbus 详情页地址 */
    private String detailUrl;

    /** gid（磁力接口参数） */
    private String gid;

    /** uc（磁力接口参数） */
    private String uc;

    /** 详情页原始 JSON（排查用） */
    private String rawJson;

    /** 入库时间 */
    private Date createdAt;

    /** 更新时间 */
    private Date updatedAt;
}
