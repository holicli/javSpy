package org.holic.javspy.javbusapi.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * javbus API 磁力链接，对应 javbus_magnet 表。
 */
@Data
public class JavbusApiMagnet implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 磁力 ID（API 返回的 id） */
    private String magnetId;

    /** 影片主键（javbus_movie.id） */
    private Long movieId;

    /** 所属影片番号 */
    private String code;

    /** 详情页 id（URL 中的路径段） */
    private String detailId;

    /** magnet:?xt=... 完整链接 */
    private String magnet;

    /** 磁力资源名称 */
    private String name;

    /** 文件大小（原始字符串，如 6.57GB） */
    private String sizeText;

    /** 文件大小字节数 */
    private Long sizeBytes;

    /** 分享日期 */
    private String shareDate;

    /** 是否高清（1 是 0 否） */
    private Integer hd;

    /** 是否有中文字幕（1 是 0 否） */
    private Integer subtitle;

    /** 入库时间 */
    private Date createdAt;
}
