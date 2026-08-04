<template>
  <div class="global-footer">
    <strong>NoCode</strong>
    <span>让每一个想法，都能成为可以使用的应用。</span>
    <a href="mailto:963906940@qq.com">联系作者</a>
  </div>
</template>

<script lang="ts" setup>
// 全局底部栏组件
</script>

<style scoped>
.global-footer {
  display: flex;
  min-height: 92px;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 24px;
  border-top: 1px solid #eef0f2;
  background: #fff;
  color: #8a93a3;
  text-align: center;
}

.global-footer strong {
  color: #111827;
}

.global-footer a {
  color: #4f78a8;
}

</style>
