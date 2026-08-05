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
            redisTemplate.opsForValue().set("qBitList",qBitList,1L, TimeUnit.HOURS);
        }
        return qBitList;
    }
    public List<String> getEmbyList() {
        List<String> embyList = (List<String>) redisTemplate.opsForValue().get("EmbyList");
        if (Objects.isNull(embyList) || embyList.isEmpty()) {
            embyList = embyNameList();
            if (embyList != null && !embyList.isEmpty()) {
                redisTemplate.opsForValue().set("EmbyList", embyList, 1L, TimeUnit.HOURS);
            }
        }
        return embyList;
    }

    /** 每次打开首页时尝试从 Emby 拉取影片名列表，能获取到内容就刷新 Redis 缓存。 */
    public void refreshEmbyListIfAvailable() {
        try {
            List<String> embyList = embyNameList();
            if (embyList != null && !embyList.isEmpty()) {
                redisTemplate.opsForValue().set("EmbyList", embyList, 1L, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            System.err.println("刷新 Emby 影片名列表失败: " + e.getMessage());
        }
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
        List<String> embyList = getEmbyList();
        return embyList != null && embyList.contains(name);
    }
}
