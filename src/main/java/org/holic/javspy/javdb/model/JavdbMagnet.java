package org.holic.javspy.javdb.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * javdb 磁力链接，对应 javdb_magnet 表。
 */
@Data
public class JavdbMagnet implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 所属影片番号 */
    private String code;

    /** javdb 详情页 id（URL 中的路径段） */
    private String detailId;

    /** magnet:?xt=... 完整链接 */
    private String magnet;

    /** 磁力资源名称 */
    private String name;

    /** 文件大小（原始字符串，如 2.3 GB） */
    private String sizeText;

    /** 文件大小字节数，解析失败时为 null */
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
