package org.holic.javspy.javdb.client;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.holic.javspy.javdb.model.JavdbMagnet;
import org.holic.javspy.javdb.model.JavdbMovie;
import org.holic.javspy.javdb.model.JavdbScrapeResult;
import org.holic.javspy.javdb.model.JavdbVideoItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * javdb.com 页面抓取客户端。
 *
 * <p>javdb 没有公开稳定的 JSON API，这里直接抓 HTML：
 * <ul>
 *     <li>搜索：GET {baseUrl}/search?q=番号&amp;filter=&amp;page=N</li>
 *     <li>详情：GET {baseUrl}/v/{detailId}</li>
 *     <li>磁力：GET {baseUrl}/v/{detailId}/magnets（详情页 tab 单独加载）</li>
 * </ul>
 * 抓取详情和磁力通常需要登录 cookie（配置 conf.javdb.cookie）。
 */
@Slf4j
@Component
public class JavdbApiClient {

    /** 站点地址，默认 https://javdb.com */
    @Value("${conf.javdb.base-url:https://javdb.com}")
    private String baseUrl;

    /** 登录 cookie（remember=xxx），抓取详情/磁力建议配置 */
    @Value("${conf.javdb.cookie:}")
    private String cookie;

    /** 代理主机（如 127.0.0.1），留空则不使用代理 */
    @Value("${conf.javdb.proxy-host:}")
    private String proxyHost;

    /** 代理端口 */
    @Value("${conf.javdb.proxy-port:0}")
    private int proxyPort;

    /** 请求超时（毫秒） */
    @Value("${conf.javdb.timeout-ms:20000}")
    private long timeoutMs;

    /** 两次请求之间的间隔（毫秒），避免请求过快 */
    @Value("${conf.javdb.request-interval-ms:1500}")
    private long requestIntervalMs;

    /** 是否把抓到的 HTML 直接打印到后台控制台（调试用） */
    @Value("${conf.javdb.print-html:false}")
    private boolean printHtml;

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final Object requestLock = new Object();
    private long lastRequestTime;

    private OkHttpClient httpClient;

    /** 磁力地址缓存：javdb 详情页磁力是跳转地址，先记住目标再补充完整 magnet 链接 */
    private final ConcurrentHashMap<String, String> magnetUrlCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(true);
        if (StringUtils.isNotBlank(proxyHost) && proxyPort > 0) {
            builder.proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort)));
            log.info("javdb client uses SOCKS5 proxy {}:{}", proxyHost, proxyPort);
        }
        this.httpClient = builder.build();
        log.info("javdb client initialized, baseUrl={}, cookieConfigured={}",
                baseUrl, StringUtils.isNotBlank(cookie));
    }

    // ==================== 对外方法 ====================

    /**
     * 按番号刮削：先搜索确认详情页，再解析详情 + 磁力。
     * 搜索不到时返回 movie = null 的结果。
     */
    public JavdbScrapeResult scrapeByCode(String code) throws Exception {
        JavdbScrapeResult result = new JavdbScrapeResult();
        String detailUrl = searchDetailUrl(code);
        if (StringUtils.isBlank(detailUrl)) {
            log.warn("javdb search no result for code={}", code);
            return result;
        }
        return scrapeByUrl(detailUrl);
    }

    /**
     * 搜索番号，返回第一个匹配的详情页地址；找不到返回 null。
     */
    public String searchDetailUrl(String code) throws Exception {
        String url = baseUrl + "/search?q=" + urlEncode(code.trim()) + "&filter=&page=1";
        String html = get(url);
        if (StringUtils.isBlank(html)) {
            return null;
        }
        // 搜索页影片卡片：<a href="/v/xxxx" class="box" title="...">
        //   <div class="video-title"><strong>番号</strong> 标题</div>
        Pattern p = Pattern.compile(
                "<a[^>]*href=\"(/v/[^\"]+)\"[^>]*class=\"box\"[^>]*>(.*?)</a>",
                Pattern.DOTALL);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String cardCode = normalizeCode(extractAttr(m.group(2), "<strong>", "</strong>"));
            if (cardCode != null && cardCode.equals(normalizeCode(code))) {
                return toAbsolute(m.group(1));
            }
        }
        return null;
    }

    /**
     * 搜索关键词，返回搜索页前 pages 页中出现的全部详情页地址。
     */
    public List<String> searchDetailUrls(String keyword, int pages) throws Exception {
        List<String> urls = new ArrayList<>();
        for (int page = 1; page <= Math.max(1, pages); page++) {
            String url = baseUrl + "/search?q=" + urlEncode(keyword.trim())
                    + "&filter=&page=" + page;
            String html = get(url);
            if (StringUtils.isBlank(html)) {
                break;
            }
            boolean any = false;
            for (JavdbVideoItem item : parseVideoCards(html)) {
                String abs = item.getUrl();
                if (!urls.contains(abs)) {
                    urls.add(abs);
                }
                any = true;
            }
            if (!any) {
                break;
            }
        }
        return urls;
    }

    /**
     * 按详情页 URL 刮削：解析详情 + 抓磁力 tab。
     */
    public JavdbScrapeResult scrapeByUrl(String detailUrl) throws Exception {
        JavdbScrapeResult result = new JavdbScrapeResult();
        String html = get(detailUrl);
        JavdbMovie movie = parseDetail(html, detailUrl);
        if (movie == null || StringUtils.isBlank(movie.getCode())) {
            log.warn("javdb detail parse failed, url={}", detailUrl);
            return result;
        }
        movie.setDetailUrl(detailUrl);
        result.setMovie(movie);
        result.setMagnets(fetchMagnets(extractDetailId(detailUrl), movie.getCode()));
        return result;
    }

    /**
     * 按页码抓取列表（默认 javdb 首页第 1 页，页面上有若干影片卡片）。
     */
    public List<JavdbVideoItem> pageMovies(int page) throws Exception {
        String url = baseUrl + "/" + (page <= 1 ? "" : "?page=" + page);
        String html = get(url);
        if (StringUtils.isBlank(html)) {
            return new ArrayList<>();
        }
        return parseVideoCards(html);
    }

    /**
     * 抓取 javdb 首页，返回首页影片列表（只解析，不落库）。
     */
    public List<JavdbVideoItem> homeMovies() throws Exception {
        return pageMovies(1);
    }

    /**
     * 抓取搜索页，返回搜索到的影片列表（只解析，不落库）。
     */
    public List<JavdbVideoItem> searchMovies(String keyword, int pages) throws Exception {
        List<JavdbVideoItem> items = new ArrayList<>();
        for (int page = 1; page <= Math.max(1, pages); page++) {
            String url = baseUrl + "/search?q=" + urlEncode(keyword.trim())
                    + "&filter=&page=" + page;
            String html = get(url);
            if (StringUtils.isBlank(html)) {
                break;
            }
            List<JavdbVideoItem> pageItems = parseVideoCards(html);
            for (JavdbVideoItem item : pageItems) {
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
     * 解析详情页 HTML -> 影片信息。
     * 真实页面结构：
     * <pre>
     * &lt;h2 class="title is-4"&gt;&lt;strong&gt;IPZZ-802 &lt;/strong&gt;
     *   &lt;strong class="current-title"&gt;标题&lt;/strong&gt;&lt;/h2&gt;
     * &lt;div class="video-meta-panel"&gt;... cover ... movie-panel-info 元数据行 ...&lt;/div&gt;
     * </pre>
     */
    public JavdbMovie parseDetail(String html, String detailUrl) {
        if (StringUtils.isBlank(html)) {
            return null;
        }
        JavdbMovie movie = new JavdbMovie();
        movie.setDetailUrl(detailUrl);
        movie.setRawHtml(html.length() > 100000 ? html.substring(0, 100000) : html);

        // 番号：h2 里第一个 <strong>
        int h2Idx = html.indexOf("<h2");
        if (h2Idx >= 0) {
            String h2 = html.substring(h2Idx, Math.min(html.length(), h2Idx + 1500));
            String code = extractAttr(h2, "<strong>", "</strong>");
            if (StringUtils.isBlank(code)) {
                code = extractAttr(h2, "<h2", "</h2>");
            }
            movie.setCode(normalizeCode(code));
            // 标题：current-title
            String title = extractAttr(h2, "class=\"current-title\">", "</strong>");
            if (StringUtils.isNotBlank(title)) {
                movie.setTitle(cleanText(title));
            } else {
                // 兜底：<title>页签里的 "番号 标题 | JavDB"
                movie.setTitle(cleanText(extractAttr(html, "<title>", "</title>")));
            }
        }

        // 封面：class="video-cover" 的 img
        movie.setCoverUrl(toAbsolute(extractAttr(html, "<img src=\"", "\" class=\"video-cover\"")));
        if (StringUtils.isBlank(movie.getCoverUrl())) {
            movie.setCoverUrl(toAbsolute(extractAttr(html, "src=\"", "\"")));
        }

        // 元数据：movie-panel-info 里的 panel-block 行（番號/日期/時長/導演/片商/系列/類別/演員）
        int panelIdx = html.indexOf("movie-panel-info");
        if (panelIdx >= 0) {
            String panel = html.substring(panelIdx, Math.min(html.length(), panelIdx + 20000));
            Pattern rowP = Pattern.compile(
                    "<strong>([^<]{1,20}?):</strong>[\\s\\S]*?<span class=\"value\">([\\s\\S]*?)</span>");
            Matcher rowM = rowP.matcher(panel);
            while (rowM.find()) {
                String key = cleanText(rowM.group(1));
                String value = cleanText(rowM.group(2));
                if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
                    continue;
                }
                if (key.contains("番號") || key.contains("番号")) {
                    if (StringUtils.isBlank(movie.getCode())) {
                        movie.setCode(normalizeCode(value));
                    }
                } else if (key.contains("日期")) {
                    movie.setReleaseDate(extractDate(value));
                } else if (key.contains("時長") || key.contains("时长")) {
                    movie.setDuration(extractDuration(value));
                } else if (key.contains("導演") || key.contains("导演")) {
                    movie.setDirector(value);
                } else if (key.contains("片商")) {
                    movie.setStudio(value);
                } else if (key.contains("系列")) {
                    movie.setSeries(value);
                } else if (key.contains("類別") || key.contains("类别")) {
                    movie.setGenres(value);
                } else if (key.contains("演員") || key.contains("演员")) {
                    // 去掉性别符号 ♀♂
                    movie.setActors(value.replace("♀", "").replace("♂", ""));
                }
            }
        }

        // 兜底：日期取正文第一个 yyyy-MM-dd
        if (StringUtils.isBlank(movie.getReleaseDate())) {
            movie.setReleaseDate(extractDate(html));
        }
        return movie;
    }

    /**
     * 抓取磁力 tab 并解析磁力列表：GET /v/{id}/magnets
     */
    public List<JavdbMagnet> fetchMagnets(String detailId, String code) {
        List<JavdbMagnet> magnets = new ArrayList<>();
        if (StringUtils.isBlank(detailId)) {
            return magnets;
        }
        try {
            String html = get(baseUrl + "/v/" + detailId + "/magnets");
            if (StringUtils.isBlank(html)) {
                return magnets;
            }
            magnets = parseMagnets(html, code, detailId);
        } catch (Exception e) {
            log.warn("javdb magnets fetch failed, detailId={}", detailId, e);
        }
        return magnets;
    }

    /**
     * 解析磁力列表 HTML。
     */
    public List<JavdbMagnet> parseMagnets(String html, String code, String detailId) {
        List<JavdbMagnet> magnets = new ArrayList<>();
        if (StringUtils.isBlank(html)) {
            return magnets;
        }
        Pattern p = Pattern.compile(
                "<a[^>]*href=\"([^\"]*magnet:?[^\"]*|/magnet/[^\"]+)\"[^>]*>([\\s\\S]*?)</a>");
        Matcher m = p.matcher(html);
        while (m.find()) {
            String link = m.group(1);
            String name = cleanText(m.group(2));
            String row = m.group(2);
            if (StringUtils.isBlank(link) && StringUtils.isBlank(name)) {
                continue;
            }
            // javdb 用 /magnet/xxx 路径占位，真实磁力地址走磁力页；这里先按 href 原样记录
            if (link.startsWith("/magnet/")) {
                link = toAbsolute(link);
            }
            JavdbMagnet magnet = new JavdbMagnet();
            magnet.setCode(code);
            magnet.setDetailId(detailId);
            magnet.setMagnet(link);
            magnet.setName(name);
            magnet.setSizeText(extractSizeText(row));
            magnet.setSizeBytes(parseSizeBytes(magnet.getSizeText()));
            magnet.setShareDate(extractDate(row));
            magnet.setHd(row.contains("高清") ? 1 : 0);
            magnet.setSubtitle(hasSubtitle(row) ? 1 : 0);
            magnets.add(magnet);
        }
        return magnets;
    }

    /**
     * 解析首页/搜索页里的影片卡片：
     * <a href="/v/xxxx" class="box" title="标题">
     *   <div class="cover"><img loading="lazy" src="封面" /></div>
     *   <div class="video-title"><strong>番号</strong> 标题</div>
     * </a>
     */
    public List<JavdbVideoItem> parseVideoCards(String html) {
        List<JavdbVideoItem> items = new ArrayList<>();
        if (StringUtils.isBlank(html)) {
            return items;
        }
        Pattern p = Pattern.compile(
                "<a[^>]*href=\"(/v/[^\"]+)\"[^>]*class=\"box\"[^>]*>(.*?)</a>",
                Pattern.DOTALL);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String card = m.group(2);
            String linkTag = m.group(0);
            JavdbVideoItem item = new JavdbVideoItem();
            item.setUrl(toAbsolute(m.group(1)));
            item.setCode(normalizeCode(extractAttr(card, "<strong>", "</strong>")));
            String linkTitle = extractAttr(linkTag, "title=\"", "\"");
            if (linkTitle != null) {
                item.setTitle(cleanText(linkTitle));
            } else {
                item.setTitle(cleanText(extractAttr(card, "</strong>", "</div>")));
            }
            item.setCover(toAbsolute(extractAttr(card, "src=\"", "\"")));
            if (item.getCode() != null) {
                items.add(item);
            }
        }
        return items;
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
        // 组装 Cookie：always 带上 over18=1（成人确认），用户 cookie 追加在后面
        StringBuilder cookieHeader = new StringBuilder("over18=1");
        if (StringUtils.isNotBlank(cookie)) {
            cookieHeader.append("; ").append(cookie);
        }
        builder.header("Cookie", cookieHeader.toString());
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                log.warn("javdb request failed, url={}, code={}", url, response.code());
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            byte[] bytes = body.bytes();
            String html = new String(bytes, StandardCharsets.UTF_8);
            if (printHtml) {
                System.out.println("==================== javdb GET: " + url + " ====================");
                System.out.println(html);
                System.out.println("==================== javdb GET end: " + url + " ====================");
            }
            return html;
        }
    }

    /**
     * 连通性自检：请求 javdb 首页，返回响应码或异常信息。
     */
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

    /** 提取 start 与 end 之间的内容（取第一次出现），找不到返回 null。 */
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

    /** 清洗 HTML 实体、标签、空白。 */
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

    /** 提取日期（yyyy-MM-dd）。 */
    private String extractDate(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Matcher m = Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(text);
        return m.find() ? m.group() : null;
    }

    /** 提取时长（分钟）：150 分鍾 / 150 分钟 / 150min。 */
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

    /** 从磁力行文本里提取大小字符串，如 2.3 GB。 */
    private String extractSizeText(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Matcher m = Pattern.compile("([\\d.]+\\s*(?:MB|GB|TB|KB|mb|gb|tb|kb))").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** 把 "2.3 GB" 之类转成字节数。 */
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

    private boolean hasSubtitle(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        return text.contains("字幕") || text.contains("SUB") || text.contains("sub");
    }

    /** 把相对路径转成绝对地址。 */
    private String toAbsolute(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    /** 从详情页 URL 提取 id：/v/xxxx -> xxxx */
    private String extractDetailId(String detailUrl) {
        if (StringUtils.isBlank(detailUrl)) {
            return null;
        }
        int idx = detailUrl.lastIndexOf('/');
        return idx >= 0 ? detailUrl.substring(idx + 1) : detailUrl;
    }

    /** 番号统一转大写并去掉空格。 */
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
