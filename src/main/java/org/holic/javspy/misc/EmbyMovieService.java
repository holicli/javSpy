package org.holic.javspy.misc;

import org.apache.commons.lang3.StringUtils;
import org.holic.javspy.javbusapi.mapper.EmbyMovieMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Emby 影片存在性检查服务，基于数据库缓存（emby_movie 表）：
 * <ul>
 *     <li>影片清单全量存数据库，覆盖更新（DELETE + INSERT 同一事务）；</li>
 *     <li>非当日数据时，查询触发一次自动全量同步；当日数据直接读库；</li>
 *     <li>手动更新：{@link #syncNow()}；状态查询：{@link #status()}；</li>
 *     <li>Emby 拉取失败时保留数据库旧数据，不清空。</li>
 * </ul>
 */
@Service
public class EmbyMovieService {

    private static final Logger log = LoggerFactory.getLogger(EmbyMovieService.class);

    @Value("${conf.emby.enabled:true}")
    private boolean enabled;

    private final EmbyMovieChecker embyMovieChecker;
    private final EmbyMovieMapper embyMovieMapper;
    private final TransactionTemplate transactionTemplate;

    public EmbyMovieService(EmbyMovieChecker embyMovieChecker,
                            EmbyMovieMapper embyMovieMapper,
                            PlatformTransactionManager transactionManager) {
        this.embyMovieChecker = embyMovieChecker;
        this.embyMovieMapper = embyMovieMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 判断番号对应的影片是否已存在于 Emby（查数据库缓存）。 */
    public boolean exists(String code) {
        if (!enabled || StringUtils.isBlank(code)) {
            return false;
        }
        return getCodes().contains(code.trim().toUpperCase());
    }

    /**
     * 获取 Emby 中全部影片番号集合（数据库缓存）。
     * 缓存非当日时自动触发全量覆盖更新；Emby 拉取失败则使用数据库旧数据。
     */
    public Set<String> getCodes() {
        if (!enabled) {
            return Collections.emptySet();
        }
        synchronized (this) {
            Date lastSyncAt = embyMovieMapper.selectLastSyncAt();
            if (!isToday(lastSyncAt)) {
                try {
                    syncFromEmby();
                } catch (Exception e) {
                    log.warn("Emby 清单自动同步失败，使用数据库旧数据: {}", e.getMessage());
                }
            }
            List<String> codes = embyMovieMapper.selectAllCodes();
            Set<String> set = new HashSet<>(Math.max(16, codes.size() * 2));
            for (String c : codes) {
                if (StringUtils.isNotBlank(c)) {
                    set.add(c.trim().toUpperCase());
                }
            }
            return set;
        }
    }

    /** 手动全量同步 Emby 影片清单到数据库（覆盖更新）。 */
    public SyncResult syncNow() {
        if (!enabled) {
            return SyncResult.disabled();
        }
        synchronized (this) {
            try {
                int count = syncFromEmby();
                Date lastSyncAt = embyMovieMapper.selectLastSyncAt();
                return SyncResult.ok(count, lastSyncAt);
            } catch (Exception e) {
                log.error("Emby 清单同步失败", e);
                return SyncResult.fail(e.getMessage());
            }
        }
    }

    /** Emby 缓存状态（数量、上次同步时间、是否当日已同步）。 */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        if (!enabled) {
            status.put("count", 0);
            status.put("lastSyncAt", null);
            status.put("todaySynced", false);
            return status;
        }
        status.put("count", embyMovieMapper.countAll());
        Date lastSyncAt = embyMovieMapper.selectLastSyncAt();
        status.put("lastSyncAt", lastSyncAt);
        status.put("todaySynced", isToday(lastSyncAt));
        return status;
    }

    /** 从 Emby 拉取全部影片番号并覆盖写入数据库（DELETE + INSERT 同一事务），返回写入条数。 */
    private int syncFromEmby() {
        List<String> names = embyMovieChecker.getAllMovieFromEmby();
        if (names == null) {
            throw new IllegalStateException("Emby 拉取失败（返回 null）");
        }
        Set<String> codes = new HashSet<>();
        for (String name : names) {
            if (StringUtils.isBlank(name)) {
                continue;
            }
            String code = name.trim().toUpperCase();
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        final List<String> list = new ArrayList<>(codes);
        transactionTemplate.executeWithoutResult(status -> {
            embyMovieMapper.deleteAll();
            if (!list.isEmpty()) {
                embyMovieMapper.insertBatch(list);
            }
        });
        log.info("Emby 影片清单同步完成，共 {} 部", list.size());
        return list.size();
    }

    /** 时间是否为今天（服务器本地时区）。 */
    private static boolean isToday(Date date) {
        if (date == null) {
            return false;
        }
        LocalDate d = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return d.equals(LocalDate.now());
    }

    /** Emby 清单同步结果。 */
    public static class SyncResult {
        public final boolean success;
        public final int count;
        public final Date lastSyncAt;
        public final String message;

        private SyncResult(boolean success, int count, Date lastSyncAt, String message) {
            this.success = success;
            this.count = count;
            this.lastSyncAt = lastSyncAt;
            this.message = message;
        }

        public static SyncResult ok(int count, Date lastSyncAt) {
            return new SyncResult(true, count, lastSyncAt, "同步成功，共 " + count + " 部影片");
        }

        public static SyncResult fail(String message) {
            return new SyncResult(false, 0, null, "同步失败：" + message);
        }

        public static SyncResult disabled() {
            return new SyncResult(false, 0, null, "Emby 功能未启用（conf.emby.enabled=false）");
        }
    }
}
