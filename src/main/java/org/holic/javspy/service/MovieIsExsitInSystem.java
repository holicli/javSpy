package org.holic.javspy.service;

import org.holic.javspy.misc.EmbyMovieChecker;
import org.holic.javspy.misc.QBittorrentAutoDownloader;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MovieIsExsitInSystem {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    //获取qbittorrent下载列表的name的list
    // 配置信息
    public List<String> qbittorrentDownloadNameQueue() {
        final String qbtUrl = "http://192.168.0.108:8085";
        final String username= "admin";
        final String password= "qwer1234";
        // 创建下载器
        QBittorrentAutoDownloader downloader = new QBittorrentAutoDownloader(qbtUrl);
        if (downloader.login(username, password)) {
            System.out.println("登录成功！");

            // 2. 获取当前种子列表
            List<Map<String, Object>> torrents = downloader.getTorrentList();
            List<String> list = torrents.stream()
                    .map(map -> {
                        Object name = map.get("name");
                        return name != null ? name.toString() : "";
                    })
                    .collect(Collectors.toList());
            return list;
        }else {
            return null;
        }
    }
    public List<String> embyNameList() {
        String serverUrl = "http://192.168.0.108:28096";
        String apiKey = "bd87f9a3632e4e409314ae45f71d99db";

        EmbyMovieChecker checker = new EmbyMovieChecker(serverUrl, apiKey);
        List<String> allMovieFromEmby = checker.getAllMovieFromEmby();

        return allMovieFromEmby;
    }
    public List<String> getqBitList() {
        List<String> qBitList = (List<String>) redisTemplate.opsForValue().get("qBitList");
        if (Objects.isNull(qBitList)){
            qBitList = qbittorrentDownloadNameQueue();
            redisTemplate.opsForValue().set("qBitList",qBitList,1L, TimeUnit.DAYS);
        }
        return qBitList;
    }
    public List<String> getEmbyList() {
        List<String> EmbyList = (List<String>) redisTemplate.opsForValue().get("EmbyList");
        if (Objects.isNull(EmbyList)){
            EmbyList = qbittorrentDownloadNameQueue();
            redisTemplate.opsForValue().set("EmbyList",EmbyList,1L, TimeUnit.DAYS);
        }
        return EmbyList;
    }
    public Boolean isInQbit(String name){
        List<String> list = getqBitList();
        String name2 = name.replace("-", "");
        boolean exists = list.stream()
                .anyMatch(item -> item.contains(name));
        boolean exists2 = list.stream()
                .anyMatch(item -> item.contains(name2));
        return exists || exists2;
    }
    public Boolean isInEmby(String name){
        return getEmbyList().contains(name);
    }
}
