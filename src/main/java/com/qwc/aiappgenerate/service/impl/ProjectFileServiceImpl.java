package com.qwc.aiappgenerate.service.impl;

import cn.hutool.core.util.StrUtil;
import com.qwc.aiappgenerate.constant.AppConstant;
import com.qwc.aiappgenerate.exception.BusinessException;
import com.qwc.aiappgenerate.exception.ErrorCode;
import com.qwc.aiappgenerate.exception.ThrowUtils;
import com.qwc.aiappgenerate.model.entity.App;
import com.qwc.aiappgenerate.model.entity.User;
import com.qwc.aiappgenerate.model.vo.ProjectFileContentVO;
import com.qwc.aiappgenerate.model.vo.ProjectFileVO;
import com.qwc.aiappgenerate.service.AppService;
import com.qwc.aiappgenerate.service.ProjectFileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 生成项目文件服务实现。
 * 所有路径都会先归一化并校验仍位于当前应用目录内，避免在线编辑接口被用于访问任意本地文件。
 */
@Service
@Slf4j
public class ProjectFileServiceImpl implements ProjectFileService {

    /** 不应发送到浏览器的依赖、构建产物和本地工具目录。 */
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            "node_modules", ".git", "dist", "build", "target", ".idea", ".vscode"
    );

    /** 可能包含密钥或本地环境信息的文件名。 */
    private static final Set<String> IGNORED_FILES = Set.of(
            ".env", ".env.local", ".env.development", ".env.production"
    );

    /** 浏览器在线编辑允许加载和保存的文本扩展名。 */
    private static final Set<String> EDITABLE_EXTENSIONS = Set.of(
            "html", "htm", "css", "scss", "less", "js", "jsx", "ts", "tsx", "vue",
            "json", "md", "txt", "xml", "yaml", "yml", "properties", "java", "sql",
            "svg", "gitignore", "npmrc", "editorconfig"
    );

    /** 单个在线编辑文件最大字节数，防止大文件占用过多内存或请求带宽。 */
    private static final long MAX_EDITABLE_FILE_SIZE = 1024L * 1024L;

    @Resource
    private AppService appService;

    /**
     * 构建目录树，目录优先、名称按不区分大小写排序。
     *
     * @param appId 应用 ID
     * @param loginUser 当前登录用户
     * @return 项目根目录下的节点列表
     */
    @Override
    public List<ProjectFileVO> listProjectFiles(Long appId, User loginUser) {
        Path projectRoot = resolveOwnedProjectRoot(appId, loginUser);
        return listChildren(projectRoot, projectRoot);
    }

    /**
     * 读取项目中的 UTF-8 文本文件。
     *
     * @param appId 应用 ID
     * @param relativePath 相对路径
     * @param loginUser 当前登录用户
     * @return 文件文本和版本信息
     */
    @Override
    public ProjectFileContentVO readProjectFile(Long appId, String relativePath, User loginUser) {
        Path projectRoot = resolveOwnedProjectRoot(appId, loginUser);
        Path target = resolveSafeFile(projectRoot, relativePath);
        validateEditableFile(target);
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            return buildContentVO(projectRoot, target, content);
        } catch (IOException e) {
            log.error("读取应用源码文件失败，appId={}, path={}", appId, relativePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取文件失败");
        }
    }

    /**
     * 覆盖保存项目中的 UTF-8 文本文件。
     * 保存仅允许操作已经存在的可编辑文件，避免接口被用于创建任意服务端文件。
     *
     * @param appId 应用 ID
     * @param relativePath 相对路径
     * @param content 完整文本
     * @param loginUser 当前登录用户
     * @return 保存后的文本和版本信息
     */
    @Override
    public ProjectFileContentVO saveProjectFile(Long appId, String relativePath, String content, User loginUser) {
        ThrowUtils.throwIf(content == null, ErrorCode.PARAMS_ERROR, "文件内容不能为空");
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        ThrowUtils.throwIf(contentBytes.length > MAX_EDITABLE_FILE_SIZE,
                ErrorCode.PARAMS_ERROR, "文件内容不能超过 1MB");
        Path projectRoot = resolveOwnedProjectRoot(appId, loginUser);
        Path target = resolveSafeFile(projectRoot, relativePath);
        validateEditableFile(target);
        try {
            Files.writeString(target, content, StandardCharsets.UTF_8);
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setUpdateTime(java.time.LocalDateTime.now());
            if (!appService.updateById(updateApp)) {
                log.error("源码已保存但应用更新时间更新失败，appId={}, path={}", appId, relativePath);
            }
            log.info("应用源码文件保存成功，appId={}, path={}", appId, relativePath);
            return buildContentVO(projectRoot, target, content);
        } catch (IOException e) {
            log.error("保存应用源码文件失败，appId={}, path={}", appId, relativePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存文件失败");
        }
    }

    /**
     * 校验应用归属并解析生成项目根目录。
     *
     * @param appId 应用 ID
     * @param loginUser 当前登录用户
     * @return 归一化后的绝对项目根目录
     */
    private Path resolveOwnedProjectRoot(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 无效");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该应用源码");
        }
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR,
                        app.getCodeGenType() + "_" + appId)
                .toAbsolutePath()
                .normalize();
        ThrowUtils.throwIf(!Files.isDirectory(projectRoot), ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");
        return projectRoot;
    }

    /**
     * 把用户提交的相对路径安全解析到项目目录中。
     *
     * @param projectRoot 项目根目录
     * @param relativePath 用户提交的相对路径
     * @return 已校验的目标文件绝对路径
     */
    private Path resolveSafeFile(Path projectRoot, String relativePath) {
        ThrowUtils.throwIf(StrUtil.isBlank(relativePath), ErrorCode.PARAMS_ERROR, "文件路径不能为空");
        Path target = projectRoot.resolve(relativePath.replace('/', java.io.File.separatorChar))
                .toAbsolutePath()
                .normalize();
        if (!target.startsWith(projectRoot)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件路径非法");
        }
        ThrowUtils.throwIf(!Files.isRegularFile(target), ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        return target;
    }

    /**
     * 递归列出指定目录下可安全展示的节点。
     *
     * @param projectRoot 项目根目录
     * @param directory 当前递归目录
     * @return 当前目录的直接子节点
     */
    private List<ProjectFileVO> listChildren(Path projectRoot, Path directory) {
        List<ProjectFileVO> nodes = new ArrayList<>();
        try (Stream<Path> pathStream = Files.list(directory)) {
            pathStream
                    .filter(path -> !shouldIgnore(path))
                    .sorted(Comparator
                            .comparing((Path path) -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> nodes.add(buildFileNode(projectRoot, path)));
            return nodes;
        } catch (IOException e) {
            log.error("读取应用项目目录失败，directory={}", directory, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取项目目录失败");
        }
    }

    /**
     * 创建单个文件树节点，目录节点会继续递归读取子节点。
     *
     * @param projectRoot 项目根目录
     * @param path 当前文件或目录
     * @return 可序列化的文件树节点
     */
    private ProjectFileVO buildFileNode(Path projectRoot, Path path) {
        ProjectFileVO node = new ProjectFileVO();
        boolean directory = Files.isDirectory(path);
        node.setName(path.getFileName().toString());
        node.setPath(toRelativePath(projectRoot, path));
        node.setDirectory(directory);
        node.setEditable(!directory && isEditableFile(path));
        node.setSize(directory ? 0L : getFileSize(path));
        node.setModifiedTime(getModifiedTime(path));
        if (directory) {
            node.setChildren(listChildren(projectRoot, path));
        }
        return node;
    }

    /**
     * 判断节点是否应从浏览器文件树中隐藏。
     *
     * @param path 待判断路径
     * @return true 表示隐藏
     */
    private boolean shouldIgnore(Path path) {
        String name = path.getFileName().toString();
        if (Files.isDirectory(path)) {
            return IGNORED_DIRECTORIES.contains(name);
        }
        return IGNORED_FILES.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * 校验目标为大小受限的可编辑文本文件。
     *
     * @param target 目标文件
     */
    private void validateEditableFile(Path target) {
        ThrowUtils.throwIf(!isEditableFile(target), ErrorCode.PARAMS_ERROR, "该文件不支持在线编辑");
        ThrowUtils.throwIf(getFileSize(target) > MAX_EDITABLE_FILE_SIZE,
                ErrorCode.PARAMS_ERROR, "文件超过 1MB，无法在线编辑");
    }

    /**
     * 根据扩展名判断文件是否适合按 UTF-8 文本在线编辑。
     *
     * @param path 文件路径
     * @return 是否可编辑
     */
    private boolean isEditableFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dotIndex = fileName.lastIndexOf('.');
        String extension = dotIndex >= 0 ? fileName.substring(dotIndex + 1) : fileName;
        return EDITABLE_EXTENSIONS.contains(extension);
    }

    /**
     * 构建文件内容响应对象。
     *
     * @param projectRoot 项目根目录
     * @param target 文件路径
     * @param content 文件文本
     * @return 内容响应
     */
    private ProjectFileContentVO buildContentVO(Path projectRoot, Path target, String content) {
        ProjectFileContentVO result = new ProjectFileContentVO();
        result.setPath(toRelativePath(projectRoot, target));
        result.setContent(content);
        result.setSize(getFileSize(target));
        result.setModifiedTime(getModifiedTime(target));
        return result;
    }

    /**
     * 获取稳定的统一斜杠相对路径，避免 Windows 分隔符泄露到前端状态中。
     */
    private String toRelativePath(Path projectRoot, Path target) {
        return projectRoot.relativize(target).toString().replace(java.io.File.separatorChar, '/');
    }

    /**
     * 安全读取文件大小；读取失败时返回 0，文件正文读取仍会给出明确异常。
     */
    private long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 安全读取最后修改时间；读取失败时返回 0，前端会将其视为未知版本。
     */
    private long getModifiedTime(Path path) {
        try {
            FileTime modifiedTime = Files.getLastModifiedTime(path);
            return modifiedTime.toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
