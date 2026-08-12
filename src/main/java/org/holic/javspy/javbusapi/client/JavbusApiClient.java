package org.holic.javspy.javbusapi.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;
import org.holic.javspy.javbusapi.model.JavbusApiMovie;
import org.holic.javspy.javbusapi.model.JavbusApiMovieSample;
import org.holic.javspy.javbusapi.model.JavbusApiScrapeResult;
import org.holic.javspy.javbusapi.model.JavbusApiSimilarMovie;
import org.holic.javspy.javbusapi.model.JavbusApiStar;
import org.holic.javspy.javbusapi.model.JavbusApiVideoItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * javbus API 客户端：直接调用 JSON 接口，不做网页解析。
 * <ul>
 *     <li>列表：GET /api/movies</li>
 *     <li>搜索：GET /api/movies/search?keyword=</li>
 *     <li>详情：GET /api/movies/{movieId}</li>
 *     <li>磁力：GET /api/magnets/{movieId}?gid=&amp;uc=</li>
 *     <li>演员：GET /api/stars/{starId}</li>
 * </ul>
 */
@Slf4j
@Component
public class JavbusApiClient {

    /** API 站点地址 */
    @Value("${conf.javbus-api.base-url:http://192.168.0.108:33000}")
    private String baseUrl;

    /** 可选 token（服务端开启权限校验时使用 j-auth-token 请求头） */
    @Value("${conf.javbus-api.token:}")
    private String token;

    /** 代理主机（如 127.0.0.1），留空则不使用代理 */
    @Value("${conf.javbus-api.proxy-host:}")
    private String proxyHost;

    /** 代理端口 */
    @Value("${conf.javbus-api.proxy-port:0}")
    private int proxyPort;

    /** 请求超时（毫秒） */
    @Value("${conf.javbus-api.timeout-ms:20000}")
    private long timeoutMs;

    /** 两次请求之间的间隔（毫秒） */
    @Value("${conf.javbus-api.request-interval-ms:300}")
    private long requestIntervalMs;

    /** 是否把返回的 JSON 打印到后台控制台（调试用） */
    @Value("${conf.javbus-api.print-json:false}")
    private boolean printJson;

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final Object requestLock = new Object();
    private long lastRequestTime;

    private OkHttpClient httpClient;

    @PostConstruct
    public void init() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(true);
        if (StringUtils.isNotBlank(proxyHost) && proxyPort > 0) {
            builder.proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort)));
            log.info("javbus-api client uses SOCKS5 proxy {}:{}", proxyHost, proxyPort);
        }
        this.httpClient = builder.build();
        log.info("javbus-api client initialized, baseUrl={}", baseUrl);
    }

    // ==================== 对外方法 ====================

    /**
     * 获取影片列表：GET /api/movies
     *
     * @param page        页码，默认 1
     * @param magnet      exist: 只返回有磁力链接的影片；all: 返回全部
     * @param filterType  star/genre/director/studio/label/series，可空
     * @param filterValue 筛选值，与 filterType 一起使用
     * @param type        normal: 有码；uncensored: 无码，可空
     */
    public List<JavbusApiVideoItem> listMovies(int page, String magnet,
                                               String filterType, String filterValue, String type)
            throws Exception {
        StringBuilder url = new StringBuilder(baseUrl).append("/api/movies");
        List<String> params = new ArrayList<>();
        params.add("page=" + Math.max(1, page));
        if (StringUtils.isNotBlank(magnet)) {
            params.add("magnet=" + magnet.trim());
        }
        if (StringUtils.isNotBlank(filterType)) {
            params.add("filterType=" + filterType.trim());
        }
        if (StringUtils.isNotBlank(filterValue)) {
            params.add("filterValue=" + filterValue.trim());
        }
        if (StringUtils.isNotBlank(type)) {
            params.add("type=" + type.trim());
        }
        url.append("?").append(String.join("&", params));

        String json = get(url.toString());
        return parseMovieList(json);
    }

    /**
     * 搜索影片：GET /api/movies/search?keyword=
     */
    public List<JavbusApiVideoItem> searchMovies(String keyword, int page, String magnet, String type)
            throws Exception {
        StringBuilder url = new StringBuilder(baseUrl).append("/api/movies/search")
                .append("?keyword=").append(urlEncode(keyword.trim()))
                .append("&page=").append(Math.max(1, page));
        if (StringUtils.isNotBlank(magnet)) {
            url.append("&magnet=").append(magnet.trim());
        }
        if (StringUtils.isNotBlank(type)) {
            url.append("&type=").append(type.trim());
        }
        String json = get(url.toString());
        return parseMovieList(json);
    }

    /**
     * 获取影片详情：GET /api/movies/{movieId}
     */
    public JavbusApiMovie getMovieDetail(String movieId) throws Exception {
        if (StringUtils.isBlank(movieId)) {
            return null;
        }
        String json = get(baseUrl + "/api/movies/" + movieId.trim());
        if (StringUtils.isBlank(json)) {
            return null;
        }
        JSONObject d = JSON.parseObject(json);
        if (d == null) {
            log.warn("javbus-api detail parse failed, movieId={}", movieId);
            return null;
        }
        JavbusApiMovie movie = new JavbusApiMovie();
        movie.setCode(firstNonBlank(d.getString("id"), movieId.trim().toUpperCase()));
        movie.setTitle(d.getString("title"));
        movie.setCoverUrl(d.getString("img"));
        movie.setReleaseDate(d.getString("date"));
        movie.setDuration(d.getInteger("videoLength"));
        movie.setGid(d.getString("gid"));
        movie.setUc(d.getString("uc"));
        movie.setRawJson(json.length() > 100000 ? json.substring(0, 100000) : json);

        // 封面大图尺寸
        JSONObject imageSize = d.getJSONObject("imageSize");
        if (imageSize != null) {
            movie.setCoverWidth(imageSize.getInteger("width"));
            movie.setCoverHeight(imageSize.getInteger("height"));
        }

        JSONObject director = d.getJSONObject("director");
        if (director != null) {
            movie.setDirector(firstNonBlank(director.getString("name"), director.getString("id")));
            movie.setDirectorId(director.getString("id"));
        }
        JSONObject producer = d.getJSONObject("producer");
        if (producer != null) {
            movie.setStudio(firstNonBlank(producer.getString("name"), producer.getString("id")));
            movie.setStudioId(producer.getString("id"));
        }
        JSONObject publisher = d.getJSONObject("publisher");
        if (publisher != null) {
            movie.setPublisher(firstNonBlank(publisher.getString("name"), publisher.getString("id")));
            movie.setPublisherId(publisher.getString("id"));
        }
        JSONObject series = d.getJSONObject("series");
        if (series != null) {
            movie.setSeries(firstNonBlank(series.getString("name"), series.getString("id")));
            movie.setSeriesId(series.getString("id"));
        }

        // 演员（含 ID，供 javbus_star / javbus_movie_star 入库）
        JSONArray stars = d.getJSONArray("stars");
        if (stars != null && !stars.isEmpty()) {
            List<JavbusApiStar> starList = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < stars.size(); i++) {
                JSONObject s = stars.getJSONObject(i);
                JavbusApiStar star = new JavbusApiStar();
                star.setId(s.getString("id"));
                star.setName(s.getString("name"));
                starList.add(star);
                if (StringUtils.isNotBlank(star.getName())) {
                    names.add(star.getName().trim());
                }
            }
            movie.setStars(starList);
            movie.setActors(String.join(",", names));
        }

        // 类别（含 ID，供 javbus_genre / javbus_movie_genre 入库）
        JSONArray genres = d.getJSONArray("genres");
        if (genres != null && !genres.isEmpty()) {
            List<JavbusApiStar> genreList = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < genres.size(); i++) {
                JSONObject g = genres.getJSONObject(i);
                JavbusApiStar genre = new JavbusApiStar();
                genre.setId(g.getString("id"));
                genre.setName(g.getString("name"));
                genreList.add(genre);
                if (StringUtils.isNotBlank(genre.getName())) {
                    names.add(genre.getName().trim());
                }
            }
            movie.setGenresList(genreList);
            movie.setGenres(String.join(",", names));
        }

        // 预览图
        JSONArray samples = d.getJSONArray("samples");
        if (samples != null && !samples.isEmpty()) {
            List<JavbusApiMovieSample> sampleList = new ArrayList<>();
            for (int i = 0; i < samples.size(); i++) {
                JSONObject s = samples.getJSONObject(i);
                JavbusApiMovieSample sample = new JavbusApiMovieSample();
                sample.setSampleId(s.getString("id"));
                sample.setAlt(s.getString("alt"));
                sample.setSrc(s.getString("src"));
                sample.setThumbnail(s.getString("thumbnail"));
                sampleList.add(sample);
            }
            movie.setSamples(sampleList);
        }

        // 相似影片
        JSONArray similar = d.getJSONArray("similarMovies");
        if (similar != null && !similar.isEmpty()) {
            List<JavbusApiSimilarMovie> similarList = new ArrayList<>();
            for (int i = 0; i < similar.size(); i++) {
                JSONObject s = similar.getJSONObject(i);
                JavbusApiSimilarMovie sm = new JavbusApiSimilarMovie();
                sm.setCode(s.getString("id"));
                sm.setTitle(s.getString("title"));
                sm.setImg(s.getString("img"));
                similarList.add(sm);
            }
            movie.setSimilarMovies(similarList);
        }
        return movie;
    }

    /**
     * 获取影片磁力链接：GET /api/magnets/{movieId}?gid=&amp;uc=
     */
    public List<JavbusApiMagnet> getMagnets(String movieId, String gid, String uc) throws Exception {
        List<JavbusApiMagnet> magnets = new ArrayList<>();
        if (StringUtils.isBlank(movieId) || StringUtils.isBlank(gid)) {
            return magnets;
        }
        String url = baseUrl + "/api/magnets/" + movieId.trim()
                + "?gid=" + gid.trim()
                + "&uc=" + (StringUtils.isBlank(uc) ? "0" : uc.trim());
        String json = get(url);
        if (StringUtils.isBlank(json)) {
            return magnets;
        }
        JSONArray array;
        try {
            array = JSON.parseArray(json);
        } catch (Exception e) {
            JSONObject obj = JSON.parseObject(json);
            if (obj != null && obj.containsKey("magnets")) {
                array = obj.getJSONArray("magnets");
            } else {
                array = null;
            }
        }
        if (array == null) {
            log.warn("javbus-api magnets parse failed, movieId={}", movieId);
            return magnets;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject m = array.getJSONObject(i);
            JavbusApiMagnet magnet = new JavbusApiMagnet();
            magnet.setCode(movieId.trim().toUpperCase());
            magnet.setMagnetId(m.getString("id"));
            magnet.setMagnet(m.getString("link"));
            if (StringUtils.isBlank(magnet.getMagnet())) {
                continue;
            }
            magnet.setName(firstNonBlank(m.getString("title"), m.getString("name")));
            magnet.setSizeText(m.getString("size"));
            Long numberSize = m.getLong("numberSize");
            magnet.setSizeBytes(numberSize != null ? numberSize : parseSizeBytes(magnet.getSizeText()));
            magnet.setShareDate(m.getString("shareDate"));
            magnet.setHd(booleanToInt(m, "isHD", "hd"));
            magnet.setSubtitle(booleanToInt(m, "hasSubtitle", "subtitle"));
            magnets.add(magnet);
        }
        return magnets;
    }

    /**
     * 获取演员详情：GET /api/stars/{starId}
     */
    public JSONObject getStar(String starId, String type) throws Exception {
        if (StringUtils.isBlank(starId)) {
            return null;
        }
        String url = baseUrl + "/api/stars/" + starId.trim();
        if (StringUtils.isNotBlank(type)) {
            url += "?type=" + type.trim();
        }
        String json = get(url);
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JSON.parseObject(json);
    }

    /**
     * 一次抓取：详情 + 磁力。
     */
    public JavbusApiScrapeResult scrapeMovie(String movieId) throws Exception {
        JavbusApiScrapeResult result = new JavbusApiScrapeResult();
        JavbusApiMovie movie = getMovieDetail(movieId);
        if (movie == null || StringUtils.isBlank(movie.getCode())) {
            return result;
        }
        result.setMovie(movie);
        result.setMagnets(getMagnets(movie.getCode(), movie.getGid(), movie.getUc()));
        return result;
    }

    // ==================== 内部方法 ====================

    /** 解析影片列表 JSON（兼容 {movies:[...]} 或直接数组）。 */
    private List<JavbusApiVideoItem> parseMovieList(String json) {
        List<JavbusApiVideoItem> items = new ArrayList<>();
        if (StringUtils.isBlank(json)) {
            return items;
        }
        JSONArray array = null;
        JSONObject obj = JSON.parseObject(json);
        if (obj != null && obj.containsKey("movies")) {
            array = obj.getJSONArray("movies");
        }
        if (array == null) {
            try {
                array = JSON.parseArray(json);
            } catch (Exception ignored) {
                // not an array
            }
        }
        if (array == null) {
            log.warn("javbus-api list parse failed, json head={}",
                    json.length() > 200 ? json.substring(0, 200) : json);
            return items;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject m = array.getJSONObject(i);
            JavbusApiVideoItem item = new JavbusApiVideoItem();
            item.setCode(m.getString("id"));
            item.setTitle(m.getString("title"));
            item.setCover(m.getString("img"));
            item.setDate(m.getString("date"));
            JSONArray tags = m.getJSONArray("tags");
            if (tags != null && !tags.isEmpty()) {
                item.setTags(String.join(",", tags.toArray(new String[0])));
            }
            if (StringUtils.isNotBlank(item.getCode())) {
                items.add(item);
            }
        }
        return items;
    }

    // ==================== HTTP 工具 ====================

    /** GET 请求，返回 JSON 字符串。 */
    public String get(String url) throws Exception {
        throttle();
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json,text/plain,*/*");
        if (StringUtils.isNotBlank(token)) {
            builder.header("j-auth-token", token.trim());
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                log.warn("javbus-api request failed, url={}, code={}", url, response.code());
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            String json = new String(body.bytes(), StandardCharsets.UTF_8);
            if (printJson) {
                System.out.println("==================== javbus-api GET: " + url + " ====================");
                System.out.println(json);
                System.out.println("==================== javbus-api GET end ====================");
            }
            return json;
        }
    }

    /** 连通性自检。 */
    public String ping() {
        try {
            String json = get(baseUrl + "/api/movies?page=1&magnet=all");
            if (json == null) {
                return "FAIL: 请求失败（响应为空或非 2xx）";
            }
            return "OK: 返回 " + json.length() + " 字符";
        } catch (Exception e) {
            return "FAIL: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    private void throttle() throws InterruptedException {
        if (requestIntervalMs <= 0) {
            return;
        }
        synchronized (requestLock) {
            long now = System.currentTimeMillis();
            long wait = requestIntervalMs - (now - lastRequestTime);
            if (wait > 0) {
                Thread.sleep(wait);
            }
            lastRequestTime = System.currentTimeMillis();
        }
    }

    /** 返回第一个非空字符串；全部为空时返回 null。 */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private Long parseSizeBytes(String sizeText) {
        if (StringUtils.isBlank(sizeText)) {
            return null;
        }
        try {
            Matcher m = Pattern.compile("([\\d.]+)\\s*([A-Za-z]+)").matcher(sizeText.trim());
            if (!m.find()) {
                return null;
            }
            double v = Double.parseDouble(m.group(1));
            String unit = m.group(2).toUpperCase();
            long factor;
            switch (unit) {
                case "TB":
                    factor = 1024L * 1024L * 1024L * 1024L;
                    break;
                case "GB":
                    factor = 1024L * 1024L * 1024L;
                    break;
                case "MB":
                    factor = 1024L * 1024L;
                    break;
                case "KB":
                    factor = 1024L;
                    break;
                default:
                    factor = 1L;
            }
            return (long) (v * factor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int booleanToInt(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.containsKey(key)) {
                Object v = obj.get(key);
                if (v instanceof Boolean) {
                    return (Boolean) v ? 1 : 0;
                }
                if (v instanceof Number) {
                    return ((Number) v).intValue() == 0 ? 0 : 1;
                }
                String s = String.valueOf(v);
                if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
                    return 1;
                }
            }
        }
        return 0;
    }

    private String urlEncode(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }
}
