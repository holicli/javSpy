package org.holic.javspy.model;

import lombok.Data;

import java.util.List;

@Data
public class MovieResponse {
    private List<Movie> movies;
    private Pagination pagination;
}
