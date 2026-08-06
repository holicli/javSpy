package org.holic.javspy.javbus.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.holic.javspy.javbus.client.JavbusApiClient;
import org.holic.javspy.javbus.mapper.JavbusMapper;
import org.holic.javspy.javbus.model.JavbusMovie;
import org.holic.javspy.javbus.model.JavbusScrapeResult;
import org.holic.javspy.javbus.model.JavbusVideoItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * javbus 刮削业务：抓取 javbus.com 并把影片/磁力信息写入数据库。
 */
@Slf4j
@Service
public class JavbusScraperService {

    private final JavbusApiClient apiClient;
    private final JavbusMapper javbusMapper;

    public JavbusScraperService(JavbusApiClient apiClient, JavbusMapper javbusMapper) {
        this.apiClient = apiClient;
        this.javbusMapper = javbusMapper;
    }

    /**
     * 按番号刮削单个影片并入库。
     */
    public JavbusScrapeResult scrapeByCode(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("番号不能为空");
        }
        try {
            JavbusScrapeResult result = apiClient.scrapeByCode(code.trim());
            if (result.getMovie() == null) {
                log.warn("javbus 未找到番号: {}", code);
                return result;
            }
            saveResult(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("javbus 刮削失败, code={}", code, e);
            throw new RuntimeException("javbus 刮削失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按详情页 URL 刮削并入库。
     */
    public JavbusScrapeResult scrapeByUrl(String detailUrl) {
        if (StringUtils.isBlank(detailUrl)) {
            throw new IllegalArgumentException("详情页地址不能为空");
        }
        try {
            JavbusScrapeResult result = apiClient.scrapeByUrl(detailUrl.trim());
            if (result.getMovie() == null) {
                log.warn("javbus 详情页解析失败: {}", detailUrl);
                return result;
            }
            saveResult(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("javbus 刮削失败, url={}", detailUrl, e);
            throw new RuntimeException("javbus 刮削失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按关键词搜索 javbus 前 pages 页，逐部刮削入库。
     */
    public List<Map<String, Object>> scrapeByKeyword(String keyword, int pages) {
        if (StringUtils.isBlank(keyword)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        List<Map<String, Object>> summary = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        try {
            List<JavbusVideoItem> items = apiClient.searchMovies(keyword.trim(), pages);
            log.info("javbus 搜索到 {} 部影片, keyword={}", items.size(), keyword);
            for (JavbusVideoItem item : items) {
                if (!visited.add(item.getUrl())) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", item.getCode());
                row.put("title", item.getTitle());
                row.put("url", item.getUrl());
                try {
                    JavbusScrapeResult result = apiClient.scrapeByUrl(item.getUrl());
                    if (result.getMovie() == null) {
                        row.put("status", "SKIP");
                        row.put("message", "详情页解析失败");
                    } else {
                        saveResult(result);
                        row.put("status", "INSERTED");
                        row.put("magnetCount",
                                result.getMagnets() == null ? 0 : result.getMagnets().size());
                    }
                } catch (Exception e) {
                    log.error("javbus 刮削失败, url={}", item.getUrl(), e);
                    row.put("status", "FAILED");
                    row.put("message", e.getMessage());
                }
                summary.add(row);
            }
        } catch (Exception e) {
            log.error("javbus 搜索失败, keyword={}", keyword, e);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", "FAILED");
            row.put("message", e.getMessage());
            summary.add(row);
        }
        return summary;
    }

    /**
     * 按页刮削 javbus 列表（默认第 1 页）。
     *
     * @param page       页码，默认 1
     * @param withDetail 是否进一步刮削每部影片详情并入库；false 时只返回列表
     */
    public List<Map<String, Object>> scrapeByPage(int page, boolean withDetail) {
        List<Map<String, Object>> summary = new ArrayList<>();
        try {
            List<JavbusVideoItem> items = apiClient.pageMovies(page);
            log.info("javbus 第 {} 页抓取到 {} 部影片", page, items.size());
            for (JavbusVideoItem item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", item.getCode());
                row.put("title", item.getTitle());
                row.put("url", item.getUrl());
                row.put("cover", item.getCover());
                if (withDetail) {
                    row.put("status", "LIST");
                    summary.add(row);
                    continue;
                }
                try {
                    JavbusScrapeResult result = apiClient.scrapeByUrl(item.getUrl());
                    if (result.getMovie() == null) {
                        row.put("status", "FAILED");
                        row.put("message", "详情页解析失败");
                    } else {
                        saveResult(result);
                        row.put("status", "INSERTED");
                        row.put("magnetCount",
                                result.getMagnets() == null ? 0 : result.getMagnets().size());
                    }
                } catch (Exception e) {
                    log.error("javbus 刮削失败, url={}", item.getUrl(), e);
                    row.put("status", "FAILED");
                    row.put("message", e.getMessage());
                }
                summary.add(row);
            }
        } catch (Exception e) {
            log.error("javbus 第 {} 页抓取失败", page, e);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", "FAILED");
            row.put("message", e.getMessage());
            summary.add(row);
        }
        return summary;
    }

    /**
     * 分页查询已入库的 javbus 影片。
     */
    public PageInfo<JavbusMovie> searchMovies(String code, String keyword,
                                              String releaseDate, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        PageHelper.startPage(safePage, safeSize);
        List<JavbusMovie> list = javbusMapper.searchMovies(
                StringUtils.trimToNull(code),
                StringUtils.trimToNull(keyword),
                StringUtils.trimToNull(releaseDate));
        return new PageInfo<>(list);
    }

    /**
     * 保存刮削结果：影片 upsert + 磁力批量插入，同事务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveResult(JavbusScrapeResult result) {
        if (result == null || result.getMovie() == null) {
            return;
        }
        JavbusMovie movie = result.getMovie();
        if (StringUtils.isBlank(movie.getCode())) {
            throw new IllegalArgumentException("影片缺少番号，无法入库");
        }
        movie.setCode(movie.getCode().trim().toUpperCase());
        Date now = new Date();
        movie.setCreatedAt(now);
        movie.setUpdatedAt(now);
        javbusMapper.insertMovie(movie);

        if (result.getMagnets() != null && !result.getMagnets().isEmpty()) {
            javbusMapper.insertMagnets(result.getMagnets());
        }
    }

    /**
     * 连通性自检。
     */
    public String ping() {
        return apiClient.ping();
    }

    /**
     * 按关键词搜索 javbus 影片列表（纯解析，不入库）。
     */
    public List<JavbusVideoItem> searchVideoItems(String keyword, int pages) {
        if (StringUtils.isBlank(keyword)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        try {
            return apiClient.searchMovies(keyword.trim(), pages);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("javbus 搜索失败, keyword={}", keyword, e);
            throw new RuntimeException("javbus 搜索失败: " + e.getMessage(), e);
        }
    }
}
