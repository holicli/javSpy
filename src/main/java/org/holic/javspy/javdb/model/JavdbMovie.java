package org.holic.javspy.javdb.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * javdb 影片信息，对应 javdb_movie 表。
 */
@Data
public class JavdbMovie implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 番号，如 SSIS-123 */
    private String code;

    /** 标题 */
    private String title;

    /** 封面图片地址 */
    private String coverUrl;

    /** 本地封面图片地址 */
    private String coverLocal;

    /** 发售日期 yyyy-MM-dd */
    private String releaseDate;

    /** 时长（分钟） */
    private Integer duration;

    /** 导演 */
    private String director;

    /** 制作商（studio） */
    private String studio;

    /** 发行商（label / publisher） */
    private String publisher;

    /** 系列 */
    private String series;

    /** 演员，多个用逗号分隔 */
    private String actors;

    /** 类型/标签，多个用逗号分隔 */
    private String genres;

    /** 简介 */
    private String description;

    /** javdb 详情页地址 */
    private String detailUrl;

    /** 详情页原始 HTML（便于排查解析问题，可空） */
    private String rawHtml;

    /** 入库时间 */
    private Date createdAt;

    /** 更新时间 */
    private Date updatedAt;
}
