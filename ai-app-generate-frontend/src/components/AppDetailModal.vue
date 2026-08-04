<template>
  <a-modal v-model:open="visible" :footer="null" width="720px" centered>
    <div class="detail-modal">
      <div class="detail-cover">
        <img v-if="app?.cover" :src="app.cover" :alt="app.appName" />
        <div v-else class="cover-placeholder">
          <span></span><span></span><span></span>
          <strong>{{ app?.appName || '应用预览' }}</strong>
        </div>
      </div>
      <div class="detail-content">
        <div class="detail-title-row">
          <div>
            <h2>{{ app?.appName || '应用详情' }}</h2>
            <p>{{ app?.initPrompt || '这个应用还没有补充说明。' }}</p>
          </div>
          <a-tag color="blue">{{ formatCodeGenType(app?.codeGenType) }}</a-tag>
        </div>

        <dl class="detail-meta">
          <div><dt>创建者</dt><dd>{{ app?.user?.userName || 'NoCode 用户' }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatTime(app?.createTime) }}</dd></div>
          <div><dt>部署状态</dt><dd>{{ app?.deployKey ? '已部署' : '未部署' }}</dd></div>
        </dl>

        <div class="detail-actions">
          <a-button v-if="app?.deployKey" @click="emit('visit')">访问应用</a-button>
          <a-button v-if="editable" type="primary" @click="emit('edit')">继续创作</a-button>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'

interface Props {
  open: boolean
  app?: API.AppVO
  editable?: boolean
}

interface Emits {
  (event: 'update:open', value: boolean): void
  (event: 'edit'): void
  (event: 'visit'): void
}

const props = withDefaults(defineProps<Props>(), { editable: false })
const emit = defineEmits<Emits>()

/** 将外部 open 属性转换为支持 v-model 的可写状态。 */
const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

/** 将后端生成类型转换为用户可读名称。 */
const formatCodeGenType = (value?: string) => {
  const labels: Record<string, string> = {
    html: '网页',
    multi_file: '多文件应用',
    vue_project: 'Vue 应用',
  }
  return labels[value || ''] || 'AI 应用'
}

/** 将接口时间统一格式化为详情弹窗中的中文日期。 */
const formatTime = (value?: string) => (value ? dayjs(value).format('YYYY 年 M 月 D 日') : '刚刚')
</script>

<style scoped>
.detail-modal {
  overflow: hidden;
  border-radius: 22px;
}

.detail-cover {
  aspect-ratio: 16 / 7;
  overflow: hidden;
  border: 1px solid #edf0f3;
  border-radius: 20px;
  background: #f6f8fa;
}

.detail-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  position: relative;
  display: grid;
  height: 100%;
  grid-template-columns: 80px 1fr 1fr;
  gap: 16px;
  align-items: end;
  padding: 28px;
  background: linear-gradient(145deg, #f8fbff 0%, #eef6f4 100%);
}

.cover-placeholder span {
  height: 70%;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
}

.cover-placeholder strong {
  position: absolute;
  align-self: start;
  color: #111827;
  font-size: 24px;
}

.detail-content {
  padding: 26px 4px 4px;
}

.detail-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.detail-title-row h2 {
  margin: 0;
  color: #111827;
  font-size: 28px;
}

.detail-title-row p {
  max-width: 560px;
  margin: 10px 0 0;
  color: #7b8492;
  line-height: 1.7;
}

.detail-meta {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin: 24px 0;
}

.detail-meta div {
  padding: 16px;
  border-radius: 16px;
  background: #f7f8f9;
}

.detail-meta dt {
  color: #9aa1ac;
  font-size: 12px;
}

.detail-meta dd {
  margin: 7px 0 0;
  color: #303744;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

</style>
