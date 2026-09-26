package org.holic.javspy.misc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：把本地图片目录映射到 /pic/**。
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
