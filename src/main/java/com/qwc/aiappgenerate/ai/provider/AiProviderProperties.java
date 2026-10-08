package com.qwc.aiappgenerate.ai.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Selects the implementation used for ordinary prompt-to-text generation and
 * supplies the OpenAI-compatible endpoint shared by Spring AI and AgentScope.
 */
@Component
@ConfigurationProperties(prefix = "ai.provider")
public class AiProviderProperties {

    /** Active adapter: langchain4j, spring-ai, or agentscope. */
    private String type = "langchain4j";
    /** Compatible API endpoint, normally the provider's /v1 base URL. */
    private String baseUrl;
    /** Credential reused from the existing LangChain4j model configuration. */
    private String apiKey;
    /** Chat model identifier passed to the selected provider adapter. */
    private String model = "deepseek-chat";

    /** Returns the selected provider type. */
    public String getType() { return type; }
    /** Sets the provider type from application configuration. */
    public void setType(String type) { this.type = type; }
    /** Returns the OpenAI-compatible endpoint. */
    public String getBaseUrl() { return baseUrl; }
    /** Sets the OpenAI-compatible endpoint. */
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    /** Returns the provider credential. */
    public String getApiKey() { return apiKey; }
    /** Sets the provider credential. */
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    /** Returns the configured model name. */
    public String getModel() { return model; }
    /** Sets the configured model name. */
    public void setModel(String model) { this.model = model; }
}
