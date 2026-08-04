<template>
  <div id="appUpdatePage">
    <div class="page-shell">
      <section class="hero-panel">
        <div class="hero-copy">
          <h1>应用工作台</h1>
          <p>在这里统一维护应用名称、封面和展示优先级，同时快速跳转到对话生成页继续迭代。</p>
        </div>
        <div class="hero-actions">
          <a-button size="large" @click="handleCancel">返回上一页</a-button>
          <a-button type="primary" size="large" @click="goToChat">继续生成</a-button>
        </div>
      </section>

      <a-spin :spinning="pageLoading">
        <div class="page-grid">
          <section class="panel form-panel">
            <div class="panel-header">
              <div>
                <h2>编辑应用信息</h2>
              </div>
              <a-tag :color="isFeatured ? 'gold' : 'default'">
                {{ isFeatured ? '精选应用' : '普通应用' }}
              </a-tag>
            </div>

            <a-form layout="vertical" :model="formData" class="edit-form" @finish="handleSubmit">
              <a-form-item label="应用名称" name="appName">
                <a-input
                  v-model:value="formData.appName"
                  placeholder="例如：智能作品集生成器"
                  :disabled="loading"
                  size="large"
                />
              </a-form-item>

              <a-form-item v-if="isAdmin" label="应用封面" name="cover">
                <a-input
                  v-model:value="formData.cover"
                  placeholder="请输入封面图片 URL"
                  :disabled="loading"
                  size="large"
                />
              </a-form-item>

              <a-form-item v-if="isAdmin" label="展示优先级" name="priority">
                <a-input-number
                  v-model:value="formData.priority"
                  :min="0"
                  :max="99"
                  :disabled="loading"
                  size="large"
                  class="full-width"
                />
                <template #extra>
                  <span>数值越高越靠前，`99` 会展示为精选应用。</span>
                </template>
              </a-form-item>

              <div class="meta-strip">
                <div class="meta-card">
                  <span class="meta-label">应用 ID</span>
                  <span class="meta-value">{{ appDetail.id || '-' }}</span>
                </div>
                <div class="meta-card">
                  <span class="meta-label">生成类型</span>
                  <span class="meta-value">{{ appDetail.codeGenType || 'html' }}</span>
                </div>
                <div class="meta-card">
                  <span class="meta-label">最近更新</span>
                  <span class="meta-value">{{
                    formatTime(appDetail.updateTime || appDetail.createTime)
                  }}</span>
                </div>
              </div>

              <div class="form-footer">
                <a-button size="large" @click="handleCancel">取消</a-button>
                <a-button type="primary" html-type="submit" size="large" :loading="loading">
                  保存修改
                </a-button>
              </div>
            </a-form>
          </section>

          <aside class="sidebar">
            <section class="panel preview-panel">
              <div class="cover-preview" :class="{ 'is-empty': !coverPreview }">
                <img v-if="coverPreview" :src="coverPreview" alt="应用封面" />
                <div v-else class="cover-fallback">
                  <span>{{ appInitial }}</span>
                </div>
              </div>
              <div class="preview-content">
                <div class="title-row">
                  <h3>{{ formData.appName || '未命名应用' }}</h3>
                  <a-tag color="processing">{{ appDetail.user?.userName || '当前用户' }}</a-tag>
                </div>
                <p class="preview-description">
                  {{ appDetail.initPrompt || '暂无初始化提示词，可前往对话页继续补充需求。' }}
                </p>
                <div class="stats-grid">
                  <div class="stat-item">
                    <span>创建时间</span>
                    <strong>{{ formatTime(appDetail.createTime) }}</strong>
                  </div>
                  <div class="stat-item">
                    <span>部署状态</span>
                    <strong>{{ appDetail.deployedTime ? '已部署' : '未部署' }}</strong>
                  </div>
                  <div class="stat-item">
                    <span>优先级</span>
                    <strong>{{ formData.priority ?? 0 }}</strong>
                  </div>
                  <div class="stat-item">
                    <span>访问口令</span>
                    <strong>{{ appDetail.deployKey || '未生成' }}</strong>
                  </div>
                </div>
              </div>
            </section>

            <section class="panel tips-panel">
              <h3 class="tips-title">使用建议</h3>
              <ul class="tips-list">
                <li>应用名称建议控制在 12 个字以内，列表页和聊天页会更清晰。</li>
                <li>封面尽量使用横向大图，能更适配首页和管理页卡片展示。</li>
                <li>如果需要继续打磨页面效果，保存后可直接进入对话生成页。</li>
              </ul>
            </section>
          </aside>
        </div>
      </a-spin>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { getAppVoById, updateApp, updateAppByAdmin } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { isSameId, normalizeRouteId } from '@/utils/id'

type AppUpdateForm = API.AppUpdateRequest & {
  cover?: string
  priority?: number
}

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = ref<string>(normalizeRouteId(route.query.id))
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')
const loading = ref(false)
const pageLoading = ref(false)
const appDetail = ref<API.AppVO>({})

const formData = reactive<AppUpdateForm>({
  id: undefined,
  appName: '',
  cover: '',
  priority: 0,
})

const coverPreview = computed(() => formData.cover || appDetail.value.cover || '')
const appInitial = computed(() => (formData.appName || appDetail.value.appName || 'A').slice(0, 1))
const isFeatured = computed(() => (formData.priority ?? appDetail.value.priority ?? 0) >= 99)

const formatTime = (time?: string) => {
  if (!time) {
    return '-'
  }
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

const fetchAppData = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  pageLoading.value = true
  try {
    const res = await getAppVoById({ id: appId.value })
    if (res.data.code === 0 && res.data.data) {
      const appData = res.data.data
      appDetail.value = appData
      formData.id = appData.id
      formData.appName = appData.appName || ''
      formData.cover = appData.cover || ''
      formData.priority = appData.priority || 0

      if (!isAdmin.value && !isSameId(appData.userId, loginUserStore.loginUser.id)) {
        message.error('无权编辑此应用')
        router.push('/')
      }
    } else {
      message.error('获取应用信息失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取应用信息失败')
  } finally {
    pageLoading.value = false
  }
}

const handleSubmit = async () => {
  if (!formData.appName?.trim()) {
    message.error('应用名称不能为空')
    return
  }

  loading.value = true
  try {
    const appName = formData.appName.trim()
    let res
    if (isAdmin.value) {
      res = await updateAppByAdmin({
        id: formData.id,
        appName,
        cover: formData.cover,
        priority: formData.priority,
      })
    } else {
      res = await updateApp({
        id: formData.id,
        appName,
      })
    }

    if (res.data.code === 0) {
      appDetail.value = {
        ...appDetail.value,
        appName,
        cover: formData.cover,
        priority: formData.priority,
      }
      message.success('更新成功')
    } else {
      message.error('更新失败，' + res.data.message)
    }
  } catch (error) {
    message.error('更新失败')
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  router.back()
}

const goToChat = () => {
  if (!appId.value) {
    return
  }
  router.push({
    path: '/app/chat',
    query: {
      id: appId.value,
    },
  })
}

onMounted(() => {
  fetchAppData()
})
</script>

<style scoped>
#appUpdatePage {
  padding: 36px 24px 64px;
  background: #f7f9fb;
}

.page-shell {
  display: flex;
  max-width: 1360px;
  margin: 0 auto;
  flex-direction: column;
  gap: 24px;
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  padding: 28px 32px;
  border-radius: 28px;
  border: 1px solid #e7ebef;
  background: #fff;
  color: #111827;
  box-shadow: 0 18px 50px rgba(17, 24, 39, 0.06);
}

.hero-copy {
  max-width: 640px;
}

.hero-copy h1 {
  margin: 0;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.hero-copy p {
  margin: 12px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: #7b8492;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.hero-actions :deep(.ant-btn) {
  min-width: 120px;
  border-radius: 14px;
}

.page-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(320px, 0.95fr);
  gap: 24px;
  align-items: start;
}

.panel {
  border-radius: 26px;
  background: #fff;
  border: 1px solid #e7ebef;
  box-shadow: 0 16px 40px rgba(17, 24, 39, 0.05);
}

.form-panel {
  padding: 28px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 28px;
}

.panel-header h2 {
  margin: 0;
  font-size: 24px;
  color: #111827;
}

.edit-form :deep(.ant-form-item-label > label) {
  color: #374151;
  font-weight: 600;
}

.full-width {
  width: 100%;
}

.meta-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 10px;
}

.meta-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
  border-radius: 18px;
  background: #f7f8f9;
  border: 1px solid #eceff2;
}

.meta-label {
  font-size: 12px;
  color: #9098a5;
}

.meta-value {
  font-size: 14px;
  font-weight: 600;
  color: #303744;
  word-break: break-all;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 28px;
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.preview-panel,
.tips-panel {
  padding: 24px;
}

.cover-preview {
  margin-top: 16px;
  height: 220px;
  border-radius: 24px;
  overflow: hidden;
  background: linear-gradient(135deg, #edf9f5 0%, #f4fbff 100%);
  border: 1px solid #e4ece9;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-preview.is-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-fallback {
  width: 84px;
  height: 84px;
  border-radius: 28px;
  background: linear-gradient(135deg, #0fbe9b 0%, #26d8ba 100%);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 34px;
  font-weight: 700;
  box-shadow: 0 18px 36px rgba(15, 190, 155, 0.2);
}

.preview-content {
  margin-top: 20px;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.title-row h3 {
  margin: 0;
  font-size: 22px;
  color: #111827;
}

.preview-description {
  margin: 14px 0 0;
  color: #707987;
  line-height: 1.7;
  min-height: 72px;
  white-space: pre-wrap;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 20px;
}

.stat-item {
  padding: 14px 16px;
  border-radius: 18px;
  background: #f7f8f9;
  border: 1px solid #eceff2;
}

.stat-item span {
  display: block;
  font-size: 12px;
  color: #9098a5;
  margin-bottom: 8px;
}

.stat-item strong {
  display: block;
  color: #303744;
  line-height: 1.5;
  word-break: break-word;
}

.tips-list {
  margin: 16px 0 0;
  padding-left: 18px;
  color: #626b78;
  line-height: 1.8;
}

.tips-title {
  margin: 0;
  color: #111827;
  font-size: 18px;
}

.tips-list li + li {
  margin-top: 8px;
}

</style>
