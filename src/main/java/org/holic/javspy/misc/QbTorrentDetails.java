package org.holic.javspy.misc;

import lombok.Data;

/**
 * qBittorrent 单个种子详细信息。
 */
@Data
public class QbTorrentDetails {

    private String hash;
    private String name;
    private String savePath;
    private long totalSize;
    private long downloaded;
    private long uploaded;
    private double ratio;
    private int seeds;
    private int peers;
    private long dlSpeed;
    private long upSpeed;
    private long eta;
    private long creationDate;
    private String comment;
    private int totalPieces;
    private long pieceSize;
    private int piecesHave;
}
