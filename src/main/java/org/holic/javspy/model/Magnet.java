package org.holic.javspy.model;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 磁力链接实体类
 * 对应接口返回的磁力链接信息
 */
@Data
public class Magnet {

    /**
     * 唯一标识ID
     */
    private String id;
    /**
     * 磁力链接地址
     */
    private String link;

    /**
     * 是否高清
     */
    @JsonProperty("isHD")
    private boolean isHD;

    /**
     * 标题/番号
     */
    private String title;

    /**
     * 文件大小（格式化后，如 "6.57GB"）
     */
    private String size;

    /**
     * 文件大小（字节数）
     */
    @JsonProperty("numberSize")
    private long numberSize;

    /**
     * 分享日期
     */
    @JsonProperty("shareDate")
    private String shareDate;

    /**
     * 是否包含字幕
     */
    @JsonProperty("hasSubtitle")
    private boolean hasSubtitle;

    private String gid;

}
