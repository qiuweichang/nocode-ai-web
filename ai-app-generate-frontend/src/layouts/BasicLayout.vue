<template>
  <a-layout class="basic-layout">
    <!-- 顶部导航栏 -->
    <GlobalHeader v-if="!immersive || showGlobalHeader" />
    <!-- 主要内容区域 -->
    <a-layout-content class="main-content">
      <router-view />
    </a-layout-content>
    <!-- 底部版权信息 -->
    <GlobalFooter v-if="!immersive" />
  </a-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const route = useRoute()

/** 沉浸式工作台自行提供导航和操作区，不重复渲染全局头尾。 */
const immersive = computed(() => Boolean(route.meta.immersive))

/** 沉浸式工作台可单独要求保留全局导航，聊天页由此与首页保持一致的首屏入口。 */
const showGlobalHeader = computed(() => Boolean(route.meta.showGlobalHeader))
</script>

<style scoped>
.basic-layout {
  background: none;
}

.main-content {
  width: 100%;
  padding: 0;
  background: none;
  margin: 0;
}
</style>
