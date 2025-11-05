package org.holic.javspy.misc.config;


import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.apache.commons.lang3.ArrayUtils;
import org.aspectj.lang.annotation.Aspect;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Arrays;
import java.util.StringJoiner;

/**
 * 程序配置
 * @author 孙修瑞
 */
@Slf4j
@EnableAsync
@Configuration
@EnableCaching
@EnableScheduling
@EnableAspectJAutoProxy
@EnableSchedulerLock(defaultLockAtMostFor = "15s")
@EnableTransactionManagement(proxyTargetClass = true)
@MapperScan(
        annotationClass = Repository.class,
        basePackages = "org.holic.javspy.**",
        sqlSessionFactoryRef = "sqlSessionFactory"
)
@ComponentScan(
        lazyInit = true,
        basePackages = "org.holic.javspy",
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Aspect.class)
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class)
        }
)
public class AppConfig extends CachingConfigurerSupport {

    /**
     * 重写缓存键key生成策略
     * @return 缓存键key生成策略
     */
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringJoiner joiner = new StringJoiner(":");
            joiner.add(target.getClass().getName());
            joiner.add(method.getName());
            if(ArrayUtils.isNotEmpty(params)) {
                joiner.add(Arrays.deepToString(params));
            }
            return joiner.toString();
        };
    }

    /**
     * 重新定义redis序列化方式
     * @param redisConnectionFactory redis工厂类
     * @return RedisTemplate对象
     */
    @Bean("redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory){
        /* 配置自己的redisTemplate
         * StringRedisTemplate 默认使用使用StringRedisSerializer来序列化
         * RedisTemplate 默认使用JdkSerializationRedisSerializer来序列化
         */
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        //redis连接工厂
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        JdkSerializationRedisSerializer jdkSerializationRedisSerializer = new JdkSerializationRedisSerializer();

        //redis.key序列化器
        redisTemplate.setKeySerializer(stringRedisSerializer);
        //redis.value序列化器
        redisTemplate.setValueSerializer(jdkSerializationRedisSerializer);

        //redis.hash.key序列化器
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        //redis.hash.value序列化器
        redisTemplate.setHashValueSerializer(jdkSerializationRedisSerializer);

        return redisTemplate;
    }

    /**
     * shedlock实现分布式定时任务锁
     * @param connectionFactory redis工厂类
     * @return 分布式锁对象
     */
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory);
    }
}