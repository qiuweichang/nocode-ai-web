package com.qwc.aiappgenerate.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.qwc.aiappgenerate.ai.AiCodeGeneratorService;
import com.qwc.aiappgenerate.constant.AppConstant;
import com.qwc.aiappgenerate.core.AiCodeGeneratorFacade;
import com.qwc.aiappgenerate.core.handler.StreamHandlerExecutor;
import com.qwc.aiappgenerate.exception.BusinessException;
import com.qwc.aiappgenerate.exception.ErrorCode;
import com.qwc.aiappgenerate.exception.ThrowUtils;
import com.qwc.aiappgenerate.mapper.AppMapper;
import com.qwc.aiappgenerate.model.dto.app.AppQueryRequest;
import com.qwc.aiappgenerate.model.entity.App;
import com.qwc.aiappgenerate.model.entity.User;
import com.qwc.aiappgenerate.model.enums.ChatHistoryMessageTypeEnum;
import com.qwc.aiappgenerate.model.enums.CodeGenTypeEnum;
import com.qwc.aiappgenerate.model.vo.AppVO;
import com.qwc.aiappgenerate.model.vo.UserVO;
import com.qwc.aiappgenerate.service.AppService;
import com.qwc.aiappgenerate.service.ChatHistoryService;
import com.qwc.aiappgenerate.service.DesignWorkflowService;
import com.qwc.aiappgenerate.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用服务实现
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    /**
     * 发送给模型的现有源码最大字符数。
     * 多轮修改必须携带磁盘上的真实版本，但需要限制长度，避免异常大文件挤占模型上下文。
     */
    private static final int MAX_EXISTING_SOURCE_CHARS = 160_000;

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    /** 为首轮代码生成提供设计确认门禁和已确认 Stitch HTML 上下文。 */
    @Resource
    private DesignWorkflowService designWorkflowService;

    @Override
    public void validApp(App app, boolean add) {
        if (app == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String appName = app.getAppName();
        String initPrompt = app.getInitPrompt();
        // 创建时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(appName), ErrorCode.PARAMS_ERROR);
            ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR);
        }
        // 有参数则校验
        if (StrUtil.isNotBlank(appName) && appName.length() > 80) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用名称过长");
        }
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }


    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        fillGeneratedCodeState(app, appVO);
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = new AppVO();
            BeanUtil.copyProperties(app, appVO);
            appVO.setUser(userVOMap.get(app.getUserId()));
            fillGeneratedCodeState(app, appVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 兼容早期没有保存生成类型的应用，避免覆盖已经明确选择的生成类型。
        if (StrUtil.isBlank(app.getCodeGenType())) {
            app.setCodeGenType(CodeGenTypeEnum.MULTI_FILE.getValue());
        }
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR,
                codeGenTypeEnum.getValue() + "_" + appId).toAbsolutePath().normalize();
        boolean hasExistingCode = Files.isDirectory(projectRoot)
                && Files.isRegularFile(projectRoot.resolve("index.html"));
        if (!hasExistingCode && !designWorkflowService.isDesignConfirmed(appId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请先确认样式方案，再开始生成代码");
        }
        // 5. 先创建 AI 服务和代码流。缓存首次加载历史时还没有本轮消息，避免同一需求以原文和增强版重复进入模型上下文。
        String generationMessage = buildGenerationMessage(message, codeGenTypeEnum, appId);
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                generationMessage, codeGenTypeEnum, appId);
        // 6. 对话流创建成功后再持久化用户看到的原始消息，历史记录不包含冗长源码上下文。
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 7. 收集 AI 响应内容并在完成后记录到对话历史
        return streamHandlerExecutor
                .doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .doOnComplete(() -> touchAppUpdatedTime(appId));
    }

    /**
     * 为多轮修改补充磁盘上的当前源码，确保模型基于真实文件而不是仅凭历史回复进行修改。
     * 首轮生成或 Vue 工具模式没有传统静态文件时，保持用户消息原样。
     *
     * @param userMessage 用户原始修改要求
     * @param codeGenType 代码生成模式
     * @param appId 应用 ID
     * @return 可直接发送给模型的完整生成指令
     */
    private String buildGenerationMessage(String userMessage, CodeGenTypeEnum codeGenType, Long appId) {
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR,
                codeGenType.getValue() + "_" + appId).toAbsolutePath().normalize();
        if (!Files.isDirectory(projectRoot) || !Files.isRegularFile(projectRoot.resolve("index.html"))) {
            return designWorkflowService.buildConfirmedDesignContext(appId, userMessage);
        }
        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            return userMessage;
        }
        List<String> sourceFiles = codeGenType == CodeGenTypeEnum.HTML
                ? List.of("index.html")
                : List.of("index.html", "style.css", "script.js");
        StringBuilder sourceContext = new StringBuilder(userMessage)
                .append("\n\n以下是项目磁盘上当前生效的完整源码。请以这些文件为唯一修改基线，")
                .append(codeGenType == CodeGenTypeEnum.MULTI_FILE
                        ? "严格落实本轮要求，只调用 writeFile 覆盖实际发生变化的文件，content 必须是该文件修改后的完整内容；不要只描述已修改：\n"
                        : "严格落实本轮要求，并输出修改后的完整文件；不要只描述已修改：\n");
        int appendedChars = 0;
        for (String fileName : sourceFiles) {
            Path sourcePath = projectRoot.resolve(fileName).normalize();
            if (!sourcePath.startsWith(projectRoot) || !Files.isRegularFile(sourcePath)) {
                continue;
            }
            try {
                String content = Files.readString(sourcePath, StandardCharsets.UTF_8);
                int remainingChars = MAX_EXISTING_SOURCE_CHARS - appendedChars;
                if (remainingChars <= 0) {
                    break;
                }
                String boundedContent = content.length() > remainingChars
                        ? content.substring(0, remainingChars)
                        : content;
                sourceContext.append("\n--- ").append(fileName).append(" ---\n")
                        .append(boundedContent).append('\n');
                appendedChars += boundedContent.length();
            } catch (Exception e) {
                log.error("读取现有源码作为 AI 修改上下文失败，appId={}, file={}", appId, fileName, e);
            }
        }
        return appendedChars > 0 ? sourceContext.toString() : userMessage;
    }

    /**
     * 标记应用最近一次实际代码生成完成的时间，使“我的作品”能够按真实更新时间排序。
     *
     * @param appId 已完成代码落盘的应用 ID
     */
    private void touchAppUpdatedTime(Long appId) {
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setUpdateTime(LocalDateTime.now());
        if (!this.updateById(updateApp)) {
            log.error("更新应用生成时间失败，appId={}", appId);
            return;
        }
        log.info("应用生成时间已更新，appId={}", appId);
    }

    /**
     * 检查应用的生成目录是否存在并写入视图对象，供作品卡片安全加载页面缩略预览。
     *
     * @param app 应用实体
     * @param appVO 待补充状态的应用视图对象
     */
    private void fillGeneratedCodeState(App app, AppVO appVO) {
        if (app == null || appVO == null || app.getId() == null || StrUtil.isBlank(app.getCodeGenType())) {
            if (appVO != null) {
                appVO.setHasGeneratedCode(false);
            }
            return;
        }
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR,
                app.getCodeGenType() + "_" + app.getId()).toAbsolutePath().normalize();
        appVO.setHasGeneratedCode(Files.isDirectory(projectRoot)
                && Files.isRegularFile(projectRoot.resolve("index.html")));
    }


    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 7. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 8. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 9. 返回可访问的 URL
        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
    }

    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }
}
