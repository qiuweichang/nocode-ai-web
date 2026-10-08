<template>
  <header class="global-header">
    <RouterLink class="brand" to="/" aria-label="返回首页">
      <img class="brand-logo" :src="logo" alt="NoCode" />
      <span>NoCode</span>
    </RouterLink>

    <nav class="main-nav" aria-label="主导航">
      <button :class="{ active: route.path === '/' }" @click="goHome">首页</button>
      <button @click="scrollToSection('my-work')">我的作品</button>
      <button @click="scrollToSection('showcase')">精选案例</button>
      <a-dropdown v-if="isAdmin">
        <button class="admin-trigger">管理后台 <DownOutlined /></button>
        <template #overlay>
          <a-menu @click="handleAdminMenuClick">
            <a-menu-item key="/admin/userManage">用户管理</a-menu-item>
            <a-menu-item key="/admin/appManage">应用管理</a-menu-item>
            <a-menu-item key="/admin/chatManage">对话管理</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </nav>

    <div class="account-area">
      <a-dropdown v-if="loginUserStore.loginUser.id" placement="bottomRight">
        <button class="account-button">
          <a-avatar :size="32" class="default-account-avatar">
            <UserOutlined />
          </a-avatar>
          <span>{{ loginUserStore.loginUser.userName || '用户' }}</span>
          <DownOutlined />
        </button>
        <template #overlay>
          <a-menu>
            <a-menu-item key="logout" @click="doLogout">
              <LogoutOutlined />
              退出登录
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
      <RouterLink v-else class="login-button" to="/user/login">登录 / 注册</RouterLink>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, type MenuProps } from 'ant-design-vue'
import { DownOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { userLogout } from '@/api/userController'
import logo from '@/assets/logo.png'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

/** 返回首页；已经在首页时保持当前位置，避免无意义的整页刷新。 */
const goHome = () => {
  if (route.path !== '/') {
    void router.push('/')
  }
}

/**
 * 滚动到首页目标区块；从其他页面触发时先回首页，再等待视图完成挂载。
 *
 * @param sectionId 首页区块 DOM id
 */
const scrollToSection = async (sectionId: string) => {
  if (route.path !== '/') {
    await router.push('/')
    window.setTimeout(() => document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' }), 80)
    return
  }
  document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' })
}

/** 从管理员下拉菜单跳转到选中的管理页面。 */
const handleAdminMenuClick: MenuProps['onClick'] = ({ key }) => {
  void router.push(String(key))
}

/** 注销当前会话并回到公开首页。 */
const doLogout = async () => {
  try {
    const response = await userLogout()
    if (response.data.code !== 0) {
      message.error('退出登录失败，' + response.data.message)
      return
    }
    loginUserStore.setLoginUser({ userName: '未登录' })
    message.success('已退出登录')
    await router.push('/')
  } catch {
    message.error('退出登录失败')
  }
}
</script>

<style scoped>
.global-header {
  position: relative;
  z-index: 20;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  min-height: 58px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(17, 24, 39, 0.08);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(18px);
}

.brand {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 8px;
  color: #111827;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.brand-logo {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  object-fit: cover;
}

.main-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.main-nav button,
.account-button {
  border: none;
  background: transparent;
  color: #5f6673;
  cursor: pointer;
  font: inherit;
}

.main-nav button {
  padding: 8px 14px;
  border-radius: 10px;
  font-size: 13px;
  transition: color 0.2s ease, background 0.2s ease;
}

.main-nav button:hover,
.main-nav button.active {
  color: #111827;
  background: #f5f6f7;
}

.admin-trigger :deep(svg) {
  width: 10px;
}

.account-area {
  display: flex;
  justify-content: flex-end;
}

.account-button {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 5px 8px 5px 5px;
  border-radius: 14px;
}

.account-button:hover {
  background: #f5f6f7;
}

/* 所有账号统一使用灰色人物轮廓，确保导航栏视觉一致且不受历史头像数据影响。 */
.default-account-avatar {
  border: 1px solid #d7dbe1;
  background: #e5e7eb;
  color: #6b7280;
}

.login-button {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  padding: 0 18px;
  border-radius: 12px;
  background: #146ff4;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  box-shadow: 0 8px 20px rgba(20, 111, 244, 0.2);
}

</style>
