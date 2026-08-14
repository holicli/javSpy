package org.holic.javspy.misc;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

/**
 * Emby 影片存在性检查服务，带短时缓存，避免每次列表请求都访问 Emby。
 */
@Service
public class EmbyMovieService {

    private static final long CACHE_TTL_MS = 60_000L;

    @Value("${conf.emby.enabled:true}")
    private boolean enabled;

    private final EmbyMovieChecker embyMovieChecker;

    private volatile Set<String> codeCache = Collections.emptySet();
    private volatile long cacheExpireAt = 0L;

    public EmbyMovieService(EmbyMovieChecker embyMovieChecker) {
        this.embyMovieChecker = embyMovieChecker;
    }

    /** 判断番号对应的影片是否已存在于 Emby。 */
    public boolean exists(String code) {
        if (!enabled || StringUtils.isBlank(code)) {
            return false;
        }
        return getCodes().contains(code.trim().toUpperCase());
    }

    /** 获取 Emby 中全部影片番号集合（60 秒缓存）。 */
    public Set<String> getCodes() {
        long now = System.currentTimeMillis();
        if (now < cacheExpireAt) {
            return codeCache;
        }
        synchronized (this) {
            if (now < cacheExpireAt) {
                return codeCache;
            }
            Set<String> loaded = embyMovieChecker.getAllMovieCodes();
            codeCache = loaded == null ? Collections.emptySet() : loaded;
            cacheExpireAt = System.currentTimeMillis() + CACHE_TTL_MS;
            return codeCache;
        }
    }

    /** 清空缓存，下次查询立即重新拉取 Emby 影片集合。 */
    public void refresh() {
        synchronized (this) {
            codeCache = Collections.emptySet();
            cacheExpireAt = 0L;
        }
    }
}
