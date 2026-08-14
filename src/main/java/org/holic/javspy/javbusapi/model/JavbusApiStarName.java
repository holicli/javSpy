package org.holic.javspy.javbusapi.model;

import lombok.Data;

/**
 * 影片-演员批量查询结果：番号 + 演员名。
 */
@Data
public class JavbusApiStarName {

    private String code;
    private String name;
}
