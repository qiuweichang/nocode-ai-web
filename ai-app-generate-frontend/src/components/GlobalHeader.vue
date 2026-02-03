<template>
  <a-row id="global-header" align="middle" :wrap="false">
    <a-col flex="200px">
      <div class="title-bar">
        <img class="logo" src="../assets/logo.png" alt="logo" />
        <div class="title">AI 应用生成</div>
      </div>
    </a-col>
    <a-col flex="auto">
      <a-menu
        v-model:selectedKeys="current"
        mode="horizontal"
        :items="items"
        @click="handleClick"
      />
    </a-col>
    <a-col flex="100px">
      <div class="user-login-status">
        <a-button type="primary" href="/user/login">登录</a-button>
      </div>
    </a-col>
  </a-row>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import type { MenuProps } from 'ant-design-vue';
import { useRouter } from 'vue-router';

// 菜单项配置
const items = ref<MenuProps['items']>([
  {
    key: '/',
    label: '主页',
    title: '主页',
  },
  {
    key: '/about',
    label: '关于',
    title: '关于',
  },
]);

const router = useRouter();
// 当前选中的菜单项
const current = ref<string[]>(['/']);
// 监听路由变化，更新当前选中的菜单项
router.afterEach((to) => {
  current.value = [to.path]
})

// 菜单点击事件
const handleClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  current.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
};

</script>

<style scoped>
.title-bar {
  display: flex;
  align-items: center;
}

.logo {
  height: 48px;
}

.title {
  color: black;
  font-size: 20px;
  margin-left: 16px;
}
</style>
