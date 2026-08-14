package org.holic.javspy.javbusapi.model;

import lombok.Data;

/**
 * 后台一键刮削任务状态。
 */
@Data
public class JavbusApiScrapeStatus {

    private boolean running;
    private int page;
    private int count;
    private String message;
    private String stopReason;
    private String stopCode;
}
