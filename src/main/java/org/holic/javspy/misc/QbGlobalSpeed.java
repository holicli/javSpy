package org.holic.javspy.misc;

import lombok.Data;

/**
 * qBittorrent 全局传输速度。
 */
@Data
public class QbGlobalSpeed {

    private long dlInfoSpeed;
    private long upInfoSpeed;
    private long dlInfoData;
    private long upInfoData;
}
