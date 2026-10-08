package com.qwc.aiappgenerate.integration.stitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qwc.aiappgenerate.exception.BusinessException;
import com.qwc.aiappgenerate.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Stitch Streamable HTTP MCP 客户端。
 * 该客户端只实现项目当前需要的 create_project、generate_screen_from_text、edit_screens 和 get_screen，
 * 避免额外引入与现有 LangChain4j 版本不兼容的 MCP SDK。
 */
@Component
@Slf4j
public class StitchMcpClient {

    /** MCP 协议版本；与 Google 当前 Streamable HTTP 服务协商使用。 */
    private static final String MCP_PROTOCOL_VERSION = "2025-06-18";

    /** Stitch MCP 服务地址，支持通过 STITCH_HOST 覆盖。 */
    private final URI endpoint;

    /** 只保存在服务进程内存中的 API Key，不会写入设计元数据或日志。 */
    private final String apiKey;

    /** 单次 Stitch 生成最长等待时间，官方生成通常需要 1 到 3 分钟。 */
    private final Duration timeout;

    /** 显式本地验收开关；正式环境必须关闭。 */
    private final boolean mockEnabled;

    /** JDK 原生 HTTP 客户端，负责 MCP 请求和签名文件下载。 */
    private final HttpClient httpClient;

    /** JSON-RPC 请求 ID，保证同一进程请求可追踪且不重复。 */
    private final AtomicLong requestId = new AtomicLong(1);

    private final ObjectMapper objectMapper;

    /** 服务端初始化后返回的会话 ID；为空表示下一次调用需要重新握手。 */
    private String sessionId;

    /**
     * 创建 Stitch MCP 客户端。
     *
     * @param endpoint Stitch MCP 地址
     * @param apiKey 从环境变量读取的 API Key
     * @param timeoutSeconds 单次调用超时秒数
     * @param mockEnabled 是否启用本地验收模式
     * @param objectMapper 项目统一 Jackson 配置
     */
    public StitchMcpClient(@Value("${stitch.endpoint}") String endpoint,
                           @Value("${stitch.api-key:}") String apiKey,
                           @Value("${stitch.timeout-seconds:300}") long timeoutSeconds,
                           @Value("${stitch.mock-enabled:false}") boolean mockEnabled,
                           ObjectMapper objectMapper) {
        this.endpoint = URI.create(endpoint);
        String configuredApiKey = apiKey == null ? "" : apiKey.trim();
        this.apiKey = configuredApiKey.isBlank()
                ? System.getenv().getOrDefault("STITCH_API_KEY", "").trim()
                : configuredApiKey;
        this.timeout = Duration.ofSeconds(Math.max(timeoutSeconds, 30));
        this.mockEnabled = mockEnabled;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 创建 Stitch 项目并生成首版桌面设计。
     * MCP 会话被串行使用，避免同一 session 上并发请求导致响应 ID 或会话状态错乱。
     *
     * @param projectTitle Stitch 项目标题
     * @param prompt 完整桌面页面设计提示词
     * @return 包含 HTML 与截图下载地址的设计产物
     */
    public synchronized StitchScreenArtifact generateDesign(String projectTitle, String prompt) {
        if (mockEnabled) {
            pauseMockGeneration();
            return buildMockArtifact("mock-project-" + UUID.randomUUID(), prompt, 1);
        }
        ensureConfigured();
        JsonNode createResult = callTool("create_project", Map.of("title", projectTitle));
        String projectId = requireResourceId(createResult, "projectId", "projects", "样式设计服务未返回项目 ID");
        JsonNode generateResult = callTool("generate_screen_from_text", Map.of(
                "projectId", projectId,
                "prompt", prompt,
                "deviceType", "DESKTOP"
        ));
        String screenId = requireGeneratedScreenId(generateResult, "样式设计服务未返回页面 ID");
        return resolveScreenArtifact(projectId, screenId, generateResult);
    }

    /**
     * 基于当前 Stitch screen 生成修改后的新版本。
     *
     * @param projectId 已存在的 Stitch 项目 ID
     * @param screenId 当前展示的 Stitch screen ID
     * @param prompt 包含局部元素上下文的修改提示词
     * @param nextRevision 本地下一版本号，仅用于验收模式生成稳定内容
     * @param previousHtml 修改前的导出 HTML，用于识别 Stitch 原地异步更新是否真正完成
     * @return 修改后的可落盘设计产物
     */
    public synchronized StitchScreenArtifact reviseDesign(String projectId,
                                                           String screenId,
                                                           String prompt,
                                                           int nextRevision,
                                                           String previousHtml) {
        if (mockEnabled) {
            pauseMockGeneration();
            return buildMockArtifact(projectId, prompt, nextRevision);
        }
        ensureConfigured();
        JsonNode editResult = callTool("edit_screens", Map.of(
                "projectId", projectId,
                "selectedScreenIds", new String[]{screenId},
                "prompt", prompt,
                "deviceType", "DESKTOP"
        ));
        String editedScreenId = findGeneratedScreenId(editResult);
        if (editedScreenId == null || editedScreenId.isBlank()) {
            String responseText = extractSessionText(editResult);
            log.info("Stitch edit_screens 未返回设计，改用当前 HTML 生成精确修改稿，响应说明={}", responseText);
            JsonNode regeneratedResult = callTool("generate_screen_from_text", Map.of(
                    "projectId", projectId,
                    "prompt", buildHtmlBasedRevisionPrompt(prompt, previousHtml),
                    "deviceType", "DESKTOP"
            ));
            String regeneratedScreenId = requireGeneratedScreenId(
                    regeneratedResult, "样式设计服务未返回重新生成的修改稿");
            StitchScreenArtifact regeneratedArtifact = resolveScreenArtifact(
                    projectId, regeneratedScreenId, regeneratedResult);
            return requireChangedArtifact(regeneratedArtifact, previousHtml);
        }
        return requireChangedArtifact(resolveScreenArtifact(projectId, editedScreenId, editResult), previousHtml);
    }

    /**
     * 构造 HTML 参考式修改提示词，让 Stitch 在 edit_screens 只返回文字时仍能产出可导出的真实 screen。
     *
     * @param revisionPrompt 包含用户修改要求与选中要素上下文的提示词
     * @param previousHtml 当前设计的完整 HTML
     * @return 用于 generate_screen_from_text 的桌面设计提示词
     */
    private String buildHtmlBasedRevisionPrompt(String revisionPrompt, String previousHtml) {
        return "请基于下面现有桌面网页的 HTML 精确复刻当前视觉设计，只应用指定修改。"
                + "不要重新设计其他区域，不要增加或删除内容，不要生成移动端、平板端或响应式版本。\n\n"
                + revisionPrompt + "\n\n现有 HTML：\n```html\n" + previousHtml + "\n```";
    }

    /**
     * 下载 Stitch 新产物并与修改前 HTML 对比，确保本轮确实产生代码级变化。
     *
     * @param artifact Stitch 新产物
     * @param previousHtml 修改前 HTML
     * @return 已携带内联 HTML 的新产物
     */
    private StitchScreenArtifact requireChangedArtifact(StitchScreenArtifact artifact, String previousHtml) {
        String currentHtml = artifact.getInlineHtml();
        if (currentHtml == null && artifact.getHtmlDownloadUrl() != null) {
            currentHtml = new String(downloadArtifact(artifact.getHtmlDownloadUrl()), StandardCharsets.UTF_8);
            artifact.setInlineHtml(currentHtml);
        }
        if (currentHtml == null || currentHtml.equals(previousHtml)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "本轮没有产生可验证的样式变更，请换一种描述后重试");
        }
        return artifact;
    }

    /**
     * 下载 Stitch 返回的签名资源。
     *
     * @param downloadUrl get_screen 返回的 HTML 或截图下载地址
     * @return 原始文件字节
     */
    public byte[] downloadArtifact(String downloadUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "下载样式设计产物失败，HTTP " + response.statusCode());
            }
            return response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载 Stitch 设计产物失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "下载样式设计产物失败");
        }
    }

    /**
     * 查询 screen 完整信息，确保拿到可下载 HTML 和截图地址。
     *
     * @param projectId Stitch 项目 ID
     * @param screenId Stitch screen ID
     * @return 可落盘设计产物
     */
    private StitchScreenArtifact loadScreenArtifact(String projectId, String screenId) {
        JsonNode screenResult = callTool("get_screen", Map.of(
                "projectId", projectId,
                "screenId", screenId,
                "name", "projects/" + projectId + "/screens/" + screenId
        ));
        log.info("Stitch get_screen 返回字段={}", listFieldNames(screenResult));
        String htmlUrl = findNestedDownloadUrl(screenResult, "htmlCode");
        String screenshotUrl = findNestedDownloadUrl(screenResult, "screenshot");
        if (htmlUrl == null || htmlUrl.isBlank()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "样式设计服务未返回可导出的 HTML");
        }
        return StitchScreenArtifact.builder()
                .projectId(projectId)
                .screenId(screenId)
                .htmlDownloadUrl(htmlUrl)
                .screenshotDownloadUrl(screenshotUrl)
                .mock(false)
                .build();
    }

    /**
     * 优先复用生成或编辑响应中已经携带的签名下载地址，缺失时才调用 get_screen。
     * 这样既减少一次 MCP 往返，也规避新 screen 刚创建时查询接口尚未完成同步的问题。
     *
     * @param projectId Stitch 项目 ID
     * @param screenId 已解析的 screen ID
     * @param generationResult generate_screen_from_text 或 edit_screens 的结果
     * @return 可落盘的设计产物
     */
    private StitchScreenArtifact resolveScreenArtifact(String projectId,
                                                        String screenId,
                                                        JsonNode generationResult) {
        JsonNode generatedScreen = findGeneratedScreen(generationResult);
        log.info("Stitch 生成响应字段={}，输出组件={}，screen字段={}",
                listFieldNames(generationResult), describeOutputComponents(generationResult),
                listFieldNames(generatedScreen));
        String htmlUrl = findNestedDownloadUrl(generatedScreen, "htmlCode");
        String screenshotUrl = findNestedDownloadUrl(generatedScreen, "screenshot");
        if (htmlUrl == null || htmlUrl.isBlank()) {
            return loadScreenArtifact(projectId, screenId);
        }
        return StitchScreenArtifact.builder()
                .projectId(projectId)
                .screenId(screenId)
                .htmlDownloadUrl(htmlUrl)
                .screenshotDownloadUrl(screenshotUrl)
                .build();
    }

    /**
     * 仅列出 JSON 对象的字段名用于协议兼容诊断，不记录提示词、下载签名或用户设计内容。
     *
     * @param node 待描述的 JSON 节点
     * @return 逗号分隔字段名；非对象返回节点类型
     */
    private String listFieldNames(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "missing";
        }
        if (!node.isObject()) {
            return node.getNodeType().name();
        }
        StringBuilder fields = new StringBuilder();
        node.fieldNames().forEachRemaining(field -> {
            if (!fields.isEmpty()) {
                fields.append(',');
            }
            fields.append(field);
        });
        return fields.toString();
    }

    /**
     * 描述 Stitch 会话输出组件的类型字段，帮助区分设计结果、进度、追问和纯文本响应。
     * 方法只输出字段名，不记录组件正文或用户内容。
     *
     * @param result generate_screen_from_text 或 edit_screens 的结果
     * @return 各输出组件的字段集合
     */
    private String describeOutputComponents(JsonNode result) {
        StringBuilder description = new StringBuilder();
        int index = 0;
        for (JsonNode component : result.path("outputComponents")) {
            if (index > 0) {
                description.append('|');
            }
            description.append(index++).append(':').append(listFieldNames(component));
        }
        return description.isEmpty() ? "none" : description.toString();
    }

    /**
     * 汇总 Stitch 输出组件中的简短文字说明，用于在没有设计产物时返回真实原因。
     * 响应会限制长度，避免异常信息和日志被模型长文本占满。
     *
     * @param result edit_screens 的工具结果
     * @return 最多 300 个字符的说明文本
     */
    private String extractSessionText(JsonNode result) {
        StringBuilder text = new StringBuilder();
        for (JsonNode component : result.path("outputComponents")) {
            String componentText = component.path("text").asText("").trim();
            if (componentText.isBlank()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(componentText);
            if (text.length() >= 300) {
                return text.substring(0, 300);
            }
        }
        return text.toString();
    }

    /**
     * 执行 MCP tools/call 并抽取 Stitch 的结构化工具结果。
     *
     * @param toolName MCP 工具名
     * @param arguments 工具参数
     * @return 工具的 structuredContent 或文本 JSON
     */
    private JsonNode callTool(String toolName, Map<String, ?> arguments) {
        ensureConnected();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", objectMapper.valueToTree(arguments));
        JsonNode response = sendJsonRpc("tools/call", params, true);
        JsonNode result = response.path("result");
        if (result.path("isError").asBoolean(false)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "样式设计工具调用失败");
        }
        if (!result.path("structuredContent").isMissingNode()) {
            return result.path("structuredContent");
        }
        String textContent = extractTextContent(result);
        if (!textContent.isBlank()) {
            try {
                return objectMapper.readTree(textContent);
            } catch (Exception ignored) {
                JsonNode embeddedJson = parseEmbeddedJson(textContent);
                if (embeddedJson != null) {
                    return embeddedJson;
                }
                ObjectNode textNode = objectMapper.createObjectNode();
                textNode.put("text", textContent);
                return textNode;
            }
        }
        return result;
    }

    /**
     * 兼容 MCP 文本内容中使用 Markdown 代码块或说明文字包裹 JSON 的返回形式。
     * 只有截取内容能够被完整解析时才返回，避免把普通说明文本误当成结构化结果。
     *
     * @param textContent MCP text content 原文
     * @return 解析后的 JSON；不存在有效 JSON 时返回 null
     */
    private JsonNode parseEmbeddedJson(String textContent) {
        int objectStart = textContent.indexOf('{');
        int objectEnd = textContent.lastIndexOf('}');
        if (objectStart < 0 || objectEnd <= objectStart) {
            return null;
        }
        try {
            return objectMapper.readTree(textContent.substring(objectStart, objectEnd + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 初始化 MCP Streamable HTTP 会话并发送 initialized 通知。
     * 会话失效时清空 sessionId，下一次业务调用会重新握手。
     */
    private void ensureConnected() {
        if (sessionId != null) {
            return;
        }
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", MCP_PROTOCOL_VERSION);
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "ai-app-generate");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);
        sendJsonRpc("initialize", params, false);
        sendNotification("notifications/initialized");
        log.info("Stitch MCP 会话初始化完成");
    }

    /**
     * 发送带请求 ID 的 JSON-RPC 请求。
     *
     * @param method JSON-RPC 方法
     * @param params 方法参数
     * @param retrySessionFailure 会话失效时是否重新初始化并重试一次
     * @return 完整 JSON-RPC 响应
     */
    private JsonNode sendJsonRpc(String method, JsonNode params, boolean retrySessionFailure) {
        long id = requestId.getAndIncrement();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("method", method);
        payload.set("params", params);
        try {
            HttpResponse<String> response = send(payload.toString());
            if ((response.statusCode() == 404 || response.statusCode() == 400)
                    && retrySessionFailure && sessionId != null) {
                sessionId = null;
                ensureConnected();
                return sendJsonRpc(method, params, false);
            }
            validateResponseStatus(response);
            JsonNode responseJson = parseResponseBody(response.body(), id);
            if (responseJson.has("error")) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "样式设计服务调用失败");
            }
            return responseJson;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stitch MCP 请求失败，method={}", method, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "连接样式设计服务失败，请稍后重试");
        }
    }

    /**
     * 发送无需响应体的 JSON-RPC 通知。
     *
     * @param method 通知方法名
     */
    private void sendNotification(String method) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        try {
            HttpResponse<String> response = send(payload.toString());
            validateResponseStatus(response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("发送 Stitch MCP 初始化通知失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "初始化样式设计服务失败");
        }
    }

    /**
     * 构造并发送一次 MCP HTTP POST，同时保存服务端返回的会话 ID。
     *
     * @param body JSON-RPC 请求体
     * @return 字符串响应
     */
    private HttpResponse<String> send(String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("X-Goog-Api-Key", apiKey)
                .header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        response.headers().firstValue("mcp-session-id").ifPresent(value -> sessionId = value);
        return response;
    }

    /**
     * 校验 MCP HTTP 状态码并转换为业务异常，日志中不包含密钥和完整响应。
     *
     * @param response MCP HTTP 响应
     */
    private void validateResponseStatus(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "样式设计服务凭证无效或无权限");
        }
        if (response.statusCode() == 429) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "样式设计调用次数已达限制，请稍后再试");
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                "样式设计服务请求失败，HTTP " + response.statusCode());
    }

    /**
     * 兼容 application/json 与 text/event-stream 两种 MCP 响应格式。
     *
     * @param body HTTP 响应体
     * @param expectedId 当前 JSON-RPC 请求 ID
     * @return 匹配当前请求的 JSON 对象
     */
    private JsonNode parseResponseBody(String body, long expectedId) throws Exception {
        String normalized = body == null ? "" : body.trim();
        if (normalized.startsWith("{")) {
            return objectMapper.readTree(normalized);
        }
        for (String line : normalized.split("\\R")) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (data.isEmpty()) {
                continue;
            }
            JsonNode candidate = objectMapper.readTree(data);
            if (candidate.path("id").asLong(-1) == expectedId) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "样式设计服务返回了无法识别的响应");
    }

    /**
     * 从 MCP content 数组拼接文本结果。
     *
     * @param result tools/call 的 result 节点
     * @return 文本内容
     */
    private String extractTextContent(JsonNode result) {
        StringBuilder text = new StringBuilder();
        for (JsonNode content : result.path("content")) {
            if ("text".equals(content.path("type").asText())) {
                text.append(content.path("text").asText());
            }
        }
        return text.toString();
    }

    /**
     * 在任意深度对象中查找第一个非空文本字段。
     *
     * @param root Stitch 工具返回对象
     * @param fieldName 目标字段名
     * @param errorMessage 未找到时的业务错误
     * @return 字段文本
     */
    private String requireTextValue(JsonNode root, String fieldName, String errorMessage) {
        String value = findTextValue(root, fieldName);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMessage);
        }
        return stripResourcePrefix(value);
    }

    /**
     * 兼容 Stitch 的显式 ID 字段和 MCP 资源名。
     * 当前服务可能返回 projectId/screenId，也可能只在 name 或文本中返回 projects/{id}/screens/{id}。
     *
     * @param root Stitch 工具结果
     * @param fieldName 首选 ID 字段名
     * @param resourceSegment 资源路径片段，例如 projects 或 screens
     * @param errorMessage 无法解析时的业务错误
     * @return 裸资源 ID
     */
    private String requireResourceId(JsonNode root,
                                     String fieldName,
                                     String resourceSegment,
                                     String errorMessage) {
        String resourceId = findResourceId(root, fieldName, resourceSegment);
        if (resourceId != null && !resourceId.isBlank()) {
            return resourceId;
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMessage);
    }

    /**
     * 从生成类工具的 outputComponents[].design.screens[] 中读取 screen ID。
     * Stitch 的当前响应字段名是 id，旧版本可能使用 screenId 或仅提供 name，因此需要按顺序兼容。
     *
     * @param result generate_screen_from_text 或 edit_screens 的工具结果
     * @param errorMessage 无法解析时的业务错误
     * @return 裸 screen ID
     */
    private String requireGeneratedScreenId(JsonNode result, String errorMessage) {
        String screenId = findGeneratedScreenId(result);
        if (screenId == null || screenId.isBlank()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMessage);
        }
        return screenId;
    }

    /**
     * 尝试读取生成类工具返回的第一个 screen ID；编辑工具原地更新且不返回 screen 时允许为空。
     *
     * @param result Stitch 生成类工具结果
     * @return 裸 screen ID；不存在时返回 null
     */
    private String findGeneratedScreenId(JsonNode result) {
        JsonNode screen = findGeneratedScreen(result);
        // name 是 Stitch 的规范资源名（projects/{projectId}/screens/{screenId}）。
        // screen.id 是画布内部实体 ID，不能用于 edit_screens/get_screen，因此必须最后兜底使用。
        String screenId = findResourceId(screen, "name", "screens");
        if (screenId == null || screenId.isBlank()) {
            screenId = findTextValue(screen, "screenId");
        }
        if (screenId != null && !screenId.isBlank()) {
            return stripResourcePrefix(screenId);
        }
        return findTextValue(screen, "id");
    }

    /**
     * 定位 Stitch 会话输出中的首个设计 screen，避免在整个响应文本中误匹配其他资源 ID。
     *
     * @param result Stitch 工具结果
     * @return screen 节点；不存在时返回 MissingNode
     */
    private JsonNode findGeneratedScreen(JsonNode result) {
        if (result == null || result.isMissingNode()) {
            return MissingNode.getInstance();
        }
        for (JsonNode component : result.path("outputComponents")) {
            JsonNode screens = component.path("design").path("screens");
            if (screens.isArray() && !screens.isEmpty()) {
                return screens.get(0);
            }
        }
        return MissingNode.getInstance();
    }

    /**
     * 尝试从 Stitch 结果中解析资源 ID，但在工具采用原地更新且不回传 ID 时允许返回空值。
     *
     * @param root Stitch 工具结果
     * @param fieldName 首选 ID 字段名
     * @param resourceSegment 资源路径片段，例如 projects 或 screens
     * @return 裸资源 ID；无法解析时返回 null
     */
    private String findResourceId(JsonNode root, String fieldName, String resourceSegment) {
        String directValue = findTextValue(root, fieldName);
        if (directValue != null && !directValue.isBlank()) {
            return stripResourcePrefix(directValue);
        }
        Pattern resourcePattern = Pattern.compile(resourceSegment + "/([A-Za-z0-9_-]+)");
        Matcher resourceMatcher = resourcePattern.matcher(root == null ? "" : root.toString());
        if (resourceMatcher.find()) {
            return resourceMatcher.group(1);
        }
        return null;
    }

    /**
     * 递归查找指定文本字段。
     *
     * @param node 当前 JSON 节点
     * @param fieldName 字段名
     * @return 首个非空值，找不到返回 null
     */
    private String findTextValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode direct = node.get(fieldName);
            if (direct != null && direct.isValueNode() && !direct.asText().isBlank()) {
                return direct.asText();
            }
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                String found = findTextValue(children.next(), fieldName);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findTextValue(child, fieldName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 在 htmlCode 或 screenshot 对象下定位 downloadUrl。
     *
     * @param root get_screen 返回对象
     * @param parentField 父对象字段名
     * @return 下载地址，未返回时为 null
     */
    private String findNestedDownloadUrl(JsonNode root, String parentField) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        if (root.isObject()) {
            JsonNode parent = root.get(parentField);
            if (parent != null) {
                String direct = findTextValue(parent, "downloadUrl");
                if (direct != null) {
                    return direct;
                }
            }
            Iterator<JsonNode> children = root.elements();
            while (children.hasNext()) {
                String found = findNestedDownloadUrl(children.next(), parentField);
                if (found != null) {
                    return found;
                }
            }
        } else if (root.isArray()) {
            for (JsonNode child : root) {
                String found = findNestedDownloadUrl(child, parentField);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Stitch 有时返回 projects/{id} 或 screens/{id}，业务侧统一保存裸 ID。
     *
     * @param value 资源名或裸 ID
     * @return 裸 ID
     */
    private String stripResourcePrefix(String value) {
        int slashIndex = value.lastIndexOf('/');
        return slashIndex >= 0 ? value.substring(slashIndex + 1) : value;
    }

    /**
     * 确认真实 Stitch 模式已经配置凭证。
     */
    private void ensureConfigured() {
        if (apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "服务端未配置样式设计凭证，暂时无法生成样式");
        }
    }

    /**
     * 生成只用于本地浏览器验收的桌面 HTML。
     * 该分支必须由 STITCH_MOCK_ENABLED=true 显式开启，不会在正式配置缺失时静默降级。
     *
     * @param projectId 模拟项目 ID
     * @param prompt 当前设计提示词
     * @param revision 版本号
     * @return 带内联 HTML 的模拟设计产物
     */
    private StitchScreenArtifact buildMockArtifact(String projectId, String prompt, int revision) {
        String safePrompt = prompt == null ? "桌面网页" : prompt
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
        String cardBackground = prompt != null && prompt.contains("红") ? "#fee2e2" : "#fbfcff";
        String cardBorder = prompt != null && prompt.contains("红") ? "#ef4444" : "#e4e8f0";
        String html = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <title>Stitch Design Preview</title>
                  <style>
                    *{box-sizing:border-box} body{margin:0;background:#f5f7fb;color:#162033;font-family:Inter,"Microsoft YaHei",sans-serif}
                    .shell{min-height:100vh}.nav{height:72px;padding:0 7%%;display:flex;align-items:center;justify-content:space-between;background:#fff;border-bottom:1px solid #e6eaf1}
                    .brand{font-size:22px;font-weight:800}.nav-links{display:flex;gap:32px;color:#657086;font-size:14px}.hero{padding:82px 8%% 70px;display:grid;grid-template-columns:1.15fr .85fr;gap:64px;align-items:center;background:linear-gradient(135deg,#fff 20%%,#eef3ff)}
                    .eyebrow{color:#3156d9;font-weight:700}.hero h1{font-size:54px;line-height:1.08;margin:18px 0}.hero p{font-size:18px;line-height:1.8;color:#5c6679}.actions{display:flex;gap:14px;margin-top:32px}.primary,.secondary{border-radius:10px;padding:14px 24px;font-weight:700}.primary{background:#3156d9;color:#fff}.secondary{background:#fff;border:1px solid #d9dfeb}
                    .visual{height:360px;border-radius:24px;background:#17213a;padding:24px;box-shadow:0 30px 70px rgba(42,60,110,.22)}.visual-card{height:100%%;border-radius:16px;background:linear-gradient(145deg,#4866eb,#7a8df2);display:grid;place-items:center;color:#fff;font-size:26px;font-weight:800}
                    .features{padding:64px 8%% 86px;background:#fff}.features h2{text-align:center;font-size:34px}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:22px;margin-top:42px}.card{padding:28px;border:1px solid %s;border-radius:16px;background:%s}.card strong{font-size:19px}.card p{color:#6a7487;line-height:1.7}.revision{position:fixed;right:18px;bottom:18px;padding:8px 12px;border-radius:999px;background:#101827;color:#fff;font-size:12px}
                  </style>
                </head>
                <body>
                  <main class="shell">
                    <nav class="nav"><div class="brand">Nova Studio</div><div class="nav-links"><span>首页</span><span>产品</span><span>方案</span><span>关于我们</span></div></nav>
                    <section class="hero">
                      <div class="hero-copy"><div class="eyebrow">STITCH DESIGN CONCEPT</div><h1>把想法变成清晰、可信的数字体验</h1><p>根据你的需求生成的桌面端高保真样式方案。确认视觉方向后，AI 才会开始编写真实网页代码。</p><div class="actions"><span class="primary">开始体验</span><span class="secondary">查看方案</span></div></div>
                      <div class="visual"><div class="visual-card">DESIGN SYSTEM</div></div>
                    </section>
                    <section class="features"><h2>围绕核心体验设计</h2><div class="grid"><article class="card"><strong>清晰的信息层级</strong><p>以易读的网格和留白组织核心内容。</p></article><article class="card"><strong>统一的视觉语言</strong><p>颜色、圆角与字体形成一致的产品感。</p></article><article class="card"><strong>可继续迭代</strong><p>选择任意模块后，可以针对局部向 Stitch 提出修改。</p></article></div></section>
                  </main>
                  <div class="revision">v%s · %s</div>
                </body>
                </html>
                """.formatted(cardBorder, cardBackground, revision,
                safePrompt.length() > 28 ? safePrompt.substring(0, 28) + "…" : safePrompt);
        return StitchScreenArtifact.builder()
                .projectId(projectId)
                .screenId("mock-screen-" + revision + "-" + UUID.randomUUID())
                .inlineHtml(html)
                .mock(true)
                .build();
    }

    /**
     * 让本地验收模式保留足够长的加载状态，便于验证设计期间只读画布和操作锁定。
     * 真实 Stitch 本身需要较长生成时间，因此该延迟不会进入正式调用分支。
     */
    private void pauseMockGeneration() {
        try {
            Thread.sleep(900);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "样式生成已取消");
        }
    }
}
