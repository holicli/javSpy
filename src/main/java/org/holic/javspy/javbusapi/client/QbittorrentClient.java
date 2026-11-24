package org.holic.javspy.javbusapi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Cookie;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class QbittorrentClient {

    private static final String MAGNET_PREFIX = "magnet:?";
    private static final Pattern FILM_CODE = Pattern.compile("[A-Z0-9]+(?:[ ._-]+[A-Z0-9]+)*");
    private static final String CODE_SEPARATOR = "[ ._-]*";

    public enum AddResult {
        ADDED,
        ALREADY_EXISTS
    }

    private final HttpUrl baseUrl;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private List<Cookie> session = List.of();

    @Autowired
    public QbittorrentClient(@Value("${javspy.qbittorrent.url:}") String url,
                             @Value("${javspy.qbittorrent.username:}") String username,
                             @Value("${javspy.qbittorrent.password:}") String password,
                             ObjectMapper objectMapper) {
        this(url, username, password, objectMapper, new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .followRedirects(false)
                .retryOnConnectionFailure(false)
                .build());
    }

    QbittorrentClient(String url, String username, String password, ObjectMapper objectMapper, OkHttpClient httpClient) {
        this.baseUrl = HttpUrl.parse(url.trim());
        this.username = username;
        this.password = password;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public synchronized AddResult addMagnet(String code, String magnet) {
        String filmCode = normalizeCode(code);
        Pattern titleCode = titleCodePattern(filmCode);
        String link = StringUtils.trimToEmpty(magnet);
        if (!StringUtils.startsWithIgnoreCase(link, MAGNET_PREFIX)
                || link.length() == MAGNET_PREFIX.length()
                || link.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("请提供一条有效的磁力链接");
        }
        if (baseUrl == null || StringUtils.isAnyBlank(username, password)) {
            throw new IllegalStateException("请先配置 qBittorrent 地址、账号和密码");
        }

        try {
            if (torrentExists(titleCode)) {
                return AddResult.ALREADY_EXISTS;
            }
            if (submit(filmCode, link)) {
                return AddResult.ADDED;
            }
            // 其他客户端可能已在查询与提交之间添加该种子。
            if (torrentExists(titleCode)) {
                return AddResult.ALREADY_EXISTS;
            }
            throw new IllegalStateException("qBittorrent 拒绝添加该磁链，请检查链接是否有效");
        } catch (IOException e) {
            throw new IllegalStateException("qBittorrent 通信失败，请检查 WebUI 是否可访问", e);
        }
    }

    private static String normalizeCode(String code) {
        String normalized = Normalizer.normalize(StringUtils.trimToEmpty(code), Normalizer.Form.NFKC)
                .trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 64 || !FILM_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("请提供有效的影片番号");
        }
        return normalized.replaceAll("[ ._-]+", "-");
    }

    private static Pattern titleCodePattern(String filmCode) {
        String expression = String.join(CODE_SEPARATOR, filmCode.split("-"));
        if (filmCode.matches("FC2(?:-PPV)?-[0-9]+")) {
            expression = "FC2(?:" + CODE_SEPARATOR + "PPV)?" + CODE_SEPARATOR
                    + StringUtils.substringAfterLast(filmCode, "-");
        }
        return Pattern.compile("(?<![A-Z0-9])" + expression + "(?![A-Z0-9])", Pattern.CASE_INSENSITIVE);
    }

    private boolean torrentExists(Pattern titleCode) throws IOException {
        Request request = new Request.Builder().url(apiUrl("torrents/info"))
                .header("Referer", baseUrl.toString()).get().build();
        try (Response response = executeAuthenticated(request)) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("无法检查 qBittorrent 中的现有任务（HTTP " + response.code() + "）");
            }
            ResponseBody body = response.body();
            JsonNode torrents = body == null ? null : objectMapper.readTree(body.byteStream());
            if (torrents == null || !torrents.isArray()) {
                throw new IllegalStateException("qBittorrent 返回了无法识别的任务列表");
            }
            for (JsonNode torrent : torrents) {
                String name = Normalizer.normalize(torrent.path("name").asText(), Normalizer.Form.NFKC);
                if (titleCode.matcher(name).find()) {
                    return true;
                }
                String tags = Normalizer.normalize(torrent.path("tags").asText(), Normalizer.Form.NFKC);
                for (String tag : tags.split(",")) {
                    if (titleCode.matcher(tag.trim()).matches()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private List<Cookie> login() throws IOException {
        RequestBody credentials = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();
        Request request = post("auth/login", credentials).build();
        try (Response response = httpClient.newCall(request).execute()) {
            List<Cookie> cookies = Cookie.parseAll(request.url(), response.headers());
            if (!response.isSuccessful() || cookies.isEmpty()) {
                throw new IllegalStateException("qBittorrent 登录失败，请检查账号、密码和 WebUI 设置");
            }
            return cookies;
        }
    }

    private boolean submit(String filmCode, String magnet) throws IOException {
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("urls", magnet)
                .addFormDataPart("tags", filmCode)
                .build();
        try (Response response = executeAuthenticated(post("torrents/add", body).build())) {
            if (response.code() == 409) {
                return false;
            }
            if (!response.isSuccessful()) {
                throw new IllegalStateException("qBittorrent 下载请求失败（HTTP " + response.code() + "）");
            }
            return accepted(response.body());
        }
    }

    private Response executeAuthenticated(Request request) throws IOException {
        for (int attempt = 0; attempt < 2; attempt++) {
            String cookies = sessionHeader(request.url());
            if (cookies.isEmpty()) {
                session = login();
                cookies = sessionHeader(request.url());
            }
            if (cookies.isEmpty()) {
                break;
            }
            Request authenticatedRequest = request.newBuilder().header("Cookie", cookies).build();
            Response response = httpClient.newCall(authenticatedRequest).execute();
            if (response.code() != 401 && response.code() != 403) {
                return response;
            }
            response.close();
            session = List.of();
        }
        throw new IllegalStateException("qBittorrent 拒绝了请求，请检查 WebUI 登录设置");
    }

    private String sessionHeader(HttpUrl url) {
        long now = System.currentTimeMillis();
        return session.stream()
                .filter(cookie -> cookie.expiresAt() > now && cookie.matches(url))
                .map(cookie -> cookie.name() + "=" + cookie.value())
                .collect(Collectors.joining("; "));
    }

    private boolean accepted(ResponseBody body) throws IOException {
        String result = body == null ? "" : body.string().trim();
        if (result.isEmpty() || "Ok.".equals(result)) {
            return true;
        }
        if (result.startsWith("{")) {
            JsonNode counts = objectMapper.readTree(result);
            return counts.path("success_count").asInt() > 0 || counts.path("pending_count").asInt() > 0;
        }
        return false;
    }

    private Request.Builder post(String action, RequestBody body) {
        return new Request.Builder()
                .url(apiUrl(action))
                .header("Referer", baseUrl.toString())
                .post(body);
    }

    private HttpUrl apiUrl(String action) {
        return baseUrl.newBuilder().addPathSegments("api/v2/" + action).build();
    }
}
