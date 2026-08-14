package org.holic.javspy.misc;

import lombok.Data;

/**
 * qBittorrent 种子列表信息。
 */
@Data
public class QbTorrentInfo {

    private String hash;
    private String name;
    private long size;
    private double progress;
    private String state;
    private long dlspeed;
    private long upspeed;
    private double ratio;
    private long addedOn;
    private long completionOn;
    private String savePath;
    private String category;
}
