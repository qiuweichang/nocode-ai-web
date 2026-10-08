# AI App Generate

AI App Generate 是一个面向 Web 应用的 AI 生成与迭代平台。用户通过聊天描述需求，系统生成 HTML、多文件或 Vue 项目；也可以继续修改已有项目、查看和编辑项目文件，并对生成结果进行预览、部署或下载。项目还包含基于 Google Stitch 的设计稿生成、调整和确认流程。

## 功能概览

- 用户注册、登录，以及应用创建和管理。
- 通过 SSE 流式生成代码，并展示模型回复和文件工具执行过程。
- 支持单 HTML、多文件静态项目和 Vue 项目生成；多文件与 Vue 修改可调用文件写入工具。
- 查看、编辑、保存项目文件，下载生成项目。
- 使用 Google Stitch 生成和修改设计稿，确认后进入代码生成流程。
- 保存聊天历史，并提供管理员管理入口。

## 技术栈

| 部分 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5、Maven、MyBatis-Flex、MySQL、Redis |
| 大模型 | LangChain4j；普通 HTML 调用可选 Spring AI 或 AgentScope |
| 前端 | Vue 3、TypeScript、Vite、Ant Design Vue、Pinia |
| 实时返回 | Spring WebFlux/Reactor 流、SSE |
| 设计生成 | Google Stitch MCP |

## 项目结构

```text
.
├── src/                         # Spring Boot 后端
│   ├── main/java/               # API、业务服务、模型调用和集成
│   └── main/resources/          # 应用配置与模型提示词
├── ai-app-generate-frontend/    # Vue 前端
├── sql/                         # 数据库脚本
├── pom.xml                      # 后端 Maven 配置
└── README.md
```

## 本地运行

### 环境要求

- JDK 21
- Node.js 22 或更高版本
- MySQL 和 Redis
- 可用的大模型 API 凭据；使用设计工作流时还需要 Google Stitch API Key

### 配置后端

应用默认监听 `8124`，上下文路径为 `/api`，并启用 `local` profile。按本地环境配置 `src/main/resources/application-local.yml`，填写 MySQL、Redis 和模型服务连接信息。此文件已加入 Git 忽略规则，请勿提交真实凭据。

数据库默认名称为 `yu_ai_code_mother`。按需执行 `sql/` 中的初始化脚本。Redis 用于聊天记忆和限流相关功能。

大模型接入方式由 `application.yml` 中的 `ai.provider.type` 选择：

```yaml
ai:
  provider:
    type: langchain4j # langchain4j、spring-ai 或 agentscope
```

- `langchain4j` 是默认方式，也负责依赖文件工具调用的多文件和 Vue 生成流程。
- `spring-ai` 使用 Spring AI 的 OpenAI 兼容 ChatClient；同时配置 `spring.ai.openai.api-key`。
- `agentscope` 使用 AgentScope 的 OpenAI 兼容模型；默认复用 LangChain4j 配置中的接口地址和密钥。

当前 Spring AI 与 AgentScope 的可切换调用覆盖普通 HTML 完整响应和文本流；文件工具工作流仍由 LangChain4j 执行。

Google Stitch 密钥通过 `STITCH_API_KEY` 环境变量读取。没有配置密钥时，Stitch 相关请求不可用。

启动后端：

```powershell
./mvnw.cmd spring-boot:run
```

### 配置并启动前端

前端开发服务器默认通过 Vite 代理访问本机后端。需要时调整 `ai-app-generate-frontend/.env` 中的 API、预览和部署地址。

```powershell
cd ai-app-generate-frontend
npm install
npm run dev
```

打开 Vite 输出的本地地址即可访问页面。

## 主要接口

后端 API 前缀为 `/api`。主要接口包括：

- `/app/chat/gen/code`：流式生成代码。
- `/app/design/generate`、`/app/design/revise`、`/app/design/confirm`：设计流程。
- `/app/files`、`/app/file`、`/app/file/save`：项目文件浏览与编辑。
- `/user/*`：用户注册、登录和会话管理。

## 注意事项

- 不要把真实 API Key、数据库密码或 Redis 密码提交到仓库。
- 用户生成的项目保存在运行目录下的 `tmp/code_output`，该目录不属于源码。
- Spring AI 和 AgentScope 目前用于对照与切换普通文本生成调用；如需切换文件工具 Agent，需要分别适配工具注册、执行和事件回传。
