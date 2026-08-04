package org.holic.javspy.javdb.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.holic.javspy.javdb.client.JavdbApiClient;
import org.holic.javspy.javdb.mapper.JavdbMapper;
import org.holic.javspy.javdb.model.JavdbMovie;
import org.holic.javspy.javdb.model.JavdbScrapeResult;
import org.holic.javspy.javdb.model.JavdbVideoItem;
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
 * javdb 刮削业务：抓取 javdb.com 并把影片/磁力信息写入数据库。
 */
@Slf4j
@Service
public class JavdbScraperService {

    private final JavdbApiClient apiClient;
    private final JavdbMapper javdbMapper;

    public JavdbScraperService(JavdbApiClient apiClient, JavdbMapper javdbMapper) {
        this.apiClient = apiClient;
        this.javdbMapper = javdbMapper;
    }

    /**
     * 按番号刮削单个影片并入库。
     *
     * @return 入库结果；抓不到时 movie 为 null
     */
    public JavdbScrapeResult scrapeByCode(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("番号不能为空");
        }
        try {
            JavdbScrapeResult result = apiClient.scrapeByCode(code.trim());
            if (result.getMovie() == null) {
                log.warn("javdb 未找到番号: {}", code);
                return result;
            }
            saveResult(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("javdb 刮削失败, code={}", code, e);
            throw new RuntimeException("javdb 刮削失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按详情页 URL 刮削并入库。
     */
    public JavdbScrapeResult scrapeByUrl(String detailUrl) {
        if (StringUtils.isBlank(detailUrl)) {
            throw new IllegalArgumentException("详情页地址不能为空");
        }
        try {
            JavdbScrapeResult result = apiClient.scrapeByUrl(detailUrl.trim());
            if (result.getMovie() == null) {
                log.warn("javdb 详情页解析失败: {}", detailUrl);
                return result;
            }
            saveResult(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("javdb 刮削失败, url={}", detailUrl, e);
            throw new RuntimeException("javdb 刮削失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按关键词搜索 javdb 前 pages 页，逐部刮削入库。
     *
     * @return 汇总：每个番号的处理结果
     */
    public List<Map<String, Object>> scrapeByKeyword(String keyword, int pages) {
        if (StringUtils.isBlank(keyword)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        List<Map<String, Object>> summary = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        try {
            List<String> urls = apiClient.searchDetailUrls(keyword.trim(), pages);
            log.info("javdb 搜索到 {} 个详情页, keyword={}", urls.size(), keyword);
            for (String url : urls) {
                if (!visited.add(url)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("detailUrl", url);
                try {
                    JavdbScrapeResult result = apiClient.scrapeByUrl(url);
                    if (result.getMovie() == null) {
                        item.put("status", "SKIP");
                        item.put("message", "详情页解析失败");
                    } else {
                        String code = result.getMovie().getCode();
                        item.put("code", code);
                        item.put("title", result.getMovie().getTitle());
                        JavdbMovie before = javdbMapper.findByCode(code);
                        saveResult(result);
                        item.put("status", before == null ? "INSERTED" : "UPDATED");
                        item.put("magnetCount", result.getMagnets() == null ? 0 : result.getMagnets().size());
                    }
                } catch (Exception e) {
                    log.error("javdb 刮削失败, url={}", url, e);
                    item.put("status", "FAILED");
                    item.put("message", e.getMessage());
                }
                summary.add(item);
            }
        } catch (Exception e) {
            log.error("javdb 搜索失败, keyword={}", keyword, e);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", "FAILED");
            item.put("message", e.getMessage());
            summary.add(item);
        }
        return summary;
    }

    /**
     * 按番号/关键词/日期分页查询已入库的影片。
     */
    public PageInfo<JavdbMovie> searchMovies(String code, String keyword,
                                             String releaseDate, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        PageHelper.startPage(safePage, safeSize);
        List<JavdbMovie> list = javdbMapper.searchMovies(
                StringUtils.trimToNull(code),
                StringUtils.trimToNull(keyword),
                StringUtils.trimToNull(releaseDate));
        return new PageInfo<>(list);
    }

    /**
     * 保存刮削结果：影片 upsert + 磁力批量插入，同事务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveResult(JavdbScrapeResult result) {
        if (result == null || result.getMovie() == null) {
            return;
        }
        JavdbMovie movie = result.getMovie();
        if (StringUtils.isBlank(movie.getCode())) {
            throw new IllegalArgumentException("影片缺少番号，无法入库");
        }
        movie.setCode(movie.getCode().trim().toUpperCase());
        Date now = new Date();
        movie.setCreatedAt(now);
        movie.setUpdatedAt(now);
        movie.setCoverUrl(null);
        javdbMapper.insertMovie(movie);

        if (result.getMagnets() != null && !result.getMagnets().isEmpty()) {
            javdbMapper.insertMagnets(result.getMagnets());
        }
    }

    /**
     * 连通性自检：确认代理/cookie 配置后能访问 javdb。
     */
    public String ping() {
        return apiClient.ping();
    }

    /**
     * 抓取 javdb 首页影片列表（纯解析，不入库）。
     */
    public List<JavdbVideoItem> homeMovies() {
        try {
            return apiClient.homeMovies();
        } catch (Exception e) {
            log.error("javdb 首页抓取失败", e);
            throw new RuntimeException("javdb 首页抓取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按关键词搜索 javdb 影片列表（纯解析，不入库）。
     */
    public List<JavdbVideoItem> searchVideoItems(String keyword, int pages) {
        if (StringUtils.isBlank(keyword)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        try {
            return apiClient.searchMovies(keyword.trim(), pages);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("javdb 搜索失败, keyword={}", keyword, e);
            throw new RuntimeException("javdb 搜索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按页刮削 javdb 列表（默认第 1 页，即首页）。
     *
     * @param page       页码，默认 1
     * @param withDetail 是否进一步刮削每部影片详情并入库；false 时只返回列表
     * @return 每部影片的处理结果摘要
     */
    public List<Map<String, Object>> scrapeByPage(int page, boolean withDetail) {
        List<Map<String, Object>> summary = new ArrayList<>();
        try {
            List<JavdbVideoItem> items = apiClient.pageMovies(page);
            log.info("javdb 第 {} 页抓取到 {} 部影片", page, items.size());
            for (JavdbVideoItem item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", item.getCode());
                row.put("title", item.getTitle());
                row.put("url", item.getUrl());
                row.put("cover", item.getCover());
                if (!withDetail) {
                    row.put("status", "LIST");
                    summary.add(row);
                    continue;
                }
                try {
                    JavdbScrapeResult result = apiClient.scrapeByUrl(item.getUrl());
                    if (result.getMovie() == null) {
                        row.put("status", "FAILED");
                        row.put("message", "详情页解析失败");
                    } else {
                        JavdbMovie before = javdbMapper.findByCode(result.getMovie().getCode());
                        saveResult(result);
                        row.put("status", before == null ? "INSERTED" : "UPDATED");
                        row.put("magnetCount",
                                result.getMagnets() == null ? 0 : result.getMagnets().size());
                    }
                } catch (Exception e) {
                    log.error("javdb 刮削失败, url={}", item.getUrl(), e);
                    row.put("status", "FAILED");
                    row.put("message", e.getMessage());
                }
                summary.add(row);
            }
        } catch (Exception e) {
            log.error("javdb 第 {} 页抓取失败", page, e);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", "FAILED");
            row.put("message", e.getMessage());
            summary.add(row);
        }
        return summary;
    }
}
