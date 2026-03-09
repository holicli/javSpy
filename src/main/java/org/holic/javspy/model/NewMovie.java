package org.holic.javspy.model;

import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class NewMovie {
    private String coverUrl;
    private String actors;
    private String isSingle;
    private String linkText;
    private String linkUrl;
    private String size;
    private Boolean exists;
    private String idNumber;
    private String title;
    private String date;
    private Boolean isDownload;

    public void fullMovie(Movie movie,MovieDetail movieDetail,Magnet magnet){
        this.coverUrl = movie.getImg();
        this.title = movie.getTitle();
        this.idNumber = movie.getId();
        this.date = movie.getDate();
        if (Objects.nonNull(magnet)) {
            this.linkText = magnet.getTitle();
            this.linkUrl = magnet.getLink();
            this.size = magnet.getSize();
            this.actors = movieDetail.getActors();
        }
    }
}
