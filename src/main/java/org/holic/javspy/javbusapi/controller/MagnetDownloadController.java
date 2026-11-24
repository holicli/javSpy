package org.holic.javspy.javbusapi.controller;

import org.holic.javspy.javbusapi.client.QbittorrentClient;
import org.holic.javspy.javbusapi.client.QbittorrentClient.AddResult;
import org.holic.javspy.misc.WebResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MagnetDownloadController {

    private final QbittorrentClient qbittorrent;

    public MagnetDownloadController(QbittorrentClient qbittorrent) {
        this.qbittorrent = qbittorrent;
    }

    @PostMapping(path = "/javbus-api/magnets/download", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WebResult<AddResult> download(@RequestBody Map<String, String> request) {
        try {
            AddResult result = qbittorrent.addMagnet(request.get("code"), request.get("magnet"));
            String message = result == AddResult.ALREADY_EXISTS ? "已存在" : "已提交到 qBittorrent";
            return new WebResult<>(true, result, message);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return new WebResult<>(false, e.getMessage());
        }
    }
}
