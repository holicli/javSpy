package org.holic.javspy.model;

import lombok.Data;

import java.util.List;

@Data
public class MovieDetail {
    private String id;
    private String title;
    private String img;
    private ImageSize imageSize;
    private String date;
    private Integer videoLength;
    private Director director;
    private Producer producer;
    private Publisher publisher;
    private Series series;
    private List<Genre> genres;
    private List<Star> stars;
    private List<Sample> samples;
    private List<SimilarMovie> similarMovies;
    private String gid;
    private String uc;
}