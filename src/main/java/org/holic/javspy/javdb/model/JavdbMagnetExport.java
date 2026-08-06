package org.holic.javspy.javdb.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * javdb 磁链导出记录，对应 javdb_magnet_export 表。
 * 保存选中影片最终选定的磁力链接（无磁链时 status = NO_MAGNET）。
 */
@Data
public class JavdbMagnetExport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    private Long id;

    /** 影片番号 */
    private String code;

    /** 选中的磁力链接，无磁链时为 null */
    private String magnet;

    /** 磁力资源名称 */
    private String name;

    /** 文件大小文本 */
    private String sizeText;

    /** 磁力分享日期 */
    private String shareDate;

    /** OK=已保存磁链，NO_MAGNET=无磁链 */
    private String status;

    /** 导出时间 */
    private Date createdAt;
}
