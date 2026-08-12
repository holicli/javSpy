package org.holic.javspy.javbusapi.model;

import lombok.Data;

import java.io.Serializable;

/**
 * javbus API 列表中的一个影片卡片。
 */
@Data
public class JavbusApiVideoItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 番号 */
    private String code;

    /** 标题 */
    private String title;

    /** 封面图 */
    private String cover;

    /** 发售日期 */
    private String date;

    /** 标签（高清/字幕/新种） */
    private String tags;
}
