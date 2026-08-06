package org.holic.javspy.javbus.client;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.holic.javspy.javbus.model.JavbusMagnet;
import org.holic.javspy.javbus.model.JavbusMovie;
import org.holic.javspy.javbus.model.JavbusScrapeResult;
import org.holic.javspy.javbus.model.JavbusVideoItem;
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
 * javbus.com 页面抓取客户端。
 *
 * <p>javbus 没有公开稳定的 JSON API，直接抓 HTML：
 * <ul>
 *     <li>首页/分类：GET {baseUrl}/?page=N 或 /uncensored/?page=N 等</li>
 *     <li>搜索：GET {baseUrl}/search/{关键词}?page=N</li>
 *     <li>详情：GET {baseUrl}/{番号或ID}</li>
 *     <li>磁力：详情页内 #magnet-table 直接包含 magnet:?xt= 链接</li>
 * </ul>
 */
@Slf4j
@Component
public class JavbusApiClient {

    /** 站点地址，默认 https://www.javbus.com */
    @Value("${conf.javbus.base-url:https://www.javbus.com}")
    private String baseUrl;

    /** 登录 cookie（抓取详情/磁力建议配置，javbus 部分页面需要） */
    @Value("${conf.javbus.cookie:}")
    private String cookie;

    /** 代理主机（如 127.0.0.1），留空则不使用代理 */
    @Value("${conf.javbus.proxy-host:}")
    private String proxyHost;

    /** 代理端口 */
    @Value("${conf.javbus.proxy-port:0}")
    private int proxyPort;

    /** 请求超时（毫秒） */
    @Value("${conf.javbus.timeout-ms:20000}")
    private long timeoutMs;

    /** 两次请求之间的间隔（毫秒） */
    @Value("${conf.javbus.request-interval-ms:1500}")
    private long requestIntervalMs;

    /** 是否把抓到的 HTML 打印到后台控制台（调试用） */
    @Value("${conf.javbus.print-html:false}")
    private boolean printHtml;

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
            log.info("javbus client uses SOCKS5 proxy {}:{}", proxyHost, proxyPort);
        }
        this.httpClient = builder.build();
        log.info("javbus client initialized, baseUrl={}", baseUrl);
    }

    // ==================== 对外方法 ====================

    /**
     * 按番号刮削：先搜索确认详情页，再解析详情 + 磁力。
     */
    public JavbusScrapeResult scrapeByCode(String code) throws Exception {
        JavbusScrapeResult result = new JavbusScrapeResult();
        String detailUrl = searchDetailUrl(code);
        if (StringUtils.isBlank(detailUrl)) {
            log.warn("javbus search no result for code={}", code);
            return result;
        }
        return scrapeByUrl(detailUrl);
    }

    /**
     * 搜索番号，返回第一个匹配的详情页地址；找不到返回 null。
     */
    public String searchDetailUrl(String code) throws Exception {
        String url = baseUrl + "/search/" + urlEncode(code.trim()) + "?page=1";
        String html = get(url);
        if (StringUtils.isBlank(html)) {
            return null;
        }
        for (JavbusVideoItem item : parseVideoCards(html)) {
            if (normalizeCode(item.getCode()).equals(normalizeCode(code))) {
                return item.getUrl();
            }
        }
        return null;
    }

    /**
     * 按详情页 URL 刮削：解析详情 + 磁力。
     */
    public JavbusScrapeResult scrapeByUrl(String detailUrl) throws Exception {
        JavbusScrapeResult result = new JavbusScrapeResult();
        String html = get(detailUrl);
        JavbusMovie movie = parseDetail(html, detailUrl);
        if (movie == null || StringUtils.isBlank(movie.getCode())) {
            log.warn("javbus detail parse failed, url={}", detailUrl);
            return result;
        }
        movie.setDetailUrl(detailUrl);
        result.setMovie(movie);
        result.setMagnets(fetchMagnets(movie, html, extractDetailId(detailUrl)));
        return result;
    }

    /**
     * 按页码抓取列表（默认 javbus 首页第 1 页）。
     */
    public List<JavbusVideoItem> pageMovies(int page) throws Exception {
        String url = baseUrl + "/" + (page <= 1 ? "" : "?page=" + page);
        String html = get(url);
        if (StringUtils.isBlank(html)) {
            return new ArrayList<>();
        }
        return parseVideoCards(html);
    }

    /**
     * 搜索关键词，返回搜索页前 pages 页中出现的全部影片卡片。
     */
    public List<JavbusVideoItem> searchMovies(String keyword, int pages) throws Exception {
        List<JavbusVideoItem> items = new ArrayList<>();
        for (int page = 1; page <= Math.max(1, pages); page++) {
            String url = baseUrl + "/search/" + urlEncode(keyword.trim()) + "?page=" + page;
            String html = get(url);
            if (StringUtils.isBlank(html)) {
                break;
            }
            List<JavbusVideoItem> pageItems = parseVideoCards(html);
            for (JavbusVideoItem item : pageItems) {
                if (!items.contains(item)) {
                    items.add(item);
                }
            }
            if (pageItems.isEmpty()) {
                break;
            }
        }
        return items;
    }

    // ==================== 页面解析 ====================

    /**
     * 解析首页/搜索页影片卡片：
     * <a class="movie-box" href="/2021/12345/">
     *   <div class="photo-frame"><img src="封面" ... /></div>
     *   <div class="photo-info"><span>日期</span><date>...</date></div>
     * </a>
     */
    public List<JavbusVideoItem> parseVideoCards(String html) {
        List<JavbusVideoItem> items = new ArrayList<>();
        if (StringUtils.isBlank(html)) {
            return items;
        }
        // javbus 卡片链接：<a class="movie-box" href="/2021/xxxxx/">
        Pattern p = Pattern.compile(
                "<a[^>]*class=\"movie-box\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                Pattern.DOTALL);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String card = m.group(2);
            String url = toAbsolute(m.group(1));
            JavbusVideoItem item = new JavbusVideoItem();
            item.setUrl(url);
            // 封面：<img src="..." 或 data-src="..."
            String cover = extractAttr(card, "src=\"", "\"");
            if (cover == null) {
                cover = extractAttr(card, "data-src=\"", "\"");
            }
            item.setCover(toAbsolute(cover));
            // 标题：<div class="title">番号</div>（有的版本是 <date> 里的内容）
            String code = extractAttr(card, "class=\"title\">", "<");
            if (code == null) {
                code = extractAttr(card, "<date>", "</date>");
            }
            item.setCode(normalizeCode(code));
            // 标题兜底：把详情页 title 属性也提取
            String title = extractAttr(m.group(0), "title=\"", "\"");
            item.setTitle(cleanText(title));
            if (StringUtils.isNotBlank(item.getCode())) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 解析详情页 HTML -> 影片信息。
     * javbus 详情页结构：
     * <pre>
     * &lt;div class="screencap"&gt;&lt;img src="封面"&gt;&lt;/div&gt;
     * &lt;h3&gt;标题&lt;/h3&gt;
     * &lt;div class="info"&gt;
     *   &lt;p&gt;&lt;span class="header"&gt;發行日期:&lt;/span&gt; 2023-01-01&lt;/p&gt;
     *   &lt;p&gt;&lt;span class="header"&gt;長度:&lt;/span&gt; 120 分鐘&lt;/p&gt;
     *   &lt;p&gt;&lt;span class="header"&gt;導演:&lt;/span&gt; &lt;a&gt;...&lt;/a&gt;&lt;/p&gt;
     *   ... 製作商 / 發行商 / 系列 ...
     * &lt;/div&gt;
     * &lt;div class="star-box"&gt;&lt;a&gt;演员&lt;/a&gt;...&lt;/div&gt;
     * &lt;div class="genre"&gt;&lt;a&gt;标签&lt;/a&gt;...&lt;/div&gt;
     * </pre>
     */
    public JavbusMovie parseDetail(String html, String detailUrl) {
        if (StringUtils.isBlank(html)) {
            return null;
        }
        JavbusMovie movie = new JavbusMovie();
        movie.setDetailUrl(detailUrl);
        movie.setRawHtml(html.length() > 100000 ? html.substring(0, 100000) : html);

        // 标题：<h3>...</h3>
        String title = cleanText(extractAttr(html, "<h3>", "</h3>"));
        if (title == null) {
            title = cleanText(extractAttr(html, "<title>", "</title>"));
        }
        movie.setTitle(title);

        // 封面：<a class="bigImage" href="/pics/cover/xxx.jpg"><img src="..."></a> 的 href
        String bigCover = extractAttr(html, "class=\"bigImage\" href=\"", "\"");
        if (bigCover == null) {
            bigCover = extractAttr(html, "class=\"bigImage\"", ">");
            if (bigCover != null) {
                bigCover = extractAttr(bigCover, "href=\"", "\"");
            }
        }
        movie.setCoverUrl(toAbsolute(bigCover));
        // 兜底：<div class="screencap"> 里的 img
        int capIdx = html.indexOf("class=\"screencap\"");
        if (StringUtils.isBlank(movie.getCoverUrl()) && capIdx >= 0) {
            String cap = html.substring(capIdx, Math.min(html.length(), capIdx + 3000));
            movie.setCoverUrl(toAbsolute(extractAttr(cap, "<img src=\"", "\"")));
        }
        if (StringUtils.isBlank(movie.getCoverUrl())) {
            movie.setCoverUrl(toAbsolute(extractAttr(html, "<img src=\"", "\"")));
        }

        // 元数据：info 区域里的 p 行，<span class="header">键:</span> 值
        int infoIdx = html.indexOf("class=\"info\"");
        if (infoIdx >= 0) {
            String info = html.substring(infoIdx, Math.min(html.length(), infoIdx + 8000));
            Pattern rowP = Pattern.compile(
                    "<span class=\"header\">([^<]{1,20}?)</span>([\\s\\S]*?)</p>");
            Matcher rowM = rowP.matcher(info);
            while (rowM.find()) {
                String key = cleanText(rowM.group(1));
                String value = cleanText(rowM.group(2));
                if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
                    continue;
                }
                if (key.contains("識別碼") || key.contains("识别码") || key.contains("番號") || key.contains("番号")) {
                    if (StringUtils.isBlank(movie.getCode())) {
                        movie.setCode(normalizeCode(value));
                    }
                } else if (key.contains("發行日期") || key.contains("发行日期")) {
                    movie.setReleaseDate(extractDate(value));
                } else if (key.contains("長度") || key.contains("长度")) {
                    movie.setDuration(extractDuration(value));
                } else if (key.contains("導演") || key.contains("导演")) {
                    movie.setDirector(value);
                } else if (key.contains("製作商") || key.contains("制作商")) {
                    movie.setStudio(value);
                } else if (key.contains("發行商") || key.contains("发行商")) {
                    movie.setPublisher(value);
                } else if (key.contains("系列")) {
                    movie.setSeries(value);
                }
            }
        }

        // 番号兜底：详情页 URL 里带番号（如 /SSIS-123/）
        if (StringUtils.isBlank(movie.getCode()) && detailUrl != null) {
            String last = detailUrl.substring(detailUrl.lastIndexOf('/') + 1);
            if (last.endsWith("/")) {
                last = last.substring(0, last.length() - 1);
            }
            movie.setCode(normalizeCode(last));
        }

        // 演员：star-box 里的 <a> 文本（有的版本是 <span class="star-name">）
        int starIdx = html.indexOf("class=\"star-box\"");
        if (starIdx >= 0) {
            String starBlock = html.substring(starIdx,
                    Math.min(html.length(), starIdx + 20000));
            List<String> names = new ArrayList<>();
            Matcher starM = Pattern.compile("class=\"star-name\">([^<]+)</").matcher(starBlock);
            while (starM.find() && names.size() < 50) {
                String name = starM.group(1).trim();
                if (!name.isEmpty() && !names.contains(name)) {
                    names.add(name);
                }
            }
            // 兜底：<a href="/star/xxx">名字</a>
            if (names.isEmpty()) {
                Matcher m2 = Pattern.compile("<a[^>]*href=\"/star/[^\"]+\"[^>]*>([^<]+)</a>")
                        .matcher(starBlock);
                while (m2.find() && names.size() < 50) {
                    String name = cleanText(m2.group(1));
                    if (name != null && !name.isEmpty() && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
            movie.setActors(String.join(",", names));
        }

        // 类型/标签：<span class="genre"><a ...>标签</a></span> 或 genre 容器里的链接
        List<String> genres = new ArrayList<>();
        Matcher genreM = Pattern.compile("class=\"genre\"[^>]*>\\s*<a[^>]*>([^<]+)</a>").matcher(html);
        while (genreM.find() && genres.size() < 50) {
            String g = cleanText(genreM.group(1));
            if (g != null && !g.isEmpty() && !genres.contains(g)) {
                genres.add(g);
            }
        }
        movie.setGenres(String.join(",", genres));

        // 日期兜底：正文第一个 yyyy-MM-dd
        if (StringUtils.isBlank(movie.getReleaseDate())) {
            movie.setReleaseDate(extractDate(html));
        }
        return movie;
    }

    /**
     * 获取磁力列表：javbus 磁力通过 AJAX 接口加载。
     * 先从详情页 HTML 里提取 gid / img 参数，再请求：
     * {baseUrl}/ajax/uncledatoolsbyajax.php?gid={gid}&lang=zh&img={img}&uc=0&floor={随机}
     */
    public List<JavbusMagnet> fetchMagnets(JavbusMovie movie, String detailHtml, String detailId) {
        List<JavbusMagnet> magnets = new ArrayList<>();
        if (StringUtils.isBlank(detailHtml)) {
            return magnets;
        }
        String code = movie == null ? null : movie.getCode();
        // 从详情页提取 gid：<script> ... gid = 69512904711; ... </script> 或 hidden input
        String gid = extractGid(detailHtml);
        if (StringUtils.isBlank(gid)) {
            log.warn("javbus detail has no gid, code={}", code);
            return magnets;
        }
        // img 参数：从详情页 <a class="bigImage" href="/pics/cover/xxx.jpg"> 的 href 取
        String img = StringUtils.EMPTY;
        Matcher bigM = Pattern.compile("class=\"bigImage\"[^>]*href=\"(/pics/cover/[^\"]+)\"").matcher(detailHtml);
        if (bigM.find()) {
            img = bigM.group(1);
        } else {
            Matcher coverM = Pattern.compile("src=\"(/pics/cover/[^\"]+)\"").matcher(detailHtml);
            if (coverM.find()) {
                img = coverM.group(1);
            }
        }
        try {
            String ajaxUrl = baseUrl + "/ajax/uncledatoolsbyajax.php?gid=" + gid
                    + "&lang=zh&img=" + urlEncode(img) + "&uc=0&floor=" + (int) (Math.random() * 900 + 100);
            String magnetHtml = get(ajaxUrl);
            if (StringUtils.isNotBlank(magnetHtml)) {
                magnets = parseMagnets(magnetHtml, code, detailId);
            }
        } catch (Exception e) {
            log.warn("javbus magnets fetch failed, code={}, gid={}", code, gid, e);
        }
        return magnets;
    }

    /** 从详情页 HTML 提取 gid。 */
    private String extractGid(String html) {
        if (StringUtils.isBlank(html)) {
            return null;
        }
        // 常见形式：gid = 69512904711; / var gid = "..."; / "gid":"69512904711"
        Matcher m1 = Pattern.compile("gid\\s*[:=]\\s*['\"]?(\\d{5,})['\"]?").matcher(html);
        if (m1.find()) {
            return m1.group(1);
        }
        return null;
    }

    /** 解析 AJAX 接口返回的磁力表格 HTML。 */
    public List<JavbusMagnet> parseMagnets(String magnetHtml, String code, String detailId) {
        List<JavbusMagnet> magnets = new ArrayList<>();
        if (StringUtils.isBlank(magnetHtml)) {
            return magnets;
        }
        // 每行：<tr> <td><a href="magnet:?xt=...">名称</a></td> <td>大小</td> <td>日期</td> ... </tr>
        Pattern rowP = Pattern.compile("<tr[^>]*>([\\s\\S]*?)</tr>");
        Matcher rowM = rowP.matcher(magnetHtml);
        while (rowM.find()) {
            String row = rowM.group(1);
            String magnetUrl = extractAttr(row, "href=\"", "\"");
            if (magnetUrl == null || !magnetUrl.startsWith("magnet:")) {
                continue;
            }
            JavbusMagnet magnet = new JavbusMagnet();
            magnet.setCode(code);
            magnet.setDetailId(detailId);
            magnet.setMagnet(magnetUrl);
            magnet.setName(cleanText(extractAttr(row, "\">", "</a>")));
            magnet.setSizeText(extractSizeText(row));
            magnet.setSizeBytes(parseSizeBytes(magnet.getSizeText()));
            magnet.setShareDate(extractDate(row));
            magnet.setHd(row.contains("高清") ? 1 : 0);
            magnet.setSubtitle(row.contains("字幕") ? 1 : 0);
            magnets.add(magnet);
        }
        return magnets;
    }

    // ==================== HTTP 工具 ====================

    /** 带 cookie/UA 的 GET 请求，返回 HTML。 */
    public String get(String url) throws Exception {
        throttle();
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", baseUrl + "/");
        if (StringUtils.isNotBlank(cookie)) {
            builder.header("Cookie", cookie.trim());
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                log.warn("javbus request failed, url={}, code={}", url, response.code());
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            byte[] bytes = body.bytes();
            String html = new String(bytes, StandardCharsets.UTF_8);
            if (printHtml) {
                System.out.println("==================== javbus GET: " + url + " ====================");
                System.out.println(html);
                System.out.println("==================== javbus GET end: " + url + " ====================");
            }
            return html;
        }
    }

    /** 连通性自检。 */
    public String ping() {
        try {
            String html = get(baseUrl + "/");
            if (html == null) {
                return "FAIL: 请求失败（响应为空或非 2xx）";
            }
            return "OK: 页面大小 " + html.length() + " 字符";
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

    private String extractAttr(String text, String start, String end) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        int s = text.indexOf(start);
        if (s < 0) {
            return null;
        }
        s += start.length();
        int e = text.indexOf(end, s);
        if (e < 0) {
            return null;
        }
        return text.substring(s, e).trim();
    }

    private String cleanText(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
        return s.isEmpty() ? null : s;
    }

    private String extractDate(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Matcher m = Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(text);
        return m.find() ? m.group() : null;
    }

    private Integer extractDuration(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d+)\\s*(?:分鍾|分钟|min)").matcher(text);
        if (m.find()) {
            try {
                return Integer.valueOf(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String extractSizeText(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Matcher m = Pattern.compile("([\\d.]+\\s*(?:MB|GB|TB|KB|mb|gb|tb|kb))").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private Long parseSizeBytes(String sizeText) {
        if (StringUtils.isBlank(sizeText)) {
            return null;
        }
        try {
            Matcher m = Pattern.compile("([\\d.]+)\\s*([A-Za-z]+)").matcher(sizeText);
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

    private String toAbsolute(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private String extractDetailId(String detailUrl) {
        if (StringUtils.isBlank(detailUrl)) {
            return null;
        }
        String s = detailUrl;
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        int idx = s.lastIndexOf('/');
        return idx >= 0 ? s.substring(idx + 1) : s;
    }

    private String normalizeCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return code.trim().replaceAll("\\s+", "").toUpperCase();
    }

    private String urlEncode(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }
}
