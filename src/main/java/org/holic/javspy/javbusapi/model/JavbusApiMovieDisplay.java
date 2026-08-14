package org.holic.javspy.javbusapi.model;

import lombok.Data;

/**
 * javbus 影片列表展示行。
 */
@Data
public class JavbusApiMovieDisplay {

    private String code;
    private String title;
    private String coverUrl;
    private String coverHd;
    private String releaseDate;
    private Integer duration;
    private String director;
    private String studio;
    private String publisher;
    private String series;
    private String genres;
    private String actors;
    private String gid;
    private String uc;
    private int magnetCount;
    private boolean embyExists;
}
