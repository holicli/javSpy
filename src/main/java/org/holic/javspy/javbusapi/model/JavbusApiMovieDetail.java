package org.holic.javspy.javbusapi.model;

import lombok.Data;

import java.util.List;

/**
 * 影片详情展示结果。
 */
@Data
public class JavbusApiMovieDetail {

    private String code;
    private boolean found;
    private JavbusApiMovieDisplay movie;
    private List<JavbusApiStar> stars;
    private List<JavbusApiMovieSample> samples;
}
