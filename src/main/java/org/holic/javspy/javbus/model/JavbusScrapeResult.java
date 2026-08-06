package org.holic.javspy.javbus.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次刮削的结果：影片信息 + 磁力链接列表。
 */
@Data
public class JavbusScrapeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 影片信息 */
    private JavbusMovie movie;

    /** 磁力链接 */
    private List<JavbusMagnet> magnets = new ArrayList<>();

    public void addMagnet(JavbusMagnet magnet) {
        if (magnet != null) {
            magnets.add(magnet);
        }
    }
}
