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
          <a-button :loading="downloadLoading" :disabled="!hasAssistantContent" @click="downloadSourceCode">
            <DownloadOutlined />
            下载源码
          </a-button>
          <a-button @click="openEditNameModal">
            <EditOutlined />
            编辑信息
          </a-button>
          <a-button @click="router.back()">
            <ArrowLeftOutlined />
            返回
          </a-button>
          <a-button
            class="deploy-btn"
            type="primary"
            :loading="deployLoading"
            :disabled="isGenerating || !hasAssistantContent"
            @click="doDeploy"
          >
            <DeploymentUnitOutlined />
            部署应用
          </a-button>
        </div>
      </div>

      <a-spin :spinning="pageLoading" tip="正在加载应用数据">
        <div class="workspace">
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
                  <div class="message-body" :class="{ streaming: item.streaming }">
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
                    <span v-if="item.streaming" class="stream-cursor"></span>
                  </div>
                  <div v-if="item.role === 'assistant' && item.content" class="message-tools">
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

            <div class="composer">
              <a-textarea
                v-model:value="userInput"
                placeholder="描述越详细，页面越具体，可以一步一步完善生成效果"
                :auto-size="{ minRows: 3, maxRows: 8 }"
                :disabled="isGenerating"
                @pressEnter="handlePressEnter"
              />
              <div class="composer-footer">
                <span>
                  {{
                    isGenerating
                      ? '当前正在生成，请等待本轮完成'
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
                <span class="panel-label">生成后的网页展示</span>
                <p class="panel-description">
                  {{
                    hasAssistantContent
                      ? '每轮完成后会自动刷新，也可以手动重新加载预览。'
                      : '左侧完成一轮生成后，这里会展示网页效果。'
                  }}
                </p>
              </div>
              <div class="title-tools">
                <a-button :disabled="!hasAssistantContent" @click="refreshPreview()">
                  <ReloadOutlined />
                  刷新
                </a-button>
                <a-button v-if="previewUrl" @click="copyText(previewUrl)">
                  <LinkOutlined />
                  复制链接
                </a-button>
                <a-button v-if="previewUrl" type="link" :href="previewUrl" target="_blank">
                  新窗口打开
                </a-button>
              </div>
            </div>

            <div v-if="previewUrl" class="browser-frame">
              <div class="browser-bar">
                <div class="browser-dots">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div class="browser-address">{{ previewUrl }}</div>
              </div>
              <iframe :src="previewUrl" class="preview-iframe" />
            </div>
            <div v-else class="preview-empty">
              <LoadingOutlined v-if="isGenerating" class="placeholder-icon spinning" />
              <FileTextOutlined v-else class="placeholder-icon" />
              <h3>{{ isGenerating ? '正在准备预览' : '等待生成结果' }}</h3>
              <p>
                {{
                  isGenerating
                    ? '本轮输出完成后会自动刷新右侧网页。'
                    : '先在左侧发送需求，完成后这里会显示生成的网站页面。'
                }}
              </p>
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
        <a-button @click="copyText(deployUrl)">复制链接</a-button>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  DeploymentUnitOutlined,
  DownloadOutlined,
  DownOutlined,
  EditOutlined,
  FileTextOutlined,
  LinkOutlined,
  LoadingOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import {
  deployApp,
  getAppVoById,
  listMyAppVoByPage,
  updateApp,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { buildApiUrl, buildDeployAppUrl, buildPreviewAppUrl } from '@/config/appConfig'
import { EventSourcePolyfill } from 'event-source-polyfill'
import dayjs from 'dayjs'
import { normalizeRouteId } from '@/utils/id'
import logo from '@/assets/logo.png'

type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  streaming?: boolean
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

let currentEventSource: EventSourcePolyfill | null = null

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
  previewUrl.value = hasAssistantContent.value ? buildPreviewUrl(withTimestamp) : ''
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
      }
    }
  }
  if (success) {
    applyPreviewFromConversation(true)
    streamStatus.value = '生成完成'
  } else {
    streamStatus.value = '生成中断'
  }
  isGenerating.value = false
  streamingMessageId.value = undefined
  void scrollToBottom()
}

const mapHistoryToMessages = (records: API.ChatHistory[] = []): ChatMessage[] => {
  return [...records]
    .sort((a, b) => dayjs(a.createTime).valueOf() - dayjs(b.createTime).valueOf())
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
  messages.value = []
  userInput.value = ''
  previewUrl.value = ''
  isGenerating.value = false
  streamStatus.value = '等待开始生成'
  streamingMessageId.value = undefined
  isAtBottom.value = true
}

const loadPageData = async () => {
  pageLoading.value = true
  let appLoaded = false
  let historyLoaded = false
  try {
    const [appResult, historyResult] = await Promise.allSettled([
      fetchAppData(),
      fetchChatHistory(),
    ])
    appLoaded = appResult.status === 'fulfilled' && appResult.value
    historyLoaded = historyResult.status === 'fulfilled' && historyResult.value
    if (appResult.status === 'rejected') {
      message.error('获取应用信息失败')
    }
    if (historyResult.status === 'rejected') {
      message.error('获取聊天记录失败')
    }
  } finally {
    pageLoading.value = false
  }

  if (!appLoaded) {
    return
  }

  if (historyLoaded && hasAssistantContent.value) {
    applyPreviewFromConversation()
  }

  if (historyLoaded && messages.value.length === 0 && appData.value.initPrompt?.trim()) {
    await sendMessage(appData.value.initPrompt.trim())
    return
  }

  if (messages.value.length === 0 && appData.value.initPrompt) {
    userInput.value = appData.value.initPrompt
  }
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
  })
  streamingMessageId.value = assistantMessageId
  isGenerating.value = true
  streamStatus.value = 'AI 正在输出内容'
  await scrollToBottom(true)

  try {
    const sseUrl = `${buildApiUrl('/app/chat/gen/code')}?appId=${currentAppId}&message=${encodeURIComponent(content)}`
    closeCurrentStream()
    currentEventSource = new EventSourcePolyfill(sseUrl, { withCredentials: true })
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
      let chunk = rawData
      try {
        const parsed = JSON.parse(rawData)
        chunk = parsed?.d ?? rawData
      } catch (error) {
        chunk = rawData
      }
      if (!chunk) return
      if (chunk === '[DONE]') {
        finalizeStreamingMessage(true)
        return
      }
      updateMessageContent(assistantMessageId, (current) => current + chunk)
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
  const normalizedContent = content.trim()
  if (!normalizedContent || isGenerating.value) return
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
    '请生成一个更现代的后台管理页面，左侧是配置表单，右侧是状态和预览卡片，整体使用干净的蓝白色，兼顾移动端。'
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
  if (!hasAssistantContent.value) {
    return
  }
  previewUrl.value = buildPreviewUrl(withTimestamp)
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
  if (!hasAssistantContent.value) {
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
  if (!hasAssistantContent.value) {
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

onMounted(() => {
  void loadPageData()
  void fetchUserApps()
})

watch(
  () => route.query.id,
  (newAppId) => {
    const normalizedAppId = normalizeRouteId(newAppId)
    if (normalizedAppId === appId.value) {
      return
    }
    appId.value = normalizedAppId
    resetPageState()
    void loadPageData()
  },
)

onBeforeUnmount(() => {
  closeCurrentStream()
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  box-sizing: border-box;
  padding: 16px 5px 20px;
  overflow: hidden;
  background: #f7f9fb;
}

.page-surface {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  gap: 16px;
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
  grid-template-columns: minmax(380px, 0.95fr) minmax(0, 1.35fr);
  align-items: center;
  gap: 20px;
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
  padding: 8px 12px 8px 8px;
  border-radius: 16px;
  border: 1px solid #e8ebef;
  background: #fff;
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.05);
  color: #1d2a42;
  white-space: nowrap;
}

.app-chip-logo {
  width: 38px;
  height: 38px;
  border-radius: 13px;
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
  gap: 12px;
  flex-wrap: wrap;
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
  grid-template-columns: minmax(380px, 0.95fr) minmax(0, 1.35fr);
  gap: 18px;
  min-height: 0;
  height: 100%;
}

.chat-panel,
.preview-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(193, 205, 222, 0.65);
  box-shadow: 0 20px 46px rgba(18, 35, 64, 0.08);
  overflow: hidden;
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 22px 22px 12px;
}

.panel-label {
  display: inline-block;
  font-size: 14px;
  font-weight: 800;
  color: #526177;
  letter-spacing: 0.04em;
}

.panel-description {
  margin: 8px 0 0;
  color: #8a96a8;
  font-size: 13px;
  line-height: 1.7;
}

.title-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.latest-prompt-card {
  margin: 0 22px 14px;
  padding: 18px 20px;
  border-radius: 22px;
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
  margin-top: 8px;
  color: #283447;
  font-size: 16px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.messages {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 22px;
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
  padding: 14px 20px 18px;
  border-top: 1px solid #eef2f7;
  background: rgba(255, 255, 255, 0.98);
}

.composer :deep(.ant-input) {
  border-radius: 20px;
  padding: 16px 16px;
  resize: none;
  border-color: #e5e7eb;
  background: #f8fafc;
  box-shadow: none;
}

.composer-footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 10px;
}

.composer-footer span {
  color: #9aa3af;
  font-size: 13px;
}

.composer-actions {
  display: flex;
  gap: 12px;
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

.browser-frame,
.preview-empty {
  margin: 6px 22px 22px;
  flex: 1;
  min-height: 0;
  border-radius: 22px;
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
  gap: 14px;
  padding: 14px 16px;
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
  padding: 10px 14px;
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

</style>
