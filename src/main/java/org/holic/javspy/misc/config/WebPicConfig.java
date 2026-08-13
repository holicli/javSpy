package org.holic.javspy.misc.config;


import at.pollux.thymeleaf.shiro.dialect.ShiroDialect;
import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.*;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Controller;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.CssLinkResourceTransformer;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;
import org.springframework.web.servlet.resource.VersionResourceResolver;
import org.springframework.web.util.UrlPathHelper;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * MVC配置
 */
@Configuration
public class WebPicConfig implements WebMvcConfigurer {

    @Value("${conf.image.storage-path:../pic}")
    private String imageStoragePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 存储目录可配置；服务器上建议配置绝对路径，例如 /data/javspy/pic
        String picPath = imageStoragePath.replace('\\', '/');
        if (!picPath.endsWith("/")) {
            picPath += "/";
        }
        registry.addResourceHandler("/pic/**")
                .addResourceLocations("file:" + picPath)
                .setCachePeriod(3600);
    }
}
