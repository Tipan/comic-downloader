<script setup lang="ts">
import { commands } from '../bindings.ts'
import { PhFolderOpen, PhGearSix } from '@phosphor-icons/vue'
import { useStore } from '../store.ts'

const store = useStore()
const emit = defineEmits<{ 'open-settings': [] }>()

async function showDownloadDirInFileManager() {
  if (store.config === undefined) {
    return
  }
  const result = await commands.showPathInFileManager(store.config.downloadDir)
  if (result.status === 'error') {
    console.error(result.error)
  }
}
</script>

<template>
  <div v-if="store.config !== undefined" class="flex gap-2 box-border px-2 py-1 overflow-hidden">
    <n-input-group class="">
      <n-input-group-label size="medium">下载目录</n-input-group-label>
      <n-input v-model:value="store.config.downloadDir" size="medium" />
      <n-button class="w-12" size="medium" @click="showDownloadDirInFileManager">
        <template #icon>
          <n-icon size="20">
            <PhFolderOpen />
          </n-icon>
        </template>
      </n-button>
    </n-input-group>
    <n-button size="medium" @click="emit('open-settings')">
      <template #icon>
        <n-icon size="20">
          <PhGearSix />
        </n-icon>
      </template>
      配置
    </n-button>
  </div>
</template>
