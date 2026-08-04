package org.holic.javspy.javdb.controller;

import com.github.pagehelper.PageInfo;
import org.holic.javspy.javdb.model.JavdbMovie;
import org.holic.javspy.javdb.model.JavdbScrapeResult;
import org.holic.javspy.javdb.model.JavdbVideoItem;
import org.holic.javspy.javdb.service.JavdbScraperService;
import org.holic.javspy.misc.WebResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * javdb 刮削与查询接口。
 */
@RestController
@RequestMapping("/javdb")
public class JavdbController {

    private final JavdbScraperService scraperService;

    public JavdbController(JavdbScraperService scraperService) {
        this.scraperService = scraperService;
    }

    /**
     * 按番号刮削一部影片并入库。
     * 示例：GET /javdb/scrape/code?code=SSIS-123
     */
    @GetMapping("/scrape/code")
    public WebResult<JavdbScrapeResult> scrapeByCode(
            @RequestParam("code") String code) {
        try {
            return WebResult.<JavdbScrapeResult>builder()
                    .success(true).data(scraperService.scrapeByCode(code))
                    .message("刮削完成").build();
        } catch (Exception e) {
            return WebResult.<JavdbScrapeResult>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /**
     * 按 javdb 详情页地址刮削并入库。
     * 示例：GET /javdb/scrape/url?url=https://javdb.com/v/xxxxx
     */
    @GetMapping("/scrape/url")
    public WebResult<JavdbScrapeResult> scrapeByUrl(
            @RequestParam("url") String url) {
        try {
            return WebResult.<JavdbScrapeResult>builder()
                    .success(true).data(scraperService.scrapeByUrl(url))
                    .message("刮削完成").build();
        } catch (Exception e) {
            return WebResult.<JavdbScrapeResult>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /**
     * 按关键词搜索 javdb 并批量刮削入库。
     * 示例：GET /javdb/scrape/search?keyword=SSIS&pages=3
     */
    @GetMapping("/scrape/search")
    public WebResult<List<Map<String, Object>>> scrapeByKeyword(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pages", defaultValue = "1") int pages) {
        try {
            List<Map<String, Object>> summary = scraperService.scrapeByKeyword(keyword, pages);
            return WebResult.<List<Map<String, Object>>>builder()
                    .success(true).data(summary)
                    .message("批量刮削完成，共处理 " + summary.size() + " 部").build();
        } catch (Exception e) {
            return WebResult.<List<Map<String, Object>>>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /**
     * 分页查询已入库的 javdb 影片。
     * 示例：GET /javdb/list?keyword=SSIS&pageNum=1&pageSize=20
     */
    @GetMapping("/list")
    public WebResult<PageInfo<JavdbMovie>> list(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "releaseDate", required = false) String releaseDate,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        PageInfo<JavdbMovie> page = scraperService.searchMovies(
                code, keyword, releaseDate, pageNum, pageSize);
        return WebResult.<PageInfo<JavdbMovie>>builder()
                .success(true).data(page)
                .message("查询成功").build();
    }

    /**
     * 连通性自检：用当前配置（代理/cookie）请求 javdb 首页。
     * 示例：GET /javdb/ping
     */
    @GetMapping("/ping")
    public WebResult<String> ping() {
        String result = scraperService.ping();
        boolean ok = result != null && result.startsWith("OK");
        return WebResult.<String>builder()
                .success(ok).data(result)
                .message(ok ? "javdb 可访问" : "javdb 不可访问，请检查代理/网络").build();
    }

    /**
     * 抓取 javdb 首页，返回影片列表（JSON，不落库）。
     * 示例：GET /javdb/home
     */
    @GetMapping("/home")
    public WebResult<List<JavdbVideoItem>> home() {
        try {
            List<JavdbVideoItem> items = scraperService.homeMovies();
            return WebResult.<List<JavdbVideoItem>>builder()
                    .success(true).data(items)
                    .message("首页共 " + items.size() + " 部影片").build();
        } catch (Exception e) {
            return WebResult.<List<JavdbVideoItem>>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /**
     * 搜索 javdb 并返回影片列表（JSON，不落库）。
     * 示例：GET /javdb/search/list?keyword=SSIS&pages=2
     */
    @GetMapping("/search/list")
    public WebResult<List<JavdbVideoItem>> searchList(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pages", defaultValue = "1") int pages) {
        try {
            List<JavdbVideoItem> items = scraperService.searchVideoItems(keyword, pages);
            return WebResult.<List<JavdbVideoItem>>builder()
                    .success(true).data(items)
                    .message("搜索到 " + items.size() + " 部影片").build();
        } catch (Exception e) {
            return WebResult.<List<JavdbVideoItem>>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /**
     * 按页刮削 javdb 影片（默认第 1 页，即首页）。
     * withDetail=false 时只返回影片列表；true 时进一步刮削每部详情并入库。
     * 示例：GET /javdb/scrape/page?page=1&withDetail=true
     */
    @GetMapping("/scrape/page")
    public WebResult<List<Map<String, Object>>> scrapePage(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "withDetail", defaultValue = "true") boolean withDetail) {
        try {
            List<Map<String, Object>> summary = scraperService.scrapeByPage(page, withDetail);
            return WebResult.<List<Map<String, Object>>>builder()
                    .success(true).data(summary)
                    .message("第 " + page + " 页共处理 " + summary.size() + " 部影片").build();
        } catch (Exception e) {
            return WebResult.<List<Map<String, Object>>>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }
}
