<script setup lang="tsx">
import { onMounted, ref, watch } from 'vue'
import { commands } from './bindings.ts'
import { useMessage, useNotification } from 'naive-ui'
import LoginDialog from './dialogs/LoginDialog.vue'
import SearchPane from './panes/SearchPane.vue'
import ChapterPane from './panes/ChapterPane.vue'
import ProgressesPane from './panes/ProgressesPane/ProgressesPane.vue'
import FavoritePane from './panes/FavoritePane.vue'
import AboutDialog from './dialogs/AboutDialog.vue'
import { PhInfo, PhUser, PhClockCounterClockwise } from '@phosphor-icons/vue'
import DownloadedPane from './panes/DownloadedPane/DownloadedPane.vue'
import { useStore } from './store.ts'
import LogDialog from './dialogs/LogDialog.vue'
import WeeklyPane from './panes/WeeklyPane.vue'

const store = useStore()

const message = useMessage()
const notification = useNotification()

const loginDialogShowing = ref<boolean>(false)
const aboutDialogShowing = ref<boolean>(false)
const logViewerShowing = ref<boolean>(false)

watch(
  () => store.config,
  async () => {
    if (store.config === undefined) {
      return
    }

    try {
      const result = await commands.saveConfig(store.config)
      if (result.status === 'error') {
        console.error(result.error)
        return
      }
      message.success('保存配置成功')
    } catch (e) {
      console.error('保存配置失败', e)
    }
  },
  { deep: true },
)

onMounted(async () => {
  // 屏蔽浏览器右键菜单
  document.oncontextmenu = (event) => {
    event.preventDefault()
  }
  // 获取配置 —— 失败时记录错误并显示错误界面，而不是让屏幕一直白屏
  try {
    const cfg = await commands.getConfig()
    store.config = cfg
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    store.initError = `加载配置失败: ${msg}`
    console.error(store.initError, e)
    return
  }
  // 如果username和password不为空，尝试登录
  if (store.config.username !== '' && store.config.password !== '') {
    try {
      const result = await commands.login(store.config.username, store.config.password)
      if (result.status === 'error') {
        console.error(result.error)
        return
      }
      store.userProfile = result.data
      message.success('自动登录成功')
    } catch (e) {
      console.error('自动登录异常', e)
    }
  }
})

onMounted(async () => {
  // 检查日志目录大小
  const result = await commands.getLogsDirSize()
  if (result.status === 'error') {
    console.error(result.error)
    return
  }
  if (result.data > 50 * 1024 * 1024) {
    notification.warning({
      title: '日志目录大小超过50MB，请及时清理日志文件',
      description: () => (
        <>
          <div>
            点击右上角的 <span class="bg-gray-2 px-1">日志</span> 按钮
          </div>
          <div>
            里边有 <span class="bg-gray-2 px-1">打开日志目录</span> 按钮
          </div>
          <div>
            你也可以在里边取消勾选 <span class="bg-gray-2 px-1">输出文件日志</span>
          </div>
          <div>这样将不再产生文件日志</div>
        </>
      ),
    })
  }
})
</script>

<template>
  <!-- 初始化错误：显示错误信息而非白屏 -->
  <div v-if="store.initError" class="h-full flex items-center justify-center p-4">
    <div class="max-w-md text-center">
      <h2 class="text-lg font-bold text-red-5 mb-2">应用初始化失败</h2>
      <pre class="text-sm text-gray-6 whitespace-pre-wrap break-all">{{ store.initError }}</pre>
      <p class="text-xs text-gray-4 mt-4">请截图反馈此错误信息</p>
    </div>
  </div>
  <!-- 配置加载中 -->
  <div v-else-if="store.config === undefined" class="h-full flex items-center justify-center">
    <span class="text-gray-4">加载中...</span>
  </div>
  <!-- 正常界面：手机单栏堆叠，PC(md+)左右双栏 -->
  <div v-else class="h-full flex flex-col md:flex-row overflow-hidden">
    <n-tabs class="h-full w-full md:w-1/2" v-model:value="store.currentTabName" type="line" size="medium" animated>
      <n-tab-pane class="h-full overflow-auto p-0!" name="search" tab="搜索" display-directive="show">
        <SearchPane />
      </n-tab-pane>
      <n-tab-pane class="h-full overflow-auto p-0!" name="favorite" tab="收藏夹" display-directive="show">
        <FavoritePane />
      </n-tab-pane>
      <n-tab-pane class="h-full overflow-auto p-0!" name="weekly" tab="每周必看" display-directive="show">
        <WeeklyPane />
      </n-tab-pane>
      <n-tab-pane class="h-full overflow-auto p-0!" name="downloaded" tab="本地库存" display-directive="show">
        <DownloadedPane />
      </n-tab-pane>
      <n-tab-pane class="h-full overflow-auto p-0!" name="chapter" tab="章节详情" display-directive="show">
        <ChapterPane />
      </n-tab-pane>
    </n-tabs>
    <div class="w-full md:w-1/2 overflow-auto flex flex-col">
      <div class="flex flex-wrap px-2 py-1 gap-2 items-center">
        <n-button type="primary" size="medium" @click="loginDialogShowing = true">
          <template #icon>
            <n-icon>
              <PhUser />
            </n-icon>
          </template>
          登录
        </n-button>
        <n-button size="medium" @click="logViewerShowing = true">
          <template #icon>
            <n-icon size="20">
              <PhClockCounterClockwise />
            </n-icon>
          </template>
          日志
        </n-button>
        <n-button size="medium" @click="aboutDialogShowing = true">
          <template #icon>
            <n-icon size="20">
              <PhInfo />
            </n-icon>
          </template>
          关于
        </n-button>
        <div v-if="store.userProfile !== undefined" class="flex items-center ml-auto overflow-hidden">
          <n-avatar
            class="flex-shrink-0"
            round
            :size="40"
            :src="store.userProfile.photo"
            fallback-src="https://cdn-msp.18comic.vip/templates/frontend/airav/img/title-png/more-ms-jm.webp?v=2" />
          <span class="whitespace-nowrap text-ellipsis overflow-hidden" :title="store.userProfile.username">
            {{ store.userProfile.username }}
          </span>
        </div>
      </div>
      <ProgressesPane />
    </div>
    <LoginDialog v-model:showing="loginDialogShowing" />
    <AboutDialog v-model:showing="aboutDialogShowing" />
    <LogDialog v-model:showing="logViewerShowing" />
  </div>
</template>

<style scoped>
:global(.n-notification-main__header) {
  @apply break-words;
}

:global(.n-tabs-pane-wrapper) {
  @apply h-full;
}

/* 触屏滚动修复：n-tabs 用 flex 布局，nav 固定高度，pane-wrapper 填充剩余空间。
   高度约束从 n-tabs→pane-wrapper→tab-pane 层层传递，
   overflow-auto 的 pane 才能触发触摸滚动(否则 pane 高度=内容高度，不溢出)。 */
:deep(.n-tabs) {
  @apply flex flex-col h-full overflow-hidden;
}
:deep(.n-tabs-nav) {
  @apply flex-shrink-0 px-2 py-1;
}
:deep(.n-tabs-pane-wrapper) {
  @apply flex-1 min-h-0 overflow-hidden;
}
:deep(.n-tab-pane) {
  @apply h-full overflow-auto;
}

/* 触屏优化：tab 标签加大可点击区域 */
:deep(.n-tabs-tab) {
  @apply text-base;
}
</style>
