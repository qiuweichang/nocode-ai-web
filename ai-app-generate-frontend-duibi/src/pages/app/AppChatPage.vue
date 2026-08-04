<template>
  <div id="appChatPage">
    <a-layout>
      <a-layout-header class="header">
        <div class="header-content">
          <div class="app-title">{{ appData.appName || '应用生成' }}</div>
          <a-button type="primary" @click="doDeploy" :loading="deployLoading" :disabled="isGenerating">
            部署应用
          </a-button>
        </div>
      </a-layout-header>
      <a-layout-content class="content">
        <a-row :gutter="16" style="height: 100%">
          <a-col :span="12" class="chat-panel">
            <div class="chat-messages" ref="messagesContainer">
              <div
                v-for="(message, index) in messages"
                :key="index"
                class="message"
                :class="message.role === 'user' ? 'user-message' : 'ai-message'"
              >
                <div class="message-content">
                  <div class="message-role">
                    {{ message.role === 'user' ? '用户' : 'AI' }}
                  </div>
                  <div class="message-text">{{ message.content }}</div>
                </div>
              </div>
              <div v-if="isGenerating" class="message ai-message">
                <div class="message-content">
                  <div class="message-role">AI</div>
                  <div class="message-text">{{ generatingMessage }}</div>
                </div>
              </div>
            </div>
            <div class="input-area">
              <a-textarea
                v-model:value="userInput"
                placeholder="输入您的需求"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                @pressEnter="handleSend"
              />
              <a-button type="primary" @click="handleSend" :loading="isGenerating"> 发送 </a-button>
            </div>
          </a-col>
          <a-col :span="12" class="preview-panel">
            <div v-if="previewUrl" class="preview-container">
              <iframe :src="previewUrl" class="preview-iframe"></iframe>
            </div>
            <div v-else class="preview-placeholder">
              <div class="placeholder-content">
                <a-spin v-if="isGenerating" />
                <div v-else class="placeholder-text">
                  <FileTextOutlined />
                  <p>网站生成后将在此预览</p>
                </div>
              </div>
            </div>
          </a-col>
        </a-row>
      </a-layout-content>
    </a-layout>
    <a-modal v-model:open="deployModalVisible" title="部署成功" @ok="handleDeployOk">
      <p>应用已成功部署</p>
      <p>访问地址：</p>
      <a-input :value="deployUrl" readonly />
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, ref, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { FileTextOutlined } from '@ant-design/icons-vue'
import { getAppVoById, chatToGenCode, deployApp } from '@/api/appController'
import { EventSourcePolyfill } from 'event-source-polyfill'
import dayjs from 'dayjs'

const route = useRoute()
const appId = ref<string>(route.query.id as string)

const appData = ref<API.AppVO>({})
const messages = ref<Array<{ role: string; content: string }>>([])
const userInput = ref('')
const isGenerating = ref(false)
const generatingMessage = ref('')
const previewUrl = ref('')
const messagesContainer = ref<HTMLElement>()
const deployLoading = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

const buildDeployUrl = (url?: string) => {
  if (!url) {
    return ''
  }
  const normalizedUrl = url.replace(/\/+$/, '')
  return normalizedUrl.endsWith('/index.html') ? normalizedUrl : `${normalizedUrl}/index.html`
}

// 获取应用信息
const fetchAppData = async () => {
  try {
    const res = await getAppVoById({ id: appId.value })
    if (res.data.code === 0 && res.data.data) {
      appData.value = res.data.data
      // 设置页面标题
      document.title = appData.value.appName || '应用生成'
      // 显示初始提示词（但不自动发送）
      if (appData.value.initPrompt) {
        userInput.value = appData.value.initPrompt
      }
    } else {
      message.error('获取应用信息失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取应用信息失败')
  }
}

// 发送消息
const handleSend = async () => {
  if (!userInput.value.trim()) {
    return
  }
  const content = userInput.value.trim()
  messages.value.push({
    role: 'user',
    content,
  })
  userInput.value = ''
  await handleChat(content)
}

// 处理对话
const handleChat = async (content: string) => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  isGenerating.value = true
  generatingMessage.value = ''

  try {
    // 构建 SSE URL
    const sseUrl = `http://localhost:8123/api/app/chat/gen/code?appId=${appId.value}&message=${encodeURIComponent(content)}`

    // 使用 EventSourcePolyfill 支持跨域携带 Cookie
    const eventSource = new EventSourcePolyfill(sseUrl, {
      withCredentials: true,
    })

    eventSource.onmessage = (event) => {
      const data = event.data
      try {
        const parsed = JSON.parse(data)
        if (parsed.d) {
          generatingMessage.value += parsed.d
          scrollToBottom()
        }
      } catch {
        generatingMessage.value += data
        scrollToBottom()
      }
    }

    eventSource.onerror = () => {
      eventSource.close()
      isGenerating.value = false
    }

    eventSource.addEventListener('done', () => {
      eventSource.close()
      isGenerating.value = false
      // 生成完成，添加到消息列表
      if (generatingMessage.value) {
        messages.value.push({
          role: 'ai',
          content: generatingMessage.value,
        })
      }
      // 设置预览 URL，使用 codeGenType
      const codeGenType = appData.value.codeGenType || 'html'
      previewUrl.value = `http://localhost:8123/api/static/${codeGenType}_${appId.value}/`
    })
  } catch (error) {
    isGenerating.value = false
    message.error('对话失败')
  }
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 部署应用
const doDeploy = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  if (isGenerating.value) {
    message.warning('AI 正在回答中，请等待本轮完成后再部署')
    return
  }
  deployLoading.value = true
  try {
    const res = await deployApp({ appId: appId.value })
    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = buildDeployUrl(res.data.data)
      deployModalVisible.value = true
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

// 部署成功确认
const handleDeployOk = () => {
  deployModalVisible.value = false
}

onMounted(() => {
  fetchAppData()
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  background: #fff;
  padding: 0 24px;
  border-bottom: 1px solid #f0f0f0;
  height: 64px;
  line-height: 64px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.app-title {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.content {
  flex: 1;
  overflow: hidden;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  border-right: 1px solid #f0f0f0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #fafafa;
}

.message {
  margin-bottom: 16px;
}

.message-content {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: 8px;
}

.message-role {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.message-text {
  line-height: 1.6;
  word-wrap: break-word;
}

.user-message .message-content {
  margin-left: auto;
  background: #1890ff;
  color: white;
}

.ai-message .message-content {
  background: white;
  border: 1px solid #e8e8e8;
  color: #333;
}

.input-area {
  padding: 16px;
  border-top: 1px solid #f0f0f0;
  background: white;
  display: flex;
  gap: 12px;
}

.input-area :deep(.ant-input) {
  resize: none;
}

.preview-panel {
  height: 100%;
}

.preview-container {
  height: 100%;
  background: white;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.preview-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
}

.placeholder-content {
  text-align: center;
}

.placeholder-text {
  margin-top: 16px;
  color: #999;
}

.placeholder-text .anticon {
  font-size: 48px;
  color: #d9d9d9;
}

.placeholder-text p {
  margin-top: 12px;
  font-size: 14px;
}
</style>
