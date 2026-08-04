<template>
  <div id="appManagePage">
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="应用名称">
        <a-input v-model:value="searchParams.appName" placeholder="输入应用名称" />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-divider />
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'cover'">
          <a-image v-if="record.cover" :src="record.cover" :width="100" />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'priority'">
          <a-tag v-if="record.priority === 99" color="gold">精选</a-tag>
          <span v-else>{{ record.priority }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.dataIndex === 'deployedTime'">
          <span v-if="record.deployedTime">
            {{ dayjs(record.deployedTime).format('YYYY-MM-DD HH:mm:ss') }}
          </span>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" @click="doEdit(record.id)">编辑</a-button>
            <a-button type="link" @click="doSetFeatured(record.id)">精选</a-button>
            <a-button danger type="link" @click="doDelete(record.id)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  listAppVoByPageByAdmin,
  deleteAppByAdmin,
  updateAppByAdmin,
} from '@/api/appController'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'

const router = useRouter()

const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
  },
  {
    title: '应用名称',
    dataIndex: 'appName',
  },
  {
    title: '封面',
    dataIndex: 'cover',
  },
  {
    title: '优先级',
    dataIndex: 'priority',
  },
  {
    title: '创建人',
    dataIndex: ['user', 'userName'],
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '部署时间',
    dataIndex: 'deployedTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]

const data = ref<API.AppVO[]>([])
const total = ref(0)

const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

const fetchData = async () => {
  try {
    const res = await listAppVoByPageByAdmin({
      ...searchParams,
    })
    if (res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取数据失败')
  }
}

const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

const doEdit = (id: string) => {
  router.push({
    path: '/app/update',
    query: { id },
  })
}

const doSetFeatured = async (id: string) => {
  if (!id) {
    return
  }
  try {
    const res = await updateAppByAdmin({
      id,
      priority: 99,
    })
    if (res.data.code === 0) {
      message.success('设置精选成功')
      fetchData()
    } else {
      message.error('设置精选失败，' + res.data.message)
    }
  } catch (error) {
    message.error('设置精选失败')
  }
}

const doDelete = async (id: string) => {
  if (!id) {
    return
  }
  try {
    const res = await deleteAppByAdmin({ id })
    if (res.data.code === 0) {
      message.success('删除成功')
      fetchData()
    } else {
      message.error('删除失败，' + res.data.message)
    }
  } catch (error) {
    message.error('删除失败')
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#appManagePage {
  padding: 24px;
  background: white;
  margin-top: 16px;
}
</style>
