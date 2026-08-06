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
        // 失败冷却：上一次尝试失败后 5 分钟内不再重试，避免 Emby 挂掉时每次请求都阻塞
        String cooldownKey = "EmbyRefreshCooldown";
        try {
            Object marker = redisTemplate.opsForValue().get(cooldownKey);
            if (marker != null) {
                return;
            }
        } catch (Exception e) {
            // Redis 不可用时忽略冷却逻辑，直接尝试刷新
            System.err.println("读取 Emby 冷却标记失败: " + e.getMessage());
        }
        try {
            List<String> embyList = embyNameList();
            if (embyList != null && !embyList.isEmpty()) {
                redisTemplate.opsForValue().set("EmbyList", embyList, 1L, TimeUnit.DAYS);
                redisTemplate.delete(cooldownKey);
            }
        } catch (Exception e) {
            System.err.println("刷新 Emby 影片名列表失败: " + e.getMessage());
            // 刷新失败：设置冷却标记，5 分钟内不再重试
            try {
                redisTemplate.opsForValue().set(cooldownKey, "1", 5L, TimeUnit.MINUTES);
            } catch (Exception ignored) {
                // 忽略冷却标记写入失败
            }
        }
    }

    /**
     * 探活：请求 Emby 公开接口（/emby/System/Info/Public），
     * 返回 200 说明服务可用；异常/超时/非 200 都视为不可用。
     * 配合短超时使用，避免 Emby 挂掉时阻塞调用方。
     */
    public boolean isEmbyAvailable() {
        final String serverUrl = "http://192.168.0.108:28096";
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(serverUrl + "/emby/System/Info/Public");
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            System.err.println("Emby 探活失败（视为不可用）: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
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
