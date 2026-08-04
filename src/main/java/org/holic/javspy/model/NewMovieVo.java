package org.holic.javspy.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.web.bind.annotation.RequestParam;

@Data
@Accessors(chain = true)
public class NewMovieVo {
    private Integer page;
    private String size;
    private String keyword;
}
