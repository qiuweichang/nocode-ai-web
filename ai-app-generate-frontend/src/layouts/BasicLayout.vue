<template>
  <div id="basic-layout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
      <a-layout-footer class="footer">
        <GlobalFooter />
      </a-layout-footer>
    </a-layout>
  </div>
</template>

<script lang="ts" setup>
import GlobalHeader from '@/components/GlobalHeader.vue';
import GlobalFooter from '@/components/GlobalFooter.vue';
</script>

<style scoped>
#basic-layout .header {
  background: white;
  color: unset;
  padding-inline: 20px;
  margin-bottom: 16px;
}

#basic-layout .content {
  background: linear-gradient(to bottom, #eee, #fff);
  padding: 20px;
  margin-bottom: 60px;
}

#basic-layout .footer {
  background: #efefef;
  padding: 16px;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
}
</style>
