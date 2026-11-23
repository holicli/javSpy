package org.holic.javspy.misc;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class EmbyMovieChecker {

    private final String serverUrl;
    private final String apiKey;
    private final boolean enabled;

    /** 连接超时（毫秒），Emby 不可达时快速失败，避免阻塞调用方 */
    private static final int CONNECT_TIMEOUT_MS = 5000;
    /** 读取超时（毫秒），Emby 响应缓慢时快速失败 */
    private static final int READ_TIMEOUT_MS = 10000;
    /** 全量清单/删除等操作的读取超时（毫秒），2823 部影片的清单响应体较大 */
    private static final int REQUEST_TIMEOUT_MS = 60000;
    private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    /**
     * 构造函数
     * @param serverUrl Emby服务器地址，例如：http://192.168.1.100:8096
     * @param apiKey Emby API密钥
     */
    public EmbyMovieChecker(@Value("${conf.emby.serverUrl:}") String serverUrl,
                            @Value("${conf.emby.apiKey:}") String apiKey,
                            @Value("${conf.emby.enabled:false}") boolean enabled) {
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.enabled = enabled;
    }

    private boolean isConfigured() {
        return enabled && StringUtils.isNotBlank(serverUrl) && StringUtils.isNotBlank(apiKey);
    }

    static boolean isValidItemId(String id) {
        return id != null && ITEM_ID.matcher(id).matches();
    }

    /**
     * 通过电影名称检查是否存在
     * @param movieName 电影名称
     * @return 如果存在返回true，否则返回false
     */
    public boolean checkMovieExistsByName(String movieName) {
        try {
            // 构建API请求URL
            String encodedName = java.net.URLEncoder.encode(movieName, "UTF-8");
            String apiUrl = String.format("%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&SearchTerm=%s",
                    serverUrl, encodedName);

            // 发送HTTP请求
            String response = sendGetRequest(apiUrl);

            // 解析JSON响应
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray items = jsonResponse.getJSONArray("Items");

            // 检查是否有匹配的影片
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String itemName = item.getString("Name");
                if (itemName.toLowerCase().contains(movieName.toLowerCase())) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            System.err.println("检查影片时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 通过IMDb ID检查是否存在
     * @param imdbId IMDb ID（例如：tt0111161）
     * @return 如果存在返回true，否则返回false
     */
    public boolean checkMovieExistsByImdbId(String imdbId) {
        try {
            // 构建API请求URL
            String apiUrl = String.format("%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&AnyProviderIdEquals=Imdb.%s",
                    serverUrl, imdbId);

            String response = sendGetRequest(apiUrl);
            JSONObject jsonResponse = new JSONObject(response);

            return jsonResponse.getInt("TotalRecordCount") > 0;

        } catch (Exception e) {
            System.err.println("检查影片时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 通过TMDB ID检查是否存在
     * @param tmdbId TMDB ID
     * @return 如果存在返回true，否则返回false
     */
    public boolean checkMovieExistsByTmdbId(String tmdbId) {
        try {
            String apiUrl = String.format("%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&AnyProviderIdEquals=Tmdb.%s",
                    serverUrl, tmdbId);

            String response = sendGetRequest(apiUrl);
            JSONObject jsonResponse = new JSONObject(response);

            return jsonResponse.getInt("TotalRecordCount") > 0;

        } catch (Exception e) {
            System.err.println("检查影片时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 发送GET请求
     */
    private String sendGetRequest(String urlString) throws IOException {
        return request(urlString, "GET", READ_TIMEOUT_MS);
    }

    /**
     * 发送请求并读取响应体（响应体统一按 UTF-8 解析，避免日文/中文标题乱码）。
     *
     * @param urlString 请求地址
     * @param method    HTTP 方法，GET 或 POST
     * @return 响应体文本；HTTP 状态非 200/204 时抛出 IOException
     */
    private String request(String urlString, String method) throws IOException {
        return request(urlString, method, REQUEST_TIMEOUT_MS);
    }

    /**
     * 发送请求并读取响应体，可指定读取超时。
     *
     * @param urlString     请求地址
     * @param method        HTTP 方法，GET 或 POST
     * @param readTimeoutMs 读取超时（毫秒）
     * @return 响应体文本；HTTP 状态非 200/204 时抛出 IOException
     */
    private String request(String urlString, String method, int readTimeoutMs) throws IOException {
        if (!isConfigured()) {
            throw new IOException("Emby 未启用或未配置");
        }
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-Emby-Token", apiKey);
        conn.setRequestProperty("Content-Length", "0");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(readTimeoutMs);

        conn.setInstanceFollowRedirects(false);
        try {
            int responseCode = conn.getResponseCode();
            if (responseCode != 200 && responseCode != 204) {
                throw new IOException("Emby HTTP " + responseCode);
            }
            return readStream(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    /** 一次性读取输入流并按 UTF-8 解码。 */
    private static String readStream(java.io.InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    /**
     * 获取影片的详细信息
     * @param movieName 电影名称
     * @return 包含影片信息的JSON对象，如果未找到返回null
     */
    public JSONObject getMovieDetails(String movieName) {
        try {
            String encodedName = java.net.URLEncoder.encode(movieName, "UTF-8");
            String apiUrl = String.format("%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&SearchTerm=%s",
                    serverUrl, encodedName);

            String response = sendGetRequest(apiUrl);
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray items = jsonResponse.getJSONArray("Items");

            if (items.length() > 0) {
                return items.getJSONObject(0);
            }
        } catch (Exception e) {
            System.err.println("获取影片详情时出错: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取所有影片
     * @return 如果存在返回true，否则返回false
     */
    public List<String> getAllMovieFromEmby() {
        if (!isConfigured()) {
            return null;
        }
        Set<String> names = new LinkedHashSet<>();
        try {
            // 构建API请求URL /emby/Items?Recursive=true&IncludeItemTypes=Movie
            String apiUrl = String.format("%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&Limit=100000",
                    serverUrl);

            // 发送HTTP请求
            String response = sendGetRequest(apiUrl);

            // 解析JSON响应
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray items = jsonResponse.getJSONArray("Items");
            if (jsonResponse.optInt("TotalRecordCount", items.length()) > items.length()) {
                throw new IOException("Emby 返回的番号清单不完整");
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String itemName = item.getString("Name");
                names.add(getStrBeforeSpace(itemName));
            }
            return new ArrayList<>(names);

        } catch (Exception e) {
            System.err.println("检查影片时出错: " + e.getMessage());
            return null;
        }
    }

    public String getStrBeforeSpace(String str){
        int firstSpaceIndex = str.indexOf(' ');

        if (firstSpaceIndex != -1) {
            String result = str.substring(0, firstSpaceIndex);
            return result;
        } else {
            return str;
        }
    }

    /**
     * 获取 Emby 中全部影片番号集合；配置缺失或请求失败时返回空集合。
     */
    public Set<String> getAllMovieCodes() {
        if (!isConfigured()) {
            return Collections.emptySet();
        }
        Set<String> codes = new HashSet<>();
        List<String> names = getAllMovieFromEmby();
        if (names != null) {
            for (String name : names) {
                if (StringUtils.isNotBlank(name)) {
                    codes.add(name.trim().toUpperCase());
                }
            }
        }
        return codes;
    }

    /** 拉取影片清单时请求的字段，MediaSources 提供文件大小与分辨率。 */
    private static final String ITEM_FIELDS = "Path,ProductionYear,MediaSources,DateCreated,RunTimeTicks";

    /**
     * 获取 Emby 中全部影片条目（含 Id、Name、Path、大小、分辨率等），用于重复检测页面。
     *
     * @return 影片条目列表；配置缺失或请求失败时返回 null
     */
    public List<EmbyMediaItem> getAllMovieItems() {
        if (!isConfigured()) {
            return null;
        }
        List<EmbyMediaItem> result = new ArrayList<>();
        try {
            String apiUrl = String.format(
                    "%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&Limit=100000&Fields=%s",
                    serverUrl, ITEM_FIELDS);
            String response = request(apiUrl, "GET");
            JSONObject root = new JSONObject(response);
            JSONArray items = root.optJSONArray("Items");
            if (items == null || root.optInt("TotalRecordCount", items.length()) > items.length()) {
                throw new IOException("Emby 返回的影片清单不完整");
            }
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                String id = o.optString("Id", "");
                if (!isValidItemId(id) || !ids.add(id)) {
                    throw new IOException("Emby 影片清单包含无效或重复的条目 ID");
                }
                EmbyMediaItem item = EmbyMediaItem.builder()
                        .id(id)
                        .name(o.optString("Name", ""))
                        .path(o.optString("Path", ""))
                        .year(o.optInt("ProductionYear", 0))
                        .dateCreated(o.optString("DateCreated", null))
                        .build();
                long ticks = o.optLong("RunTimeTicks", 0L);
                JSONArray sources = o.optJSONArray("MediaSources");
                item.setMediaSourceCount(sources == null ? 0 : sources.length());
                if (sources != null && sources.length() > 0) {
                    JSONObject source = sources.optJSONObject(0);
                    if (source != null) {
                        item.setSize(source.optLong("Size", 0L));
                        // 分辨率在 MediaStreams 里（Type=Video），MediaSource 本身没有 Width/Height
                        JSONArray streams = source.optJSONArray("MediaStreams");
                        if (streams != null) {
                            for (int s = 0; s < streams.length(); s++) {
                                JSONObject stream = streams.optJSONObject(s);
                                if (stream == null || !"Video".equalsIgnoreCase(stream.optString("Type", ""))) {
                                    continue;
                                }
                                int width = stream.optInt("Width", 0);
                                int height = stream.optInt("Height", 0);
                                if (width > 0 && height > 0) {
                                    item.setResolution(qualityLabel(width, height));
                                }
                                break;
                            }
                        }
                        if (ticks <= 0) {
                            ticks = source.optLong("RunTimeTicks", 0L);
                        }
                    }
                }
                if (ticks > 0) {
                    item.setDurationMinutes((int) (ticks / 600000000L));
                }
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            System.err.println("拉取 Emby 影片清单出错: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从宽高生成简短清晰度标签，如 4K / 1080p / 720p。
     *
     * @param width  视频宽度
     * @param height 视频高度
     * @return 清晰度标签
     */
    private static String qualityLabel(int width, int height) {
        if (height >= 2000 || width >= 3800) {
            return "4K";
        }
        if (height >= 1000) {
            return "1080p";
        }
        if (height >= 700) {
            return "720p";
        }
        return height + "p";
    }

    /**
     * 从 Emby 删除影片条目。
     * <p>
     * 注意：Emby 的删除接口会同时删除磁盘上的媒体文件，操作不可恢复。
     *
     * @param ids 待删除的 Emby 条目 Id
     * @return 删除失败的条目：id -> 失败原因；全部成功时为空 Map
     */
    public Map<String, String> deleteItems(List<String> ids) {
        Map<String, String> failed = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return failed;
        }
        if (!isConfigured() || ids.stream().anyMatch(id -> !isValidItemId(id))) {
            for (String id : ids) {
                failed.put(id, "Emby 未启用、未配置或条目 ID 无效");
            }
            return failed;
        }
        // Ids 是逗号分隔的查询参数，分批请求避免 URL 过长
        int batchSize = 40;
        for (int start = 0; start < ids.size(); start += batchSize) {
            List<String> batch = ids.subList(start, Math.min(ids.size(), start + batchSize));
            String joined = String.join(",", batch);
            String apiUrl = String.format("%s/emby/Items/Delete?Ids=%s", serverUrl, joined);
            try {
                request(apiUrl, "POST");
            } catch (Exception e) {
                for (String id : batch) {
                    failed.put(id, "Emby 未确认删除结果，请重新扫描核对");
                }
            }
        }
        return failed;
    }

    /**
     * 获取条目主图（封面）原始字节，供后端代理给前端展示。
     *
     * @param id        Emby 条目 Id
     * @param maxWidth  最大宽度（像素）
     * @param maxHeight 最大高度（像素）
     * @return 图片字节；条目无图或请求失败时返回 null
     */
    public byte[] fetchItemImage(String id, int maxWidth, int maxHeight) {
        if (!isConfigured() || !isValidItemId(id)) {
            return null;
        }
        try {
            String apiUrl = String.format("%s/emby/Items/%s/Images/Primary?maxWidth=%d&maxHeight=%d&quality=80",
                    serverUrl, id, maxWidth, maxHeight);
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Emby-Token", apiKey);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }
            try (java.io.InputStream in = conn.getInputStream();
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

}
