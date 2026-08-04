<template>
  <div id="homePage">
    <div class="hero-section">
      <h1 class="site-title">NoCode-AiWeb</h1>
      <p class="site-description">与 AI 对话轻松创建应用和网站</p>

      <div class="prompt-input-area">
        <a-textarea
          v-model:value="userPrompt"
          placeholder="请输入您的需求，例如：帮我生成一个个人博客网站"
          :auto-size="{ minRows: 3, maxRows: 5 }"
          class="prompt-textarea"
        />
        <a-button
          type="primary"
          size="large"
          @click="handleCreateApp"
          :loading="createLoading"
          class="create-btn"
        >
          生成应用
        </a-button>
      </div>
    </div>

    <div class="content-section">
      <a-row :gutter="24">
        <a-col :span="12">
          <a-card title="我的应用" class="app-card">
            <template #extra>
              <a-input-search
                v-model:value="myAppSearch"
                placeholder="搜索应用名称"
                style="width: 200px"
                @search="fetchMyApps"
              />
            </template>
            <a-list :data-source="myApps" :loading="myAppsLoading">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <a @click="handleGoToChat(item.id)">{{ item.appName }}</a>
                    </template>
                    <template #description>
                      {{ dayjs(item.createTime).format('YYYY-MM-DD HH:mm') }}
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a-button type="link" size="small" @click="handleGoToChat(item.id)">
                      继续
                    </a-button>
                    <a-button type="link" danger size="small" @click="handleDeleteMyApp(item.id)">
                      删除
                    </a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
            <div class="pagination-wrapper" v-if="myAppsTotal > 0">
              <a-pagination
                v-model:current="myAppParams.pageNum"
                v-model:pageSize="myAppParams.pageSize"
                :total="myAppsTotal"
                :show-total="(total) => `共 ${total} 条`"
                @change="fetchMyApps"
                size="small"
              />
            </div>
          </a-card>
        </a-col>
        <a-col :span="12">
          <a-card title="精选应用" class="app-card">
            <template #extra>
              <a-input-search
                v-model:value="featuredAppSearch"
                placeholder="搜索应用名称"
                style="width: 200px"
                @search="fetchFeaturedApps"
              />
            </template>
            <a-list :data-source="featuredApps" :loading="featuredAppsLoading">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <a @click="handleViewFeatured(item.id)">{{ item.appName }}</a>
                    </template>
                    <template #avatar>
                      <a-avatar v-if="item.cover" :src="item.cover" :size="48" />
                      <a-avatar v-else :size="48">
                        <template #icon>
                          <AppstoreOutlined />
                        </template>
                      </a-avatar>
                    </template>
                    <template #description>
                      <div>创建人: {{ item.user?.userName }}</div>
                      <div>{{ dayjs(item.createTime).format('YYYY-MM-DD HH:mm') }}</div>
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a-button type="link" size="small" @click="handleViewFeatured(item.id)">
                      查看
                    </a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
            <div class="pagination-wrapper" v-if="featuredAppsTotal > 0">
              <a-pagination
                v-model:current="featuredAppParams.pageNum"
                v-model:pageSize="featuredAppParams.pageSize"
                :total="featuredAppsTotal"
                :show-total="(total) => `共 ${total} 条`"
                @change="fetchFeaturedApps"
                size="small"
              />
            </div>
          </a-card>
        </a-col>
      </a-row>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { AppstoreOutlined } from '@ant-design/icons-vue'
import {
  addApp,
  listMyAppVoByPage,
  listGoodAppVoByPage,
  deleteApp,
  getAppVoById,
} from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import dayjs from 'dayjs'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const userPrompt = ref('')
const createLoading = ref(false)

const myApps = ref<API.AppVO[]>([])
const myAppsLoading = ref(false)
const myAppsTotal = ref(0)
const myAppSearch = ref('')
const myAppParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

const featuredApps = ref<API.AppVO[]>([])
const featuredAppsLoading = ref(false)
const featuredAppsTotal = ref(0)
const featuredAppSearch = ref('')
const featuredAppParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

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
      appName: myAppSearch.value || undefined,
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
      appName: featuredAppSearch.value || undefined,
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

const handleGoToChat = (id: string) => {
  router.push({
    path: '/app/chat',
    query: { id },
  })
}

const handleDeleteMyApp = async (id: string) => {
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

const handleViewFeatured = async (id: string) => {
  if (!id) {
    return
  }
  try {
    const res = await getAppVoById({ id })
    if (res.data.code === 0 && res.data.data) {
      const app = res.data.data
      if (app.userId === loginUserStore.loginUser.id) {
        router.push({
          path: '/app/chat',
          query: { id },
        })
      } else {
        message.info('只能查看自己创建的应用')
      }
    } else {
      message.error('获取应用信息失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取应用信息失败')
  }
}

onMounted(() => {
  if (loginUserStore.loginUser.id) {
    fetchMyApps()
    fetchFeaturedApps()
  }
})
</script>

<style scoped>
#homePage {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 0;
}

.hero-section {
  text-align: center;
  padding: 80px 20px;
  color: white;
}

.site-title {
  font-size: 48px;
  font-weight: bold;
  margin-bottom: 16px;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
}

.site-description {
  font-size: 18px;
  margin-bottom: 40px;
  opacity: 0.9;
}

.prompt-input-area {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.prompt-textarea {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.create-btn {
  height: 48px;
  font-size: 16px;
  font-weight: bold;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.content-section {
  max-width: 1400px;
  margin: 0 auto;
  padding: 40px 20px;
}

.app-card {
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.pagination-wrapper {
  text-align: center;
  margin-top: 16px;
}
</style>
