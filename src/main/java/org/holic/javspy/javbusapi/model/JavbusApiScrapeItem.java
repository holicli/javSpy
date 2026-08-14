package org.holic.javspy.javbusapi.model;

import lombok.Data;

import java.util.List;

/**
 * 抓取结果行：列表项、入库结果或失败信息。
 */
@Data
public class JavbusApiScrapeItem {

    private String code;
    private String title;
    private String cover;
    private String date;
    private String status;
    private String message;
    private JavbusApiMovie movie;
    private String actors;
    private Integer duration;
    private String genres;
    private String director;
    private String studio;
    private String series;
    private List<JavbusApiStar> start;
    private String coverUrl;
    private String HDUrl;
    private String releaseDate;
    private int magnetCount;

    /** 从列表展示行复制展示字段。 */
    public static JavbusApiScrapeItem fromDisplay(JavbusApiMovieDisplay display) {
        JavbusApiScrapeItem item = new JavbusApiScrapeItem();
        if (display != null) {
            item.setCode(display.getCode());
            item.setTitle(display.getTitle());
            item.setCover(display.getCoverUrl());
            item.setDate(display.getReleaseDate());
            item.setActors(display.getActors());
            item.setDuration(display.getDuration());
            item.setGenres(display.getGenres());
            item.setDirector(display.getDirector());
            item.setStudio(display.getStudio());
            item.setSeries(display.getSeries());
            item.setCoverUrl(display.getCoverUrl());
            item.setHDUrl(display.getCoverHd());
            item.setReleaseDate(display.getReleaseDate());
            item.setMagnetCount(display.getMagnetCount());
        }
        return item;
    }
}
