package com.qwc.aiappgenerate.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.qwc.aiappgenerate.exception.BusinessException;
import com.qwc.aiappgenerate.exception.ErrorCode;
import com.qwc.aiappgenerate.exception.ThrowUtils;
import com.qwc.aiappgenerate.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

/**
 * 项目源码下载服务实现，统一处理下载响应头、敏感文件过滤和 ZIP 流式输出。
 */
@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {

    /** 不进入交付包的目录或文件名，避免把依赖、仓库元数据和本地密钥一并下载。 */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules", ".git", "dist", "build", ".DS_Store", ".env",
            "target", ".mvn", ".idea", ".vscode"
    );

    /** 不进入交付包的临时文件扩展名。 */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(".log", ".tmp", ".cache");

    /**
     * 将项目目录直接压缩到响应输出流，避免先在磁盘生成中间压缩包。
     *
     * @param projectPath      待打包项目目录
     * @param downloadFileName 下载文件名，不包含扩展名
     * @param response         ZIP 输出响应
     */
    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response) {
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR, "项目路径不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName), ErrorCode.PARAMS_ERROR, "下载文件名不能为空");
        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.NOT_FOUND_ERROR, "项目路径不存在");
        ThrowUtils.throwIf(!projectDir.isDirectory(), ErrorCode.PARAMS_ERROR, "项目路径不是目录");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.addHeader("Content-Disposition", String.format("attachment; filename=\"%s.zip\"", downloadFileName));
        FileFilter filter = file -> isPathAllowed(projectDir.toPath(), file.toPath());
        log.info("开始打包应用源码: {} -> {}.zip", projectPath, downloadFileName);
        try {
            ZipUtil.zip(response.getOutputStream(), StandardCharsets.UTF_8, false, filter, projectDir);
            log.info("应用源码打包完成: {}.zip", downloadFileName);
        } catch (IOException exception) {
            log.error("应用源码打包失败: {}", projectPath, exception);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "打包下载项目失败");
        }
    }

    /**
     * 判断文件是否允许进入压缩包；会检查相对路径的每一级，防止嵌套依赖或配置目录漏网。
     *
     * @param projectRoot 项目根目录
     * @param fullPath    当前候选文件路径
     * @return 允许打包时返回 true
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        Path relativePath = projectRoot.relativize(fullPath);
        for (Path part : relativePath) {
            String partName = part.toString();
            if (IGNORED_NAMES.contains(partName)) {
                return false;
            }
            String normalizedName = partName.toLowerCase();
            if (IGNORED_EXTENSIONS.stream().anyMatch(normalizedName::endsWith)) {
                return false;
            }
        }
        return true;
    }
}
