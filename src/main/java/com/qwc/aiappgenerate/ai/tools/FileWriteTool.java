package com.qwc.aiappgenerate.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import com.qwc.aiappgenerate.constant.AppConstant;
import com.qwc.aiappgenerate.model.enums.CodeGenTypeEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 项目文件写入工具。
 * 支持 AI 通过工具调用覆盖当前应用内的源码文件，并将所有路径严格限制在当前项目目录中。
 */
@Slf4j
@Component
public class FileWriteTool extends BaseTool {

    /**
     * 当前工具实例对应的生成类型，用于把同一个 appId 定位到正确的项目目录。
     * Spring 管理的默认实例用于工具展示，工厂创建的实例会传入真实生成类型执行写入。
     */
    private final CodeGenTypeEnum codeGenType;

    /**
     * 创建供 Spring 工具管理器使用的默认实例。
     *
     * @see #FileWriteTool(CodeGenTypeEnum)
     */
    public FileWriteTool() {
        this(CodeGenTypeEnum.VUE_PROJECT);
    }

    /**
     * 创建绑定指定项目类型的文件写入工具。
     *
     * @param codeGenType 当前 AI 会话使用的代码生成类型
     */
    public FileWriteTool(CodeGenTypeEnum codeGenType) {
        this.codeGenType = codeGenType == null ? CodeGenTypeEnum.VUE_PROJECT : codeGenType;
    }

    /**
     * 使用完整内容覆盖项目内的单个源码文件。
     * 相对路径不能越过当前应用目录；内容未变化时不会重复写盘，并会把结果告知模型。
     *
     * @param relativeFilePath 相对于项目根目录的文件路径
     * @param content 要写入的完整文件内容
     * @param appId 工具调用所属的应用 ID，由对话记忆 ID 自动注入
     * @return 文件写入结果，供模型决定是否需要继续修改
     */
    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要写入文件的内容")
            String content,
            @ToolMemoryId Long appId
    ) {
        try {
            if (relativeFilePath == null || relativeFilePath.isBlank()) {
                return "文件写入失败: 文件路径不能为空";
            }
            Path relativePath = Paths.get(relativeFilePath);
            if (relativePath.isAbsolute()) {
                return "文件写入失败: 只允许使用项目内的相对路径";
            }
            String projectDirName = codeGenType.getValue() + "_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName)
                    .toAbsolutePath()
                    .normalize();
            Path path = projectRoot.resolve(relativePath).normalize();
            if (!path.startsWith(projectRoot)) {
                return "文件写入失败: 路径不能超出当前项目目录";
            }
            String safeContent = content == null ? "" : content;
            if (Files.isRegularFile(path)
                    && Files.readString(path, StandardCharsets.UTF_8).equals(safeContent)) {
                log.info("文件内容未变化，跳过写入: {}", path.toAbsolutePath());
                return "文件内容未变化: " + relativeFilePath;
            }
            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // 写入文件内容
            Files.writeString(path, safeContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        return String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, getDisplayName(), relativeFilePath, suffix, content);
    }
}
