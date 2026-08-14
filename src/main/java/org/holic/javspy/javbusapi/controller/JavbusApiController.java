package org.holic.javspy.javbusapi.controller;

import com.github.pagehelper.PageInfo;
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;
import org.holic.javspy.javbusapi.model.JavbusApiMovieDetail;
import org.holic.javspy.javbusapi.model.JavbusApiMovieDisplay;
import org.holic.javspy.javbusapi.model.JavbusApiScrapeItem;
import org.holic.javspy.javbusapi.model.JavbusApiScrapeResult;
import org.holic.javspy.javbusapi.model.JavbusApiScrapeStatus;
import org.holic.javspy.javbusapi.model.JavbusApiStarDetail;
import org.holic.javspy.javbusapi.model.JavbusFollowActor;
import org.holic.javspy.javbusapi.service.JavbusApiService;
import org.holic.javspy.misc.WebResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * javbus API 刮削与查询接口。
 */
@RestController
@RequestMapping("/javbus-api")
public class JavbusApiController {

    private final JavbusApiService service;

    public JavbusApiController(JavbusApiService service) {
        this.service = service;
    }

    /** 连通性自检：GET /javbus-api/ping */
    @GetMapping("/ping")
    public WebResult<String> ping() {
        String result = service.ping();
        boolean ok = result != null && result.startsWith("OK");
        return WebResult.<String>builder()
                .success(ok).data(result)
                .message(ok ? "javbus API 可访问" : "javbus API 不可访问").build();
    }

    /** 按番号抓取详情+磁力并入库：GET /javbus-api/scrape/code?code=SSIS-406 */
    @GetMapping("/scrape/code")
    public WebResult<JavbusApiScrapeResult> scrapeByCode(
            @RequestParam("code") String code) {
        try {
            return WebResult.<JavbusApiScrapeResult>builder()
                    .success(true).data(service.scrapeByCode(code))
                    .message("抓取完成").build();
        } catch (Exception e) {
            return WebResult.<JavbusApiScrapeResult>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 启动后台一键刮削直到命中 Emby：POST /javbus-api/scrape/until-emby */
    @org.springframework.web.bind.annotation.PostMapping("/scrape/until-emby")
    public WebResult<Boolean> startScrapeUntilEmby() {
        boolean started = service.startScrapeUntilEmby();
        return WebResult.<Boolean>builder()
                .success(true).data(started)
                .message(started ? "已开始后台刮削" : "刮削任务已在运行").build();
    }

    /** 后台一键刮削状态：GET /javbus-api/scrape/until-emby/status */
    @GetMapping("/scrape/until-emby/status")
    public WebResult<JavbusApiScrapeStatus> scrapeUntilEmbyStatus() {
        return WebResult.<JavbusApiScrapeStatus>builder()
                .success(true).data(service.scrapeUntilEmbyStatus())
                .message("查询成功").build();
    }

    /** 按关键词搜索并逐部入库：GET /javbus-api/scrape/search?keyword=三上&pages=2&magnet=exist */
    @GetMapping("/scrape/search")
    public WebResult<List<JavbusApiScrapeItem>> scrapeByKeyword(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pages", defaultValue = "1") int pages,
            @RequestParam(value = "magnet", defaultValue = "exist") String magnet) {
        try {
            List<JavbusApiScrapeItem> summary = service.scrapeByKeyword(keyword, pages, magnet);
            return WebResult.<List<JavbusApiScrapeItem>>builder()
                    .success(true).data(summary)
                    .message("搜索抓取完成，共处理 " + summary.size() + " 部").build();
        } catch (Exception e) {
            return WebResult.<List<JavbusApiScrapeItem>>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 按页抓取列表（默认第 1 页，有磁力）：GET /javbus-api/scrape/page?page=1&magnet=exist&withDetail=true */
    @GetMapping("/scrape/page")
    public WebResult<List<JavbusApiScrapeItem>> scrapePage(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "magnet", defaultValue = "exist") String magnet,
            @RequestParam(value = "withDetail", defaultValue = "true") boolean withDetail) {
        try {
            List<JavbusApiScrapeItem> summary = service.scrapeByPage(page, magnet, withDetail);
            return WebResult.<List<JavbusApiScrapeItem>>builder()
                    .success(true).data(summary)
                    .message("第 " + page + " 页共处理 " + summary.size() + " 部影片").build();
        } catch (Exception e) {
            return WebResult.<List<JavbusApiScrapeItem>>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 分页查询已入库的影片：GET /javbus-api/list?keyword=SSIS&pageNum=1&pageSize=20 */
    @GetMapping("/list")
    public WebResult<PageInfo<JavbusApiMovieDisplay>> list(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "releaseDate", required = false) String releaseDate,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        PageInfo<JavbusApiMovieDisplay> page = service.searchMovies(
                code, keyword, releaseDate, pageNum, pageSize);
        return WebResult.<PageInfo<JavbusApiMovieDisplay>>builder()
                .success(true).data(page)
                .message("查询成功").build();
    }

    /** 最新入库影片分页查询：GET /javbus-api/newest?pageNum=1&pageSize=30 */
    @GetMapping("/newest")
    public WebResult<PageInfo<JavbusApiMovieDisplay>> newest(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "30") int pageSize) {
        PageInfo<JavbusApiMovieDisplay> page = service.newestMovies(pageNum, pageSize);
        return WebResult.<PageInfo<JavbusApiMovieDisplay>>builder()
                .success(true).data(page)
                .message("查询成功").build();
    }

    /** 影片详情（基本信息+演员+预览图）：GET /javbus-api/movie?code=SSIS-406 */
    @GetMapping("/movie")
    public WebResult<JavbusApiMovieDetail> movieDetail(
            @RequestParam("code") String code) {
        try {
            JavbusApiMovieDetail detail = service.movieDetail(code);
            return WebResult.<JavbusApiMovieDetail>builder()
                    .success(true).data(detail)
                    .message("查询成功").build();
        } catch (Exception e) {
            return WebResult.<JavbusApiMovieDetail>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 演员详情：GET /javbus-api/star?id=2xi&type=normal */
    @GetMapping("/star")
    public WebResult<JavbusApiStarDetail> starDetail(
            @RequestParam("id") String id,
            @RequestParam(value = "type", defaultValue = "normal") String type) {
        try {
            JavbusApiStarDetail detail = service.starDetail(id, type);
            return WebResult.<JavbusApiStarDetail>builder()
                    .success(true).data(detail)
                    .message("查询成功").build();
        } catch (Exception e) {
            return WebResult.<JavbusApiStarDetail>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 关注演员列表：GET /javbus-api/follow/actors */
    @GetMapping("/follow/actors")
    public WebResult<List<JavbusFollowActor>> followActors() {
        try {
            return WebResult.<List<JavbusFollowActor>>builder()
                    .success(true).data(service.listFollowActors())
                    .message("查询成功").build();
        } catch (Exception e) {
            return WebResult.<List<JavbusFollowActor>>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 新增关注演员：POST /javbus-api/follow/add?name=葵つかさ&remark= */
    @org.springframework.web.bind.annotation.PostMapping("/follow/add")
    public WebResult<Boolean> addFollowActor(
            @org.springframework.web.bind.annotation.RequestParam("name") String name,
            @org.springframework.web.bind.annotation.RequestParam(value = "remark", required = false) String remark) {
        try {
            boolean ok = service.addFollowActor(name, remark);
            return WebResult.<Boolean>builder()
                    .success(ok).data(ok)
                    .message(ok ? "已添加关注演员" : "演员已存在").build();
        } catch (Exception e) {
            return WebResult.<Boolean>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 删除关注演员：POST /javbus-api/follow/remove?name=葵つかさ */
    @org.springframework.web.bind.annotation.PostMapping("/follow/remove")
    public WebResult<Boolean> removeFollowActor(
            @org.springframework.web.bind.annotation.RequestParam("name") String name) {
        try {
            boolean ok = service.removeFollowActor(name);
            return WebResult.<Boolean>builder()
                    .success(ok).data(ok)
                    .message(ok ? "已取消关注" : "演员不存在").build();
        } catch (Exception e) {
            return WebResult.<Boolean>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 影片磁力列表：GET /javbus-api/magnets?code=SSIS-406 */
    @GetMapping("/magnets")
    public WebResult<List<JavbusApiMagnet>> magnets(
            @org.springframework.web.bind.annotation.RequestParam("code") String code) {
        try {
            List<JavbusApiMagnet> magnets = service.magnetsByCode(code);
            return WebResult.<List<JavbusApiMagnet>>builder()
                    .success(true).data(magnets)
                    .message("共 " + magnets.size() + " 条磁力").build();
        } catch (Exception e) {
            return WebResult.<List<JavbusApiMagnet>>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }

    /** 保存磁力到单独表：POST /javbus-api/magnets/save?code=SSIS-406&magnet=magnet:?xt=... */
    @org.springframework.web.bind.annotation.PostMapping("/magnets/save")
    public WebResult<Boolean> saveMagnet(
            @org.springframework.web.bind.annotation.RequestParam("code") String code,
            @org.springframework.web.bind.annotation.RequestParam("magnet") String magnet) {
        try {
            boolean ok = service.saveMagnet(code, magnet);
            return WebResult.<Boolean>builder()
                    .success(ok).data(ok)
                    .message(ok ? "磁力已保存" : "保存失败").build();
        } catch (Exception e) {
            return WebResult.<Boolean>builder()
                    .success(false).message(e.getMessage()).build();
        }
    }
}
