package org.holic.javspy.model;

import lombok.Data;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    private String genresstr;
    private String starstr;
    private String simstr;
    private String similarmoviesstr;

    public String getSimilarmoviesstr() {
        return similarMovies.stream()
                .filter(Objects::nonNull)
                .map(obj -> ((SimilarMovie)obj).getId())
                .collect(Collectors.joining(","));
    }

    public String getSimstr() {
        return samples.stream()
                .filter(Objects::nonNull)
                .map(obj -> ((Sample)obj).getId())
                .collect(Collectors.joining(","));
    }

    public String getStarstr() {
        return stars.stream()
                .filter(Objects::nonNull)
                .map(obj -> ((Star)obj).getName())
                .collect(Collectors.joining(","));
    }

    public String getGenresstr() {
        return genres.stream()
                .filter(Objects::nonNull)
                .map(obj -> ((Genre)obj).getId())
                .collect(Collectors.joining(","));
    }
}