package org.holic.javspy.javbusapi.model;

import lombok.Data;

/**
 * 影片磁力数量批量统计结果：番号 + 磁力数量。
 */
@Data
public class JavbusApiMagnetCount {

    private String code;
    private Long cnt;
}
