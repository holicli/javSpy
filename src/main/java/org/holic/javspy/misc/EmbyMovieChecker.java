package org.holic.javspy.misc;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.DataInput;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class EmbyMovieChecker {

    private final String serverUrl;
    private final String apiKey;

    /** 连接超时（毫秒），Emby 不可达时快速失败，避免阻塞调用方 */
    private static final int CONNECT_TIMEOUT_MS = 5000;
    /** 读取超时（毫秒），Emby 响应缓慢时快速失败 */
    private static final int READ_TIMEOUT_MS = 10000;

    /**
     * 构造函数
     * @param serverUrl Emby服务器地址，例如：http://192.168.1.100:8096
     * @param apiKey Emby API密钥
     */
    public EmbyMovieChecker(@Value("${conf.emby.serverUrl:}") String serverUrl,
                            @Value("${conf.emby.apiKey:}") String apiKey) {
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
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
            String apiUrl = String.format("%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&SearchTerm=%s&api_key=%s",
                    serverUrl, encodedName, apiKey);

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
            String apiUrl = String.format("%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&AnyProviderIdEquals=Imdb.%s&api_key=%s",
                    serverUrl, imdbId, apiKey);

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
            String apiUrl = String.format("%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&AnyProviderIdEquals=Tmdb.%s&api_key=%s",
                    serverUrl, tmdbId, apiKey);

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
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP错误代码: " + responseCode);
        }

        Scanner scanner = new Scanner(conn.getInputStream());
        StringBuilder response = new StringBuilder();
        while (scanner.hasNextLine()) {
            response.append(scanner.nextLine());
        }
        scanner.close();
        conn.disconnect();

        return response.toString();
    }

    /**
     * 获取影片的详细信息
     * @param movieName 电影名称
     * @return 包含影片信息的JSON对象，如果未找到返回null
     */
    public JSONObject getMovieDetails(String movieName) {
        try {
            String encodedName = java.net.URLEncoder.encode(movieName, "UTF-8");
            String apiUrl = String.format("%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&SearchTerm=%s&api_key=%s",
                    serverUrl, encodedName, apiKey);

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
        List<String> names = new ArrayList<>();
        List<String> dyname = new ArrayList<>();
        try {
            // 构建API请求URL /emby/Items?Recursive=true&IncludeItemTypes=Movie
            String apiUrl = String.format("%s/emby/Items?IncludeItemTypes=Movie&Recursive=true&Limit=100000&api_key=%s",
                    serverUrl, apiKey);

            // 发送HTTP请求
            String response = sendGetRequest(apiUrl);

            // 解析JSON响应
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray items = jsonResponse.getJSONArray("Items");


//            List<MediaItem> mediaItemList = JSON.parseArray(items.toString(), MediaItem.class);
            // 检查是否有匹配的影片
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String itemName = item.getString("Name");
                String strBeforeSpace = getStrBeforeSpace(itemName);
                if (names.contains(strBeforeSpace)) {
                    dyname.add(strBeforeSpace);
                } else {
                    names.add(strBeforeSpace);
                }

            }
            return names;

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
        if (StringUtils.isBlank(serverUrl) || StringUtils.isBlank(apiKey)) {
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

    /**
     * 示例使用方法
     */
    public static void main(String[] args) {
        // 配置信息 - 请替换为你的实际信息
        String serverUrl = "http://192.168.0.108:28096";
        String apiKey = "bd87f9a3632e4e409314ae45f71d99db";

        EmbyMovieChecker checker = new EmbyMovieChecker(serverUrl, apiKey);
        List<String> allMovieFromEmby = checker.getAllMovieFromEmby();
        System.out.println(allMovieFromEmby);
        List<String> lists = Arrays.asList(new String[]{"ABF-306","ACH-078","ADN-734","HMN-787","JUR-568","JUR-605","JUR-624","MIDA-459","MIDA-462","MIMK-257","NSFS-449","START-451"});
        for (String name :lists){
            boolean b = checker.checkMovieExistsByName(name);
            if (!b){
                System.out.println(name);
            }
        }

    }
}
