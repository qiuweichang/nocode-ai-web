<template>
  <div id="appUpdatePage">
    <a-card title="应用信息修改">
      <a-form
        :model="formData"
        :label-col="{ span: 4 }"
        :wrapper-col="{ span: 16 }"
        @finish="handleSubmit"
      >
        <a-form-item label="应用名称" name="appName">
          <a-input
            v-model:value="formData.appName"
            placeholder="请输入应用名称"
            :disabled="loading"
          />
        </a-form-item>
        <a-form-item v-if="isAdmin" label="应用封面" name="cover">
          <a-input
            v-model:value="formData.cover"
            placeholder="请输入应用封面URL"
            :disabled="loading"
          />
        </a-form-item>
        <a-form-item v-if="isAdmin" label="优先级" name="priority">
          <a-input-number
            v-model:value="formData.priority"
            :min="0"
            :max="99"
            placeholder="请输入优先级"
            :disabled="loading"
            style="width: 100%"
          />
          <template #extra>
            <span>99 为精选应用</span>
          </template>
        </a-form-item>
        <a-form-item :wrapper-col="{ span: 16, offset: 4 }">
          <a-space>
            <a-button type="primary" html-type="submit" :loading="loading"> 提交 </a-button>
            <a-button @click="handleCancel">取消</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getAppVoById, updateApp, updateAppByAdmin } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appId = ref<string>(route.query.id as string)
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')
const loading = ref(false)

const formData = reactive<API.AppUpdateRequest>({
  id: undefined,
  appName: '',
})

const fetchAppData = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  try {
    const res = await getAppVoById({ id: appId.value })
    if (res.data.code === 0 && res.data.data) {
      const appData = res.data.data
      formData.id = appData.id
      formData.appName = appData.appName || ''

      if (isAdmin.value) {
        formData.cover = appData.cover || ''
        formData.priority = appData.priority || 0
      } else {
        if (appData.userId !== loginUserStore.loginUser.id) {
          message.error('无权编辑此应用')
          router.push('/')
        }
      }
    } else {
      message.error('获取应用信息失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取应用信息失败')
  }
}

const handleSubmit = async () => {
  if (!formData.appName?.trim()) {
    message.error('应用名称不能为空')
    return
  }

  loading.value = true
  try {
    let res
    if (isAdmin.value) {
      res = await updateAppByAdmin({
        id: formData.id,
        appName: formData.appName,
        cover: formData.cover,
        priority: formData.priority,
      })
    } else {
      res = await updateApp({
        id: formData.id,
        appName: formData.appName,
      })
    }

    if (res.data.code === 0) {
      message.success('更新成功')
      router.back()
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

onMounted(() => {
  fetchAppData()
})
</script>

<style scoped>
#appUpdatePage {
  padding: 24px;
  background: white;
  margin-top: 16px;
}
</style>
