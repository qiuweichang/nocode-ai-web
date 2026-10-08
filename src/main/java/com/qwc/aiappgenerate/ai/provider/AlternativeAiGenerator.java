package com.qwc.aiappgenerate.ai.provider;

import com.qwc.aiappgenerate.exception.BusinessException;
import com.qwc.aiappgenerate.exception.ErrorCode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Provides the same prompt-in/text-out contract through Spring AI or AgentScope.
 * The existing LangChain4j tool-call workflows remain owned by their current service.
 */
@Service
public class AlternativeAiGenerator {

    private final AiProviderProperties properties;
    private final ResourceLoader resourceLoader;
    private final ChatClient springAiClient;
    private final io.agentscope.core.model.ChatModel agentScopeModel;

    /**
     * Wires both optional adapters; the selected adapter is invoked per request.
     * Spring AI receives its auto-configured ChatModel while AgentScope uses the
     * same OpenAI-compatible endpoint and credentials as the existing model setup.
     */
    public AlternativeAiGenerator(AiProviderProperties properties,
                                  ResourceLoader resourceLoader,
                                  ObjectProvider<ChatModel> chatModelProvider,
                                  Environment environment) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        this.springAiClient = chatModel == null ? null : ChatClient.builder(chatModel).build();
        String apiKey = properties.getApiKey() == null || properties.getApiKey().isBlank()
                ? environment.getProperty("langchain4j.open-ai.chat-model.api-key") : properties.getApiKey();
        String baseUrl = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                ? environment.getProperty("langchain4j.open-ai.chat-model.base-url") : properties.getBaseUrl();
        String modelName = properties.getModel() == null || properties.getModel().isBlank()
                ? environment.getProperty("langchain4j.open-ai.chat-model.model-name", "deepseek-chat")
                : properties.getModel();
        this.agentScopeModel = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    /**
     * Calls the selected framework and returns its complete response.
     * @param userPrompt user request sent to the model
     * @param systemPromptResource classpath resource containing system instructions
     * @return complete model text
     */
    public String generate(String userPrompt, String systemPromptResource) {
        String systemPrompt = loadSystemPrompt(systemPromptResource);
        return switch (properties.getType().toLowerCase()) {
            case "spring-ai" -> springAiClient.prompt().system(systemPrompt).user(userPrompt).call().content();
            case "agentscope" -> createAgent(systemPrompt).call(new UserMessage(userPrompt)).map(msg -> msg.getTextContent()).block();
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "该调用入口仅支持 spring-ai 或 agentscope");
        };
    }

    /**
     * Streams text fragments from the selected framework as Reactor strings.
     * @param userPrompt user request sent to the model
     * @param systemPromptResource classpath resource containing system instructions
     * @return publisher of incremental response text
     */
    public Flux<String> stream(String userPrompt, String systemPromptResource) {
        String systemPrompt = loadSystemPrompt(systemPromptResource);
        return switch (properties.getType().toLowerCase()) {
            case "spring-ai" -> springAiClient.prompt().system(systemPrompt).user(userPrompt).stream().content();
            case "agentscope" -> createAgent(systemPrompt).streamEvents(new UserMessage(userPrompt))
                    .filter(event -> event instanceof TextBlockDeltaEvent)
                    .map(event -> ((TextBlockDeltaEvent) event).getDelta());
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "该调用入口仅支持 spring-ai 或 agentscope");
        };
    }

    /** Builds a request-scoped AgentScope agent so each prompt can use its own system instructions. */
    private ReActAgent createAgent(String systemPrompt) {
        return ReActAgent.builder().name("code-generator").sysPrompt(systemPrompt).model(agentScopeModel).build();
    }

    /** Reads prompt text from the classpath, preserving the existing prompt files as source of truth. */
    private String loadSystemPrompt(String resourcePath) {
        Resource resource = resourceLoader.getResource("classpath:" + resourcePath);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法读取模型系统提示词");
        }
    }
}
