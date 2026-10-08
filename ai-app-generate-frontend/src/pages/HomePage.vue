<template>
  <div id="homePage">
    <section class="hero-section">
      <div class="hero-orb orb-left"></div>
      <div class="hero-orb orb-right"></div>
      <div class="hero-orb orb-bottom"></div>

      <div class="hero-inner">
        <h1 class="hero-title">
          <span>一句话</span>
          <img :src="logo" alt="NoCode Logo" class="hero-logo" />
          <span>呈所想</span>
        </h1>
        <p class="hero-subtitle">与 AI 对话轻松创建应用和网站</p>

        <div class="prompt-card">
          <a-textarea
            v-model:value="userPrompt"
            placeholder="描述你的应用创意，AI 将为你生成完整网站……"
            :auto-size="{ minRows: 6, maxRows: 8 }"
            class="prompt-textarea"
          />

          <div class="prompt-footer">
            <div class="prompt-tools">
              <a-button class="ghost-pill" @click="openFilePicker">
                <PaperClipOutlined />
                上传
              </a-button>
              <input
                ref="promptFileInput"
                class="visually-hidden"
                type="file"
                accept=".txt,.md,.json,.html,.css,.js,.ts,.vue,text/plain,application/json"
                @change="handlePromptFileChange"
              />
              <a-button class="ghost-pill" @click="handleOptimizePrompt">
                <BgColorsOutlined />
                优化
              </a-button>
            </div>

            <a-button
              class="submit-circle"
              :loading="createLoading"
              :disabled="!userPrompt.trim()"
              @click="handleCreateApp"
            >
              <ArrowUpOutlined />
            </a-button>
          </div>
        </div>

        <div class="example-pills">
          <button
            v-for="item in promptSuggestions"
            :key="item"
            class="example-pill"
            @click="selectSuggestion(item)"
          >
            {{ item }}
          </button>
        </div>
      </div>
    </section>

    <section id="my-work" class="showcase-section">
      <div class="showcase-panel">
        <div class="section-block">
          <div class="section-header">
            <div>
              <h2>我的作品</h2>
              <p>{{ hasLogin ? '继续完善你的应用作品集' : '登录后即可查看并继续编辑你的作品' }}</p>
            </div>
          </div>

          <div v-if="hasLogin" class="app-grid">
            <article
              v-for="(item, index) in myApps"
              :key="item.id"
              class="showcase-card"
              @click="handleGoToChat(item.id)"
            >
              <div
                class="card-preview"
                :class="`variant-${index % 3}`"
                title="页面缩略预览，请使用下方“继续”按钮进入编辑"
                @click.stop
              >
                <img
                  v-if="item.cover"
                  :src="item.cover"
                  :alt="item.appName"
                  class="preview-image"
                />
                <div v-else-if="item.hasGeneratedCode" class="site-preview-shell">
                  <iframe
                    :src="buildAppCardPreviewUrl(item)"
                    :title="`${item.appName || '未命名应用'}页面预览`"
                    class="site-preview-frame"
                    loading="lazy"
                    sandbox="allow-scripts"
                    tabindex="-1"
                    aria-hidden="true"
                  ></iframe>
                  <span class="site-preview-label">页面预览</span>
                </div>
                <div v-else class="preview-fallback">
                  <div class="fallback-window">
                    <div class="window-bar">
                      <span></span>
                      <span></span>
                      <span></span>
                    </div>
                    <div class="fallback-body">
                      <div class="fallback-side">
                        <img :src="logo" alt="logo" class="fallback-logo" />
                        <span></span>
                        <span></span>
                      </div>
                      <div class="fallback-main">
                        <div class="line long"></div>
                        <div class="line medium"></div>
                        <div class="line short"></div>
                        <div class="block"></div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="card-info">
                <div class="card-title-row">
                  <h3>{{ item.appName }}</h3>
                  <a-tag color="purple">用户应用</a-tag>
                </div>
                <p class="card-meta">更新于 {{ formatRelativeTime(item.updateTime || item.createTime) }}</p>
                <div class="card-actions" @click.stop>
                  <a-button type="text" @click="handleGoToChat(item.id)">
                    继续
                    <ArrowRightOutlined />
                  </a-button>
                  <a-popconfirm
                    title="确定删除这个应用吗？"
                    ok-text="删除"
                    cancel-text="取消"
                    @confirm="handleDeleteMyApp(item.id)"
                  >
                    <a-button type="text" danger>
                      <DeleteOutlined />
                      删除
                    </a-button>
                  </a-popconfirm>
                </div>
              </div>
            </article>
          </div>

          <a-empty
            v-else
            description="登录后即可查看你的作品，并继续在对话页迭代"
            class="section-empty"
          />

          <div
            class="pagination-wrapper"
            v-if="hasLogin && myAppsTotal > (myAppParams.pageSize ?? 10)"
          >
            <a-pagination
              v-model:current="myAppParams.pageNum"
              v-model:pageSize="myAppParams.pageSize"
              :total="myAppsTotal"
              :show-total="(total: number) => `共 ${total} 条`"
              @change="fetchMyApps"
            />
          </div>
        </div>

        <div id="showcase" class="section-block">
          <div class="section-header">
            <div>
              <h2>精选案例</h2>
              <p>浏览社区里更成熟的应用案例，快速获得灵感</p>
            </div>
          </div>

          <div class="app-grid featured-grid">
            <article
              v-for="(item, index) in featuredApps"
              :key="item.id"
              class="showcase-card featured-card"
              @click="handleViewFeatured(item.id)"
            >
              <div class="card-preview" :class="`featured-${index % 3}`">
                <img
                  v-if="item.cover"
                  :src="item.cover"
                  :alt="item.appName"
                  class="preview-image"
                />
                <div v-else class="preview-fallback featured-fallback">
                  <div class="featured-surface">
                    <div class="featured-topbar"></div>
                    <div class="featured-columns">
                      <div class="featured-column left"></div>
                      <div class="featured-column middle"></div>
                      <div class="featured-column right"></div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="card-info">
                <div class="featured-meta">
                  <img :src="logo" alt="logo" class="mini-logo" />
                  <span>{{ item.user?.userName || 'NoCode 官方' }}</span>
                </div>
                <div class="card-title-row">
                  <h3>{{ item.appName }}</h3>
                  <a-tag :color="featuredTagColors[index % featuredTagColors.length]">
                    {{ featuredTagLabels[index % featuredTagLabels.length] }}
                  </a-tag>
                </div>
                <p class="card-meta">{{ item.user?.userName || 'NoCode 官方' }}</p>
              </div>
            </article>
          </div>

          <div
            class="pagination-wrapper"
            v-if="featuredAppsTotal > (featuredAppParams.pageSize ?? 10)"
          >
            <a-pagination
              v-model:current="featuredAppParams.pageNum"
              v-model:pageSize="featuredAppParams.pageSize"
              :total="featuredAppsTotal"
              :show-total="(total: number) => `共 ${total} 条`"
              @change="fetchFeaturedApps"
            />
          </div>
        </div>
      </div>
    </section>

    <AppDetailModal
      v-model:open="featuredDetailVisible"
      :app="selectedFeaturedApp"
      :editable="selectedFeaturedEditable"
      @edit="openSelectedFeaturedWorkspace"
      @visit="visitSelectedFeaturedApp"
    />
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowRightOutlined,
  ArrowUpOutlined,
  BgColorsOutlined,
  DeleteOutlined,
  PaperClipOutlined,
} from '@ant-design/icons-vue'
import {
  addApp,
  listMyAppVoByPage,
  listGoodAppVoByPage,
  deleteApp,
  getAppVoById,
} from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import logo from '@/assets/logo.png'
import dayjs from 'dayjs'
import { isSameId } from '@/utils/id'
import { buildDeployAppUrl, buildPreviewAppUrl } from '@/config/appConfig'
import AppDetailModal from '@/components/AppDetailModal.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const promptSuggestions = ['波普风电商页面', '企业网站', '电商运营后台', '暗黑话题社区']

const featuredTagLabels = ['用户应用', '网站', '工具']
const featuredTagColors = ['purple', 'blue', 'gold']

const userPrompt = ref('')
const promptFileInput = ref<HTMLInputElement>()
const createLoading = ref(false)
const featuredDetailVisible = ref(false)
const selectedFeaturedApp = ref<API.AppVO>()

const myApps = ref<API.AppVO[]>([])
const myAppsLoading = ref(false)
const myAppsTotal = ref(0)
const myAppParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 6,
  sortField: 'updateTime',
  sortOrder: 'descend',
})

const featuredApps = ref<API.AppVO[]>([])
const featuredAppsLoading = ref(false)
const featuredAppsTotal = ref(0)
const featuredAppParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 6,
})

const hasLogin = computed(() => Boolean(loginUserStore.loginUser.id))
const selectedFeaturedEditable = computed(() =>
  isSameId(selectedFeaturedApp.value?.userId, loginUserStore.loginUser.id),
)

const formatRelativeTime = (time?: string) => {
  if (!time) {
    return '刚刚创建'
  }
  const now = dayjs()
  const target = dayjs(time)
  const minuteDiff = now.diff(target, 'minute')
  if (minuteDiff < 60) {
    return `${Math.max(minuteDiff, 1)} 分钟前`
  }
  const hourDiff = now.diff(target, 'hour')
  if (hourDiff < 24) {
    return `${hourDiff} 小时前`
  }
  const dayDiff = now.diff(target, 'day')
  if (dayDiff < 30) {
    return `${dayDiff} 天前`
  }
  return target.format('YYYY-MM-DD')
}

const handleOptimizePrompt = () => {
  userPrompt.value = userPrompt.value.trim()
    ? `${userPrompt.value.trim()}，请补充更清晰的页面结构、核心功能、目标用户和视觉风格，使用完整的桌面端布局。`
    : '帮我创建一个现代感强、层次清晰的桌面端企业官网，包含首页、服务介绍、案例展示和联系表单。'
}

/**
 * 构建作品卡片中的同源静态页面地址。
 * 预览 iframe 仅在后端确认生成目录存在时渲染，避免空项目显示浏览器 404 页面。
 *
 * @param app 当前作品数据
 * @returns 可用于 iframe 的页面预览地址
 */
const buildAppCardPreviewUrl = (app: API.AppVO) => {
  if (!app.id || !app.codeGenType) return ''
  return buildPreviewAppUrl(`/api/static/${app.codeGenType}_${app.id}/index.html`)
}

/** 打开隐藏文件选择器，让用户把文本需求或已有前端代码作为上下文导入。 */
const openFilePicker = () => promptFileInput.value?.click()

/**
 * 读取用户选择的文本文件并追加到提示词；限制体积以避免误选大型二进制文件。
 *
 * @param event 文件输入框 change 事件
 */
const handlePromptFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 1024 * 1024) {
    message.warning('文件不能超过 1 MB')
    input.value = ''
    return
  }
  try {
    const content = await file.text()
    const prefix = userPrompt.value.trim() ? `${userPrompt.value.trim()}\n\n` : ''
    userPrompt.value = `${prefix}参考文件 ${file.name}：\n${content}`
    message.success(`已读取 ${file.name}`)
  } catch {
    message.error('文件读取失败，请选择文本文件')
  } finally {
    input.value = ''
  }
}

const selectSuggestion = (value: string) => {
  userPrompt.value = value
}

const handleCreateApp = async () => {
  if (!userPrompt.value.trim()) {
    message.error('请输入需求')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.error('请先登录')
    router.push('/user/login')
    return
  }

  createLoading.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
    })
    if (res.data.code === 0 && res.data.data) {
      message.success('创建成功，正在跳转...')
      router.push({
        path: '/app/chat',
        query: { id: res.data.data },
      })
    } else {
      message.error('创建失败，' + res.data.message)
    }
  } catch (error) {
    message.error('创建失败')
  } finally {
    createLoading.value = false
  }
}

const fetchMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  myAppsLoading.value = true
  try {
    const res = await listMyAppVoByPage({
      ...myAppParams,
    })
    if (res.data.data) {
      myApps.value = res.data.data.records ?? []
      myAppsTotal.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取我的应用失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取我的应用失败')
  } finally {
    myAppsLoading.value = false
  }
}

const fetchFeaturedApps = async () => {
  featuredAppsLoading.value = true
  try {
    const res = await listGoodAppVoByPage({
      ...featuredAppParams,
    })
    if (res.data.data) {
      featuredApps.value = res.data.data.records ?? []
      featuredAppsTotal.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取精选应用失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取精选应用失败')
  } finally {
    featuredAppsLoading.value = false
  }
}

const handleGoToChat = (id?: string) => {
  if (!id) {
    return
  }
  router.push({
    path: '/app/chat',
    query: { id },
  })
}

const handleDeleteMyApp = async (id?: string) => {
  if (!id) {
    return
  }
  try {
    const res = await deleteApp({ id })
    if (res.data.code === 0) {
      message.success('删除成功')
      fetchMyApps()
    } else {
      message.error('删除失败，' + res.data.message)
    }
  } catch (error) {
    message.error('删除失败')
  }
}

const handleViewFeatured = async (id?: string) => {
  if (!id) {
    return
  }
  try {
    const res = await getAppVoById({ id })
    if (res.data.code === 0 && res.data.data) {
      selectedFeaturedApp.value = res.data.data
      featuredDetailVisible.value = true
    } else {
      message.error('获取应用信息失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取应用信息失败')
  }
}

/** 打开当前精选应用的工作台，仅应用所有者会看到此操作。 */
const openSelectedFeaturedWorkspace = () => {
  const id = selectedFeaturedApp.value?.id
  if (!id) return
  featuredDetailVisible.value = false
  void router.push({ path: '/app/chat', query: { id } })
}

/** 在新窗口访问当前精选应用已部署的公开页面。 */
const visitSelectedFeaturedApp = () => {
  const deployUrl = buildDeployAppUrl(selectedFeaturedApp.value?.deployKey)
  if (!deployUrl) {
    message.info('该应用暂未部署')
    return
  }
  window.open(deployUrl, '_blank', 'noopener,noreferrer')
}

onMounted(() => {
  fetchFeaturedApps()
  if (loginUserStore.loginUser.id) {
    fetchMyApps()
  }
})
</script>

<style scoped>
#homePage {
  --page-font: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans SC', sans-serif;
  min-height: var(--app-viewport-height);
  font-family: var(--page-font);
  color: #111827;
  background:
    radial-gradient(circle at 18% 16%, rgba(255, 246, 225, 0.88), transparent 22%),
    radial-gradient(circle at 84% 18%, rgba(180, 255, 245, 0.72), transparent 24%),
    radial-gradient(circle at 88% 78%, rgba(64, 150, 255, 0.5), transparent 24%),
    linear-gradient(180deg, #f8f8f3 0%, #f7fbf5 28%, #bef4ef 68%, #4f92ff 100%);
}

.hero-section {
  position: relative;
  overflow: hidden;
  padding: 72px 24px 140px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  clip-path: inset(50%);
}

.hero-inner {
  position: relative;
  z-index: 1;
  max-width: 1480px;
  margin: 0 auto;
  text-align: center;
}

.hero-title {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
  font-size: clamp(48px, 6vw, 86px);
  line-height: 1.05;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #111827;
  margin: 0;
}

.hero-logo {
  width: clamp(68px, 7vw, 94px);
  height: clamp(68px, 7vw, 94px);
  border-radius: 28px;
  box-shadow: 0 12px 28px rgba(32, 208, 184, 0.18);
}

.hero-subtitle {
  margin: 28px auto 0;
  font-size: clamp(20px, 2vw, 28px);
  line-height: 1.6;
  color: #7f8794;
}

.prompt-card {
  max-width: 1140px;
  margin: 64px auto 0;
  padding: 24px 24px 18px;
  border-radius: 34px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.85);
  box-shadow:
    0 28px 60px rgba(125, 145, 177, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(18px);
}

.prompt-textarea :deep(.ant-input) {
  min-height: 240px;
  padding: 4px 6px;
  background: transparent;
  border: none;
  box-shadow: none;
  color: #1f2937;
  font-size: clamp(20px, 2vw, 28px);
  line-height: 1.7;
  font-family: var(--page-font);
}

.prompt-textarea :deep(textarea::placeholder) {
  color: #b4bcc9;
}

.prompt-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
}

.prompt-tools {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.ghost-pill {
  height: 44px;
  border: none;
  border-radius: 999px;
  background: #f5f5f4;
  color: #333c4f;
  font-size: 14px;
  box-shadow: none;
}

.ghost-pill:hover {
  background: #eeeff2 !important;
  color: #111827 !important;
}

.submit-circle {
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  background: #9ca3af;
  color: #fff;
  box-shadow: none;
}

.submit-circle:hover,
.submit-circle:focus {
  background: #6b7280 !important;
  color: #fff !important;
}

.submit-circle:disabled {
  background: #d1d5db;
  color: #fff;
}

.example-pills {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 30px;
}

.example-pill {
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 18px;
  padding: 14px 22px;
  background: rgba(255, 255, 255, 0.92);
  color: #5f6675;
  font-size: 16px;
  box-shadow: 0 10px 26px rgba(73, 94, 126, 0.08);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.example-pill:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(73, 94, 126, 0.14);
}

.showcase-section {
  position: relative;
  margin-top: -44px;
  padding: 0 24px 56px;
}

.showcase-panel {
  max-width: 1800px;
  margin: 0 auto;
  padding: 44px 48px 36px;
  border-radius: 40px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.78);
  box-shadow: 0 36px 80px rgba(44, 81, 146, 0.16);
}

.section-block + .section-block {
  margin-top: 54px;
}

.section-header h2 {
  margin: 0;
  font-size: clamp(34px, 4vw, 56px);
  line-height: 1.1;
  font-weight: 800;
  color: #131926;
}

.section-header p {
  margin: 12px 0 0;
  color: #8a93a3;
  font-size: 16px;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 30px;
  margin-top: 28px;
}

.showcase-card {
  cursor: pointer;
}

.card-preview {
  position: relative;
  aspect-ratio: 1.55 / 1;
  overflow: hidden;
  border-radius: 24px;
  border: 1px solid #edf1f6;
  background: #f8fafc;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.88);
  cursor: default;
}

.preview-image {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.site-preview-shell {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #eef2f7;
  cursor: default;
}

.site-preview-frame {
  position: absolute;
  top: 0;
  left: 0;
  width: 1200px;
  height: 760px;
  border: 0;
  background: #fff;
  pointer-events: none;
  transform: scale(0.34);
  transform-origin: left top;
}

.site-preview-label {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 5px 9px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 9px;
  background: rgba(17, 24, 39, 0.72);
  color: #fff;
  font-size: 11px;
  line-height: 1;
  backdrop-filter: blur(8px);
}

.preview-fallback {
  width: 100%;
  height: 100%;
  padding: 18px;
}

.fallback-window,
.featured-surface {
  width: 100%;
  height: 100%;
  border-radius: 22px;
  overflow: hidden;
  background: linear-gradient(180deg, #ffffff 0%, #f7f8fb 100%);
  border: 1px solid #eef2f5;
}

.window-bar {
  display: flex;
  gap: 8px;
  padding: 12px 14px;
}

.window-bar span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #d8dee8;
}

.fallback-body {
  display: grid;
  grid-template-columns: 108px 1fr;
  gap: 18px;
  padding: 0 14px 14px;
  height: calc(100% - 34px);
}

.fallback-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  padding: 18px 12px;
  border-radius: 18px;
  background: rgba(242, 245, 250, 0.92);
}

.fallback-logo {
  width: 54px;
  height: 54px;
  border-radius: 16px;
}

.fallback-side span,
.line,
.block,
.featured-column {
  background: #edf1f5;
  border-radius: 999px;
}

.fallback-side span:nth-child(2) {
  width: 70px;
  height: 12px;
}

.fallback-side span:nth-child(3) {
  width: 54px;
  height: 12px;
}

.fallback-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 18px;
}

.line.long {
  width: 92%;
  height: 14px;
}

.line.medium {
  width: 78%;
  height: 12px;
}

.line.short {
  width: 42%;
  height: 12px;
}

.block {
  flex: 1;
  border-radius: 20px;
}

.featured-surface {
  padding: 16px;
}

.featured-topbar {
  height: 16px;
  border-radius: 999px;
  background: linear-gradient(90deg, #ebf2ff 0%, #f8fbff 100%);
}

.featured-columns {
  display: grid;
  grid-template-columns: 0.82fr 1.28fr 0.92fr;
  gap: 16px;
  height: calc(100% - 34px);
  padding-top: 16px;
}

.featured-column {
  border-radius: 20px;
}

.card-preview.variant-1 {
  background: linear-gradient(180deg, #fbfcff 0%, #f6f9ff 100%);
}

.card-preview.variant-2 {
  background: linear-gradient(180deg, #fffdfa 0%, #fff7ef 100%);
}

.card-preview.featured-1 {
  background: linear-gradient(180deg, #eef8ff 0%, #f8fbff 100%);
}

.card-preview.featured-2 {
  background: linear-gradient(180deg, #fffdf6 0%, #fff8e8 100%);
}

.card-info {
  padding: 18px 6px 0;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.card-title-row h3 {
  margin: 0;
  font-size: clamp(24px, 2vw, 36px);
  line-height: 1.2;
  font-weight: 700;
  color: #131926;
}

.card-meta {
  margin: 10px 0 0;
  color: #838c99;
  font-size: 16px;
}

.card-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.featured-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  color: #6d7684;
  font-size: 16px;
}

.mini-logo {
  width: 42px;
  height: 42px;
  border-radius: 14px;
}

.section-empty {
  margin-top: 26px;
  padding: 48px 0;
  border-radius: 28px;
  background: #fbfcfd;
  border: 1px dashed #e7ecf2;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

.hero-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(12px);
  opacity: 0.68;
  pointer-events: none;
}

.orb-left {
  width: 240px;
  height: 240px;
  left: -40px;
  top: 40px;
  background: radial-gradient(circle, rgba(255, 247, 230, 0.95) 0%, transparent 70%);
}

.orb-right {
  width: 420px;
  height: 420px;
  right: -70px;
  top: 40px;
  background: radial-gradient(circle, rgba(130, 255, 240, 0.7) 0%, transparent 70%);
}

.orb-bottom {
  width: 460px;
  height: 460px;
  right: 0;
  bottom: 40px;
  background: radial-gradient(circle, rgba(76, 148, 255, 0.48) 0%, transparent 72%);
}

</style>
