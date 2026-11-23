package org.holic.javspy.misc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Emby 影片条目（重复检测页面用的轻量视图对象）。
 * <p>
 * 字段全部来自 Emby <code>/Items</code> 接口，额外补充了番号、可读文件大小与版本标签，
 * 便于前端直接展示与勾选。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbyMediaItem {

    /** Emby 条目 Id（删除时使用） */
    private String id;

    /** Emby 中的名称，通常形如 “SSIS-123 タイトル” */
    private String name;

    /** 文件在 Emby 服务器上的绝对路径 */
    private String path;

    /** 归一化番号，如 SSIS-123；无法识别时为 null */
    private String code;

    /** 番号来源：name / path / name+path / conflict，便于排查 */
    private String codeFrom;

    /** 名称和文件名都带有相同的明确番号。 */
    private boolean codeVerified;

    /** 制作年份 */
    private Integer year;

    /** 文件大小（字节） */
    private Long size;

    /** Emby 合并在此条目下的媒体版本数。 */
    private int mediaSourceCount;

    /** 可读文件大小，如 4.2 GB */
    private String sizeText;

    /** 分辨率，如 1920x1080 */
    private String resolution;

    /** 时长（分钟） */
    private Integer durationMinutes;

    /** 入库时间（Emby 返回的 ISO 字符串） */
    private String dateCreated;

    /** 版本标签：破解/无码、H265、AI修复、FHD、4K 等 */
    private List<String> tags;
}
