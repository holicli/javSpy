package org.holic.javspy.javbus.model;

import lombok.Data;

import java.io.Serializable;

/**
 * javbus 首页/搜索列表中的一个影片卡片（轻量展示用，不落库）。
 */
@Data
public class JavbusVideoItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标题 */
    private String title;

    /** 番号 */
    private String code;

    /** 封面图地址 */
    private String cover;

    /** 详情页地址 */
    private String url;
}
