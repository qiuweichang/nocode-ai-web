package com.qwc.aiappgenerate.core;

import cn.hutool.json.JSONUtil;
import com.qwc.aiappgenerate.ai.AiCodeGeneratorService;
import com.qwc.aiappgenerate.ai.AiCodeGeneratorServiceFactory;
import com.qwc.aiappgenerate.ai.provider.AiProviderProperties;
import com.qwc.aiappgenerate.ai.provider.AlternativeAiGenerator;
import com.qwc.aiappgenerate.ai.model.HtmlCodeResult;
import com.qwc.aiappgenerate.ai.model.MultiFileCodeResult;
import com.qwc.aiappgenerate.ai.model.message.AiResponseMessage;
import com.qwc.aiappgenerate.ai.model.message.ToolExecutedMessage;
import com.qwc.aiappgenerate.ai.model.message.ToolRequestMessage;
import com.qwc.aiappgenerate.constant.AppConstant;
import com.qwc.aiappgenerate.core.parser.CodeParserExecutor;
import com.qwc.aiappgenerate.core.saver.CodeFileSaverExecutor;
import com.qwc.aiappgenerate.exception.BusinessException;
import com.qwc.aiappgenerate.exception.ErrorCode;
import com.qwc.aiappgenerate.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 代码生成门面类，组合代码生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    /**
     * 多文件工具最后一次成功写盘后的静默收敛时间。
     * 模型常会在工具完成后再次请求生成总结；若该请求迟迟不结束，不能让已经落盘的页面一直停在“生成中”。
     * 该窗口也允许首轮生成连续写入 HTML、CSS、JS，后续每次成功写入都会重新计时。
     */
    private static final long MULTI_FILE_TOOL_SETTLE_SECONDS = 5L;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private AiProviderProperties aiProviderProperties;

    @Resource
    private AlternativeAiGenerator alternativeAiGenerator;


    /**
     * 统一入口：根据类型生成并保存代码（使用 appId）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result;
                if ("langchain4j".equalsIgnoreCase(aiProviderProperties.getType())) {
                    result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                } else {
                    result = new HtmlCodeResult();
                    result.setHtmlCode(alternativeAiGenerator.generate(userMessage,
                            "prompt/codegen-html-system-prompt.txt"));
                }
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式，使用 appId）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = "langchain4j".equalsIgnoreCase(aiProviderProperties.getType())
                        ? aiCodeGeneratorService.generateHtmlCodeStream(userMessage)
                        : alternativeAiGenerator.stream(userMessage, "prompt/codegen-html-system-prompt.txt");
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateMultiFileProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId, CodeGenTypeEnum.MULTI_FILE);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId, CodeGenTypeEnum.VUE_PROJECT);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 通用流式代码处理方法（使用 appId）
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用 ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                if (matchesExistingGeneratedCode(parsedResult, codeGenType, appId)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "AI 本轮没有产生实际代码变更，请补充更明确的修改要求后重试");
                }
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (BusinessException e) {
                log.error("AI 代码未能落盘，appId={}, message={}", appId, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("AI 代码保存失败，appId={}", appId, e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "AI 输出解析或保存失败，请重新生成");
            }
        });
    }

    /**
     * 判断模型输出的有效文件是否与磁盘版本完全一致。
     * 已有项目若没有任何实际差异，应把本轮标记为失败，避免前端显示“完成”却看不到变化。
     *
     * @param parsedResult 已解析的代码结果
     * @param codeGenType 代码生成模式
     * @param appId 应用 ID
     * @return 至少一个有效输出文件参与比较且全部与现有文件相同时返回 true
     */
    private boolean matchesExistingGeneratedCode(Object parsedResult,
                                                  CodeGenTypeEnum codeGenType,
                                                  Long appId) {
        Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR,
                codeGenType.getValue() + "_" + appId).toAbsolutePath().normalize();
        if (!Files.isDirectory(projectRoot)) {
            return false;
        }
        if (codeGenType == CodeGenTypeEnum.HTML && parsedResult instanceof HtmlCodeResult htmlResult) {
            return matchesExistingFile(projectRoot.resolve("index.html"), htmlResult.getHtmlCode());
        }
        if (codeGenType == CodeGenTypeEnum.MULTI_FILE
                && parsedResult instanceof MultiFileCodeResult multiFileResult) {
            boolean compared = false;
            if (multiFileResult.getHtmlCode() != null && !multiFileResult.getHtmlCode().isBlank()) {
                compared = true;
                if (!matchesExistingFile(projectRoot.resolve("index.html"), multiFileResult.getHtmlCode())) {
                    return false;
                }
            }
            if (multiFileResult.getCssCode() != null && !multiFileResult.getCssCode().isBlank()) {
                compared = true;
                if (!matchesExistingFile(projectRoot.resolve("style.css"), multiFileResult.getCssCode())) {
                    return false;
                }
            }
            if (multiFileResult.getJsCode() != null && !multiFileResult.getJsCode().isBlank()) {
                compared = true;
                if (!matchesExistingFile(projectRoot.resolve("script.js"), multiFileResult.getJsCode())) {
                    return false;
                }
            }
            return compared;
        }
        return false;
    }

    /**
     * 比较单个模型输出文件与磁盘文件内容，统一处理换行和首尾空白差异。
     *
     * @param existingFile 磁盘上的现有文件
     * @param generatedContent 模型输出的完整文件内容
     * @return 文件存在且规范化内容完全一致时返回 true
     */
    private boolean matchesExistingFile(Path existingFile, String generatedContent) {
        if (generatedContent == null || generatedContent.isBlank() || !Files.isRegularFile(existingFile)) {
            return false;
        }
        try {
            String existingContent = Files.readString(existingFile, StandardCharsets.UTF_8);
            return normalizeGeneratedContent(existingContent)
                    .equals(normalizeGeneratedContent(generatedContent));
        } catch (Exception e) {
            log.error("比较 AI 输出与现有源码失败，file={}", existingFile, e);
            return false;
        }
    }

    /**
     * 统一不同平台换行并移除模型代码块常见的首尾空白，避免产生无意义的文件变更。
     *
     * @param content 待比较的源码文本
     * @return 可稳定比较的源码文本
     */
    private String normalizeGeneratedContent(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param appId 应用 ID，用于日志和项目定位
     * @param codeGenType 当前项目生成类型
     * @return 包含 AI 文本及工具调用详情的流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream,
                                            Long appId,
                                            CodeGenTypeEnum codeGenType) {
        return Flux.create(sink -> {
            // 多文件模式必须至少产生一次真实写入，避免再次出现“回答已修改、文件未变化”。
            AtomicBoolean hasActualFileChange = new AtomicBoolean(false);
            // 只保存最新的收敛任务；连续写多个文件时会取消旧任务，确保最后一个文件完成后再结束前端流。
            AtomicReference<Disposable> pendingCompletion = new AtomicReference<>();
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        if ("writeFile".equals(toolExecution.request().name())
                                && toolExecution.result() != null
                                && toolExecution.result().startsWith("文件写入成功")) {
                            hasActualFileChange.set(true);
                        }
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                        if (codeGenType == CodeGenTypeEnum.MULTI_FILE && hasActualFileChange.get()) {
                            Disposable nextCompletion = Schedulers.parallel().schedule(() -> {
                                if (!sink.isCancelled() && hasActualFileChange.get()) {
                                    log.info("多文件工具写入已收敛，结束流式响应，appId={}", appId);
                                    sink.complete();
                                }
                            }, MULTI_FILE_TOOL_SETTLE_SECONDS, TimeUnit.SECONDS);
                            Disposable previousCompletion = pendingCompletion.getAndSet(nextCompletion);
                            if (previousCompletion != null) {
                                previousCompletion.dispose();
                            }
                        }
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        Disposable completion = pendingCompletion.getAndSet(null);
                        if (completion != null) {
                            completion.dispose();
                        }
                        if (codeGenType == CodeGenTypeEnum.MULTI_FILE && !hasActualFileChange.get()) {
                            sink.error(new BusinessException(ErrorCode.OPERATION_ERROR,
                                    "AI 未调用文件工具产生实际变更，请重新描述修改要求后重试"));
                            return;
                        }
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        Disposable completion = pendingCompletion.getAndSet(null);
                        if (completion != null) {
                            completion.dispose();
                        }
                        if (codeGenType == CodeGenTypeEnum.MULTI_FILE && hasActualFileChange.get()) {
                            log.info("模型总结阶段异常，但文件已成功写入，按生成成功结束，appId={}", appId);
                            sink.complete();
                            return;
                        }
                        log.error("AI 工具流执行失败，appId={}, codeGenType={}", appId, codeGenType, error);
                        sink.error(error);
                    })
                    .start();
        });
    }

}

