package org.holic.javspy.javbusapi.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * javbus API 一次抓取的结果：影片信息 + 磁力链接列表。
 */
@Data
public class JavbusApiScrapeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 影片信息 */
    private JavbusApiMovie movie;

    /** 磁力链接 */
    private List<JavbusApiMagnet> magnets = new ArrayList<>();

    public void addMagnet(JavbusApiMagnet magnet) {
        if (magnet != null) {
            magnets.add(magnet);
        }
    }
}
