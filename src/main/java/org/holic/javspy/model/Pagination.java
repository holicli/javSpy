package org.holic.javspy.model;

import lombok.Data;
import java.util.List;

@Data
public class Pagination {
    private int currentPage;
    private boolean hasNextPage;
    private int nextPage;
    private List<Integer> pages;
}
