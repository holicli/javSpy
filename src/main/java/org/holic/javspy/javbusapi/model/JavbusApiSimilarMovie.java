package org.holic.javspy.javbusapi.model;

import lombok.Data;

import java.io.Serializable;

/**
 * javbus 相似影片，对应 javbus_movie_similar 表。
 */
@Data
public class JavbusApiSimilarMovie implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 相似影片番号 */
    private String code;

    /** 相似影片标题 */
    private String title;

    /** 相似影片封面 */
    private String img;
}
