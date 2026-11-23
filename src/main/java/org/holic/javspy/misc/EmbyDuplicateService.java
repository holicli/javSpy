package org.holic.javspy.misc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emby 重复影片检测服务。
 * <p>
 * 拉取 Emby 全部影片后按两种规则分组，供页面勾选删除：
 * <ol>
 *     <li><b>同番号</b>：仅匹配名称或文件名开头的番号，如 {@code SSIS-123}。
 *     名称和文件名相互印证后，才允许批量勾选。</li>
 *     <li><b>标题相似</b>：未被同番号分组的条目，按归一化标题的二元组 Dice 系数聚类（阈值 {@value #TITLE_SIMILARITY_THRESHOLD}）。</li>
 * </ol>
 * 删除走 Emby 的 {@code /Items/Delete} 接口，会同时删除磁盘文件。
 */
@Service
public class EmbyDuplicateService {

    private static final Logger log = LoggerFactory.getLogger(EmbyDuplicateService.class);

    /** 标题相似度阈值（二元组 Dice 系数），仅用于「无番号」条目之间的兜底比对。 */
    private static final double TITLE_SIMILARITY_THRESHOLD = 0.95;

    private static final Pattern CODE_FC2 =
            Pattern.compile("(?i)^FC2[-_](?:PPV[-_])?(\\d{5,8})(?![A-Z0-9])");
    private static final Pattern CODE_EXPLICIT =
            Pattern.compile("(?i)^((?:\\d{2,6})?[A-Z]{1,6}|[A-Z]\\d{1,3})[-_](\\d{2,6})(?![A-Z0-9])");
    private static final Pattern CODE_WEAK =
            Pattern.compile("(?i)^((?:\\d{2,6})?[A-Z]{2,6}) ?(\\d{2,5})(?![A-Z0-9])");

    /** 版本标签识别规则：正则 -> 标签。 */
    private static final String[][] TAG_RULES = {
            {"(?i)(uncensored|无码|無碼|破解|流出|leak)", "破解/无码"},
            {"(?i)(h265|hevc|x265)", "H265"},
            {"(?i)(^|[-_. ])ai([-_. ]|$)", "AI修复"},
            {"(?i)fhd", "FHD"},
            {"(?i)(4k|2160p)", "4K"},
            {"(?i)(^|[-_. ])u([-_. ]|$)", "U"},
            {"(?i)(^|[-_. ])uc([-_. ]|$)", "UC"},
    };

    /** 影片清单短时缓存，避免页面反复刷新时重复拉取 Emby。 */
    private static final long CACHE_TTL_MS = 30_000L;

    private final EmbyMovieChecker embyMovieChecker;
    private final EmbyMovieService embyMovieService;

    private volatile List<EmbyMediaItem> cachedItems;
    private volatile long cachedAt;

    public EmbyDuplicateService(EmbyMovieChecker embyMovieChecker,
                                EmbyMovieService embyMovieService) {
        this.embyMovieChecker = embyMovieChecker;
        this.embyMovieService = embyMovieService;
    }

    /**
     * 扫描 Emby 重复影片。
     *
     * @param refresh 是否强制重新拉取 Emby（false 时命中 30 秒缓存）
     * @param keyword 过滤关键词，匹配名称/路径/番号
     * @return 分组结果 + 统计信息
     */
    public Map<String, Object> duplicates(boolean refresh, String keyword) {
        List<EmbyMediaItem> all = loadItems(refresh);
        Set<String> matched = filter(all, keyword);
        List<Group> groups = groupItems(all);
        groups.removeIf(group -> group.getItems().stream().noneMatch(item -> matched.contains(item.getId())));

        long totalSize = 0L;
        int extraItems = 0;
        long reclaimable = 0L;
        for (Group group : groups) {
            totalSize += group.getTotalSize();
            extraItems += group.getCount() - 1;
            reclaimable += group.getExtraSize();
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        stats.put("matched", matched.size());
        stats.put("groupCount", groups.size());
        stats.put("extraItems", extraItems);
        stats.put("duplicateSize", totalSize);
        stats.put("duplicateSizeText", formatSize(totalSize));
        stats.put("reclaimable", reclaimable);
        stats.put("reclaimableText", formatSize(reclaimable));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", stats);
        data.put("groups", groups);
        return data;
    }

    /**
     * 纯分组逻辑：先按归一化番号分组，剩余条目再按标题相似度聚类。
     * 组按「可释放空间」倒序排列。
     *
     * @param items 已补全番号的影片条目
     * @return 重复分组列表
     */
    static List<Group> groupItems(List<EmbyMediaItem> items) {
        List<Group> groups = new ArrayList<>();

        // 1) 同番号分组
        Map<String, List<EmbyMediaItem>> byCode = new LinkedHashMap<>();
        for (EmbyMediaItem item : items) {
            if (StringUtils.isNotBlank(item.getCode())) {
                byCode.computeIfAbsent(item.getCode(), k -> new ArrayList<>()).add(item);
            }
        }
        for (Map.Entry<String, List<EmbyMediaItem>> entry : byCode.entrySet()) {
            List<EmbyMediaItem> list = entry.getValue();
            if (list.size() < 2 || list.stream().map(EmbyMediaItem::getYear)
                    .filter(EmbyDuplicateService::knownYear).distinct().limit(2).count() > 1) {
                continue;
            }
            groups.add(buildGroup("code", entry.getKey(), entry.getKey(), list));
        }

        // 2) 标题相似分组：只处理「没有番号」的条目。
        // 有番号的条目即使标题完全相同也属于不同作品（同系列不同女优/不同集数），
        // 绝不能因为标题相似而并成一组，否则会误删正常影片。
        List<EmbyMediaItem> rest = new ArrayList<>();
        for (EmbyMediaItem item : items) {
            if (StringUtils.isBlank(item.getCode()) && !"conflict".equals(item.getCodeFrom())) {
                rest.add(item);
            }
        }
        groups.addAll(clusterByTitle(rest));

        Map<String, Integer> pathCounts = new HashMap<>();
        for (EmbyMediaItem item : items) {
            pathCounts.merge(pathKey(item.getPath()), 1, Integer::sum);
        }
        for (Group group : groups) {
            boolean deletable = group.getItems().stream().allMatch(item -> {
                String path = pathKey(item.getPath());
                return !path.isEmpty() && pathCounts.get(path) == 1 && item.getMediaSourceCount() <= 1
                        && EmbyMovieChecker.isValidItemId(item.getId());
            });
            group.setDeletable(deletable);
            group.setBatchEligible(deletable && "code".equals(group.getReason()) && group.getItems().stream()
                    .allMatch(item -> item.isCodeVerified() && item.getSize() != null && item.getSize() > 0));
        }

        groups.sort(Comparator.comparingLong(Group::getExtraSize).reversed()
                .thenComparing(Comparator.comparingLong(Group::getTotalSize).reversed()));
        return groups;
    }

    /**
     * 删除选中的 Emby 条目（同时删除磁盘文件）。
     *
     * @param ids Emby 条目 Id 列表
     * @return 删除结果：请求数、成功数、失败明细
     */
    public synchronized Map<String, Object> delete(List<String> ids) {
        requireEnabled();
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("未选择任何影片");
        }
        if (ids.stream().anyMatch(id -> !EmbyMovieChecker.isValidItemId(id))) {
            throw new IllegalArgumentException("条目 ID 无效，请重新扫描");
        }
        List<String> clean = new ArrayList<>(new LinkedHashSet<>(ids));
        List<Group> groups = groupItems(loadItems(true));
        Set<String> remaining = new HashSet<>(clean);
        for (Group group : groups) {
            long selected = group.getItems().stream().filter(item -> remaining.contains(item.getId())).count();
            if (selected == 0) {
                continue;
            }
            if (!group.isDeletable()) {
                throw new IllegalArgumentException("无法确定此组条目各自对应单独文件，已取消删除");
            }
            if (selected == group.getCount()) {
                throw new IllegalArgumentException("每个重复分组至少要保留一个条目，已取消删除");
            }
            group.getItems().forEach(item -> remaining.remove(item.getId()));
        }
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException("部分条目已不在最新重复分组中，请重新扫描");
        }
        Map<String, String> failed = embyMovieChecker.deleteItems(clean);
        int deleted = clean.size() - failed.size();

        List<Map<String, String>> failedList = new ArrayList<>();
        for (Map.Entry<String, String> entry : failed.entrySet()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", entry.getKey());
            row.put("message", entry.getValue());
            failedList.add(row);
        }

        // 即使 Emby 返回超时，也可能已经删除了部分条目，旧快照不再可靠。
        cachedItems = null;
        cachedAt = 0L;
        embyMovieService.invalidate();
        CompletableFuture.runAsync(embyMovieService::refreshInvalidatedSnapshot);
        if (deleted > 0) {
            log.info("已从 Emby 删除 {} 个影片文件（失败 {} 个）", deleted, failed.size());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requested", clean.size());
        data.put("deleted", deleted);
        data.put("failed", failedList);
        return data;
    }

    /** 拉取（或复用缓存）全部影片并补齐番号、标签、可读大小。 */
    private List<EmbyMediaItem> loadItems(boolean refresh) {
        requireEnabled();
        if (!refresh) {
            List<EmbyMediaItem> cached = cachedItems;
            if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return cached;
            }
        }
        synchronized (this) {
            if (!refresh) {
                List<EmbyMediaItem> cached = cachedItems;
                if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                    return cached;
                }
            }
            List<EmbyMediaItem> items = embyMovieChecker.getAllMovieItems();
            if (items == null) {
                throw new IllegalStateException("拉取 Emby 影片清单失败，请检查 conf.emby 配置与 Emby 服务状态");
            }
            for (EmbyMediaItem item : items) {
                resolveCode(item);
                item.setTags(detectTags(item));
                item.setSize(item.getSize() == null ? 0L : item.getSize());
                item.setSizeText(formatSize(item.getSize()));
            }
            List<EmbyMediaItem> snapshot = Collections.unmodifiableList(items);
            cachedItems = snapshot;
            cachedAt = System.currentTimeMillis();
            return snapshot;
        }
    }

    private void requireEnabled() {
        if (!embyMovieService.isEnabled()) {
            throw new IllegalStateException("Emby 功能未启用");
        }
    }

    /** 关键词过滤：名称、路径、番号任一包含即命中（忽略大小写）。 */
    private static Set<String> filter(List<EmbyMediaItem> items, String keyword) {
        String kw = StringUtils.trimToEmpty(keyword).toLowerCase(Locale.ROOT);
        Set<String> result = new HashSet<>();
        for (EmbyMediaItem item : items) {
            String name = StringUtils.defaultString(item.getName()).toLowerCase(Locale.ROOT);
            String path = StringUtils.defaultString(item.getPath()).toLowerCase(Locale.ROOT);
            String code = StringUtils.defaultString(item.getCode()).toLowerCase(Locale.ROOT);
            if (name.contains(kw) || path.contains(kw) || code.contains(kw)) {
                result.add(item.getId());
            }
        }
        return result;
    }

    /** 按标题相似度聚类（先按归一化标题前 6 位分桶，桶内做贪心聚类）。 */
    private static List<Group> clusterByTitle(List<EmbyMediaItem> rest) {
        Map<String, List<EmbyMediaItem>> buckets = new LinkedHashMap<>();
        for (EmbyMediaItem item : rest) {
            String norm = normalizeTitle(item.getName());
            if (norm.length() < 4) {
                continue;
            }
            // 上映年份缺失的条目仍可人工检查，但不能与已知年份的条目相混。
            int year = knownYear(item.getYear()) ? item.getYear() : 0;
            String key = year + ":" + norm.substring(0, Math.min(6, norm.length()));
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        List<Group> groups = new ArrayList<>();
        for (List<EmbyMediaItem> bucket : buckets.values()) {
            if (bucket.size() < 2) {
                continue;
            }
            List<List<EmbyMediaItem>> clusters = new ArrayList<>();
            List<String> clusterTitles = new ArrayList<>();
            for (EmbyMediaItem item : bucket) {
                String norm = normalizeTitle(item.getName());
                int hit = -1;
                for (int i = 0; i < clusters.size(); i++) {
                    if (dice(clusterTitles.get(i), norm) >= TITLE_SIMILARITY_THRESHOLD) {
                        hit = i;
                        break;
                    }
                }
                if (hit < 0) {
                    clusters.add(new ArrayList<>());
                    clusterTitles.add(norm);
                    hit = clusters.size() - 1;
                }
                clusters.get(hit).add(item);
            }
            for (List<EmbyMediaItem> cluster : clusters) {
                if (cluster.size() < 2) {
                    continue;
                }
                String title = cluster.get(0).getName();
                groups.add(buildGroup("title", "T:" + cluster.get(0).getId(), title, cluster));
            }
        }
        return groups;
    }

    /** 组装分组：组内按文件大小倒序，并计算总大小 / 多余空间。 */
    private static Group buildGroup(String reason, String key, String title, List<EmbyMediaItem> items) {
        List<EmbyMediaItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong((EmbyMediaItem i) -> {
            Long size = i.getSize();
            return size == null ? 0L : size;
        }).reversed());
        long totalSize = 0L;
        long maxSize = 0L;
        for (EmbyMediaItem item : sorted) {
            long size = item.getSize() == null ? 0L : item.getSize();
            totalSize += size;
            maxSize = Math.max(maxSize, size);
        }
        long extraSize = totalSize - maxSize;
        return Group.builder()
                .key(key)
                .reason(reason)
                .title(title)
                .count(sorted.size())
                .totalSize(totalSize)
                .totalSizeText(formatSize(totalSize))
                .extraSize(extraSize)
                .extraSizeText(formatSize(extraSize))
                .items(sorted)
                .build();
    }

    /** 名称和文件番号矛盾时，不生成删除候选。 */
    static void resolveCode(EmbyMediaItem item) {
        String name = cleanCandidate(item.getName());
        String filename = cleanCandidate(fileStem(item.getPath()));
        String fromName = matchCode(name, true);
        String fromPath = matchCode(filename, true);
        item.setCodeVerified(false);
        if (fromName != null && fromPath != null && !fromName.equals(fromPath)) {
            item.setCode(null);
            item.setCodeFrom("conflict");
            return;
        }
        if (fromName != null && fromPath != null) {
            item.setCode(fromPath);
            item.setCodeFrom("name+path");
            item.setCodeVerified(fromPath.equals(matchCode(name, false))
                    && fromPath.equals(matchCode(filename, false))
                    && !looksLikeYear(fromPath.substring(fromPath.lastIndexOf('-') + 1)));
        } else if (fromName != null || fromPath != null) {
            item.setCode(fromPath == null ? fromName : fromPath);
            item.setCodeFrom(fromPath == null ? "name" : "path");
        } else {
            item.setCode(null);
            item.setCodeFrom(null);
        }
    }

    /**
     * 从一段文本中提取并归一化番号。
     *
     * @param raw 原始文本（影片名或文件名）
     * @return 归一化番号，如 SSIS-123；无法识别返回 null
     */
    static String extractCode(String raw) {
        return matchCode(cleanCandidate(raw), true);
    }

    private static String matchCode(String text, boolean allowWeak) {
        Matcher matcher = CODE_FC2.matcher(text);
        if (matcher.find()) {
            return "FC2-" + matcher.group(1);
        }
        matcher = CODE_EXPLICIT.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase(Locale.ROOT) + "-" + matcher.group(2);
        }
        if (allowWeak) {
            matcher = CODE_WEAK.matcher(text);
            if (matcher.find() && !looksLikeYear(matcher.group(2))) {
                return matcher.group(1).toUpperCase(Locale.ROOT) + "-" + matcher.group(2);
            }
        }
        return null;
    }

    private static boolean looksLikeYear(String digits) {
        return digits.length() == 4 && (digits.startsWith("19") || digits.startsWith("20"));
    }

    private static boolean knownYear(Integer year) {
        return year != null && year > 0;
    }

    private static String pathKey(String path) {
        return StringUtils.trimToEmpty(path).replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    /** 清理候选文本：取最后一个 @ 之后的内容（站点前缀形如 hhd800.com@），去扩展名与开头括号修饰。 */
    private static String cleanCandidate(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        int at = text.lastIndexOf('@');
        if (at >= 0 && at < text.length() - 1) {
            text = text.substring(at + 1).trim();
        }
        text = text.replaceAll("(?i)\\.(mp4|mkv|avi|wmv|ts|mov|iso|rmvb|m2ts|flv|webm|strm)$", "");
        text = stripLeadingBrackets(text);
        text = text.replace('\uff0d', '-').replace('\uff3f', '_').replace('\u3000', ' ');
        return text.trim();
    }

    /** 去掉开头的【...】/[ ... ]/（...）修饰段。 */
    private static String stripLeadingBrackets(String value) {
        String text = value;
        for (int guard = 0; guard < 5 && !text.isEmpty(); guard++) {
            char first = text.charAt(0);
            char close;
            if (first == '\u3010') {
                close = '\u3011';
            } else if (first == '[') {
                close = ']';
            } else if (first == '\uff08') {
                close = '\uff09';
            } else if (first == '(') {
                close = ')';
            } else {
                break;
            }
            int idx = text.indexOf(close);
            if (idx <= 0 || idx > 60) {
                break;
            }
            text = text.substring(idx + 1).trim();
        }
        return text;
    }

    /** 文件名（去目录、去扩展名）。 */
    private static String fileStem(String path) {
        if (StringUtils.isBlank(path)) {
            return "";
        }
        String name = path;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }
        return name;
    }

    /** 版本标签识别（基于文件名与名称）。 */
    static List<String> detectTags(EmbyMediaItem item) {
        String text = (fileStem(item.getPath()) + " " + (item.getName() == null ? "" : item.getName()));
        List<String> tags = new ArrayList<>();
        for (String[] rule : TAG_RULES) {
            if (Pattern.compile(rule[0]).matcher(text).find()) {
                tags.add(rule[1]);
            }
        }
        return tags;
    }

    /** 标题归一化：仅保留字母/数字/假名/汉字，统一小写。 */
    private static String normalizeTitle(String name) {
        String text = name == null ? "" : name;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** 二元组 Dice 相似度。 */
    private static double dice(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.length() < 2 || b.length() < 2) {
            return 0.0;
        }
        Map<String, Integer> grams = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) {
            grams.merge(a.substring(i, i + 2), 1, Integer::sum);
        }
        int intersection = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String gram = b.substring(i, i + 2);
            Integer count = grams.get(gram);
            if (count != null && count > 0) {
                grams.put(gram, count - 1);
                intersection++;
            }
        }
        return 2.0 * intersection / ((a.length() - 1) + (b.length() - 1));
    }

    /** 字节数 -> 可读大小。 */
    static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return unit == 0 ? bytes + " B" : String.format("%.2f %s", value, units[unit]);
    }

    /** 重复分组。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {

        /** 分组键（番号分组为番号，标题分组为 T:归一化标题） */
        private String key;

        /** 分组依据：code / title */
        private String reason;

        /** 能独立定位组内的每个文件。 */
        private boolean deletable;

        /** 高置信度，允许页面批量勾选。 */
        private boolean batchEligible;

        /** 展示标题 */
        private String title;

        /** 组内影片数量 */
        private int count;

        /** 组内文件总大小（字节） */
        private long totalSize;

        /** 组内文件总大小（可读） */
        private String totalSizeText;

        /** 只保留最大文件时可释放的空间（字节） */
        private long extraSize;

        /** 只保留最大文件时可释放的空间（可读） */
        private String extraSizeText;

        /** 组内影片（按大小倒序） */
        private List<EmbyMediaItem> items;
    }
}
