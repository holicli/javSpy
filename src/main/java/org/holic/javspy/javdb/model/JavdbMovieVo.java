package org.holic.javspy.javdb.model;

import lombok.Data;

import java.io.Serializable;

/**
 * javdb 影片列表展示对象，不包含 raw_html 等大字段。
 */
@Data
public class JavdbMovieVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 番号 */
    private String code;

    /** 标题 */
    private String title;

    /** 封面地址 */
    private String coverUrl;

    /** 发行日期 yyyy-MM-dd */
    private String releaseDate;

    /** 时长（分钟） */
    private Integer duration;

    /** 导演 */
    private String director;

    /** 制作商 */
    private String studio;

    /** 系列 */
    private String series;

    /** 演员，多个用逗号分隔 */
    private String actors;

    /** 类型/标签，多个用逗号分隔 */
    private String genres;

    /** javdb 详情页地址 */
    private String detailUrl;

    /** 磁力链接数量 */
    private Integer magnetCount;

    /** 数据来源：DB 表示来自数据库，JAVDB 表示本次新抓取 */
    private String source;
}
