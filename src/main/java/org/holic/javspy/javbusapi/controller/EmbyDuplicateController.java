package org.holic.javspy.javbusapi.controller;

import org.apache.commons.lang3.StringUtils;
import org.holic.javspy.misc.EmbyDuplicateService;
import org.holic.javspy.misc.EmbyMovieChecker;
import org.holic.javspy.misc.WebResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Emby 重复影片管理接口：扫描标题/番号重复的影片、勾选删除、封面代理。
 */
@RestController
@RequestMapping("/javbus-api/emby")
public class EmbyDuplicateController {

    private static final Logger log = LoggerFactory.getLogger(EmbyDuplicateController.class);

    private final EmbyDuplicateService embyDuplicateService;
    private final EmbyMovieChecker embyMovieChecker;
    private final String deleteAdminToken;

    public EmbyDuplicateController(EmbyDuplicateService embyDuplicateService,
                                   EmbyMovieChecker embyMovieChecker,
                                   @Value("${conf.emby.delete-admin-token:}") String deleteAdminToken) {
        this.embyDuplicateService = embyDuplicateService;
        this.embyMovieChecker = embyMovieChecker;
        this.deleteAdminToken = deleteAdminToken;
    }

    /**
     * 扫描重复影片（同番号 / 标题相似）。
     *
     * @param refresh 是否强制刷新 Emby 清单（默认使用 30 秒缓存）
     * @param keyword 关键词过滤（名称/路径/番号）
     */
    @GetMapping("/duplicates")
    public WebResult<Map<String, Object>> duplicates(
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh,
            @RequestParam(value = "keyword", required = false) String keyword) {
        try {
            Map<String, Object> data = embyDuplicateService.duplicates(refresh, keyword);
            data.put("deleteAvailable", StringUtils.isNotBlank(deleteAdminToken));
            return WebResult.<Map<String, Object>>builder()
                    .success(true)
                    .data(data)
                    .message("扫描完成")
                    .build();
        } catch (Exception e) {
            log.warn("扫描 Emby 重复影片失败: {}", e.getMessage());
            return WebResult.<Map<String, Object>>builder()
                    .success(false)
                    .message("扫描失败：" + e.getMessage())
                    .build();
        }
    }

    /**
     * 删除选中的影片（会同时删除磁盘文件）。
     *
     * @param body 请求体，形如 {"ids": ["1", "2"], "confirm": true}；confirm 必须显式传 true
     */
    @PostMapping("/delete")
    public ResponseEntity<WebResult<Map<String, Object>>> delete(
            @RequestHeader(value = "X-JavSpy-Admin-Token", required = false) String token,
            @RequestBody(required = false) Map<String, Object> body) {
        if (StringUtils.isBlank(deleteAdminToken) || token == null || !MessageDigest.isEqual(
                deleteAdminToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            return deleteError(HttpStatus.FORBIDDEN, "永久删除未配置或管理员令牌无效");
        }
        if (body == null || !Boolean.TRUE.equals(body.get("confirm"))) {
            return deleteError(HttpStatus.BAD_REQUEST, "缺少删除确认，已取消删除");
        }
        List<String> ids = new ArrayList<>();
        Object raw = body.get("ids");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (!(o instanceof String)) {
                    return deleteError(HttpStatus.BAD_REQUEST, "条目 ID 格式无效");
                }
                ids.add((String) o);
            }
        }
        if (ids.isEmpty()) {
            return deleteError(HttpStatus.BAD_REQUEST, "未选择任何影片");
        }
        try {
            Map<String, Object> data = embyDuplicateService.delete(ids);
            int deleted = ((Number) data.get("deleted")).intValue();
            int requested = ((Number) data.get("requested")).intValue();
            String message = deleted == requested
                    ? "已删除 " + deleted + " 个影片"
                    : "已删除 " + deleted + " 个，未确认 " + (requested - deleted) + " 个";
            return ResponseEntity.ok(WebResult.<Map<String, Object>>builder()
                    .success(true)
                    .data(data)
                    .message(message)
                    .build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return deleteError(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.warn("删除 Emby 影片时发生未预期错误", e);
            return deleteError(HttpStatus.INTERNAL_SERVER_ERROR, "删除结果未确认，请重新扫描核对");
        }
    }

    private static ResponseEntity<WebResult<Map<String, Object>>> deleteError(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(WebResult.<Map<String, Object>>builder()
                .success(false).message(message).build());
    }

    /** 代理 Emby 条目主图，避免前端跨域与 api_key 暴露。 */
    @GetMapping("/cover")
    public ResponseEntity<byte[]> cover(@RequestParam("id") String id,
                                        @RequestParam(value = "maxWidth", defaultValue = "300") int maxWidth,
                                        @RequestParam(value = "maxHeight", defaultValue = "420") int maxHeight) {
        byte[] bytes = embyMovieChecker.fetchItemImage(id, maxWidth, maxHeight);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        headers.setCacheControl("max-age=86400");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
