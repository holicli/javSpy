package org.holic.javspy.javbusapi.model;

import lombok.Data;

/**
 * 影片-类别批量查询结果：番号 + 类别名。
 */
@Data
public class JavbusApiGenreName {

    private String code;
    private String name;
}
