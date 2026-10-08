<template>
  <div id="appChatPage">
    <div class="page-surface">
      <div class="workspace-header">
        <div class="header-left">
          <a-popover
            v-model:open="appSwitcherOpen"
            trigger="click"
            placement="bottomLeft"
            overlay-class-name="app-switcher-popover"
            @openChange="handleAppSwitcherOpenChange"
          >
            <template #content>
              <div class="app-switcher-panel">
                <div class="app-switcher-title">切换应用</div>
                <div v-if="userAppsLoading" class="app-switcher-state">正在加载应用列表...</div>
                <div v-else-if="userApps.length === 0" class="app-switcher-state">
                  暂无可切换的应用
                </div>
                <button
                  v-for="item in userApps"
                  :key="item.id"
                  type="button"
                  class="app-switcher-item"
                  :class="{ active: item.id === appId }"
                  @click="switchApp(item.id)"
                >
                  <div class="app-switcher-item-content">
                    <strong>{{ item.appName || '未命名应用' }}</strong>
                    <span>{{ formatDateTime(item.createTime) }}</span>
                  </div>
                </button>
              </div>
            </template>

            <button type="button" class="app-chip app-chip-button">
              <img :src="logo" alt="NoCode" class="app-chip-logo" />
              <div class="app-chip-content">
                <strong>{{ appData.appName || '应用生成器' }}</strong>
              </div>
              <DownOutlined class="app-chip-arrow" />
            </button>
          </a-popover>
        </div>

        <div class="header-actions">
          <a-button :loading="downloadLoading" :disabled="!hasGeneratedContent" @click="downloadSourceCode">
            <DownloadOutlined />
            下载源码
          </a-button>
          <a-button @click="openEditNameModal">
            <EditOutlined />
            编辑信息
          </a-button>
          <a-button
            class="deploy-btn"
            type="primary"
            :loading="deployLoading"
            :disabled="isGenerating || !hasGeneratedContent"
            @click="doDeploy"
          >
            <DeploymentUnitOutlined />
            部署应用
          </a-button>
        </div>
      </div>

      <a-spin :spinning="pageLoading" tip="正在加载应用数据">
        <div class="workspace" :class="{ 'design-workspace': isDesignPhase }">
          <section class="chat-panel">
            <div class="panel-title-row">
              <div>
                <span class="panel-label">用户消息</span>
              </div>
              <div class="title-tools">
                <a-tag>{{ messages.length }} 条消息</a-tag>
              </div>
            </div>

            <div class="latest-prompt-card">
              <div class="latest-prompt-caption">当前需求</div>
              <div class="latest-prompt-text">
                {{
                  latestDemandText ||
                  '描述越详细，页面越具体。你可以继续一步一步完善布局、配色、组件和交互。'
                }}
              </div>
            </div>

            <div ref="messagesContainer" class="messages" @scroll="handleMessagesScroll">
              <div v-if="messages.length === 0 && !pageLoading" class="empty-state">
                <div class="empty-badge">AI 回复</div>
                <h3>先发送一条需求</h3>
                <p>例如：生成个人博客、企业官网、运营后台，或者继续迭代已有页面效果。</p>
                <div class="empty-actions">
                  <a-button v-if="appData.initPrompt" @click="useInitPrompt"
                    >填入初始提示词</a-button
                  >
                  <a-button type="primary" ghost @click="fillExamplePrompt">填入示例需求</a-button>
                </div>
              </div>

              <div v-for="item in messages" :key="item.id" class="message-row" :class="item.role">
                <div class="avatar">{{ item.role === 'user' ? 'U' : 'AI' }}</div>
                <article class="bubble">
                  <div class="message-meta">
                    <span>{{ item.role === 'user' ? '你' : 'AI 助手' }}</span>
                    <span>{{ item.createdAt }}</span>
                  </div>
                  <div
                    v-if="item.role === 'assistant' && (item.streaming || item.executionSteps?.length)"
                    class="execution-card"
                    :class="{ active: item.streaming, failed: item.generationSucceeded === false }"
                  >
                    <button
                      type="button"
                      class="execution-summary"
                      @click="toggleExecutionDetails(item.id)"
                    >
                      <span class="execution-icon">
                        <LoadingOutlined v-if="item.streaming" spin />
                        <CodeOutlined v-else />
                      </span>
                      <span class="execution-summary-copy">
                        <strong>{{ getExecutionTitle(item) }}</strong>
                        <small>{{ getExecutionSubtitle(item) }}</small>
                      </span>
                      <DownOutlined
                        class="execution-chevron"
                        :class="{ expanded: item.executionExpanded }"
                      />
                    </button>

                    <div v-if="item.executionExpanded" class="execution-details">
                      <div
                        v-for="step in item.executionSteps"
                        :key="step.id"
                        class="execution-step"
                      >
                        <div class="execution-step-heading">
                          <span><FileTextOutlined /> {{ getExecutionStepTitle(step) }}</span>
                          <em :class="step.status">
                            {{ step.status === 'completed' ? '完成' : step.status === 'failed' ? '失败' : '执行中' }}
                          </em>
                        </div>
                        <div class="execution-tool-name">工具：{{ step.name }}</div>
                        <pre v-if="step.code"><code>{{ step.code }}</code></pre>
                        <div v-if="step.result" class="execution-result">{{ step.result }}</div>
                      </div>
                      <div
                        v-if="!item.executionSteps?.length && item.content"
                        class="execution-model-output"
                      >
                        <span>模型实时输出</span>
                        <pre>{{ item.content }}</pre>
                      </div>
                    </div>
                  </div>

                  <div
                    v-if="item.role === 'user' || (!item.streaming && item.content)"
                    class="message-body"
                  >
                    <template
                      v-for="(block, blockIndex) in parseMessageBlocks(item.content)"
                      :key="`${item.id}-${blockIndex}`"
                    >
                      <div v-if="block.type === 'text'" class="text-block">{{ block.content }}</div>
                      <div v-else class="code-block">
                        <div class="code-header">
                          <span>{{ block.language || 'code' }}</span>
                          <a-button type="text" size="small" @click="copyText(block.content)">
                            复制代码
                          </a-button>
                        </div>
                        <pre><code>{{ block.content }}</code></pre>
                      </div>
                    </template>
                  </div>
                  <div
                    v-if="item.role === 'assistant' && item.content && !item.streaming"
                    class="message-tools"
                  >
                    <a-button type="text" size="small" @click="copyText(item.content)">
                      复制回答
                    </a-button>
                  </div>
                </article>
              </div>

              <button v-if="showScrollToBottom" class="jump-btn" @click="jumpToLatest">
                回到底部
              </button>
            </div>

            <div v-if="selectedElementInfo" class="selected-element-card">
              <div class="selected-element-heading">
                <div>
                  <span class="selected-element-label">{{ isDesignPhase ? '已选择样式要素' : '已选择页面模块' }}</span>
                  <strong>{{ selectedElementDisplayName }}</strong>
                </div>
                <a-button type="text" size="small" @click="clearSelectedElement">清除</a-button>
              </div>
              <p v-if="selectedElementInfo.textContent">{{ selectedElementInfo.textContent }}</p>
              <code>{{ selectedElementInfo.selector }}</code>
            </div>

            <div class="composer">
              <a-textarea
                v-model:value="userInput"
                :placeholder="composerPlaceholder"
                :auto-size="{ minRows: 3, maxRows: 8 }"
                :disabled="isGenerating"
                @pressEnter="handlePressEnter"
              />
              <div class="composer-footer">
                <span>
                  {{
                    isGenerating
                      ? isDesignBusy
                        ? '正在设计样式，当前预览不可修改'
                        : '当前正在生成代码，请等待本轮完成'
                      : isDesignReview
                        ? 'Enter 发送修改要求；也可以先在预览中选择要素'
                        : 'Enter 发送，Shift + Enter 换行，可连续对话完善页面'
                  }}
                </span>
                <div class="composer-actions">
                  <a-button v-if="appData.initPrompt && !userInput" @click="useInitPrompt">
                    使用初始提示词
                  </a-button>
                  <a-button
                    type="primary"
                    :loading="isGenerating"
                    :disabled="!userInput.trim() || isGenerating"
                    @click="handleSend"
                  >
                    发送
                  </a-button>
                </div>
              </div>
            </div>
          </section>

          <aside class="preview-panel">
            <div class="panel-title-row preview-title-row">
              <div>
                <span class="panel-label">{{ workspacePanelTitle }}</span>
                <p v-if="!isDesignReview && showPreviewWorkspace" class="panel-description">
                  {{
                    isDesignBusy
                      ? '正在生成不可编辑的高保真桌面样式，请稍候。'
                      : hasGeneratedContent
                      ? '每轮完成后会自动刷新，也可以手动重新加载预览。'
                      : '样式确认完成后，这里会展示生成的网页效果。'
                  }}
                </p>
              </div>
              <div class="title-tools">
                <div v-if="!isDesignPhase" class="workspace-view-switch" aria-label="工作区视图切换">
                  <a-tooltip title="网页预览">
                    <button
                      type="button"
                      class="workspace-view-button"
                      :class="{ active: workspaceView === 'preview' }"
                      aria-label="切换到网页预览"
                      @click="setWorkspaceView('preview')"
                    >
                      <DesktopOutlined />
                    </button>
                  </a-tooltip>
                  <a-tooltip title="项目代码">
                    <button
                      type="button"
                      class="workspace-view-button"
                      :class="{ active: workspaceView === 'code' }"
                      aria-label="切换到项目代码"
                      @click="setWorkspaceView('code')"
                    >
                      <CodeOutlined />
                    </button>
                  </a-tooltip>
                </div>
                <a-select
                  v-if="isDesignPhase && designRevisionOptions.length"
                  :value="viewingDesignRevision"
                  class="design-version-select"
                  :disabled="isDesignBusy"
                  aria-label="选择样式版本"
                  @change="handleDesignRevisionChange"
                >
                  <a-select-option
                    v-for="revision in designRevisionOptions"
                    :key="revision.number"
                    :value="revision.number"
                  >
                    版本 {{ revision.number }}{{ revision.confirmed ? ' · 已确认' : '' }}
                  </a-select-option>
                </a-select>
                <a-button
                  v-if="isDesignReview"
                  class="confirm-design-btn"
                  type="primary"
                  :loading="confirmingDesign"
                  :disabled="isGenerating || !viewingDesignRevision"
                  @click="confirmCurrentDesign"
                >
                  <CheckCircleOutlined />
                  确认样式并生成代码
                </a-button>
                <a-tooltip v-if="!isDesignPhase && showPreviewWorkspace" title="预览后退">
                  <a-button :disabled="!activePreviewUrl || isDesignBusy" aria-label="预览后退" @click="navigatePreviewHistory(-1)">
                    <ArrowLeftOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip v-if="!isDesignPhase && showPreviewWorkspace" title="预览前进">
                  <a-button :disabled="!activePreviewUrl || isDesignBusy" aria-label="预览前进" @click="navigatePreviewHistory(1)">
                    <ArrowRightOutlined />
                  </a-button>
                </a-tooltip>
                <a-button
                  v-if="showPreviewWorkspace"
                  :type="isEditMode ? 'primary' : 'default'"
                  :disabled="!activePreviewUrl || isDesignBusy"
                  @click="toggleEditMode"
                >
                  <AimOutlined />
                  {{ isEditMode ? '退出选择' : isDesignPhase ? '选择要素' : '选择元素' }}
                </a-button>
                <a-button
                  v-if="showPreviewWorkspace"
                  :disabled="!activePreviewUrl || isDesignBusy"
                  @click="refreshPreview()"
                >
                  <ReloadOutlined />
                  刷新
                </a-button>
                <a-button
                  v-if="showPreviewWorkspace && activePreviewUrl"
                  type="link"
                  :href="activePreviewUrl"
                  target="_blank"
                >
                  新窗口打开
                </a-button>
                <a-button
                  v-if="!showPreviewWorkspace"
                  :loading="filesLoading"
                  :disabled="isGenerating"
                  @click="syncProjectFiles(true)"
                >
                  <SyncOutlined />
                  同步文件
                </a-button>
              </div>
            </div>

            <template v-if="showPreviewWorkspace">
            <div v-if="isDesignBusy" class="stitch-loading-canvas" aria-label="正在生成样式">
              <div class="stitch-loading-toolbar">
                <div class="stitch-loading-brand"><LoadingOutlined spin /> 样式设计</div>
                <span>{{ designState.stage === 'REVISING' ? '正在调整所选要素' : '正在构建设计画布' }}</span>
              </div>
              <div class="stitch-loading-body">
                <div class="loading-line loading-line-short"></div>
                <div class="loading-line loading-line-title"></div>
                <div class="loading-line loading-line-copy"></div>
                <div class="loading-actions"><i></i><i></i></div>
                <div class="loading-grid"><i></i><i></i><i></i></div>
              </div>
              <div class="stitch-loading-tip">样式生成期间仅可查看进度，完成后可选择要素继续修改</div>
            </div>
            <div v-else-if="activePreviewUrl" class="browser-frame">
              <div class="browser-bar">
                <div class="browser-dots">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div class="browser-address">
                  {{ isDesignPhase ? `样式预览 · 版本 ${viewingDesignRevision || '-'}` : activePreviewUrl }}
                </div>
              </div>
              <iframe
                ref="previewIframe"
                :src="activePreviewUrl"
                class="preview-iframe"
                @load="handlePreviewLoad"
              />
            </div>
            <div v-else class="preview-empty">
              <LoadingOutlined v-if="isGenerating" class="placeholder-icon spinning" />
              <FileTextOutlined v-else class="placeholder-icon" />
              <h3>{{ isGenerating ? '正在准备预览' : '等待样式方案' }}</h3>
              <p>
                {{
                  isGenerating
                    ? '本轮处理完成后会自动展示结果。'
                    : '先生成并确认样式方案，再开始开发真实网页。'
                }}
              </p>
            </div>
            </template>

            <div v-else class="code-workspace">
              <aside class="project-browser">
                <div class="project-panel-header">
                  <div>
                    <span class="panel-label">项目文件</span>
                    <p class="panel-description">{{ projectFileCount }} 个文件 · AI 完成后同步</p>
                  </div>
                </div>
                <div class="project-tree-wrap">
                  <div v-if="filesLoading && projectFiles.length === 0" class="file-state">正在读取项目目录...</div>
                  <div v-else-if="projectFiles.length === 0" class="file-state">
                    完成首轮生成后，这里会展示项目全部文件。
                  </div>
                  <ProjectFileTree
                    v-else
                    :nodes="projectFiles"
                    :selected-path="activeFilePath"
                    @select="selectProjectFile"
                  />
                </div>
              </aside>

              <section class="code-editor-panel">
              <div class="editor-tabbar">
                <div class="editor-tab" :class="{ empty: !activeFilePath }">
                  <FileTextOutlined />
                  <span>{{ activeFileName || '选择文件查看内容' }}</span>
                  <i v-if="fileDirty" title="存在未保存修改"></i>
                </div>
                <div class="editor-actions">
                  <a-button
                    type="text"
                    size="small"
                    :disabled="!activeFilePath || fileUndoStack.length === 0 || isGenerating || fileSaving"
                    @click="undoActiveFileEdit"
                  >
                    <UndoOutlined />
                    撤回
                  </a-button>
                  <a-button
                    type="text"
                    size="small"
                    :disabled="!activeFilePath || !fileDirty || isGenerating"
                    :loading="fileSaving"
                    @click="saveActiveFile(false)"
                  >
                    <SaveOutlined />
                    保存
                  </a-button>
                </div>
              </div>
              <div class="editor-statusbar top-statusbar">
                <span :class="{ syncing: isGenerating || fileSaving }">
                  {{ fileEditorStatus }}
                </span>
                <span>保存后刷新预览</span>
              </div>
              <textarea
                v-if="activeFilePath"
                :value="activeFileContent"
                class="code-editor"
                spellcheck="false"
                :disabled="activeFileLoading || isGenerating || fileSaving"
                aria-label="项目文件内容编辑器"
                @input="handleFileEditorInput"
              ></textarea>
              <div v-else class="editor-empty">
                <CodeOutlined />
                <span>从上方目录选择一个文本文件</span>
              </div>
              <div class="editor-statusbar">
                <span>{{ activeFilePath || '未打开文件' }}</span>
                <span v-if="activeFilePath">{{ formatFileSize(activeFileSize) }} · UTF-8</span>
              </div>
              </section>
            </div>
          </aside>
        </div>
      </a-spin>
    </div>

    <a-modal v-model:open="deployModalVisible" title="部署成功">
      <p>应用已成功部署</p>
      <p>访问地址：</p>
      <a-input :value="deployUrl" readonly />
      <template #footer>
        <a-button type="primary" :href="deployUrl" target="_blank" @click="handleDeployOk">
          打开应用
        </a-button>
      </template>
    </a-modal>

    <a-modal
      v-model:open="editNameModalVisible"
      title="编辑应用名称"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="editNameSubmitting"
      @ok="submitAppNameUpdate"
    >
      <div class="edit-app-name-form">
        <div class="edit-app-name-label">当前应用名称</div>
        <a-input
          v-model:value="editAppName"
          :maxlength="80"
          placeholder="请输入应用名称"
          @pressEnter="submitAppNameUpdate"
        />
        <div class="edit-app-name-tip">这里只允许修改当前应用名称，不会变更提示词和生成记录。</div>
      </div>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AimOutlined,
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CheckCircleOutlined,
  CodeOutlined,
  DeploymentUnitOutlined,
  DesktopOutlined,
  DownloadOutlined,
  DownOutlined,
  EditOutlined,
  FileTextOutlined,
  LoadingOutlined,
  ReloadOutlined,
  SaveOutlined,
  SyncOutlined,
  UndoOutlined,
} from '@ant-design/icons-vue'
import {
  confirmAppDesign,
  deployApp,
  generateAppDesign,
  getAppDesign,
  getAppVoById,
  listProjectFiles,
  listMyAppVoByPage,
  readProjectFile,
  reviseAppDesign,
  saveProjectFile,
  updateApp,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { buildApiUrl, buildDeployAppUrl, buildPreviewAppUrl } from '@/config/appConfig'
import { EventSourcePolyfill } from 'event-source-polyfill'
import dayjs from 'dayjs'
import { normalizeRouteId } from '@/utils/id'
import logo from '@/assets/logo.png'
import ProjectFileTree from '@/components/ProjectFileTree.vue'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  streaming?: boolean
  /** 本轮工具调用步骤；实时流默认折叠，用户可按需查看写入了哪个文件及完整代码。 */
  executionSteps?: ExecutionStep[]
  executionExpanded?: boolean
  generationSucceeded?: boolean
  /** design 表示 Stitch 非流式设计调用，code 表示现有代码生成 SSE。 */
  operation?: 'design' | 'code'
}

type ExecutionStep = {
  id: string
  name: string
  status: 'running' | 'completed' | 'failed'
  filePath?: string
  code?: string
  result?: string
}

type ToolStreamPayload = {
  type: 'ai_response' | 'tool_request' | 'tool_executed'
  id?: string
  name?: string
  arguments?: string
  result?: string
  data?: string
}

/** EventSource polyfill 在类型声明中只导出构造值，这里声明页面实际使用的最小接口。 */
type StreamEventSource = {
  close: () => void
  onmessage: ((event: MessageEvent) => void) | null
  onerror: ((event: Event) => void) | null
  addEventListener: (type: string, listener: (event: Event) => void) => void
}

type MessageBlock =
  | { type: 'text'; content: string }
  | { type: 'code'; language: string; content: string }

const route = useRoute()
const router = useRouter()
const appId = ref<string>(normalizeRouteId(route.query.id))

const appData = ref<API.AppVO>({})
const messages = ref<ChatMessage[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const pageLoading = ref(false)
const previewUrl = ref('')
/** Stitch 设计预览地址与代码预览地址分离，确认设计前不会误判为已生成项目。 */
const designPreviewUrl = ref('')
const designState = ref<API.DesignWorkflowVO>({ stage: 'EMPTY', revisions: [] })
/** 当前在样式预览区查看的版本；修改和确认都以该版本为基线，而不是强制使用最新版本。 */
const viewingDesignRevision = ref<number>()
const designRequestRunning = ref(false)
const confirmingDesign = ref(false)
const messagesContainer = ref<HTMLElement>()
const deployLoading = ref(false)
const downloadLoading = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')
/**
 * 应用名称编辑弹窗状态。
 * 这组状态只服务当前聊天页内的轻量编辑流程，不再跳转完整编辑页面。
 */
const editNameModalVisible = ref(false)
const editNameSubmitting = ref(false)
const editAppName = ref('')
/**
 * 应用切换器状态。
 * 这里缓存当前登录用户的全部应用，供聊天页顶部直接切换，不需要返回首页再选择。
 */
const appSwitcherOpen = ref(false)
const userAppsLoading = ref(false)
const userApps = ref<API.AppVO[]>([])
const streamStatus = ref('等待开始生成')
const isAtBottom = ref(true)
const streamingMessageId = ref<string>()

/** 预览 iframe 引用和可视化元素选择状态。 */
const previewIframe = ref<HTMLIFrameElement>()
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)

/**
 * 代码开发阶段的主工作区视图。
 * 预览和代码共用右侧大画布，避免常驻第三栏压缩网页与编辑器的可用空间。
 */
const workspaceView = ref<'preview' | 'code'>('preview')

/**
 * 项目文件编辑状态。
 * savedFileContent 是后端确认保存的版本，用于准确判断编辑器是否仍有未落盘修改。
 */
const projectFiles = ref<API.ProjectFileVO[]>([])
const filesLoading = ref(false)
const activeFilePath = ref('')
const activeFileContent = ref('')
const savedFileContent = ref('')
const activeFileSize = ref(0)
const activeFileModifiedTime = ref(0)
const activeFileLoading = ref(false)
const fileSaving = ref(false)
const lastFileSavedAt = ref('')
/** 当前文件按连续输入批次保存的撤回快照，只存在浏览器内存中，不会自动写盘。 */
const fileUndoStack = ref<string[]>([])

/** 可视化编辑器实例在页面生命周期内复用，避免 iframe 每次刷新重复注册外层回调。 */
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo) => {
    selectedElementInfo.value = elementInfo
    userInput.value = ''
  },
})

let currentEventSource: StreamEventSource | null = null
let loadingFileContent = false
let projectFilesSyncing = false
let fileEditHistoryTimer: number | undefined
let fileEditBurstActive = false

const hasAssistantContent = computed(() =>
  messages.value.some((item) => item.role === 'assistant' && item.content.trim()),
)

const latestUserMessage = computed(() => {
  return [...messages.value].reverse().find((item) => item.role === 'user')
})

const latestDemandText = computed(
  () => latestUserMessage.value?.content || appData.value.initPrompt || '',
)

const showScrollToBottom = computed(() => messages.value.length > 0 && !isAtBottom.value)

/** 当前目录树中的文件总数，目录节点不计入。 */
const projectFileCount = computed(() => {
  const countFiles = (nodes: API.ProjectFileVO[]): number =>
    nodes.reduce(
      (total, node) => total + (node.directory ? countFiles(node.children || []) : 1),
      0,
    )
  return countFiles(projectFiles.value)
})
const hasGeneratedContent = computed(() => Boolean(appData.value.hasGeneratedCode) || projectFileCount.value > 0)
const isDesignBusy = computed(() =>
  designRequestRunning.value || designState.value.stage === 'GENERATING' || designState.value.stage === 'REVISING',
)
const isDesignReview = computed(() =>
  !hasGeneratedContent.value && Number(designState.value.currentRevision || 0) > 0 && !designState.value.confirmed,
)
const isDesignPhase = computed(() => !hasGeneratedContent.value)
const activePreviewUrl = computed(() => (isDesignPhase.value ? designPreviewUrl.value : previewUrl.value))
/** 样式确认阶段固定显示预览；代码开发阶段由顶部视图按钮切换。 */
const showPreviewWorkspace = computed(() => isDesignPhase.value || workspaceView.value === 'preview')
/** 主画布标题跟随当前视图变化，让切换后的上下文始终明确。 */
const workspacePanelTitle = computed(() =>
  showPreviewWorkspace.value ? (isDesignPhase.value ? '样式预览' : '生成后的网页展示') : '项目代码',
)
/** 版本下拉框按新到旧排列，便于用户快速回到最近一次结果。 */
const designRevisionOptions = computed(() =>
  [...(designState.value.revisions || [])]
    .filter((revision) => typeof revision.number === 'number')
    .sort((left, right) => Number(right.number) - Number(left.number)),
)

const activeFileName = computed(() => activeFilePath.value.split('/').pop() || '')
const fileDirty = computed(() => activeFileContent.value !== savedFileContent.value)
const selectedElementDisplayName = computed(() => {
  if (!selectedElementInfo.value) return ''
  const element = selectedElementInfo.value
  return element.id
    ? `${element.tagName.toLowerCase()}#${element.id}`
    : element.className
      ? `${element.tagName.toLowerCase()}.${element.className.split(' ')[0]}`
      : element.tagName.toLowerCase()
})
const composerPlaceholder = computed(() =>
  selectedElementInfo.value
    ? isDesignPhase.value
      ? `描述要如何修改 ${selectedElementDisplayName.value}，AI 会围绕该要素调整`
      : `描述要如何修改 ${selectedElementDisplayName.value}，AI 只会围绕该模块调整`
    : isDesignReview.value
      ? '描述想调整的样式，也可以先在中间预览中选择要素'
      : '描述越详细，页面越具体，可以一步一步完善生成效果',
)
const fileEditorStatus = computed(() => {
  if (isGenerating.value) return 'AI 输出中，文件编辑已锁定'
  if (activeFileLoading.value) return '正在读取文件'
  if (fileSaving.value) return '正在保存并刷新预览'
  if (fileDirty.value) return '存在未保存修改'
  if (lastFileSavedAt.value) return `${lastFileSavedAt.value} 已保存`
  return activeFilePath.value ? '文件已同步' : '等待选择文件'
})

const createMessageId = () => `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

/**
 * 获取当前页面正在操作的应用 ID。
 * 应用 ID 统一按字符串透传，避免 Long 在浏览器侧被 Number 转换后发生精度丢失。
 *
 * @returns 当前可用的应用 ID；不存在时返回 undefined
 */
const getCurrentAppId = () => {
  return appId.value || undefined
}

const formatDateTime = (time?: string) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-')

const formatMessageTime = (time?: string) => {
  if (!time) {
    return dayjs().format('HH:mm')
  }
  const target = dayjs(time)
  return target.isSame(dayjs(), 'day') ? target.format('HH:mm') : target.format('MM-DD HH:mm')
}

const normalizeMessageRole = (messageType?: string): ChatMessage['role'] => {
  const normalizedType = String(messageType || '').toLowerCase()
  if (
    normalizedType.includes('user') ||
    normalizedType.includes('human') ||
    normalizedType.includes('question')
  ) {
    return 'user'
  }
  return 'assistant'
}

const buildPreviewUrl = (withTimestamp = false) => {
  if (!appId.value) {
    return ''
  }
  const codeGenType = appData.value.codeGenType || 'html'
  const baseUrl = buildPreviewAppUrl(`/api/static/${codeGenType}_${appId.value}/index.html`)
  if (!withTimestamp) {
    return baseUrl
  }
  const separator = baseUrl.includes('?') ? '&' : '?'
  return `${baseUrl}${separator}t=${Date.now()}`
}

const applyPreviewFromConversation = (withTimestamp = false) => {
  previewUrl.value = hasGeneratedContent.value ? buildPreviewUrl(withTimestamp) : ''
}

/**
 * 将后端返回的设计静态路径转换为当前前端站点的同源 iframe 地址。
 * 同源是可视化选中元素能够读取设计 DOM 的必要条件。
 */
const applyDesignState = (nextState: API.DesignWorkflowVO, withTimestamp = true) => {
  designState.value = {
    ...nextState,
    revisions: nextState.revisions || [],
  }
  viewingDesignRevision.value = nextState.currentRevision || undefined
  if (!nextState.previewPath) {
    designPreviewUrl.value = ''
    return
  }
  const baseUrl = buildPreviewAppUrl(nextState.previewPath)
  designPreviewUrl.value = withTimestamp
    ? `${baseUrl}${baseUrl.includes('?') ? '&' : '?'}t=${Date.now()}`
    : baseUrl
}

/** 获取当前应用已持久化的设计状态。 */
const fetchDesignData = async () => {
  const currentAppId = getCurrentAppId()
  if (!currentAppId) return false
  const res = await getAppDesign(currentAppId)
  if (res.data.code !== 0 || !res.data.data) {
    message.error('获取样式状态失败，' + res.data.message)
    return false
  }
  applyDesignState(res.data.data, false)
  return true
}

/**
 * 在目录树中按路径查找节点。
 *
 * @param nodes 当前递归节点集合
 * @param path 目标相对路径
 * @returns 命中的节点；未找到时返回 undefined
 */
const findProjectFile = (nodes: API.ProjectFileVO[], path: string): API.ProjectFileVO | undefined => {
  for (const node of nodes) {
    if (node.path === path) return node
    const matchedChild = findProjectFile(node.children || [], path)
    if (matchedChild) return matchedChild
  }
  return undefined
}

/**
 * 获取目录树中第一个可编辑文件，优先打开最能代表页面入口的文件。
 *
 * @param nodes 项目文件树
 * @returns 默认打开的文本文件节点
 */
const findDefaultProjectFile = (nodes: API.ProjectFileVO[]) => {
  const preferredPaths = ['index.html', 'src/App.vue', 'src/main.ts', 'src/main.js', 'style.css', 'styles.css']
  for (const path of preferredPaths) {
    const preferred = findProjectFile(nodes, path)
    if (preferred?.editable) return preferred
  }
  const queue = [...nodes]
  while (queue.length) {
    const node = queue.shift()!
    if (node.directory) queue.unshift(...(node.children || []))
    else if (node.editable) return node
  }
  return undefined
}

/**
 * 读取并打开目录树中的文本文件。
 * 如果当前文件存在未保存修改，会先完成保存再切换，避免自动同步覆盖用户输入。
 *
 * @param node 待打开文件节点
 */
const selectProjectFile = async (node: API.ProjectFileVO) => {
  const currentAppId = getCurrentAppId()
  if (!currentAppId || !node.path || node.directory) return
  if (isGenerating.value) {
    message.info('AI 输出期间文件编辑已锁定，请等待本轮完成')
    return
  }
  if (!node.editable) {
    message.info('该文件为二进制或超大文件，只在目录中展示，暂不支持在线编辑')
    return
  }
  if (fileDirty.value && activeFilePath.value && activeFilePath.value !== node.path) {
    message.warning('当前文件有未保存修改，请先保存或撤回后再切换文件')
    return
  }
  activeFileLoading.value = true
  try {
    const res = await readProjectFile(currentAppId, node.path)
    if (res.data.code !== 0 || !res.data.data) {
      message.error('读取文件失败，' + res.data.message)
      return
    }
    const fileData = res.data.data
    loadingFileContent = true
    activeFilePath.value = fileData.path || node.path
    activeFileContent.value = fileData.content || ''
    savedFileContent.value = fileData.content || ''
    activeFileSize.value = Number(fileData.size || 0)
    activeFileModifiedTime.value = Number(fileData.modifiedTime || 0)
    lastFileSavedAt.value = ''
    fileUndoStack.value = []
    fileEditBurstActive = false
    window.clearTimeout(fileEditHistoryTimer)
  } catch (error) {
    message.error('读取文件失败')
  } finally {
    loadingFileContent = false
    activeFileLoading.value = false
  }
}

/**
 * 同步项目目录树，并在 AI 生成或外部更新后刷新当前打开的文件内容。
 *
 * @param showFeedback 是否向用户显示手动刷新结果
 */
const syncProjectFiles = async (showFeedback = false) => {
  const currentAppId = getCurrentAppId()
  if (!currentAppId || projectFilesSyncing || (isGenerating.value && !showFeedback)) return
  projectFilesSyncing = true
  const showLoading = showFeedback || projectFiles.value.length === 0
  if (showLoading) filesLoading.value = true
  try {
    const res = await listProjectFiles(currentAppId)
    if (res.data.code !== 0 || !res.data.data) {
      if (showFeedback) message.warning(res.data.message || '项目文件暂未生成')
      return
    }
    projectFiles.value = res.data.data
    const currentNode = activeFilePath.value
      ? findProjectFile(projectFiles.value, activeFilePath.value)
      : undefined
    if (activeFilePath.value && !currentNode) {
      loadingFileContent = true
      activeFilePath.value = ''
      activeFileContent.value = ''
      savedFileContent.value = ''
      loadingFileContent = false
    }
    if (
      currentNode?.editable &&
      !fileDirty.value &&
      Number(currentNode.modifiedTime || 0) !== activeFileModifiedTime.value
    ) {
      await selectProjectFile(currentNode)
    } else if (!activeFilePath.value) {
      const defaultFile = findDefaultProjectFile(projectFiles.value)
      if (defaultFile) await selectProjectFile(defaultFile)
    }
    if (showFeedback) message.success('项目文件已同步')
  } catch (error) {
    if (showFeedback) message.error('刷新项目文件失败')
  } finally {
    projectFilesSyncing = false
    if (showLoading) filesLoading.value = false
  }
}

/**
 * 保存当前打开的文件，并立即重载中央预览。
 * 保存过程中若用户继续输入，会在本次写入结束后再次调度自动保存，保证最终内容不丢失。
 *
 * @param silent 是否隐藏成功提示；自动保存和切换文件时使用静默模式
 */
const saveActiveFile = async (silent = false) => {
  const currentAppId = getCurrentAppId()
  if (!currentAppId || !activeFilePath.value || !fileDirty.value) return
  if (isGenerating.value) {
    message.warning('AI 输出期间不能保存文件，请等待本轮完成')
    return
  }
  if (fileSaving.value) {
    return
  }
  const contentSnapshot = activeFileContent.value
  fileSaving.value = true
  try {
    const res = await saveProjectFile({
      appId: currentAppId,
      path: activeFilePath.value,
      content: contentSnapshot,
    })
    if (res.data.code !== 0 || !res.data.data) {
      message.error('保存文件失败，' + res.data.message)
      return
    }
    savedFileContent.value = contentSnapshot
    activeFileSize.value = Number(res.data.data.size || 0)
    activeFileModifiedTime.value = Number(res.data.data.modifiedTime || 0)
    lastFileSavedAt.value = dayjs().format('HH:mm:ss')
    await syncProjectFiles(false)
    refreshPreview(true)
    if (!silent) message.success('文件已保存，预览已刷新')
  } catch (error) {
    message.error('保存文件失败')
  } finally {
    fileSaving.value = false
  }
}

/**
 * 接收代码编辑器输入并按连续输入批次记录撤回快照。
 * 输入只修改浏览器内存，必须由用户点击保存才会写入后端和刷新预览。
 *
 * @param event 原生 textarea 输入事件
 */
const handleFileEditorInput = (event: Event) => {
  if (loadingFileContent || isGenerating.value || fileSaving.value) return
  const nextContent = (event.target as HTMLTextAreaElement).value
  if (nextContent === activeFileContent.value) return
  if (!fileEditBurstActive) {
    fileUndoStack.value.push(activeFileContent.value)
    if (fileUndoStack.value.length > 100) {
      fileUndoStack.value.shift()
    }
    fileEditBurstActive = true
  }
  activeFileContent.value = nextContent
  window.clearTimeout(fileEditHistoryTimer)
  fileEditHistoryTimer = window.setTimeout(() => {
    fileEditBurstActive = false
  }, 500)
}

/**
 * 撤回当前文件最近一批连续输入，不触发保存或预览刷新。
 * 用户撤回到已保存版本后，未保存状态会自动清除。
 */
const undoActiveFileEdit = () => {
  if (isGenerating.value || fileSaving.value || fileUndoStack.value.length === 0) return
  const previousContent = fileUndoStack.value.pop()
  if (previousContent === undefined) return
  window.clearTimeout(fileEditHistoryTimer)
  fileEditBurstActive = false
  activeFileContent.value = previousContent
}

/**
 * 格式化文件大小供编辑器状态栏展示。
 *
 * @param size 文件字节数
 * @returns 紧凑的人类可读文本
 */
const formatFileSize = (size: number) => {
  if (size < 1024) return `${size} B`
  return `${(size / 1024).toFixed(size < 10240 ? 1 : 0)} KB`
}

/**
 * iframe 加载完成后绑定可视化编辑器，并在源码保存重载后恢复选择模式。
 */
const handlePreviewLoad = () => {
  if (!previewIframe.value) return
  visualEditor.init(previewIframe.value)
  try {
    visualEditor.onIframeLoad()
  } catch (error) {
    isEditMode.value = false
    message.error(error instanceof Error ? error.message : '无法启用元素选择')
  }
}

/**
 * 切换代码开发阶段的右侧主工作区。
 * 离开预览时会关闭元素选择，防止隐藏 iframe 继续拦截鼠标事件；进入代码视图时同步一次目录状态。
 *
 * @param view 目标视图，preview 展示网页，code 展示目录和文件编辑器
 */
const setWorkspaceView = (view: 'preview' | 'code') => {
  if (workspaceView.value === view) return
  if (view === 'code') {
    visualEditor.disableEditMode()
    isEditMode.value = false
    selectedElementInfo.value = null
    if (!isGenerating.value) void syncProjectFiles(false)
  }
  workspaceView.value = view
}

/** 切换中央预览的元素选择模式。 */
const toggleEditMode = () => {
  if (!previewIframe.value) {
    message.warning('请等待预览页面加载完成')
    return
  }
  try {
    visualEditor.init(previewIframe.value)
    isEditMode.value = visualEditor.toggleEditMode()
    if (!isEditMode.value) selectedElementInfo.value = null
  } catch (error) {
    isEditMode.value = false
    message.error(error instanceof Error ? error.message : '无法启用元素选择')
  }
}

/** 清除选中模块但保留当前预览和聊天内容。 */
const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

/** 把当前 iframe 的选择消息交给可视化编辑器进行来源校验和解析。 */
const handlePreviewMessage = (event: MessageEvent) => {
  visualEditor.handleIframeMessage(event)
}

const parseMessageBlocks = (content: string): MessageBlock[] => {
  if (!content) return [{ type: 'text', content: '' }]
  const blocks: MessageBlock[] = []
  const regex = /```([\w-]*)\n?([\s\S]*?)```/g
  let lastIndex = 0
  let match: RegExpExecArray | null

  const pushTextBlock = (text: string) => {
    const cleanText = text.replace(/^\n+|\n+$/g, '')
    if (cleanText) {
      blocks.push({ type: 'text', content: cleanText })
    }
  }

  while ((match = regex.exec(content)) !== null) {
    pushTextBlock(content.slice(lastIndex, match.index))
    blocks.push({
      type: 'code',
      language: match[1] || 'code',
      content: match[2].replace(/\n$/, ''),
    })
    lastIndex = regex.lastIndex
  }

  pushTextBlock(content.slice(lastIndex))
  return blocks.length ? blocks : [{ type: 'text', content }]
}

const closeCurrentStream = () => {
  currentEventSource?.close()
  currentEventSource = null
}

/**
 * 解析 SSE 中的异常消息。
 * 后端异常可能以自定义事件或普通 message 事件返回，这里统一兼容两种结构，
 * 让前端能够稳定拿到可展示、可写入当前会话气泡的错误文本。
 *
 * @param rawData SSE 原始数据
 * @returns 可展示的异常文本；不是异常载荷时返回 undefined
 */
const parseStreamErrorMessage = (rawData: string) => {
  if (!rawData) {
    return undefined
  }
  try {
    const parsed = JSON.parse(rawData)
    if (parsed?.error) {
      return parsed?.message ? `发生异常：${parsed.message}` : '发生异常，请稍后重试。'
    }
  } catch (error) {
    return undefined
  }
  return undefined
}

const updateMessageContent = (messageId: string, updater: (current: string) => string) => {
  const index = messages.value.findIndex((item) => item.id === messageId)
  if (index < 0) return
  const target = messages.value[index]
  messages.value[index] = { ...target, content: updater(target.content) }
}

/** 切换 AI 执行过程详情，流式状态默认保持收起，避免大段源码挤占对话区域。 */
const toggleExecutionDetails = (messageId: string) => {
  const index = messages.value.findIndex((item) => item.id === messageId)
  if (index < 0) return
  const target = messages.value[index]
  messages.value[index] = { ...target, executionExpanded: !target.executionExpanded }
}

/** 根据流式消息状态生成 Codex 风格的执行摘要标题。 */
const getExecutionTitle = (item: ChatMessage) => {
  if (item.operation === 'design') {
    if (item.streaming) {
      const isRevision = item.executionSteps?.some((step) => step.name.includes('修改'))
      return isRevision ? '正在修改样式' : '正在设计样式'
    }
    if (item.generationSucceeded === false) return '样式设计已中断'
    return '样式设计已完成'
  }
  if (item.streaming) {
    const runningFile = [...(item.executionSteps || [])].reverse().find((step) => step.filePath)
    return runningFile?.filePath ? `正在编辑 ${runningFile.filePath}` : '正在编辑代码'
  }
  if (item.generationSucceeded === false) return '执行已中断'
  const changedFiles = new Set((item.executionSteps || []).map((step) => step.filePath).filter(Boolean))
  return changedFiles.size > 0 ? `已编辑 ${changedFiles.size} 个文件` : '执行详情'
}

/** 生成执行摘要的辅助说明，提示用户可以展开查看真实工具调用。 */
const getExecutionSubtitle = (item: ChatMessage) => {
  if (item.operation === 'design') {
    if (item.streaming) return '正在生成并导出设计 HTML，完成前样式不可修改'
    if (item.generationSucceeded === false) return '点击查看本轮样式设计调用'
    return '点击查看本轮调用的样式设计工具'
  }
  if (item.streaming) return 'AI 正在调用工具修改项目，点击查看实时详情'
  if (item.generationSucceeded === false) return '点击查看中断前的工具调用'
  return '点击查看调用的工具和写入代码'
}

/** 将文件工具步骤转换为便于阅读的标题。 */
const getExecutionStepTitle = (step: ExecutionStep) => {
  return step.filePath ? `编辑 ${step.filePath}` : step.name || '执行工具'
}

/**
 * 解析工具参数。参数来自模型，可能在工具请求阶段仍是不完整 JSON，因此解析失败时安全忽略。
 */
const parseToolArguments = (argumentsText?: string) => {
  if (!argumentsText) return {} as { relativeFilePath?: string; content?: string }
  try {
    return JSON.parse(argumentsText) as { relativeFilePath?: string; content?: string }
  } catch (error) {
    return {} as { relativeFilePath?: string; content?: string }
  }
}

/**
 * 兼容后端 SSE 外层 d 包装与工具流内层 JSON，返回可直接分发的文本或结构化事件。
 */
const parseStreamPayload = (rawData: string): string | ToolStreamPayload => {
  let payload: unknown = rawData
  try {
    const wrapper = JSON.parse(rawData)
    payload = wrapper?.d ?? wrapper
  } catch (error) {
    return rawData
  }
  if (typeof payload === 'string') {
    try {
      const structured = JSON.parse(payload)
      if (structured?.type) return structured as ToolStreamPayload
    } catch (error) {
      return payload
    }
  }
  if (payload && typeof payload === 'object' && 'type' in payload) {
    return payload as ToolStreamPayload
  }
  return typeof payload === 'string' ? payload : ''
}

/** 新增或更新一条工具执行步骤，确保同一个工具调用在列表中只出现一次。 */
const upsertExecutionStep = (messageId: string, payload: ToolStreamPayload) => {
  const index = messages.value.findIndex((item) => item.id === messageId)
  if (index < 0) return
  const target = messages.value[index]
  const argumentsData = parseToolArguments(payload.arguments)
  const stepId = payload.id || `${payload.name || 'tool'}-${target.executionSteps?.length || 0}`
  const nextStep: ExecutionStep = {
    id: stepId,
    name: payload.name || 'unknown',
    status: payload.type === 'tool_executed'
      ? payload.result?.startsWith('文件写入失败') ? 'failed' : 'completed'
      : 'running',
    filePath: argumentsData.relativeFilePath,
    code: argumentsData.content,
    result: payload.result,
  }
  const steps = [...(target.executionSteps || [])]
  const stepIndex = steps.findIndex((step) => step.id === stepId)
  if (stepIndex >= 0) {
    steps[stepIndex] = {
      ...steps[stepIndex],
      ...nextStep,
      filePath: nextStep.filePath || steps[stepIndex].filePath,
      code: nextStep.code || steps[stepIndex].code,
    }
  } else {
    steps.push(nextStep)
  }
  messages.value[index] = { ...target, executionSteps: steps }
}

const scrollToBottom = async (force = false) => {
  await nextTick()
  if (!messagesContainer.value) return
  if (force || isAtBottom.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    isAtBottom.value = true
  }
}

const jumpToLatest = () => {
  void scrollToBottom(true)
}

const handleMessagesScroll = () => {
  if (!messagesContainer.value) return
  const { scrollTop, clientHeight, scrollHeight } = messagesContainer.value
  isAtBottom.value = scrollHeight - (scrollTop + clientHeight) < 48
}

const finalizeStreamingMessage = (success: boolean) => {
  closeCurrentStream()
  if (streamingMessageId.value) {
    const index = messages.value.findIndex((item) => item.id === streamingMessageId.value)
    if (index >= 0) {
      const target = messages.value[index]
      messages.value[index] = {
        ...target,
        content: target.content || (success ? '本轮生成已完成。' : '生成中断，请重试。'),
        streaming: false,
        generationSucceeded: success,
      }
    }
  }
  isGenerating.value = false
  if (success) {
    appData.value = { ...appData.value, hasGeneratedCode: true }
    applyPreviewFromConversation(true)
    streamStatus.value = '生成完成'
    void syncProjectFiles(false).then(() => refreshPreview(true))
  } else {
    streamStatus.value = '生成中断'
  }
  streamingMessageId.value = undefined
  void scrollToBottom()
}

/**
 * 将历史记录恢复为严格的写入顺序。
 * 数据库时间精度不足时一问一答可能拥有相同 createTime，因此必须用雪花 ID 继续升序比较。
 */
const mapHistoryToMessages = (records: API.ChatHistory[] = []): ChatMessage[] => {
  return [...records]
    .sort((a, b) => {
      const timeDifference = dayjs(a.createTime).valueOf() - dayjs(b.createTime).valueOf()
      if (timeDifference !== 0) return timeDifference
      const leftId = String(a.id || '')
      const rightId = String(b.id || '')
      if (leftId.length !== rightId.length) return leftId.length - rightId.length
      return leftId.localeCompare(rightId)
    })
    .map((item, index) => ({
      id: String(item.id ?? `${index}_${item.createTime ?? ''}`),
      role: normalizeMessageRole(item.messageType),
      content: item.message || '',
      createdAt: formatMessageTime(item.createTime),
    }))
}

const fetchAppData = async () => {
  const currentAppId = getCurrentAppId()
  if (!currentAppId) {
    message.error('应用ID不存在')
    return false
  }
  const res = await getAppVoById({ id: currentAppId })
  if (res.data.code === 0 && res.data.data) {
    appData.value = res.data.data
    document.title = appData.value.appName || '应用生成'
    return true
  }
  message.error('获取应用信息失败，' + res.data.message)
  return false
}

const fetchChatHistory = async () => {
  const currentAppId = getCurrentAppId()
  if (!currentAppId) {
    message.error('应用ID不存在')
    return false
  }
  const res = await listAppChatHistory({
    appId: currentAppId,
    pageSize: 10,
  })
  if (res.data.code === 0 && res.data.data) {
    messages.value = mapHistoryToMessages(res.data.data.records)
    applyPreviewFromConversation()
    return true
  }
  message.error('获取聊天记录失败，' + res.data.message)
  return false
}

/**
 * 拉取当前用户的全部应用列表。
 * 后端单页最多返回 20 条，这里通过分页循环把当前用户全部应用收集到本地，
 * 供顶部应用切换器完整展示。
 */
const fetchUserApps = async () => {
  if (userAppsLoading.value) {
    return
  }
  userAppsLoading.value = true
  try {
    const pageSize = 20
    let pageNum = 1
    let totalRow = 0
    const collectedApps: API.AppVO[] = []
    do {
      const res = await listMyAppVoByPage({
        pageNum,
        pageSize,
      })
      if (res.data.code !== 0 || !res.data.data) {
        message.error('获取应用列表失败，' + res.data.message)
        break
      }
      const pageData = res.data.data
      collectedApps.push(...(pageData.records || []))
      totalRow = Number(pageData.totalRow || 0)
      pageNum += 1
    } while (collectedApps.length < totalRow)

    userApps.value = collectedApps.filter(
      (item, index, array) => array.findIndex((current) => current.id === item.id) === index,
    )
  } catch (error) {
    message.error('获取应用列表失败')
  } finally {
    userAppsLoading.value = false
  }
}

/**
 * 切换顶部应用选择器开关。
 * 打开前会先确保应用列表已经加载，避免首开时出现空白面板。
 */
const handleAppSwitcherOpenChange = async (nextOpen: boolean) => {
  appSwitcherOpen.value = nextOpen
  if (nextOpen && userApps.value.length === 0) {
    await fetchUserApps()
  }
}

/**
 * 切换到指定应用聊天页。
 * 如果目标应用就是当前应用，只关闭选择器，不重复刷新页面。
 *
 * @param targetAppId 目标应用 ID
 */
const switchApp = async (targetAppId?: string) => {
  appSwitcherOpen.value = false
  if (!targetAppId || targetAppId === appId.value) {
    return
  }
  await router.push({
    path: '/app/chat',
    query: {
      id: targetAppId,
    },
  })
}

/**
 * 在切换应用前重置当前聊天页状态。
 * 这样可以避免路由切换瞬间继续显示上一应用的消息、预览和流式状态。
 */
const resetPageState = () => {
  closeCurrentStream()
  visualEditor.disableEditMode()
  messages.value = []
  userInput.value = ''
  previewUrl.value = ''
  designPreviewUrl.value = ''
  designState.value = { stage: 'EMPTY', revisions: [] }
  designRequestRunning.value = false
  confirmingDesign.value = false
  isGenerating.value = false
  streamStatus.value = '等待开始生成'
  streamingMessageId.value = undefined
  isAtBottom.value = true
  isEditMode.value = false
  selectedElementInfo.value = null
  workspaceView.value = 'preview'
  projectFiles.value = []
  activeFilePath.value = ''
  activeFileContent.value = ''
  savedFileContent.value = ''
  activeFileSize.value = 0
  activeFileModifiedTime.value = 0
  lastFileSavedAt.value = ''
  fileUndoStack.value = []
  fileEditBurstActive = false
  window.clearTimeout(fileEditHistoryTimer)
}

const loadPageData = async () => {
  pageLoading.value = true
  let appLoaded = false
  let historyLoaded = false
  let designLoaded = false
  try {
    const [appResult, historyResult, designResult] = await Promise.allSettled([
      fetchAppData(),
      fetchChatHistory(),
      fetchDesignData(),
    ])
    appLoaded = appResult.status === 'fulfilled' && appResult.value
    historyLoaded = historyResult.status === 'fulfilled' && historyResult.value
    designLoaded = designResult.status === 'fulfilled' && designResult.value
    if (appResult.status === 'rejected') {
      message.error('获取应用信息失败')
    }
    if (historyResult.status === 'rejected') {
      message.error('获取聊天记录失败')
    }
    if (designResult.status === 'rejected') {
      message.error('获取样式状态失败')
    }
  } finally {
    pageLoading.value = false
  }

  if (!appLoaded) {
    return
  }

  await syncProjectFiles(false)

  if (projectFileCount.value > 0) {
    applyPreviewFromConversation()
  }

  if (historyLoaded && hasGeneratedContent.value) {
    applyPreviewFromConversation()
  }

  if (
    designLoaded &&
    projectFileCount.value === 0 &&
    designState.value.stage === 'EMPTY' &&
    appData.value.initPrompt?.trim()
  ) {
    await generateInitialDesign(appData.value.initPrompt.trim())
    return
  }

  if (messages.value.length === 0 && appData.value.initPrompt) {
    userInput.value = appData.value.initPrompt
  }
}

/**
 * 创建一条收起展示的 Stitch 执行消息。
 * 设计接口不返回 token 流，因此只展示工具状态，不把供应商中间文本逐字写进对话框。
 */
const beginDesignExecution = (toolName: string) => {
  const assistantMessageId = createMessageId()
  messages.value.push({
    id: assistantMessageId,
    role: 'assistant',
    content: '',
    createdAt: dayjs().format('HH:mm'),
    streaming: true,
    operation: 'design',
    executionExpanded: false,
    executionSteps: [{
      id: `${toolName}-${Date.now()}`,
      name: toolName,
      status: 'running',
    }],
  })
  isGenerating.value = true
  designRequestRunning.value = true
  void scrollToBottom(true)
  return assistantMessageId
}

/**
 * 完成 Stitch 执行消息并保留可展开的工具调用记录。
 *
 * @param messageId 设计执行消息 ID
 * @param success 是否成功
 * @param content 完成或失败摘要
 */
const finishDesignExecution = (messageId: string, success: boolean, content: string) => {
  const index = messages.value.findIndex((item) => item.id === messageId)
  if (index >= 0) {
    const target = messages.value[index]
    messages.value[index] = {
      ...target,
      content,
      streaming: false,
      generationSucceeded: success,
      executionSteps: (target.executionSteps || []).map((step) => ({
        ...step,
        status: success ? 'completed' : 'failed',
        result: content,
      })),
    }
  }
  isGenerating.value = false
  designRequestRunning.value = false
  void scrollToBottom()
}

/** 从 Axios 或普通异常中提取可展示消息。 */
const resolveRequestError = (error: unknown, fallback: string) => {
  const requestError = error as { response?: { data?: { message?: string } }; message?: string }
  return requestError?.response?.data?.message || requestError?.message || fallback
}

/**
 * 调用样式设计服务生成首版桌面样式。
 * 页面创建后会自动执行一次；失败时保留原提示词，用户可直接再次发送重试。
 */
const generateInitialDesign = async (prompt: string) => {
  const currentAppId = getCurrentAppId()
  const normalizedPrompt = prompt.trim()
  if (!currentAppId || !normalizedPrompt || isGenerating.value) return
  messages.value.push({
    id: createMessageId(),
    role: 'user',
    content: normalizedPrompt,
    createdAt: dayjs().format('HH:mm'),
  })
  userInput.value = ''
  designState.value = { ...designState.value, stage: 'GENERATING', stageText: '正在生成样式' }
  const assistantMessageId = beginDesignExecution('样式生成器')
  try {
    const res = await generateAppDesign({ appId: currentAppId, prompt: normalizedPrompt })
    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || '样式生成失败')
    }
    applyDesignState(res.data.data)
    finishDesignExecution(assistantMessageId, true, '首版样式方案已生成，请在右侧预览并确认，或选择页面要素后继续提出修改。')
    message.success('样式方案已生成')
  } catch (error) {
    const errorText = resolveRequestError(error, '样式生成失败')
    finishDesignExecution(assistantMessageId, false, `发生异常：${errorText}`)
    await fetchDesignData().catch(() => undefined)
    message.error(errorText)
  }
}

/**
 * 调用样式设计服务修改当前查看的版本。
 * 被选要素通过独立字段提交，后端会把定位信息追加到修改提示词中。
 */
const reviseCurrentDesign = async (prompt: string) => {
  const currentAppId = getCurrentAppId()
  const normalizedPrompt = prompt.trim()
  if (!currentAppId || !normalizedPrompt || isGenerating.value) return
  const selectedElement = selectedElementInfo.value
  const baseRevisionNumber = viewingDesignRevision.value || designState.value.currentRevision
  const visibleContent = selectedElement
    ? `${normalizedPrompt}\n\n已选择样式要素：${selectedElement.selector}`
    : normalizedPrompt
  messages.value.push({
    id: createMessageId(),
    role: 'user',
    content: visibleContent,
    createdAt: dayjs().format('HH:mm'),
  })
  userInput.value = ''
  selectedElementInfo.value = null
  visualEditor.disableEditMode()
  isEditMode.value = false
  designState.value = { ...designState.value, stage: 'REVISING', stageText: '正在修改样式' }
  const assistantMessageId = beginDesignExecution('样式修改器')
  try {
    const res = await reviseAppDesign({
      appId: currentAppId,
      prompt: normalizedPrompt,
      baseRevisionNumber,
      pagePath: selectedElement?.pagePath,
      tagName: selectedElement?.tagName,
      selector: selectedElement?.selector,
      elementId: selectedElement?.id,
      className: selectedElement?.className,
      textContent: selectedElement?.textContent,
    })
    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || '样式修改失败')
    }
    applyDesignState(res.data.data)
    finishDesignExecution(
      assistantMessageId,
      true,
      `样式方案已更新为第 ${res.data.data.currentRevision || ''} 版，请继续确认或选择要素修改。`,
    )
    message.success('样式已更新')
  } catch (error) {
    const errorText = resolveRequestError(error, '样式修改失败')
    finishDesignExecution(assistantMessageId, false, `发生异常：${errorText}`)
    await fetchDesignData().catch(() => undefined)
    message.error(errorText)
  }
}

/**
 * 确认当前设计并立即启动现有代码生成 SSE。
 * 服务端会校验用户当前查看的版本号，并把对应设计 HTML 追加到代码模型上下文。
 */
const confirmCurrentDesign = async () => {
  const currentAppId = getCurrentAppId()
  const revisionNumber = viewingDesignRevision.value
  if (!currentAppId || !revisionNumber || confirmingDesign.value || isGenerating.value) return
  confirmingDesign.value = true
  try {
    const res = await confirmAppDesign({ appId: currentAppId, revisionNumber })
    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || '确认样式失败')
    }
    applyDesignState(res.data.data, false)
    clearSelectedElement()
    const codePrompt = '样式方案已确认，请严格按照已确认的桌面样式生成完整、可运行且交互正常的网页代码。'
    messages.value.push({
      id: createMessageId(),
      role: 'user',
      content: codePrompt,
      createdAt: dayjs().format('HH:mm'),
    })
    message.success('样式已确认，开始生成网页代码')
    await startStreaming(codePrompt)
  } catch (error) {
    message.error(resolveRequestError(error, '确认样式失败'))
  } finally {
    confirmingDesign.value = false
  }
}

/**
 * 切换样式预览版本，并把该版本设置为后续修改和确认的基线。
 *
 * @param revision 用户从版本下拉框选择的设计版本
 */
const showDesignRevision = (revision: API.DesignRevisionVO) => {
  if (!revision.previewPath || !revision.number || isDesignBusy.value) return
  clearSelectedElement()
  viewingDesignRevision.value = revision.number
  designPreviewUrl.value = `${buildPreviewAppUrl(revision.previewPath)}?t=${Date.now()}`
}

/**
 * 响应版本下拉框变更，统一复用预览切换逻辑。
 *
 * @param revisionNumber 下拉框选中的版本号
 */
const handleDesignRevisionChange = (revisionNumber: number) => {
  const revision = designState.value.revisions?.find((item) => item.number === revisionNumber)
  if (revision) showDesignRevision(revision)
}

const startStreaming = async (content: string) => {
  const currentAppId = getCurrentAppId()
  if (!currentAppId) {
    message.error('应用ID不存在')
    return
  }

  const assistantMessageId = createMessageId()
  messages.value.push({
    id: assistantMessageId,
    role: 'assistant',
    content: '',
    createdAt: dayjs().format('HH:mm'),
    streaming: true,
    operation: 'code',
    executionSteps: [],
    executionExpanded: false,
  })
  streamingMessageId.value = assistantMessageId
  isGenerating.value = true
  streamStatus.value = 'AI 正在编辑代码'
  await scrollToBottom(true)

  try {
    const sseUrl = `${buildApiUrl('/app/chat/gen/code')}?appId=${currentAppId}&message=${encodeURIComponent(content)}`
    closeCurrentStream()
    currentEventSource = new EventSourcePolyfill(sseUrl, {
      withCredentials: true,
    }) as unknown as StreamEventSource
    let streamFailed = false

    currentEventSource.onmessage = (event: MessageEvent) => {
      const rawData = event.data
      const streamErrorMessage = parseStreamErrorMessage(rawData)
      if (streamErrorMessage) {
        streamFailed = true
        updateMessageContent(assistantMessageId, () => streamErrorMessage)
        finalizeStreamingMessage(false)
        message.error(streamErrorMessage)
        return
      }
      const payload = parseStreamPayload(rawData)
      if (!payload) return
      if (payload === '[DONE]') {
        finalizeStreamingMessage(true)
        return
      }
      if (typeof payload === 'object') {
        if (payload.type === 'ai_response' && payload.data) {
          updateMessageContent(assistantMessageId, (current) => current + payload.data)
        } else if (payload.type === 'tool_request' || payload.type === 'tool_executed') {
          upsertExecutionStep(assistantMessageId, payload)
        }
      } else {
        updateMessageContent(assistantMessageId, (current) => current + payload)
      }
      void scrollToBottom()
    }

    currentEventSource.addEventListener('done', () => {
      if (streamFailed) {
        return
      }
      finalizeStreamingMessage(true)
    })

    currentEventSource.addEventListener('business-error', (event: Event) => {
      const customEvent = event as MessageEvent
      const streamErrorMessage = parseStreamErrorMessage(customEvent.data) || '发生异常，请稍后重试。'
      streamFailed = true
      updateMessageContent(assistantMessageId, () => streamErrorMessage)
      finalizeStreamingMessage(false)
      message.error(streamErrorMessage)
    })

    currentEventSource.onerror = () => {
      if (!isGenerating.value) return
      streamFailed = true
      finalizeStreamingMessage(false)
      message.error('生成连接已中断，请重试')
    }
  } catch (error) {
    finalizeStreamingMessage(false)
    message.error('对话失败')
  }
}

const sendMessage = async (content: string) => {
  let normalizedContent = content.trim()
  if (!normalizedContent || isGenerating.value) return
  if (isDesignPhase.value && !designState.value.confirmed) {
    if (Number(designState.value.currentRevision || 0) > 0) {
      await reviseCurrentDesign(normalizedContent)
    } else {
      await generateInitialDesign(normalizedContent)
    }
    return
  }
  if (fileDirty.value) {
    message.warning('当前文件有未保存修改，请先保存或撤回后再发送 AI 请求')
    return
  }
  if (selectedElementInfo.value) {
    const element = selectedElementInfo.value
    normalizedContent += [
      '',
      '',
      '请只针对我在预览中选中的页面模块进行修改：',
      `- 页面路径：${element.pagePath}`,
      `- 元素标签：${element.tagName.toLowerCase()}`,
      `- CSS 选择器：${element.selector}`,
      element.id ? `- 元素 ID：${element.id}` : '',
      element.className ? `- 元素类名：${element.className}` : '',
      element.textContent ? `- 当前内容：${element.textContent}` : '',
      '请保留页面其他模块和既有功能。',
    ]
      .filter(Boolean)
      .join('\n')
    selectedElementInfo.value = null
    visualEditor.disableEditMode()
    isEditMode.value = false
  }
  messages.value.push({
    id: createMessageId(),
    role: 'user',
    content: normalizedContent,
    createdAt: dayjs().format('HH:mm'),
  })
  userInput.value = ''
  await scrollToBottom(true)
  await startStreaming(normalizedContent)
}

const handleSend = async () => {
  await sendMessage(userInput.value)
}

const handlePressEnter = (event: KeyboardEvent) => {
  if (event.shiftKey) return
  event.preventDefault()
  void handleSend()
}

const useInitPrompt = () => {
  userInput.value = appData.value.initPrompt || ''
}

const fillExamplePrompt = () => {
  userInput.value =
    '请生成一个更现代的后台管理页面，左侧是配置表单，右侧是状态和预览卡片，整体使用干净的蓝白色桌面布局。'
}

const copyText = async (content: string) => {
  if (!content) return
  try {
    await navigator.clipboard.writeText(content)
    message.success('复制成功')
  } catch (error) {
    message.error('复制失败')
  }
}

const refreshPreview = (withTimestamp = true) => {
  if (isDesignPhase.value) {
    if (!designState.value.previewPath) return
    const baseUrl = buildPreviewAppUrl(designState.value.previewPath)
    designPreviewUrl.value = withTimestamp
      ? `${baseUrl}${baseUrl.includes('?') ? '&' : '?'}t=${Date.now()}`
      : baseUrl
    return
  }
  if (!hasGeneratedContent.value) {
    return
  }
  previewUrl.value = buildPreviewUrl(withTimestamp)
}

/**
 * 控制中央 iframe 的浏览历史前进或后退。
 * 该操作只影响生成页面内部导航，不会离开当前工作台或改变聊天路由。
 *
 * @param delta -1 表示后退，1 表示前进
 */
const navigatePreviewHistory = (delta: -1 | 1) => {
  const previewWindow = previewIframe.value?.contentWindow
  if (!previewWindow) {
    message.info('预览页面尚未加载完成')
    return
  }
  if (delta < 0) {
    previewWindow.history.back()
    return
  }
  previewWindow.history.forward()
}

/**
 * 打开应用名称编辑弹窗。
 * 弹窗只允许修改当前应用名称，避免用户在聊天页误进入完整编辑流程。
 */
const openEditNameModal = () => {
  if (!getCurrentAppId()) {
    message.error('应用ID不存在')
    return
  }
  editAppName.value = appData.value.appName || ''
  editNameModalVisible.value = true
}

/**
 * 提交当前应用名称更新。
 * 成功后同步刷新页面标题和顶部应用名称，保持聊天上下文不丢失。
 */
const submitAppNameUpdate = async () => {
  const currentAppId = getCurrentAppId()
  if (!currentAppId) {
    message.error('应用ID不存在')
    return
  }
  const normalizedAppName = editAppName.value.trim()
  if (!normalizedAppName) {
    message.warning('应用名称不能为空')
    return
  }
  if (normalizedAppName === (appData.value.appName || '').trim()) {
    editNameModalVisible.value = false
    return
  }
  editNameSubmitting.value = true
  try {
    const res = await updateApp({
      id: currentAppId,
      appName: normalizedAppName,
    })
    if (res.data.code === 0 && res.data.data) {
      appData.value = {
        ...appData.value,
        appName: normalizedAppName,
      }
      userApps.value = userApps.value.map((item) =>
        item.id === currentAppId ? { ...item, appName: normalizedAppName } : item,
      )
      document.title = normalizedAppName || '应用生成'
      editNameModalVisible.value = false
      message.success('应用名称已更新')
    } else {
      message.error('更新应用名称失败，' + res.data.message)
    }
  } catch (error) {
    message.error('更新应用名称失败')
  } finally {
    editNameSubmitting.value = false
  }
}

const doDeploy = async () => {
  const currentAppId = getCurrentAppId()
  if (!currentAppId) {
    message.error('应用ID不存在')
    return
  }
  if (!hasGeneratedContent.value) {
    message.warning('请先完成至少一轮生成后再部署')
    return
  }
  if (isGenerating.value) {
    message.warning('AI 正在回答中，请等待本轮完成后再部署')
    return
  }
  deployLoading.value = true
  try {
    const res = await deployApp({ appId: currentAppId })
    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = buildDeployAppUrl(res.data.data)
      deployModalVisible.value = true
      appData.value = {
        ...appData.value,
        deployedTime: dayjs().toISOString(),
      }
      message.success('部署成功')
    } else {
      message.error('部署失败，' + res.data.message)
    }
  } catch (error) {
    message.error('部署失败')
  } finally {
    deployLoading.value = false
  }
}

/**
 * 下载当前应用的源码 ZIP，并在浏览器端使用应用 ID 生成稳定文件名。
 */
const downloadSourceCode = () => {
  const currentAppId = getCurrentAppId()
  if (!currentAppId) {
    message.error('应用 ID 不存在')
    return
  }
  if (!hasGeneratedContent.value) {
    message.warning('请先完成至少一轮生成')
    return
  }
  downloadLoading.value = true
  const anchor = document.createElement('a')
  anchor.href = buildApiUrl(`/app/download/${currentAppId}`)
  anchor.download = `app-${currentAppId}.zip`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  message.success('源码下载已开始')
  window.setTimeout(() => {
    downloadLoading.value = false
  }, 400)
}

const handleDeployOk = () => {
  deployModalVisible.value = false
}

let pageInitialized = false
window.addEventListener('message', handlePreviewMessage)
void fetchUserApps()

watch(
  () => route.query.id,
  (newAppId) => {
    const normalizedAppId =
      normalizeRouteId(newAppId) || new URLSearchParams(window.location.search).get('id') || ''
    if (!normalizedAppId || (pageInitialized && normalizedAppId === appId.value)) {
      return
    }
    if (pageInitialized) {
      resetPageState()
    }
    appId.value = normalizedAppId
    pageInitialized = true
    void loadPageData()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  closeCurrentStream()
  window.clearTimeout(fileEditHistoryTimer)
  visualEditor.disableEditMode()
  window.removeEventListener('message', handlePreviewMessage)
})
</script>

<style scoped>
#appChatPage {
  min-width: 1280px;
  height: calc(var(--app-viewport-height) - 58px);
  box-sizing: border-box;
  padding: 8px;
  overflow: hidden;
  background: #f7f9fb;
}

.page-surface {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  gap: 8px;
}

#appChatPage :deep(.ant-spin-nested-loading),
#appChatPage :deep(.ant-spin-container) {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.workspace-header {
  display: grid;
  grid-template-columns: minmax(300px, auto) minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}

.header-left {
  min-width: 0;
  display: flex;
  align-items: center;
}

.app-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px 5px 5px;
  border-radius: 12px;
  border: 1px solid #e8ebef;
  background: #fff;
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.05);
  color: #1d2a42;
  white-space: nowrap;
}

.app-chip-logo {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  object-fit: cover;
}

.app-chip-button {
  width: fit-content;
  min-width: 0;
  border: none;
  cursor: pointer;
  text-align: left;
}

.app-chip-content {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
}

.chip-label {
  font-size: 11px;
  color: #7d8ca3;
}

.app-chip-content strong {
  color: #1b2638;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-chip-arrow {
  color: #64748b;
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  justify-self: end;
  gap: 8px;
  flex-wrap: nowrap;
}

.deploy-btn {
  border-radius: 14px;
  background: #111827;
  border: none;
  color: #fff;
  box-shadow: 0 12px 28px rgba(17, 24, 39, 0.18);
}

.deploy-btn:hover,
.deploy-btn:focus {
  background: #222b3a !important;
  color: #fff !important;
}

.deploy-btn:disabled {
  background: linear-gradient(135deg, #b8cbe6 0%, #d6e3f3 100%) !important;
  color: #6d7f98 !important;
  box-shadow: none;
}

.workspace {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(310px, 0.7fr) minmax(590px, 1.45fr) minmax(310px, 0.75fr);
  gap: 8px;
  min-height: 0;
  height: 100%;
}

/* 样式确认阶段只保留对话与大画布，避免空置侧栏挤压预览。 */
.workspace.design-workspace {
  grid-template-columns: minmax(340px, 0.62fr) minmax(760px, 1.7fr);
}

.chat-panel,
.preview-panel,
.project-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(193, 205, 222, 0.65);
  box-shadow: 0 20px 46px rgba(18, 35, 64, 0.08);
  overflow: hidden;
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 14px 9px;
}

.panel-label {
  display: inline-block;
  font-size: 14px;
  font-weight: 800;
  color: #526177;
  letter-spacing: 0.04em;
}

.panel-description {
  margin: 4px 0 0;
  color: #8a96a8;
  font-size: 11px;
  line-height: 1.5;
}

.title-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.title-tools :deep(.ant-btn) {
  height: 32px;
  padding-inline: 10px;
}

.design-version-select {
  width: 132px;
}

.latest-prompt-card {
  margin: 0 14px 8px;
  padding: 12px 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(242, 247, 255, 0.96) 0%, rgba(249, 251, 255, 0.98) 100%);
  border: 1px solid #e3ebf7;
}

.latest-prompt-caption {
  font-size: 12px;
  font-weight: 700;
  color: #6f7e95;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.latest-prompt-text {
  margin-top: 5px;
  color: #283447;
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.messages {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 14px;
  background: linear-gradient(180deg, rgba(251, 252, 255, 0.8) 0%, rgba(255, 255, 255, 1) 100%);
  border-top: 1px solid #eff3f8;
}

.empty-state {
  max-width: 520px;
  margin: 20px auto 0;
  padding: 34px;
  text-align: center;
  border-radius: 28px;
  background: #fafcff;
  border: 1px solid #e5ebf4;
}

.empty-badge {
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  background: #eef4fb;
  color: #586170;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.empty-state h3 {
  margin: 16px 0 10px;
  font-size: 24px;
  color: #172033;
}

.empty-state p {
  margin: 0;
  color: #6b7280;
  line-height: 1.8;
}

.empty-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 22px;
  flex-wrap: wrap;
}

.message-row {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.message-row + .message-row {
  margin-top: 18px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 38px;
  height: 38px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #60718a 0%, #374151 100%);
}

.message-row.user .avatar {
  background: linear-gradient(135deg, #dbe4ef 0%, #c6d0dd 100%);
  color: #334155;
}

.bubble {
  max-width: min(92%, 760px);
  padding: 16px 18px 14px;
  border-radius: 22px;
  background: #fff;
  border: 1px solid #e7ebf2;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.message-row.user .bubble {
  background: linear-gradient(180deg, #f4f7fb 0%, #eef2f7 100%);
  border-color: #e3e8ef;
}

.message-meta {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 12px;
  color: #95a0b0;
}

.message-row.user .message-meta,
.message-row.user .message-body {
  color: #4b5563;
}

.execution-card {
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid #dfe7f2;
  border-radius: 16px;
  background: #f8fafc;
}

.execution-card.active {
  border-color: #bfd8f5;
  background: linear-gradient(135deg, #f2f8ff 0%, #f8fbff 100%);
}

.execution-card.failed {
  border-color: #f1c9c9;
  background: #fff8f8;
}

.execution-summary {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 12px 13px;
  border: 0;
  background: transparent;
  color: #253247;
  cursor: pointer;
  text-align: left;
}

.execution-icon {
  display: inline-flex;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: #1769bd;
  background: #e6f1fd;
}

.execution-summary-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.execution-summary-copy strong {
  font-size: 13px;
  font-weight: 700;
}

.execution-summary-copy small {
  overflow: hidden;
  color: #7c899a;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.execution-chevron {
  color: #8b97a8;
  transition: transform 0.2s ease;
}

.execution-chevron.expanded {
  transform: rotate(180deg);
}

.execution-details {
  padding: 0 12px 12px;
  border-top: 1px solid #e7edf5;
}

.execution-step {
  margin-top: 10px;
  overflow: hidden;
  border: 1px solid #e4eaf2;
  border-radius: 12px;
  background: #fff;
}

.execution-step-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 11px;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.execution-step-heading em {
  color: #217a4d;
  font-size: 11px;
  font-style: normal;
}

.execution-step-heading em.running {
  color: #1769bd;
}

.execution-step-heading em.failed {
  color: #c43f3f;
}

.execution-tool-name,
.execution-result {
  padding: 0 11px 9px;
  color: #7a8798;
  font-size: 11px;
}

.execution-step pre,
.execution-model-output pre {
  max-height: 320px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-top: 1px solid #edf1f5;
  background: #111827;
  color: #e2e8f0;
  font-family: 'Cascadia Code', 'Consolas', monospace;
  font-size: 11px;
  line-height: 1.65;
  white-space: pre;
}

.execution-result {
  padding-top: 9px;
  border-top: 1px solid #edf1f5;
  color: #34725a;
}

.execution-model-output {
  margin-top: 10px;
  overflow: hidden;
  border-radius: 12px;
}

.execution-model-output > span {
  display: block;
  padding: 8px 10px;
  background: #eef3f8;
  color: #66758a;
  font-size: 11px;
}

.message-body {
  margin-top: 12px;
  line-height: 1.8;
  color: #2a3447;
}

.text-block {
  white-space: pre-wrap;
  word-break: break-word;
}

.text-block + .code-block,
.code-block + .text-block,
.code-block + .code-block {
  margin-top: 14px;
}

.code-block {
  overflow: hidden;
  border-radius: 18px;
  background: #111827;
}

.code-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  color: rgba(255, 255, 255, 0.72);
  background: rgba(255, 255, 255, 0.08);
  font-size: 12px;
}

.code-header :deep(.ant-btn) {
  color: rgba(255, 255, 255, 0.82);
}

.code-block pre {
  margin: 0;
  padding: 14px 16px 16px;
  overflow-x: auto;
  color: #e2e8f0;
  font-size: 13px;
  line-height: 1.7;
  font-family: 'Cascadia Code', 'Consolas', monospace;
}

.message-tools {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.stream-cursor {
  display: inline-block;
  width: 8px;
  height: 18px;
  margin-left: 4px;
  vertical-align: middle;
  border-radius: 99px;
  background: #0d6dd8;
  animation: blink 1s ease-in-out infinite;
}

.jump-btn {
  position: sticky;
  display: block;
  bottom: 8px;
  z-index: 3;
  width: fit-content;
  margin: 14px 18px 0 auto;
  border: none;
  border-radius: 999px;
  padding: 10px 16px;
  color: #fff;
  background: #111827;
  cursor: pointer;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.18);
}

.composer {
  padding: 10px 14px 12px;
  border-top: 1px solid #eef2f7;
  background: rgba(255, 255, 255, 0.98);
}

.composer :deep(.ant-input) {
  border-radius: 12px;
  padding: 10px 12px;
  resize: none;
  border-color: #e5e7eb;
  background: #f8fafc;
  box-shadow: none;
}

.composer-footer {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-top: 7px;
}

.composer-footer span {
  color: #9aa3af;
  font-size: 11px;
}

.composer-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.app-switcher-panel {
  display: flex;
  min-width: 320px;
  max-width: 360px;
  max-height: 360px;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
}

.app-switcher-title {
  color: #334155;
  font-size: 14px;
  font-weight: 800;
}

.app-switcher-state {
  color: #8a96a8;
  font-size: 13px;
  line-height: 1.7;
}

.app-switcher-item {
  display: flex;
  width: 100%;
  align-items: center;
  padding: 12px 14px;
  border: 1px solid #e5edf7;
  border-radius: 18px;
  background: #f8fbff;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.app-switcher-item:hover {
  border-color: #b9d3f2;
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(44, 90, 160, 0.08);
}

.app-switcher-item.active {
  border-color: #80b5ef;
  background: linear-gradient(135deg, #eef6ff 0%, #f8fbff 100%);
}

.app-switcher-item-content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.app-switcher-item-content strong {
  color: #1f2937;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-switcher-item-content span {
  color: #8a96a8;
  font-size: 12px;
}

.edit-app-name-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.edit-app-name-label {
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.edit-app-name-tip {
  color: #8a96a8;
  font-size: 12px;
  line-height: 1.7;
}

.confirm-design-btn {
  border-radius: 10px;
  background: #1f5ed7;
  box-shadow: 0 10px 22px rgba(31, 94, 215, 0.18);
}

.stitch-loading-canvas {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  margin: 4px 14px 14px;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #dfe5ef;
  border-radius: 14px;
  background: #f8fafc;
}

.stitch-loading-toolbar {
  display: flex;
  height: 44px;
  flex: 0 0 44px;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid #e8ecf2;
  background: rgba(255, 255, 255, 0.92);
  color: #7a8798;
  font-size: 12px;
}

.stitch-loading-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #182235;
  font-size: 15px;
  font-weight: 800;
}

.stitch-loading-body {
  position: relative;
  flex: 1;
  min-height: 0;
  padding: 78px 9% 58px;
  background:
    radial-gradient(circle at 78% 24%, rgba(86, 115, 230, 0.12), transparent 30%),
    linear-gradient(140deg, #ffffff 0%, #f4f7fc 100%);
}

.loading-line,
.loading-actions i,
.loading-grid i {
  display: block;
  overflow: hidden;
  border-radius: 12px;
  background: linear-gradient(90deg, #e7ebf2 20%, #f4f6fa 45%, #e7ebf2 70%);
  background-size: 260% 100%;
  animation: stitch-shimmer 1.5s ease-in-out infinite;
}

.loading-line-short {
  width: 18%;
  height: 14px;
}

.loading-line-title {
  width: 58%;
  height: 52px;
  margin-top: 24px;
}

.loading-line-copy {
  width: 46%;
  height: 18px;
  margin-top: 22px;
}

.loading-actions {
  display: flex;
  gap: 12px;
  margin-top: 30px;
}

.loading-actions i {
  width: 128px;
  height: 44px;
}

.loading-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 70px;
}

.loading-grid i {
  height: 170px;
}

.stitch-loading-tip {
  padding: 13px 18px;
  border-top: 1px solid #e6eaf0;
  background: #fff;
  color: #7e8a9d;
  font-size: 12px;
  text-align: center;
}

@keyframes stitch-shimmer {
  0% {
    background-position: 110% 0;
  }
  100% {
    background-position: -110% 0;
  }
}

.browser-frame,
.preview-empty {
  margin: 4px 14px 14px;
  flex: 1;
  min-height: 0;
  border-radius: 14px;
  overflow: hidden;
}

.browser-frame {
  display: flex;
  flex-direction: column;
  border: 1px solid #e4e8ef;
  background: #fff;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.browser-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #edf2f7;
}

.browser-dots {
  display: flex;
  gap: 7px;
}

.browser-dots span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #cbd8ec;
}

.browser-dots span:nth-child(1) {
  background: #fb7185;
}

.browser-dots span:nth-child(2) {
  background: #fbbf24;
}

.browser-dots span:nth-child(3) {
  background: #34d399;
}

.browser-address {
  flex: 1;
  min-width: 0;
  padding: 6px 12px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #e5e7eb;
  color: #7b8798;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-iframe {
  flex: 1;
  min-height: 0;
  height: 100%;
  border: none;
  background: #fff;
}

.preview-empty {
  border: 1px dashed #d7dde6;
  background: linear-gradient(180deg, #fafcff 0%, #f7f9fc 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 32px;
}

.placeholder-icon {
  font-size: 48px;
  color: #94a3b8;
}

.preview-empty h3 {
  margin: 18px 0 10px;
  font-size: 24px;
  color: #172033;
}

.preview-empty p {
  max-width: 340px;
  margin: 0;
  color: #6b7280;
  line-height: 1.8;
}

.spinning {
  animation: spin 1.2s linear infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.25;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.selected-element-card {
  margin: 0 18px 10px;
  padding: 12px 14px;
  border: 1px solid #b8d5ff;
  border-radius: 14px;
  background: #f4f8ff;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.08);
}

.selected-element-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.selected-element-heading > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.selected-element-label {
  color: #7182a0;
  font-size: 11px;
}

.selected-element-heading strong {
  overflow: hidden;
  color: #1f5faa;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-element-card p {
  display: -webkit-box;
  margin: 8px 0 6px;
  overflow: hidden;
  color: #536176;
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.selected-element-card code {
  display: block;
  overflow: hidden;
  color: #7d8ca3;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-panel {
  min-width: 0;
}

.project-panel-header {
  display: flex;
  min-height: 66px;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 14px 10px;
  border-bottom: 1px solid #edf0f4;
}

.project-panel-header .panel-description {
  margin-top: 3px;
  font-size: 11px;
}

.design-panel-header {
  min-height: 78px;
  align-items: center;
}

.design-version-panel {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding: 18px;
  background: linear-gradient(180deg, #fbfcff 0%, #f5f7fb 100%);
}

.design-side-loading {
  display: flex;
  min-height: 340px;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border: 1px dashed #cdd7e6;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  color: #718096;
  text-align: center;
}

.design-side-loading :deep(.anticon) {
  color: #3164d8;
  font-size: 30px;
}

.design-side-loading strong {
  color: #26344b;
  font-size: 14px;
}

.design-side-loading span {
  max-width: 230px;
  font-size: 12px;
  line-height: 1.7;
}

.design-current-card {
  padding: 20px;
  border: 1px solid #dce5f2;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 26px rgba(25, 49, 86, 0.06);
}

.design-current-card > span {
  color: #8290a4;
  font-size: 11px;
  letter-spacing: 0.08em;
}

.design-current-card strong {
  display: block;
  margin-top: 7px;
  color: #1c2a40;
  font-size: 22px;
}

.design-current-card p {
  margin: 12px 0 0;
  color: #69778d;
  font-size: 12px;
  line-height: 1.75;
}

.design-version-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.design-version-title {
  margin-bottom: 2px;
  color: #526177;
  font-size: 12px;
  font-weight: 800;
}

.design-version-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding: 11px 12px;
  border: 1px solid #e1e7f0;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  color: #536176;
  cursor: default;
  font-size: 12px;
}

.design-version-item.active {
  border-color: #9bbced;
  background: #eef5ff;
  color: #2457ad;
}

.design-version-item:disabled {
  opacity: 0.62;
}

.design-version-item small {
  color: #8b98aa;
  font-size: 10px;
}

.project-tree-wrap {
  flex: 0 0 42%;
  min-height: 150px;
  overflow: auto;
  padding: 6px 0;
  border-bottom: 1px solid #e5e7eb;
  background: #fbfcfe;
}

.file-state {
  padding: 26px 20px;
  color: #8b96a8;
  font-size: 12px;
  line-height: 1.7;
  text-align: center;
}

.code-editor-panel {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  background: #fff;
}

.editor-tabbar {
  display: flex;
  min-height: 39px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 8px 0 12px;
  border-bottom: 1px solid #edf0f4;
}

.editor-tab {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
  color: #2f3b4c;
  font-size: 12px;
}

.editor-tab.empty {
  color: #9aa4b3;
}

.editor-tab span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-tab i {
  width: 7px;
  height: 7px;
  flex: 0 0 7px;
  border-radius: 50%;
  background: #f59e0b;
}

.editor-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
}

.editor-actions :deep(.ant-btn) {
  padding-inline: 7px;
  font-size: 12px;
}

.top-statusbar {
  min-height: 26px;
  border-bottom: 1px solid #edf0f4;
  background: #f8fafc;
  color: #6e7c90;
}

.top-statusbar span:last-child::before {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 5px;
  border-radius: 50%;
  background: #22c55e;
  content: '';
}

.top-statusbar .syncing {
  color: #2563eb;
}

.code-editor {
  width: 100%;
  min-height: 0;
  flex: 1;
  box-sizing: border-box;
  padding: 14px 12px 30px 42px;
  resize: none;
  border: 0;
  outline: none;
  background:
    linear-gradient(90deg, #f6f8fb 0, #f6f8fb 32px, #e7ebf0 32px, #e7ebf0 33px, #fff 33px);
  color: #263245;
  font-family: 'Cascadia Code', Consolas, 'Courier New', monospace;
  font-size: 11px;
  line-height: 1.65;
  tab-size: 2;
  white-space: pre;
}

.code-editor:disabled {
  color: #9aa4b3;
}

.editor-empty {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #a0a8b5;
  font-size: 12px;
}

.editor-empty :deep(.anticon) {
  font-size: 28px;
}

.editor-statusbar {
  display: flex;
  min-height: 24px;
  box-sizing: border-box;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 10px;
  border-top: 1px solid #edf0f4;
  color: #8a96a8;
  font-size: 10px;
}

.editor-statusbar span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/*
 * NoCode 风格桌面工作台：左侧保留完整对话上下文，右侧使用一块连续的大画布。
 * 代码与预览复用画布，不再通过第三栏压缩页面内容。
 */
#appChatPage {
  min-width: 1366px;
  padding: 10px 22px 20px;
  background: #fff;
}

.page-surface {
  gap: 12px;
}

.workspace-header {
  min-height: 52px;
  grid-template-columns: minmax(390px, 0.72fr) minmax(0, 1.28fr);
}

.workspace,
.workspace.design-workspace {
  grid-template-columns: minmax(420px, 0.72fr) minmax(760px, 1.28fr);
  gap: 24px;
}

.chat-panel,
.preview-panel {
  border: 0;
  border-radius: 0;
  background: #fff;
  box-shadow: none;
}

.chat-panel {
  padding-right: 2px;
}

.panel-title-row {
  padding: 10px 8px 14px;
}

.panel-label {
  color: #172033;
  font-size: 16px;
  letter-spacing: 0;
}

.panel-description {
  font-size: 12px;
}

.latest-prompt-card {
  margin: 0 8px 12px;
  padding: 16px 18px;
  border-radius: 18px;
  border-color: #edf0f3;
  background: #f7f8fa;
}

.latest-prompt-text {
  font-size: 15px;
  line-height: 1.7;
}

.messages {
  padding: 20px 8px 24px;
  border-top: 0;
  background: #fff;
}

.message-row + .message-row {
  margin-top: 26px;
}

.message-row.assistant .bubble {
  padding: 4px 4px 8px;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.message-row.user .bubble {
  max-width: min(78%, 620px);
  border: 0;
  border-radius: 18px;
  background: #f4f5f7;
  box-shadow: none;
}

.message-body {
  font-size: 15px;
  line-height: 1.85;
}

.composer {
  margin: 0 8px;
  padding: 12px 14px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 12px 34px rgba(15, 23, 42, 0.07);
}

.composer :deep(.ant-input) {
  min-height: 78px;
  padding: 6px 8px;
  border: 0;
  background: #fff;
}

.preview-title-row {
  min-height: 58px;
  box-sizing: border-box;
  align-items: flex-start;
}

.title-tools {
  justify-content: flex-end;
}

.workspace-view-switch {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  padding: 3px;
  border-radius: 11px;
  background: #f3f4f6;
}

.workspace-view-button {
  display: inline-flex;
  width: 34px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
}

.workspace-view-button:hover {
  color: #111827;
}

.workspace-view-button.active {
  background: #fff;
  color: #111827;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.1);
}

.browser-frame,
.preview-empty,
.stitch-loading-canvas {
  margin: 4px 8px 0;
  border-radius: 22px;
}

.browser-bar {
  min-height: 48px;
  box-sizing: border-box;
  padding: 9px 16px;
}

.code-workspace {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  flex: 1;
  min-height: 0;
  margin: 4px 8px 0;
  overflow: hidden;
  border: 1px solid #e3e7ed;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 14px 38px rgba(15, 23, 42, 0.06);
}

.project-browser {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid #e5e7eb;
  background: #f8fafc;
}

.project-panel-header {
  min-height: 72px;
  box-sizing: border-box;
  padding: 16px 18px 12px;
}

.project-tree-wrap {
  min-height: 0;
  flex: 1;
  border-bottom: 0;
}

.code-editor-panel {
  min-width: 0;
}

.editor-tabbar {
  min-height: 48px;
  padding-inline: 16px 12px;
}

.editor-tab,
.editor-actions :deep(.ant-btn) {
  font-size: 13px;
}

.code-editor {
  padding: 18px 16px 38px 54px;
  background:
    linear-gradient(90deg, #f6f8fb 0, #f6f8fb 42px, #e7ebf0 42px, #e7ebf0 43px, #fff 43px);
  font-size: 13px;
  line-height: 1.75;
}

</style>
