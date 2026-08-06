package org.holic.javspy.javbus.controller;

import com.github.pagehelper.PageInfo;
import org.holic.javspy.javbus.model.JavbusMovie;
import org.holic.javspy.javbus.model.JavbusScrapeResult;
import org.holic.javspy.javbus.model.JavbusVideoItem;
import org.holic.javspy.javbus.service.JavbusScraperService;
import org.holic.javspy.misc.WebResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * javbus 刮削与查询接口。
 */
@RestController
@RequestMapping("/javbus")
public class JavbusController {

    private final JavbusScraperService scraperService;

    public JavbusController(JavbusScraperService scraperService) {
        this.scraperService = scraperService;
    }

    /** 连通性自检：GET /javbus/ping */
    @GetMapping("/ping")
    public WebResult<String> ping() {
        String result = scraperService.ping();
        boolean ok = result != null && result.startsWith("OK");
        return WebResult.<String>builder()
                .success(ok).data(result)
                .message(ok ? "javbus 可访问" : "javbus 不可访问，请检查代理/网络").build();
    }

    /** 按番号刮削一部影片并入库：GET /javbus/scrape/code?code=SSIS-123 */
    @GetMapping("/scrape/code")
    public WebResult<JavbusScrapeResult> scrapeByCode(
            @RequestParam("code") String code) {
        try {
            return WebResult.<JavbusScrapeResult>builder()
                    .success(true).data(scraperService.scrapeByCode(code))
                    .message("刮削完成").build();
        } catch (Exception e) {
            return WebResult.<JavbusScrapeResult>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 按详情页地址刮削并入库：GET /javbus/scrape/url?url=https://www.javbus.com/xxxx */
    @GetMapping("/scrape/url")
    public WebResult<JavbusScrapeResult> scrapeByUrl(
            @RequestParam("url") String url) {
        try {
            return WebResult.<JavbusScrapeResult>builder()
                    .success(true).data(scraperService.scrapeByUrl(url))
                    .message("刮削完成").build();
        } catch (Exception e) {
            return WebResult.<JavbusScrapeResult>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 按关键词搜索并批量刮削入库：GET /javbus/scrape/search?keyword=SSIS&pages=2 */
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

    /** 按页刮削 javbus 影片（默认第 1 页）：GET /javbus/scrape/page?page=1&withDetail=true */
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

    /** 搜索 javbus 并返回影片列表（JSON，不落库）：GET /javbus/search/list?keyword=SSIS&pages=1 */
    @GetMapping("/search/list")
    public WebResult<List<JavbusVideoItem>> searchList(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pages", defaultValue = "1") int pages) {
        try {
            List<JavbusVideoItem> items = scraperService.searchVideoItems(keyword, pages);
            return WebResult.<List<JavbusVideoItem>>builder()
                    .success(true).data(items)
                    .message("搜索到 " + items.size() + " 部影片").build();
        } catch (Exception e) {
            return WebResult.<List<JavbusVideoItem>>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 分页查询已入库的 javbus 影片：GET /javbus/list?keyword=SSIS&pageNum=1&pageSize=20 */
    @GetMapping("/list")
    public WebResult<PageInfo<JavbusMovie>> list(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "releaseDate", required = false) String releaseDate,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        PageInfo<JavbusMovie> page = scraperService.searchMovies(
                code, keyword, releaseDate, pageNum, pageSize);
        return WebResult.<PageInfo<JavbusMovie>>builder()
                .success(true).data(page)
                .message("查询成功").build();
    }
}
