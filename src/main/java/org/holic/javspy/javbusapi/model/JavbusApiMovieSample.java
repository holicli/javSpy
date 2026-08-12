package org.holic.javspy.javbusapi.model;

import lombok.Data;

import java.io.Serializable;

/**
 * javbus 影片预览图，对应 javbus_movie_sample 表。
 */
@Data
public class JavbusApiMovieSample implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预览图 ID（API 返回的 id） */
    private String sampleId;

    /** alt */
    private String alt;

    /** 大图地址 */
    private String src;

    /** 缩略图地址 */
    private String thumbnail;
}
