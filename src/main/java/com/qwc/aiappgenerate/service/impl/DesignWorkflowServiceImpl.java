package com.qwc.aiappgenerate.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qwc.aiappgenerate.constant.AppConstant;
import com.qwc.aiappgenerate.exception.BusinessException;
import com.qwc.aiappgenerate.exception.ErrorCode;
import com.qwc.aiappgenerate.exception.ThrowUtils;
import com.qwc.aiappgenerate.integration.stitch.StitchMcpClient;
import com.qwc.aiappgenerate.integration.stitch.StitchScreenArtifact;
import com.qwc.aiappgenerate.mapper.AppMapper;
import com.qwc.aiappgenerate.model.design.DesignRevisionMetadata;
import com.qwc.aiappgenerate.model.design.DesignWorkflowMetadata;
import com.qwc.aiappgenerate.model.dto.design.DesignReviseRequest;
import com.qwc.aiappgenerate.model.entity.App;
import com.qwc.aiappgenerate.model.entity.User;
import com.qwc.aiappgenerate.model.enums.ChatHistoryMessageTypeEnum;
import com.qwc.aiappgenerate.model.enums.DesignStageEnum;
import com.qwc.aiappgenerate.model.vo.DesignRevisionVO;
import com.qwc.aiappgenerate.model.vo.DesignWorkflowVO;
import com.qwc.aiappgenerate.service.ChatHistoryService;
import com.qwc.aiappgenerate.service.DesignWorkflowService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于本地版本目录和 Google Stitch MCP 的设计工作流实现。
 * 每个应用独立加锁，保证生成、修改、确认严格串行，避免慢速 Stitch 调用覆盖较新的用户操作。
 */
@Service
@Slf4j
public class DesignWorkflowServiceImpl implements DesignWorkflowService {

    /** 发送给代码模型的 Stitch HTML 最大字符数，避免异常导出文件挤占整个上下文。 */
    private static final int MAX_DESIGN_HTML_CHARS = 180_000;

    /** 每个应用的顺序锁；不同应用可以并行设计，同一应用只能有一个状态流转。 */
    private final ConcurrentHashMap<Long, ReentrantLock> appLocks = new ConcurrentHashMap<>();

    @Resource
    private AppMapper appMapper;

    @Resource
    private StitchMcpClient stitchMcpClient;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 读取设计状态；未开始设计时返回 EMPTY，不创建无意义目录。
     *
     * @param appId 应用 ID
     * @param loginUser 当前登录用户
     * @return 设计工作流视图
     */
    @Override
    public DesignWorkflowVO getDesign(Long appId, User loginUser) {
        App app = requireOwnedApp(appId, loginUser);
        return toVO(readMetadataOrEmpty(app));
    }

    /**
     * 生成首版桌面样式并导出 HTML、截图和元数据。
     * 请求期间先写入 GENERATING，页面刷新后仍能看到真实运行状态；失败则写入 FAILED 供用户重试。
     *
     * @param appId 应用 ID
     * @param prompt 用户页面需求
     * @param loginUser 当前登录用户
     * @return 最新设计状态
     */
    @Override
    public DesignWorkflowVO generateInitialDesign(Long appId, String prompt, User loginUser) {
        ThrowUtils.throwIf(StrUtil.isBlank(prompt), ErrorCode.PARAMS_ERROR, "样式需求不能为空");
        App app = requireOwnedApp(appId, loginUser);
        ReentrantLock lock = lockFor(appId);
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前样式正在生成，请等待完成");
        }
        DesignWorkflowMetadata metadata = readMetadataOrEmpty(app);
        try {
            if (!metadata.getRevisions().isEmpty()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "样式方案已经生成，请直接提出修改");
            }
            metadata.setStage(DesignStageEnum.GENERATING.getValue());
            metadata.setOriginalPrompt(prompt.trim());
            metadata.setFailureMessage(null);
            metadata.setUpdatedAt(LocalDateTime.now());
            writeMetadata(metadata);
            String stitchPrompt = buildInitialPrompt(prompt);
            StitchScreenArtifact artifact = stitchMcpClient.generateDesign(
                    "NoCode-" + appId + "-" + app.getAppName(), stitchPrompt);
            persistRevision(metadata, artifact, stitchPrompt, null, 1);
            metadata.setStage(DesignStageEnum.REVIEW.getValue());
            metadata.setStitchProjectId(artifact.getProjectId());
            metadata.setCurrentRevision(1);
            metadata.setConfirmedRevision(null);
            metadata.setUpdatedAt(LocalDateTime.now());
            writeMetadata(metadata);
            persistDesignConversation(appId, prompt.trim(), "首版样式方案已生成，请在右侧预览并确认，或选择页面要素后继续提出修改。", loginUser);
            log.info("应用首版 Stitch 样式已生成，appId={}, revision=1", appId);
            return toVO(metadata);
        } catch (BusinessException e) {
            markFailed(metadata, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("生成首版 Stitch 样式失败，appId={}", appId, e);
            markFailed(metadata, "样式生成失败，请稍后重试");
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "样式生成失败，请稍后重试");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 修改当前样式并保存为不可覆盖的新版本。
     * 选中元素信息会被结构化追加到提示词，Stitch 仍负责重绘，浏览器不会直接改写设计 HTML。
     *
     * @param request 样式修改请求
     * @param loginUser 当前登录用户
     * @return 最新设计状态
     */
    @Override
    public DesignWorkflowVO reviseDesign(DesignReviseRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || request.getAppId() == null, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(request.getPrompt()), ErrorCode.PARAMS_ERROR, "修改要求不能为空");
        App app = requireOwnedApp(request.getAppId(), loginUser);
        ReentrantLock lock = lockFor(app.getId());
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前样式正在生成，请等待完成");
        }
        DesignWorkflowMetadata metadata = readMetadataOrEmpty(app);
        try {
            DesignRevisionMetadata current = findCurrentRevision(metadata);
            ThrowUtils.throwIf(current == null || StrUtil.isBlank(metadata.getStitchProjectId()),
                    ErrorCode.OPERATION_ERROR, "请先生成首版样式");
            Integer requestedBaseRevision = request.getBaseRevisionNumber();
            DesignRevisionMetadata baseRevision = requestedBaseRevision == null
                    ? current
                    : findRevision(metadata, requestedBaseRevision);
            ThrowUtils.throwIf(baseRevision == null,
                    ErrorCode.OPERATION_ERROR, "选择的样式版本不存在，请刷新后重试");
            int nextRevision = metadata.getCurrentRevision() + 1;
            metadata.setStage(DesignStageEnum.REVISING.getValue());
            metadata.setFailureMessage(null);
            metadata.setUpdatedAt(LocalDateTime.now());
            writeMetadata(metadata);
            String selectedSummary = buildSelectedElementSummary(request);
            String stitchPrompt = buildRevisionPrompt(request, selectedSummary);
            String previousHtml = Files.readString(
                    designRoot(app.getId()).resolve(baseRevision.getHtmlPath()), StandardCharsets.UTF_8);
            StitchScreenArtifact artifact = stitchMcpClient.reviseDesign(
                    metadata.getStitchProjectId(), baseRevision.getScreenId(), stitchPrompt, nextRevision, previousHtml);
            persistRevision(metadata, artifact, stitchPrompt, selectedSummary, nextRevision);
            metadata.setStage(DesignStageEnum.REVIEW.getValue());
            metadata.setCurrentRevision(nextRevision);
            metadata.setConfirmedRevision(null);
            metadata.setFailureMessage(null);
            metadata.setUpdatedAt(LocalDateTime.now());
            writeMetadata(metadata);
            persistDesignConversation(app.getId(), request.getPrompt().trim(),
                    "样式方案已按要求更新为第 " + nextRevision + " 版，请继续确认或选择要素修改。", loginUser);
            log.info("应用 Stitch 样式已修改，appId={}, revision={}", app.getId(), nextRevision);
            return toVO(metadata);
        } catch (BusinessException e) {
            markFailed(metadata, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("修改 Stitch 样式失败，appId={}", app.getId(), e);
            markFailed(metadata, "样式修改失败，请稍后重试");
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "样式修改失败，请稍后重试");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 确认用户当前预览的版本。
     * 历史版本可以直接确认，后续代码生成会读取 confirmedRevision 对应的设计文件。
     *
     * @param appId 应用 ID
     * @param revisionNumber 待确认版本号
     * @param loginUser 当前登录用户
     * @return 确认后的设计状态
     */
    @Override
    public DesignWorkflowVO confirmDesign(Long appId, Integer revisionNumber, User loginUser) {
        App app = requireOwnedApp(appId, loginUser);
        ReentrantLock lock = lockFor(appId);
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前样式正在生成，请等待完成");
        }
        try {
            DesignWorkflowMetadata metadata = readMetadataOrEmpty(app);
            ThrowUtils.throwIf(metadata.getCurrentRevision() == null,
                    ErrorCode.OPERATION_ERROR, "请先生成样式方案");
            ThrowUtils.throwIf(revisionNumber == null,
                    ErrorCode.PARAMS_ERROR, "请选择需要确认的样式版本");
            DesignRevisionMetadata revision = findRevision(metadata, revisionNumber);
            ThrowUtils.throwIf(revision == null || !Files.isRegularFile(designRoot(appId).resolve(revision.getHtmlPath())),
                    ErrorCode.OPERATION_ERROR, "当前样式文件不存在，请重新生成");
            metadata.setStage(DesignStageEnum.CONFIRMED.getValue());
            metadata.setConfirmedRevision(revisionNumber);
            metadata.setFailureMessage(null);
            metadata.setUpdatedAt(LocalDateTime.now());
            writeMetadata(metadata);
            log.info("应用样式已确认，appId={}, revision={}", appId, revisionNumber);
            return toVO(metadata);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 判断设计元数据是否指向可读取的已确认 HTML，不能只依赖状态字符串。
     *
     * @param appId 应用 ID
     * @return 已确认且文件存在返回 true
     */
    @Override
    public boolean isDesignConfirmed(Long appId) {
        if (appId == null) {
            return false;
        }
        DesignWorkflowMetadata metadata = readMetadata(appId);
        if (metadata == null || metadata.getConfirmedRevision() == null
                || !DesignStageEnum.CONFIRMED.getValue().equals(metadata.getStage())) {
            return false;
        }
        DesignRevisionMetadata revision = findRevision(metadata, metadata.getConfirmedRevision());
        return revision != null && Files.isRegularFile(designRoot(appId).resolve(revision.getHtmlPath()));
    }

    /**
     * 为首轮代码生成追加已确认设计 HTML 和严格实现约束。
     *
     * @param appId 应用 ID
     * @param userMessage 用户原始需求
     * @return 代码模型完整输入
     */
    @Override
    public String buildConfirmedDesignContext(Long appId, String userMessage) {
        DesignWorkflowMetadata metadata = readMetadata(appId);
        if (metadata == null || metadata.getConfirmedRevision() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请先确认样式方案，再开始生成代码");
        }
        DesignRevisionMetadata revision = findRevision(metadata, metadata.getConfirmedRevision());
        if (revision == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已确认样式版本不存在，请重新确认");
        }
        Path htmlPath = designRoot(appId).resolve(revision.getHtmlPath()).normalize();
        if (!htmlPath.startsWith(designRoot(appId)) || !Files.isRegularFile(htmlPath)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已确认样式文件不存在，请重新生成");
        }
        try {
            String designHtml = Files.readString(htmlPath, StandardCharsets.UTF_8);
            if (designHtml.length() > MAX_DESIGN_HTML_CHARS) {
                designHtml = designHtml.substring(0, MAX_DESIGN_HTML_CHARS);
            }
            return userMessage + """


                    用户已经确认以下桌面端样式方案。请把它作为首要视觉基线，完整实现其信息层级、布局、颜色、字体、间距、圆角和主要内容，同时补齐真实网页交互。
                    只开发桌面端，不要添加移动端、平板端、响应式断点或媒体查询。
                    参考设计中的图标字体和外部组件只代表视觉意图，必须改用内联 SVG 或纯 CSS，不能把图标名称渲染成文字。
                    表单输入、密码显示切换、提交按钮、选项卡等必须具备真实的原生 JavaScript 行为，并确保输入文本不被图标覆盖。
                    必须调用写文件工具把可运行代码保存到项目目录，不能只回复说明。

                    --- confirmed-design.html ---
                    """ + designHtml;
        } catch (Exception e) {
            log.error("读取已确认 Stitch HTML 失败，appId={}", appId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取已确认样式失败");
        }
    }

    /**
     * 校验应用存在且属于当前用户。
     *
     * @param appId 应用 ID
     * @param loginUser 当前登录用户
     * @return 已授权应用
     */
    private App requireOwnedApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 无效");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!loginUser.getId().equals(app.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用设计");
        }
        return app;
    }

    /**
     * 获取应用级顺序锁。
     *
     * @param appId 应用 ID
     * @return 可重入锁
     */
    private ReentrantLock lockFor(Long appId) {
        return appLocks.computeIfAbsent(appId, ignored -> new ReentrantLock());
    }

    /**
     * 构造强调桌面端和完整页面的首版 Stitch 提示词。
     *
     * @param prompt 用户原始需求
     * @return Stitch 提示词
     */
    private String buildInitialPrompt(String prompt) {
        return "请设计一个高保真的桌面端网页界面，不要设计移动端、平板端或响应式版本。"
                + "画布宽度以 1440px 桌面浏览器为基准，需要有完整导航、首屏、核心内容区和必要的操作状态。"
                + "视觉要专业、清晰、有真实产品感，并保留可导出的语义化 HTML 结构。"
                + "页面内容中不要出现设计工具或供应商名称。用户需求：\n" + prompt.trim();
    }

    /**
     * 构造局部或整页修改提示词。
     *
     * @param request 修改请求
     * @param selectedSummary 已选元素摘要
     * @return Stitch 修改提示词
     */
    private String buildRevisionPrompt(DesignReviseRequest request, String selectedSummary) {
        StringBuilder prompt = new StringBuilder("继续保持当前设计的桌面端画布和既有视觉系统，不要生成移动端或响应式版本。\n")
                .append("页面内容中不要出现设计工具或供应商名称。\n")
                .append("修改要求：").append(request.getPrompt().trim());
        if (StrUtil.isNotBlank(selectedSummary)) {
            prompt.append("\n\n只修改当前设计中的指定模块，其他区域保持不变：\n").append(selectedSummary);
        } else {
            prompt.append("\n请保留未被明确要求修改的布局、内容和样式。");
        }
        return prompt.toString();
    }

    /**
     * 把 iframe 选中元素转换为 Stitch 可理解的定位上下文。
     *
     * @param request 修改请求
     * @return 多行元素摘要；未选择元素时为空
     */
    private String buildSelectedElementSummary(DesignReviseRequest request) {
        if (StrUtil.isBlank(request.getSelector())) {
            return null;
        }
        StringBuilder summary = new StringBuilder()
                .append("- CSS 选择器：").append(request.getSelector()).append('\n');
        if (StrUtil.isNotBlank(request.getTagName())) {
            summary.append("- 元素标签：").append(request.getTagName()).append('\n');
        }
        if (StrUtil.isNotBlank(request.getElementId())) {
            summary.append("- 元素 ID：").append(request.getElementId()).append('\n');
        }
        if (StrUtil.isNotBlank(request.getClassName())) {
            summary.append("- 元素类名：").append(request.getClassName()).append('\n');
        }
        if (StrUtil.isNotBlank(request.getTextContent())) {
            summary.append("- 当前内容：").append(request.getTextContent());
        }
        return summary.toString().trim();
    }

    /**
     * 下载并写入不可变版本目录，再把版本加入内存元数据。
     *
     * @param metadata 当前工作流元数据
     * @param artifact Stitch 产物
     * @param prompt 实际调用提示词
     * @param selectedSummary 选中元素摘要
     * @param revisionNumber 新版本号
     */
    private void persistRevision(DesignWorkflowMetadata metadata,
                                 StitchScreenArtifact artifact,
                                 String prompt,
                                 String selectedSummary,
                                 int revisionNumber) throws Exception {
        Path revisionRoot = designRoot(metadata.getAppId()).resolve("revision_" + revisionNumber);
        Files.createDirectories(revisionRoot);
        Path htmlPath = revisionRoot.resolve("stitch.html");
        if (StrUtil.isNotBlank(artifact.getInlineHtml())) {
            writeBytesAtomically(htmlPath, artifact.getInlineHtml().getBytes(StandardCharsets.UTF_8));
        } else {
            writeBytesAtomically(htmlPath, stitchMcpClient.downloadArtifact(artifact.getHtmlDownloadUrl()));
        }
        String screenshotPath = null;
        if (StrUtil.isNotBlank(artifact.getScreenshotDownloadUrl())) {
            Path screenshotFile = revisionRoot.resolve("screenshot.png");
            writeBytesAtomically(screenshotFile, stitchMcpClient.downloadArtifact(artifact.getScreenshotDownloadUrl()));
            screenshotPath = "revision_" + revisionNumber + "/screenshot.png";
        }
        DesignRevisionMetadata revision = DesignRevisionMetadata.builder()
                .number(revisionNumber)
                .screenId(artifact.getScreenId())
                .prompt(prompt)
                .selectedElementSummary(selectedSummary)
                .htmlPath("revision_" + revisionNumber + "/stitch.html")
                .screenshotPath(screenshotPath)
                .createdAt(LocalDateTime.now())
                .build();
        metadata.getRevisions().add(revision);
    }

    /**
     * 将元数据原子写入 metadata.json，避免进程中断留下半截 JSON。
     *
     * @param metadata 待保存元数据
     */
    private void writeMetadata(DesignWorkflowMetadata metadata) {
        try {
            Path root = designRoot(metadata.getAppId());
            Files.createDirectories(root);
            writeBytesAtomically(root.resolve("metadata.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(metadata));
        } catch (Exception e) {
            log.error("保存设计元数据失败，appId={}", metadata.getAppId(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存样式状态失败");
        }
    }

    /**
     * 在目标目录内先写临时文件再替换正式文件。
     *
     * @param target 目标文件
     * @param content 文件字节
     */
    private void writeBytesAtomically(Path target, byte[] content) throws Exception {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, content);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 读取应用元数据，文件不存在时返回 null。
     *
     * @param appId 应用 ID
     * @return 元数据或 null
     */
    private DesignWorkflowMetadata readMetadata(Long appId) {
        Path metadataPath = designRoot(appId).resolve("metadata.json");
        if (!Files.isRegularFile(metadataPath)) {
            return null;
        }
        try {
            DesignWorkflowMetadata metadata = objectMapper.readValue(metadataPath.toFile(), DesignWorkflowMetadata.class);
            if (metadata.getRevisions() == null) {
                metadata.setRevisions(new ArrayList<>());
            }
            metadata.getRevisions().sort(Comparator.comparing(DesignRevisionMetadata::getNumber));
            return metadata;
        } catch (Exception e) {
            log.error("读取设计元数据失败，appId={}", appId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取样式状态失败");
        }
    }

    /**
     * 读取已有元数据或根据应用创建 EMPTY 内存对象。
     *
     * @param app 应用实体
     * @return 非空元数据
     */
    private DesignWorkflowMetadata readMetadataOrEmpty(App app) {
        DesignWorkflowMetadata existing = readMetadata(app.getId());
        if (existing != null) {
            return existing;
        }
        return DesignWorkflowMetadata.builder()
                .appId(app.getId())
                .stage(DesignStageEnum.EMPTY.getValue())
                .originalPrompt(app.getInitPrompt())
                .currentRevision(0)
                .revisions(new ArrayList<>())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 标记本轮 Stitch 调用失败，同时保留之前成功版本供再次修改。
     *
     * @param metadata 当前元数据
     * @param failureMessage 失败信息
     */
    private void markFailed(DesignWorkflowMetadata metadata, String failureMessage) {
        metadata.setStage(DesignStageEnum.FAILED.getValue());
        metadata.setFailureMessage(failureMessage);
        metadata.setUpdatedAt(LocalDateTime.now());
        try {
            writeMetadata(metadata);
        } catch (Exception e) {
            log.error("写入设计失败状态失败，appId={}", metadata.getAppId(), e);
        }
    }

    /**
     * 获取当前展示版本。
     *
     * @param metadata 工作流元数据
     * @return 当前版本或 null
     */
    private DesignRevisionMetadata findCurrentRevision(DesignWorkflowMetadata metadata) {
        return findRevision(metadata, metadata.getCurrentRevision());
    }

    /**
     * 按版本号查找版本。
     *
     * @param metadata 工作流元数据
     * @param revisionNumber 版本号
     * @return 匹配版本或 null
     */
    private DesignRevisionMetadata findRevision(DesignWorkflowMetadata metadata, Integer revisionNumber) {
        if (metadata == null || revisionNumber == null || metadata.getRevisions() == null) {
            return null;
        }
        return metadata.getRevisions().stream()
                .filter(item -> revisionNumber.equals(item.getNumber()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 将持久化元数据转换为前端安全视图。
     *
     * @param metadata 工作流元数据
     * @return 设计视图
     */
    private DesignWorkflowVO toVO(DesignWorkflowMetadata metadata) {
        DesignStageEnum stage = DesignStageEnum.fromValue(metadata.getStage());
        DesignRevisionMetadata current = findCurrentRevision(metadata);
        List<DesignRevisionVO> revisions = metadata.getRevisions().stream()
                .map(item -> DesignRevisionVO.builder()
                        .number(item.getNumber())
                        .previewPath(buildStaticPath(metadata.getAppId(), item.getHtmlPath()))
                        .screenshotPath(StrUtil.isBlank(item.getScreenshotPath())
                                ? null : buildStaticPath(metadata.getAppId(), item.getScreenshotPath()))
                        .prompt(item.getPrompt())
                        .confirmed(item.getNumber().equals(metadata.getConfirmedRevision()))
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();
        return DesignWorkflowVO.builder()
                .appId(metadata.getAppId())
                .stage(stage.getValue())
                .stageText(stage.getText())
                .currentRevision(metadata.getCurrentRevision())
                .confirmedRevision(metadata.getConfirmedRevision())
                .previewPath(current == null ? null : buildStaticPath(metadata.getAppId(), current.getHtmlPath()))
                .screenshotPath(current == null || StrUtil.isBlank(current.getScreenshotPath())
                        ? null : buildStaticPath(metadata.getAppId(), current.getScreenshotPath()))
                .failureMessage(metadata.getFailureMessage())
                .confirmed(DesignStageEnum.CONFIRMED == stage && metadata.getConfirmedRevision() != null)
                .revisions(revisions)
                .build();
    }

    /**
     * 生成同源静态资源路径，供 iframe 元素选择脚本访问。
     *
     * @param appId 应用 ID
     * @param relativePath 设计根目录内相对路径
     * @return /api/static 路径
     */
    private String buildStaticPath(Long appId, String relativePath) {
        return "/api/static/design_" + appId + "/" + relativePath.replace('\\', '/');
    }

    /**
     * 获取应用设计根目录并规范化路径。
     *
     * @param appId 应用 ID
     * @return design_{appId} 绝对路径
     */
    private Path designRoot(Long appId) {
        return Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, "design_" + appId).toAbsolutePath().normalize();
    }

    /**
     * 设计请求不是 LLM token 流，但仍持久化为普通对话，确保刷新页面后能看到每次设计迭代结果。
     *
     * @param appId 应用 ID
     * @param userMessage 用户可见修改要求
     * @param assistantMessage 系统可见结果摘要
     * @param loginUser 当前用户
     */
    private void persistDesignConversation(Long appId,
                                           String userMessage,
                                           String assistantMessage,
                                           User loginUser) {
        chatHistoryService.addChatMessage(appId, userMessage,
                ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        chatHistoryService.addChatMessage(appId, assistantMessage,
                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
    }
}
