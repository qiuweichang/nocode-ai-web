<template>
  <main id="userRegisterPage" class="auth-page">
    <section class="auth-copy">
      <img :src="logo" alt="NoCode" />
      <h1>一句话<br />呈所想</h1>
      <p>创建账号，保存每一次灵感、生成记录和已部署应用。</p>
      <div class="auth-preview" aria-hidden="true"><span></span><span></span><span></span></div>
    </section>

    <section class="auth-card">
      <div class="auth-card-heading">
        <h2>创建账号</h2>
        <p>开始你的第一个 AI 应用。</p>
      </div>
      <a-form :model="formState" layout="vertical" autocomplete="on" @finish="handleSubmit">
        <a-form-item label="账号" name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="formState.userAccount" size="large" autocomplete="username" placeholder="请输入账号" />
        </a-form-item>
        <a-form-item label="密码" name="userPassword" :rules="[{ required: true, message: '请输入密码' }, { min: 6, message: '密码不能少于 6 位' }]">
          <a-input-password v-model:value="formState.userPassword" size="large" autocomplete="new-password" placeholder="至少 6 位" />
        </a-form-item>
        <a-form-item label="确认密码" name="checkPassword" :rules="[{ required: true, message: '请再次输入密码' }, { min: 6, message: '密码不能少于 6 位' }]">
          <a-input-password v-model:value="formState.checkPassword" size="large" autocomplete="new-password" placeholder="再次输入密码" />
        </a-form-item>
        <a-button class="submit-button" type="primary" html-type="submit" size="large" :loading="submitting">注册</a-button>
      </a-form>
      <p class="switch-link">已经有账号？<RouterLink to="/user/login">直接登录</RouterLink></p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { userRegister } from '@/api/userController'
import logo from '@/assets/logo.png'

const formState = reactive<API.UserRegisterRequest>({ userAccount: '', userPassword: '', checkPassword: '' })
const submitting = ref(false)
const router = useRouter()

/**
 * 校验两次密码后提交注册；成功时进入登录页并保留明确的成功反馈。
 *
 * @param values 已通过基础表单规则校验的注册字段
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  if (values.userPassword !== values.checkPassword) {
    message.error('两次输入的密码不一致')
    return
  }
  submitting.value = true
  try {
    const response = await userRegister(values)
    if (response.data.code !== 0) {
      message.error('注册失败，' + response.data.message)
      return
    }
    message.success('注册成功，请登录')
    await router.replace('/user/login')
  } catch {
    message.error('注册失败，请检查网络或稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: grid;
  min-height: calc(100vh - 164px);
  grid-template-columns: minmax(0, 1.08fr) minmax(420px, 0.92fr);
  gap: 72px;
  align-items: center;
  padding: 56px max(32px, 8vw);
  background: linear-gradient(135deg, #faf9f4 0%, #f6fbf8 52%, #d8f7f1 100%);
}

.auth-copy { max-width: 650px; }
.auth-copy > img { width: 58px; height: 58px; border-radius: 18px; }
.auth-copy h1 { margin: 28px 0 18px; color: #111827; font-size: clamp(52px, 6vw, 88px); line-height: 1.02; letter-spacing: -0.05em; }
.auth-copy p { max-width: 520px; color: #717987; font-size: 18px; line-height: 1.8; }
.auth-preview { display: grid; max-width: 560px; height: 150px; grid-template-columns: 0.72fr 1.2fr 0.9fr; gap: 16px; margin-top: 34px; padding: 22px; border-radius: 28px; background: rgba(255,255,255,.88); box-shadow: 0 24px 60px rgba(72,133,140,.12); }
.auth-preview span { border-radius: 18px; background: #eef2f1; }
.auth-card { width: 100%; max-width: 480px; justify-self: end; padding: 38px 42px; border: 1px solid rgba(255,255,255,.9); border-radius: 30px; background: rgba(255,255,255,.94); box-shadow: 0 28px 72px rgba(57,94,103,.14); }
.auth-card-heading h2 { margin: 0; color: #111827; font-size: 32px; }
.auth-card-heading p { margin: 10px 0 26px; color: #8a93a3; }
.submit-button { width: 100%; min-height: 48px; margin-top: 4px; }
.switch-link { margin: 22px 0 0; color: #8a93a3; text-align: center; }
.switch-link a { margin-left: 6px; color: #146ff4; font-weight: 700; }

</style>
