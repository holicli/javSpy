package org.holic.javspy.model;

import lombok.Data;

/**
 * 电影数据类
 */
@Data
public  class MovieItem {
    private final String id;
    private final String name;
    private final int year;
}