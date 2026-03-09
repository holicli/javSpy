package org.holic.javspy.misc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * qBittorrent自动下载工具类
 * 支持：登录认证、添加下载、管理种子、监控状态等
 */
public class QBittorrentAutoDownloader {

    private final String qbtUrl;
    private String sid = null;
    private boolean isAuthenticated = false;

    public QBittorrentAutoDownloader(String qbtUrl) {
        this.qbtUrl = qbtUrl;
    }
    /**
     * 登录到qBittorrent
     * @param username 用户名（默认：admin）
     * @param password 密码（默认：adminadmin）
     * @return 登录是否成功
     */
    public boolean login(String username, String password) {
        try {
            String loginUrl = qbtUrl + "/api/v2/auth/login";

            // 构建POST数据
            String postData = "username=" + URLEncoder.encode(username, "UTF-8") +
                    "&password=" + URLEncoder.encode(password, "UTF-8");

            HttpURLConnection conn = (HttpURLConnection) new URL(loginUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            // 发送POST数据
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            // 检查响应头中的Cookie
            String cookieHeader = conn.getHeaderField("Set-Cookie");
            if (cookieHeader != null && cookieHeader.contains("SID")) {
                // 提取SID
                String[] cookies = cookieHeader.split(";");
                for (String cookie : cookies) {
                    if (cookie.trim().startsWith("SID=")) {
                        sid = cookie.trim();
                        break;
                    }
                }
            }

            // 读取响应
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                // 登录成功响应应为"Ok."
                if (response.toString().equals("Ok.")) {
                    isAuthenticated = true;
                    System.out.println("登录成功！SID: " + sid);
                    return true;
                }
            }

            conn.disconnect();

        } catch (Exception e) {
            System.err.println("登录失败: " + e.getMessage());
            e.printStackTrace();
        }

        isAuthenticated = false;
        return false;
    }

    /**
     * 添加磁力链接下载
     * @param magnet 磁力链接
     * @param savePath 保存路径（可选）
     * @param category 分类（可选）
     * @return 是否添加成功
     */
    public boolean addMagnet(String magnet, String savePath, String category) {
        if (!isAuthenticated) {
            System.err.println("请先登录！");
            return false;
        }

        try {
            String url = qbtUrl + "/api/v2/torrents/add";

            // 构建POST数据
            StringBuilder postData = new StringBuilder();
            postData.append("urls=").append(URLEncoder.encode(magnet, "UTF-8"));

            if (savePath != null && !savePath.isEmpty()) {
                postData.append("&savepath=").append(URLEncoder.encode(savePath, "UTF-8"));
            }

            if (category != null && !category.isEmpty()) {
                postData.append("&category=").append(URLEncoder.encode(category, "UTF-8"));
            }

            // 添加更多选项
            postData.append("&autoTMM=true");
            postData.append("&paused=false");  // 不暂停，立即开始下载
//            postData.append("&skip_checking=true");  // 跳过哈希检查
            postData.append("&stopCondition=None");



            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Cookie", sid);
            conn.setDoOutput(true);

            // 发送POST数据
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = postData.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                System.out.println("磁力链接添加成功: " + magnet);
                return true;
            } else {
                System.err.println("添加失败，HTTP代码: " + responseCode);
                // 读取错误信息
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    String errorLine;
                    while ((errorLine = br.readLine()) != null) {
                        System.err.println("错误信息: " + errorLine);
                    }
                }
            }

            conn.disconnect();

        } catch (Exception e) {
            System.err.println("添加磁力链接失败: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 添加种子文件下载
     * @param torrentFile 种子文件路径
     * @param savePath 保存路径（可选）
     * @param category 分类（可选）
     * @return 是否添加成功
     */
    public boolean addTorrentFile(String torrentFile, String savePath, String category) {
        if (!isAuthenticated) {
            System.err.println("请先登录！");
            return false;
        }

        try {
            String url = qbtUrl + "/api/v2/torrents/add";

            // 读取种子文件
            File file = new File(torrentFile);
            if (!file.exists()) {
                System.err.println("种子文件不存在: " + torrentFile);
                return false;
            }

            byte[] fileBytes;
            try (FileInputStream fis = new FileInputStream(file);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                fileBytes = baos.toByteArray();
            }

            // 构建多部分表单数据
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Cookie", sid);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

                // 添加种子文件部分
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"torrents\"; filename=\"")
                        .append(file.getName()).append("\"\r\n");
                writer.append("Content-Type: application/x-bittorrent\r\n\r\n");
                writer.flush();

                os.write(fileBytes);
                os.flush();

                writer.append("\r\n");

                // 添加其他参数
                if (savePath != null && !savePath.isEmpty()) {
                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"savepath\"\r\n\r\n");
                    writer.append(savePath).append("\r\n");
                }

                if (category != null && !category.isEmpty()) {
                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"category\"\r\n\r\n");
                    writer.append(category).append("\r\n");
                }

                // 添加下载选项
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"paused\"\r\n\r\n");
                writer.append("false\r\n");

                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"skip_checking\"\r\n\r\n");
                writer.append("true\r\n");

                writer.append("--").append(boundary).append("--\r\n");
                writer.flush();
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                System.out.println("种子文件添加成功: " + torrentFile);
                return true;
            } else {
                System.err.println("添加失败，HTTP代码: " + responseCode);
            }

            conn.disconnect();

        } catch (Exception e) {
            System.err.println("添加种子文件失败: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 获取所有种子列表
     * @return 种子信息列表
     */
    public List<Map<String, Object>> getTorrentList() {
        List<Map<String, Object>> torrents = new ArrayList<>();

        if (!isAuthenticated) {
            System.err.println("请先登录！");
            return torrents;
        }

        try {
            String url = qbtUrl + "/api/v2/torrents/info";

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", sid);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }

                    JSONArray jsonArray = new JSONArray(response.toString());

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject torrent = jsonArray.getJSONObject(i);
                        Map<String, Object> torrentInfo = new HashMap<>();

                        torrentInfo.put("hash", torrent.getString("hash"));
                        torrentInfo.put("name", torrent.getString("name"));
                        torrentInfo.put("size", torrent.getLong("size"));
                        torrentInfo.put("progress", torrent.getDouble("progress"));
                        torrentInfo.put("state", torrent.getString("state"));
                        torrentInfo.put("dlspeed", torrent.getLong("dlspeed"));
                        torrentInfo.put("upspeed", torrent.getLong("upspeed"));
                        torrentInfo.put("ratio", torrent.getDouble("ratio"));
                        torrentInfo.put("added_on", torrent.getLong("added_on"));
                        torrentInfo.put("completion_on", torrent.optLong("completion_on", 0));
                        torrentInfo.put("save_path", torrent.getString("save_path"));
                        torrentInfo.put("category", torrent.optString("category", ""));

                        torrents.add(torrentInfo);
                    }
                }
            }

            conn.disconnect();

        } catch (Exception e) {
            System.err.println("获取种子列表失败: " + e.getMessage());
            e.printStackTrace();
        }

        return torrents;
    }

    /**
     * 获取单个种子的详细信息
     * @param torrentHash 种子哈希
     * @return 详细信息
     */
    public Map<String, Object> getTorrentDetails(String torrentHash) {
        Map<String, Object> details = new HashMap<>();

        if (!isAuthenticated) {
            System.err.println("请先登录！");
            return details;
        }

        try {
            String url = qbtUrl + "/api/v2/torrents/properties?hash=" + torrentHash;

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", sid);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject json = new JSONObject(response.toString());

                    // 提取详细信息
                    details.put("hash", torrentHash);
                    details.put("name", json.optString("name", ""));
                    details.put("save_path", json.optString("save_path", ""));
                    details.put("total_size", json.optLong("total_size", 0));
                    details.put("downloaded", json.optLong("downloaded", 0));
                    details.put("uploaded", json.optLong("uploaded", 0));
                    details.put("ratio", json.optDouble("ratio", 0.0));
                    details.put("seeds", json.optInt("seeds", 0));
                    details.put("peers", json.optInt("peers", 0));
                    details.put("dl_speed", json.optLong("dl_speed", 0));
                    details.put("up_speed", json.optLong("up_speed", 0));
                    details.put("eta", json.optLong("eta", 0));
                    details.put("creation_date", json.optLong("creation_date", 0));
                    details.put("comment", json.optString("comment", ""));
                    details.put("total_pieces", json.optInt("total_pieces", 0));
                    details.put("piece_size", json.optLong("piece_size", 0));
                    details.put("pieces_have", json.optInt("pieces_have", 0));
                }
            }

            conn.disconnect();

        } catch (Exception e) {
            System.err.println("获取种子详情失败: " + e.getMessage());
            e.printStackTrace();
        }

        return details;
    }

    /**
     * 暂停种子
     * @param torrentHash 种子哈希
     * @return 是否成功
     */
    public boolean pauseTorrent(String torrentHash) {
        return controlTorrent(torrentHash, "pause");
    }

    /**
     * 恢复种子
     * @param torrentHash 种子哈希
     * @return 是否成功
     */
    public boolean resumeTorrent(String torrentHash) {
        return controlTorrent(torrentHash, "resume");
    }

    /**
     * 删除种子（不删除文件）
     * @param torrentHash 种子哈希
     * @return 是否成功
     */
    public boolean deleteTorrent(String torrentHash) {
        return controlTorrent(torrentHash, "delete");
    }

    /**
     * 删除种子和文件
     * @param torrentHash 种子哈希
     * @return 是否成功
     */
    public boolean deleteTorrentWithFiles(String torrentHash) {
        try {
            String url = qbtUrl + "/api/v2/torrents/delete";

            String postData = "hashes=" + torrentHash + "&deleteFiles=true";

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Cookie", sid);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            conn.disconnect();

            return responseCode == 200;

        } catch (Exception e) {
            System.err.println("删除种子失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 控制种子的通用方法
     */
    private boolean controlTorrent(String torrentHash, String action) {
        if (!isAuthenticated) {
            System.err.println("请先登录！");
            return false;
        }

        try {
            String url = qbtUrl + "/api/v2/torrents/" + action;

            String postData = "hashes=" + torrentHash;

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Cookie", sid);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            conn.disconnect();

            return responseCode == 200;

        } catch (Exception e) {
            System.err.println("控制种子失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取全局下载/上传速度
     * @return 速度信息
     */
    public Map<String, Long> getGlobalSpeed() {
        Map<String, Long> speeds = new HashMap<>();

        if (!isAuthenticated) {
            System.err.println("请先登录！");
            return speeds;
        }

        try {
            String url = qbtUrl + "/api/v2/transfer/info";

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", sid);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject json = new JSONObject(response.toString());
                    speeds.put("dl_info_speed", json.getLong("dl_info_speed"));
                    speeds.put("up_info_speed", json.getLong("up_info_speed"));
                    speeds.put("dl_info_data", json.getLong("dl_info_data"));
                    speeds.put("up_info_data", json.getLong("up_info_data"));
                }
            }

            conn.disconnect();

        } catch (Exception e) {
            System.err.println("获取全局速度失败: " + e.getMessage());
        }

        return speeds;
    }

    /**
     * 获取偏好设置
     * @return 偏好设置
     */
    public JSONObject getPreferences() {
        if (!isAuthenticated) {
            System.err.println("请先登录！");
            return new JSONObject();
        }

        try {
            String url = qbtUrl + "/api/v2/app/preferences";

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", sid);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }

                    return new JSONObject(response.toString());
                }
            }

            conn.disconnect();

        } catch (Exception e) {
            System.err.println("获取偏好设置失败: " + e.getMessage());
        }

        return new JSONObject();
    }

    /**
     * 监控下载任务（自动下载完成后执行操作）
     * @param completionCallback 下载完成回调
     * @param checkInterval 检查间隔（秒）
     */
    public void monitorDownloads(Runnable completionCallback, int checkInterval) {
        if (!isAuthenticated) {
            System.err.println("请先登录！");
            return;
        }

        new Thread(() -> {
            try {
                Set<String> completedTorrents = new HashSet<>();

                while (true) {
                    List<Map<String, Object>> torrents = getTorrentList();

                    for (Map<String, Object> torrent : torrents) {
                        String hash = (String) torrent.get("hash");
                        double progress = (Double) torrent.get("progress");
                        String state = (String) torrent.get("state");

                        // 检查是否下载完成（进度=1）
                        if (progress >= 1.0 && !completedTorrents.contains(hash)) {
                            System.out.println("种子下载完成: " + torrent.get("name"));
                            completedTorrents.add(hash);

                            // 执行回调
                            if (completionCallback != null) {
                                completionCallback.run();
                            }

                            // 这里可以添加更多操作，比如：
                            // 1. 移动到指定目录
                            // 2. 发送通知
                            // 3. 调用其他系统
                        }
                    }

                    Thread.sleep(checkInterval * 1000L);
                }
            } catch (InterruptedException e) {
                System.out.println("监控线程被中断");
            } catch (Exception e) {
                System.err.println("监控失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 自动下载管理器（批量处理）
     */
    public static class AutoDownloadManager {
        private final QBittorrentAutoDownloader qbt;
        private final List<String> downloadQueue;
        private boolean isRunning;

        public AutoDownloadManager(QBittorrentAutoDownloader qbt) {
            this.qbt = qbt;
            this.downloadQueue = new ArrayList<>();
            this.isRunning = false;
        }

        /**
         * 添加到下载队列
         */
        public void addToQueue(String magnetOrFile, boolean isMagnet) {
            synchronized (downloadQueue) {
                if (isMagnet) {
                    downloadQueue.add("MAGNET:" + magnetOrFile);
                } else {
                    downloadQueue.add("FILE:" + magnetOrFile);
                }
                System.out.println("已添加到队列，当前队列大小: " + downloadQueue.size());
            }
        }

        /**
         * 从队列中移除
         */
        public void removeFromQueue(int index) {
            synchronized (downloadQueue) {
                if (index >= 0 && index < downloadQueue.size()) {
                    downloadQueue.remove(index);
                }
            }
        }

        /**
         * 开始处理队列
         * @param maxConcurrent 最大并发数
         * @param interval 间隔时间（秒）
         */
        public void startProcessing(int maxConcurrent, int interval) {
            if (isRunning) {
                System.out.println("管理器已在运行中");
                return;
            }

            isRunning = true;
            new Thread(() -> {
                System.out.println("开始自动下载管理器...");

                try {
                    while (isRunning) {
                        // 获取当前活跃下载数
                        List<Map<String, Object>> activeTorrents = qbt.getTorrentList();
                        int activeCount = 0;
                        for (Map<String, Object> torrent : activeTorrents) {
                            String state = (String) torrent.get("state");
                            if (!"paused".equals(state) && !"finished".equals(state)) {
                                activeCount++;
                            }
                        }

                        // 如果有空闲位置并且队列不为空
                        if (activeCount < maxConcurrent && !downloadQueue.isEmpty()) {
                            synchronized (downloadQueue) {
                                String item = downloadQueue.remove(0);

                                if (item.startsWith("MAGNET:")) {
                                    String magnet = item.substring(7);
                                    System.out.println("开始下载磁力链接: " + magnet);
                                    qbt.addMagnet(magnet, null, "auto-download");
                                } else if (item.startsWith("FILE:")) {
                                    String filePath = item.substring(5);
                                    System.out.println("开始下载种子文件: " + filePath);
                                    qbt.addTorrentFile(filePath, null, "auto-download");
                                }
                            }
                        }

                        Thread.sleep(interval * 1000L);
                    }
                } catch (InterruptedException e) {
                    System.out.println("管理器线程被中断");
                } catch (Exception e) {
                    System.err.println("管理器运行出错: " + e.getMessage());
                }

                System.out.println("自动下载管理器已停止");
            }).start();
        }

        /**
         * 停止管理器
         */
        public void stopProcessing() {
            isRunning = false;
            System.out.println("正在停止自动下载管理器...");
        }

        /**
         * 获取队列信息
         */
        public List<String> getQueueInfo() {
            synchronized (downloadQueue) {
                return new ArrayList<>(downloadQueue);
            }
        }


    }

    /**
     * 示例：主函数测试
     */
    public static void main(String[] args) {
        // 配置信息
        final String qbtUrl = "http://192.168.0.108:8085";

        final String username= "admin";

        final String password= "qwer1234";
        // 创建下载器
        QBittorrentAutoDownloader downloader = new QBittorrentAutoDownloader(qbtUrl);

        // 1. 登录
        if (downloader.login(username, password)) {
            System.out.println("登录成功！");

            // 2. 获取当前种子列表
            List<Map<String, Object>> torrents = downloader.getTorrentList();
            System.out.println("当前种子数量: " + torrents.size());

            // 3. 添加磁力链接下载
//            String magnetLink = "magnet:?xt=urn:btih:TESTHASH123456789&dn=Test+Movie";
//            downloader.addMagnet(magnetLink, "/downloads/movies", "movies");

            // 4. 添加种子文件下载
            // downloader.addTorrentFile("/path/to/torrent.torrent", "/downloads/movies", "movies");

            // 5. 创建自动下载管理器
            AutoDownloadManager manager = new AutoDownloadManager(downloader);
            // 添加一些磁力链接到队列
//            manager.addToQueue("magnet:?xt=urn:btih:35a726d3f663a8ed2548fef31ddc988c7f2ee33c", true);

            // 启动管理器（最多同时下载2个，每10秒检查一次）
//            manager.startProcessing(2, 10);

            // 6. 启动下载监控
            downloader.monitorDownloads(() -> {
                System.out.println("有下载任务完成了！");
                // 这里可以添加完成后的处理逻辑
            }, 30); // 每30秒检查一次

            // 7. 获取全局速度
            Map<String, Long> speeds = downloader.getGlobalSpeed();
            System.out.println("下载速度: " + speeds.getOrDefault("dl_info_speed", 0L) + " B/s");
            System.out.println("上传速度: " + speeds.getOrDefault("up_info_speed", 0L) + " B/s");

            // 8. 等待一段时间，然后停止管理器
            try {
                Thread.sleep(60000); // 等待60秒
                manager.stopProcessing();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } else {
            System.err.println("登录失败，请检查配置！");
        }
    }
}