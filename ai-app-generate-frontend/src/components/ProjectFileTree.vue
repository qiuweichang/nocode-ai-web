<template>
  <ul class="project-file-tree">
    <li v-for="node in nodes" :key="node.path">
      <button
        type="button"
        class="file-tree-row"
        :class="{ active: !node.directory && node.path === selectedPath }"
        :style="{ paddingLeft: `${10 + depth * 14}px` }"
        @click="handleNodeClick(node)"
      >
        <CaretRightOutlined
          v-if="node.directory"
          class="tree-caret"
          :class="{ expanded: isPathExpanded(node.path) }"
        />
        <span v-else class="tree-caret-placeholder"></span>
        <FolderOpenOutlined v-if="node.directory && isPathExpanded(node.path)" class="folder-icon" />
        <FolderOutlined v-else-if="node.directory" class="folder-icon" />
        <FileTextOutlined v-else class="file-icon" />
        <span class="file-name">{{ node.name }}</span>
        <span v-if="!node.directory && !node.editable" class="binary-label">只读</span>
      </button>
      <ProjectFileTree
        v-if="node.directory && isPathExpanded(node.path) && node.children?.length"
        :nodes="node.children"
        :selected-path="selectedPath"
        :depth="depth + 1"
        @select="emit('select', $event)"
      />
    </li>
  </ul>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue'
import {
  CaretRightOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  FolderOutlined,
} from '@ant-design/icons-vue'

defineOptions({ name: 'ProjectFileTree' })

/** 文件树组件输入，depth 用于递归层级缩进。 */
const props = withDefaults(
  defineProps<{
    nodes: API.ProjectFileVO[]
    selectedPath?: string
    depth?: number
  }>(),
  { selectedPath: '', depth: 0 },
)

/** 文件选择事件只向上传递可在线编辑的文本文件。 */
const emit = defineEmits<{
  select: [node: API.ProjectFileVO]
}>()

/** 当前递归层级展开的目录路径集合。 */
const expandedPaths = ref(new Set<string>())

/**
 * 安全判断可选目录路径是否已展开，避免把后端可选字段直接传给 Set。
 *
 * @param path 目录相对路径
 * @returns 路径存在且处于展开集合时返回 true
 */
const isPathExpanded = (path?: string) => Boolean(path && expandedPaths.value.has(path))

/**
 * 点击目录时切换展开状态；点击文件时把节点交给父级加载内容。
 *
 * @param node 当前点击的文件树节点
 */
const handleNodeClick = (node: API.ProjectFileVO) => {
  if (node.directory) {
    const nextPaths = new Set(expandedPaths.value)
    if (nextPaths.has(node.path || '')) {
      nextPaths.delete(node.path || '')
    } else if (node.path) {
      nextPaths.add(node.path)
    }
    expandedPaths.value = nextPaths
    return
  }
  emit('select', node)
}

/**
 * 首次收到目录数据时默认展开全部目录，让用户立即看到生成项目中的所有文件。
 */
watch(
  () => props.nodes,
  (nodes) => {
    const nextPaths = new Set(expandedPaths.value)
    nodes.filter((node) => node.directory && node.path).forEach((node) => nextPaths.add(node.path!))
    expandedPaths.value = nextPaths
  },
  { immediate: true },
)
</script>

<style scoped>
.project-file-tree {
  margin: 0;
  padding: 0;
  list-style: none;
}

.file-tree-row {
  display: flex;
  width: 100%;
  height: 30px;
  align-items: center;
  gap: 6px;
  padding-right: 8px;
  border: 0;
  background: transparent;
  color: #374151;
  cursor: pointer;
  font-size: 12px;
  text-align: left;
}

.file-tree-row:hover {
  background: #f3f6fa;
}

.file-tree-row.active {
  background: #eaf2ff;
  color: #1859b8;
}

.tree-caret,
.tree-caret-placeholder {
  width: 10px;
  flex: 0 0 10px;
}

.tree-caret {
  color: #9ca3af;
  font-size: 9px;
  transition: transform 0.16s ease;
}

.tree-caret.expanded {
  transform: rotate(90deg);
}

.folder-icon {
  color: #e1a31c;
}

.file-icon {
  color: #7b8ca5;
}

.file-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.binary-label {
  margin-left: auto;
  color: #a0a8b5;
  font-size: 10px;
}
</style>
