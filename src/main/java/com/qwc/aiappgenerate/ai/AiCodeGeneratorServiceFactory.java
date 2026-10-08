package com.qwc.aiappgenerate.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.qwc.aiappgenerate.ai.tools.FileWriteTool;
import com.qwc.aiappgenerate.exception.BusinessException;
import com.qwc.aiappgenerate.exception.ErrorCode;
import com.qwc.aiappgenerate.model.enums.CodeGenTypeEnum;
import com.qwc.aiappgenerate.service.ChatHistoryService;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 代码生成服务工厂。
 * HTML 与 Vue 会话按应用缓存并使用持久化聊天记忆；多文件工具会话每轮独立创建，
 * 避免上一轮未闭合的 tool_calls 污染下一轮文件修改请求。
 *
 * @author qiuwc
 * @since 2026/2/4 15:58
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel openAiStreamingChatModel;

    @Resource
    private StreamingChatModel reasoningStreamingChatModel;

    @Resource
    private ChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * AI 服务实例缓存
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.info("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（带缓存）这个方法是为了兼容历史逻辑
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据应用和生成类型获取 AI 服务。
     * 多文件模式的请求已经携带磁盘完整源码，因此每轮使用隔离内存；其他模式继续复用缓存服务。
     *
     * @param appId 应用 ID，同时用于文件工具定位项目目录
     * @param codeGenType 代码生成类型
     * @return 可执行当前代码生成请求的 AI 服务
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        if (codeGenType == CodeGenTypeEnum.MULTI_FILE) {
            return createIsolatedMultiFileService();
        }
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 创建单轮隔离的多文件工具服务。
     * 这里不挂接 Redis，也不加载历史工具消息；模型修改所需上下文由 AppServiceImpl 从磁盘实时提供。
     * 同一轮内部仍使用 MessageWindowChatMemory，以保证 writeFile 执行结果能够正确跟随 tool_calls
     * 参与模型的后续总结请求。
     *
     * @return 仅服务一次多文件生成或修改请求的 AI 服务
     */
    private AiCodeGeneratorService createIsolatedMultiFileService() {
        MessageWindowChatMemory isolatedMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
        return AiServices.builder(AiCodeGeneratorService.class)
                .streamingChatModel(reasoningStreamingChatModel)
                .chatMemoryProvider(memoryId -> isolatedMemory)
                .tools(new FileWriteTool(CodeGenTypeEnum.MULTI_FILE))
                .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                        toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                ))
                .build();
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }

    /**
     * 创建新的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            // Vue 项目保留持久化工具会话；多文件模式已在入口处使用单轮隔离服务。
            case VUE_PROJECT -> AiServices.builder(AiCodeGeneratorService.class)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .tools(new FileWriteTool(codeGenType))
                    .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                            toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                    ))
                    .build();
            case MULTI_FILE -> throw new IllegalStateException("多文件服务必须通过单轮隔离入口创建");
            // 单 HTML 生成保留传统文本解析模式。
            case HTML -> AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemory(chatMemory)
                    .build();
        };
    }

}
