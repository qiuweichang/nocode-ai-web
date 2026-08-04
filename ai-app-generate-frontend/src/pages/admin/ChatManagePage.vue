<template>
  <main id="chatManagePage">
    <header class="page-heading">
      <div>
        <h1>对话管理</h1>
        <p>检索生成记录并快速回到对应应用工作台。</p>
      </div>
      <a-button @click="resetSearch">重置筛选</a-button>
    </header>

    <a-form class="filter-bar" layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="消息内容">
        <a-input v-model:value="searchParams.message" allow-clear placeholder="输入关键词" />
      </a-form-item>
      <a-form-item label="消息类型">
        <a-select v-model:value="searchParams.messageType" allow-clear placeholder="全部" style="width: 140px">
          <a-select-option value="user">用户消息</a-select-option>
          <a-select-option value="assistant">AI 消息</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="应用 ID">
        <a-input v-model:value="searchParams.appId" allow-clear placeholder="应用 ID" />
      </a-form-item>
      <a-form-item label="用户 ID">
        <a-input v-model:value="searchParams.userId" allow-clear placeholder="用户 ID" />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>

    <a-table
      row-key="id"
      :columns="columns"
      :data-source="records"
      :loading="loading"
      :pagination="pagination"
      :scroll="{ x: 1100 }"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'message'">
          <a-tooltip :title="record.message">
            <p class="message-cell">{{ record.message }}</p>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'messageType'">
          <a-tag :color="record.messageType === 'user' ? 'blue' : 'green'">
            {{ record.messageType === 'user' ? '用户消息' : 'AI 消息' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" @click="viewAppChat(record.appId)">查看工作台</a-button>
        </template>
      </template>
    </a-table>
  </main>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { listAllChatHistoryByPageForAdmin } from '@/api/chatHistoryController'

/** 表格字段保持精简，长消息通过悬浮提示查看完整内容。 */
const columns = [
  { title: 'ID', dataIndex: 'id', width: 100 },
  { title: '消息内容', dataIndex: 'message', width: 380 },
  { title: '消息类型', dataIndex: 'messageType', width: 120 },
  { title: '应用 ID', dataIndex: 'appId', width: 120 },
  { title: '用户 ID', dataIndex: 'userId', width: 120 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 130 },
]

const router = useRouter()
const records = ref<API.ChatHistory[]>([])
const total = ref(0)
const loading = ref(false)
const searchParams = reactive<API.ChatHistoryQueryRequest>({ pageNum: 1, pageSize: 10 })

/**
 * 从管理端接口加载对话记录，并保留当前筛选与分页状态。
 *
 * @returns 接口完成后的 Promise
 */
const fetchData = async () => {
  loading.value = true
  try {
    const response = await listAllChatHistoryByPageForAdmin({ ...searchParams })
    if (response.data.code === 0 && response.data.data) {
      records.value = response.data.data.records ?? []
      total.value = response.data.data.totalRow ?? 0
      return
    }
    message.error('获取对话记录失败，' + response.data.message)
  } catch {
    message.error('获取对话记录失败')
  } finally {
    loading.value = false
  }
}

/** 将接口时间格式化为管理表格易读格式。 */
const formatTime = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-')

/** 提交筛选时回到第一页，避免旧页码导致空结果。 */
const doSearch = () => {
  searchParams.pageNum = 1
  void fetchData()
}

/** 清空全部筛选条件并重新加载首屏数据。 */
const resetSearch = () => {
  Object.assign(searchParams, { pageNum: 1, pageSize: 10, message: undefined, messageType: undefined, appId: undefined, userId: undefined })
  void fetchData()
}

/** 根据 Ant Design 表格分页事件加载对应页。 */
const handleTableChange = (page: { current?: number; pageSize?: number }) => {
  searchParams.pageNum = page.current ?? 1
  searchParams.pageSize = page.pageSize ?? 10
  void fetchData()
}

/** 从记录跳转到对应应用的生成工作台。 */
const viewAppChat = (appId?: string | number) => {
  if (!appId) return
  void router.push({ path: '/app/chat', query: { id: String(appId) } })
}

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (value: number) => `共 ${value} 条`,
}))

onMounted(() => void fetchData())
</script>

<style scoped>
#chatManagePage {
  max-width: 1440px;
  min-height: calc(100vh - 144px);
  margin: 0 auto;
  padding: 44px 32px 64px;
  background: #fff;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
}

.page-heading h1 {
  margin: 0;
  color: #111827;
  font-size: 32px;
}

.page-heading p {
  margin: 8px 0 0;
  color: #8a93a3;
}

.filter-bar {
  margin-bottom: 24px;
  padding: 20px;
  border: 1px solid #edf0f3;
  border-radius: 20px;
  background: #fafafa;
}

.message-cell {
  max-width: 360px;
  margin: 0;
  overflow: hidden;
  color: #374151;
  text-overflow: ellipsis;
  white-space: nowrap;
}

</style>
