package com.qwc.aiappgenerate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 注册生成项目和部署项目的本地静态资源映射。
     * 编辑态 /static 资源禁用浏览器缓存，确保 index.html 刷新时关联的 CSS、JS 也立即读取磁盘新版本；
     * /deploy 保持默认缓存语义，不影响已部署应用的访问性能。
     *
     * @param registry Spring MVC 静态资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:./tmp/code_output/")
                .setCacheControl(CacheControl.noStore());
        registry.addResourceHandler("/deploy/**")
                .addResourceLocations("file:./tmp/code_deploy/");
    }
}
