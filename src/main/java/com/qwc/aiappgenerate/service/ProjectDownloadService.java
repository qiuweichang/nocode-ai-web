package com.qwc.aiappgenerate.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 项目源码下载服务，负责把指定的代码生成目录安全地打包为 ZIP 并写入 HTTP 响应。
 */
public interface ProjectDownloadService {

    /**
     * 将项目目录压缩为 ZIP 下载，打包时会过滤依赖、构建产物和本地配置等不应交付的文件。
     *
     * @param projectPath     待打包项目的绝对或工作目录相对路径
     * @param downloadFileName 下载文件名，不包含扩展名
     * @param response        用于输出 ZIP 内容的 HTTP 响应
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
