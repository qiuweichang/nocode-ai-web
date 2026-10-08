<template>
  <main id="userLoginPage" class="auth-page">
    <section class="auth-copy">
      <img :src="logo" alt="NoCode" />
      <h1>继续把想法<br />变成真正的应用</h1>
      <p>登录后查看你的作品、继续与 AI 对话，并随时部署或下载源码。</p>
      <div class="auth-preview" aria-hidden="true">
        <span></span><span></span><span></span>
      </div>
    </section>

    <section class="auth-card">
      <div class="auth-card-heading">
        <h2>欢迎回来</h2>
        <p>登录 NoCode，继续你的创作。</p>
      </div>
      <a-form :model="formState" layout="vertical" autocomplete="on" @finish="handleSubmit">
        <a-form-item label="账号" name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="formState.userAccount" size="large" autocomplete="username" placeholder="请输入账号" />
        </a-form-item>
        <a-form-item
          label="密码"
          name="userPassword"
          :rules="[{ required: true, message: '请输入密码' }, { min: 6, message: '密码不能少于 6 位' }]"
        >
          <a-input-password v-model:value="formState.userPassword" size="large" autocomplete="current-password" placeholder="请输入密码" />
        </a-form-item>
        <a-button class="submit-button" type="primary" html-type="submit" size="large" :loading="submitting">登录</a-button>
      </a-form>
      <p class="switch-link">还没有账号？<RouterLink to="/user/register">创建账号</RouterLink></p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { userLogin } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import logo from '@/assets/logo.png'

const formState = reactive<API.UserLoginRequest>({ userAccount: '', userPassword: '' })
const submitting = ref(false)
const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

/**
 * 提交登录表单并恢复登录前目标路由；只接受站内绝对路径，避免开放重定向。
 *
 * @param values 已通过表单规则校验的登录字段
 */
const handleSubmit = async (values: API.UserLoginRequest) => {
  submitting.value = true
  try {
    const response = await userLogin(values)
    if (response.data.code !== 0 || !response.data.data) {
      message.error('登录失败，' + response.data.message)
      return
    }
    loginUserStore.setLoginUser(response.data.data)
    message.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/'
    await router.replace(redirect)
  } catch {
    message.error('登录失败，请检查网络或稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: grid;
  min-height: calc(var(--app-viewport-height) - 164px);
  grid-template-columns: minmax(0, 1.08fr) minmax(420px, 0.92fr);
  gap: 72px;
  align-items: center;
  padding: 64px max(32px, 8vw);
  background: linear-gradient(135deg, #faf9f4 0%, #f6fbf8 52%, #d8f7f1 100%);
}

.auth-copy {
  max-width: 650px;
}

.auth-copy > img {
  width: 58px;
  height: 58px;
  border-radius: 18px;
}

.auth-copy h1 {
  margin: 28px 0 18px;
  color: #111827;
  font-size: clamp(44px, 5vw, 76px);
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.auth-copy p {
  max-width: 540px;
  color: #717987;
  font-size: 18px;
  line-height: 1.8;
}

.auth-preview {
  display: grid;
  max-width: 560px;
  height: 170px;
  grid-template-columns: 0.72fr 1.2fr 0.9fr;
  gap: 16px;
  margin-top: 36px;
  padding: 22px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 24px 60px rgba(72, 133, 140, 0.12);
}

.auth-preview span {
  border-radius: 18px;
  background: #eef2f1;
}

.auth-card {
  width: 100%;
  max-width: 480px;
  justify-self: end;
  padding: 42px;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 28px 72px rgba(57, 94, 103, 0.14);
}

.auth-card-heading h2 {
  margin: 0;
  color: #111827;
  font-size: 32px;
}

.auth-card-heading p {
  margin: 10px 0 30px;
  color: #8a93a3;
}

.submit-button {
  width: 100%;
  min-height: 48px;
  margin-top: 4px;
}

.switch-link {
  margin: 24px 0 0;
  color: #8a93a3;
  text-align: center;
}

.switch-link a {
  margin-left: 6px;
  color: #146ff4;
  font-weight: 700;
}

</style>
