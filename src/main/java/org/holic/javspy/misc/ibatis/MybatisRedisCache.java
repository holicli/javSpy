package org.holic.javspy.misc.ibatis;

import com.google.common.base.Preconditions;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author fly
 */
@Slf4j
@Component
@NoArgsConstructor
public class MybatisRedisCache implements Cache {

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /**
     * 缓存刷新间隔，单位为毫秒
     * flushInterval 参数(自定义cache无法使用默认的flushInterval)
     */
    @Setter
    private long flushInterval = 0L;

    @Autowired
    private RedisTemplate<String, Object> springRedisTemplate;

    private static RedisTemplate<String, Object> redisTemplate;

    /** cache instance id */
    private String id;

    @PostConstruct
    public void init() { redisTemplate = springRedisTemplate; }

    public MybatisRedisCache(String id) {
        Preconditions.checkArgument(Objects.nonNull(id), "Cache instances require an ID");
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * Put query result to redis
     */
    @Override
    public void putObject(Object key, Object value) {
        try {
            redisTemplate.opsForHash().put(getId(), key.toString(), value);

            if (flushInterval > 0L) {
                redisTemplate.expire(getId(), flushInterval, TimeUnit.MILLISECONDS);
            }
        }
        catch (Exception t) {
            log.error("Redis put failed", t);
        }
    }

    /**
     * Get cached query result from redis
     */
    @Override
    public Object getObject(Object key) {
        try {
            return redisTemplate.opsForHash().get(getId(), key.toString());
        }
        catch (Exception t) {
            return null;
        }
    }

    /**
     * Remove cached query result from redis
     */
    @Override
    public Object removeObject(Object key) {
        try {
            redisTemplate.opsForHash().delete(getId(), key.toString());
        }
        catch (Exception t) {
            log.error("Redis remove failed", t);
        }
        return null;
    }

    /**
     * Clears this cache instance
     */
    @Override
    public void clear() {
        redisTemplate.delete(getId());
    }

    /**
     * This method is not used
     */
    @Override
    public int getSize() {
        return redisTemplate.opsForHash().size(getId()).intValue();
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return readWriteLock;
    }

    @Override
    public String toString() {
        return "MyBatisRedisCache {" + id + "}";
    }
}