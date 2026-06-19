<script setup lang="tsx">
import { Comic, commands } from '../../bindings.ts'
import { computed, nextTick, ref, watch, watchEffect } from 'vue'
import DownloadedComicCard from './components/DownloadedComicCard.vue'
import { PhFolderOpen } from '@phosphor-icons/vue'
import { useStore } from '../../store.ts'
import { DropdownOption, NIcon } from 'naive-ui'
import { SelectionArea, SelectionEvent } from '@viselect/vue'
import { PhChecks, PhCheck, PhX } from '@phosphor-icons/vue'
import UpdateDownloadedComicsButton from './components/UpdateDownloadedComicsButton.vue'
import { useLongPress } from '../../composables/useLongPress'

const store = useStore()

const selectedIds = ref<Set<number>>(new Set())
const checkedIds = ref<Set<number>>(new Set())
const { dropdownX, dropdownY, dropdownShowing, dropdownOptions, showDropdown, showDropdownTouch } = useDropdown()
const selectionAreaRef = ref<InstanceType<typeof SelectionArea>>()
const { onTouchStart, onTouchEnd, onTouchMove } = useLongPress(showDropdownTouch)

const PAGE_SIZE = 20
// 已下载的漫画
const downloadedComics = ref<Comic[]>([])
// 当前页码
const currentPage = ref<number>(1)
// 总页数
const pageCount = computed<number>(() => {
  if (downloadedComics.value.length === 0) {
    return 1
  }
  return Math.ceil(downloadedComics.value.length / PAGE_SIZE)
})
// 当前页的漫画
const currentPageComics = computed<Comic[]>(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  const end = start + PAGE_SIZE
  return downloadedComics.value.slice(start, end)
})
// 确保当前页码不超过总页数
watchEffect(() => {
  if (currentPage.value > pageCount.value) {
    currentPage.value = pageCount.value
  }
})

watch(currentPage, () => {
  selectedIds.value.clear()
  checkedIds.value.clear()
  selectionAreaRef.value?.selection?.clearSelection()
  selectionAreaRef.value?.$el.scrollTo({ top: 0, behavior: 'instant' })
})

// 监听标签页变化，更新下载的漫画列表
watch(
  () => store.currentTabName,
  async () => {
    if (store.currentTabName !== 'downloaded') {
      return
    }

    downloadedComics.value = await commands.getDownloadedComics()
  },
  { immediate: true },
)

async function showExportDirInFileManager() {
  if (store.config === undefined) {
    return
  }
  const result = await commands.showPathInFileManager(store.config.exportDir)
  if (result.status === 'error') {
    console.error(result.error)
  }
}

function extractIds(elements: Element[]): number[] {
  return elements
    .map((element) => element.getAttribute('data-key'))
    .filter(Boolean)
    .map(Number)
}

function updateSelectedIds({
  store: {
    changed: { added, removed },
  },
}: SelectionEvent) {
  extractIds(added).forEach((id) => selectedIds.value.add(id))
  extractIds(removed).forEach((id) => selectedIds.value.delete(id))
}

function unselectAll({ event, selection }: SelectionEvent) {
  if (!event?.ctrlKey && !event?.metaKey) {
    selection.clearSelection()
    selectedIds.value.clear()
  }
}

function checkboxChecked(comic: Comic): boolean {
  return checkedIds.value.has(comic.id)
}

function handleCheckboxClick(comic: Comic) {
  if (checkedIds.value.has(comic.id)) {
    checkedIds.value.delete(comic.id)
  } else {
    checkedIds.value.add(comic.id)
  }
}

function handleContextMenu(comic: Comic) {
  if (selectedIds.value.has(comic.id)) {
    return
  }

  selectedIds.value.clear()
  selectedIds.value.add(comic.id)
}

async function exportCbz() {
  if (checkedIds.value.size === 0) {
    return
  }

  store.progressesPaneTabName = 'export'
  const comics = currentPageComics.value.filter((comic) => checkedIds.value.has(comic.id))
  for (const comic of comics) {
    const result = await commands.exportCbz(comic)
    if (result.status === 'error') {
      console.error(result.error)
      return
    }
  }
}

async function exportPdf() {
  if (checkedIds.value.size === 0) {
    return
  }

  store.progressesPaneTabName = 'export'
  const comics = currentPageComics.value.filter((comic) => checkedIds.value.has(comic.id))
  for (const comic of comics) {
    const result = await commands.exportPdf(comic)
    if (result.status === 'error') {
      console.error(result.error)
      return
    }
  }
}

function useDropdown() {
  const dropdownX = ref<number>(0)
  const dropdownY = ref<number>(0)
  const dropdownShowing = ref<boolean>(false)
  const dropdownOptions: DropdownOption[] = [
    {
      label: '勾选',
      key: 'check',
      icon: () => (
        <NIcon size="20">
          <PhCheck />
        </NIcon>
      ),
      props: {
        onClick: () => {
          selectedIds.value.forEach((id) => checkedIds.value.add(id))
          dropdownShowing.value = false
        },
      },
    },
    {
      label: '取消勾选',
      key: 'uncheck',
      icon: () => (
        <NIcon size="20">
          <PhX />
        </NIcon>
      ),
      props: {
        onClick: () => {
          selectedIds.value.forEach((id) => checkedIds.value.delete(id))
          dropdownShowing.value = false
        },
      },
    },
    {
      label: '全选',
      key: 'select-all',
      icon: () => (
        <NIcon size="20">
          <PhChecks />
        </NIcon>
      ),
      props: {
        onClick: () => {
          currentPageComics.value.forEach((comic) => selectedIds.value.add(comic.id))
          dropdownShowing.value = false
        },
      },
    },
  ]

  async function showDropdown(e: MouseEvent) {
    dropdownShowing.value = false
    await nextTick()
    dropdownShowing.value = true
    dropdownX.value = e.clientX
    dropdownY.value = e.clientY
  }

  // 触屏长按：从 TouchEvent 取坐标
  async function showDropdownTouch(e: TouchEvent) {
    const touch = e.touches[0] || e.changedTouches[0]
    if (!touch) return
    dropdownShowing.value = false
    await nextTick()
    dropdownShowing.value = true
    dropdownX.value = touch.clientX
    dropdownY.value = touch.clientY
  }

  return {
    dropdownX,
    dropdownY,
    dropdownShowing,
    dropdownOptions,
    showDropdown,
    showDropdownTouch,
  }
}
</script>

<template>
  <div v-if="store.config !== undefined" class="h-full flex flex-col">
    <div class="flex gap-2 box-border px-2 pt-2">
      <n-input-group>
        <n-input-group-label size="medium">导出目录</n-input-group-label>
        <!-- Android 上 dialog 不支持目录选择，改为可编辑输入，用户手动填路径。
             打开按钮仍可调起文件管理器查看。 -->
        <n-input v-model:value="store.config.exportDir" size="medium" />
        <n-button class="w-12" size="medium" @click="showExportDirInFileManager">
          <template #icon>
            <n-icon size="20">
              <PhFolderOpen />
            </n-icon>
          </template>
        </n-button>
      </n-input-group>
      <update-downloaded-comics-button />
    </div>
    <div class="flex gap-2 items-center px-2 py-1 select-none">
      <div class="animate-pulse text-sm text-red flex flex-col">
        <div>长按打开菜单，点击勾选</div>
        <div>右边的按钮作用于勾选项</div>
      </div>
      <n-button class="ml-auto" type="primary" size="medium" @click="exportCbz">导出cbz</n-button>
      <n-button type="primary" size="medium" @click="exportPdf">导出pdf</n-button>
    </div>
    <SelectionArea
      class="flex flex-col overflow-auto box-border px-2 selection-container mb-2"
      ref="selectionAreaRef"
      :options="{ selectables: '.selectable', features: { deselectOnBlur: true } }"
      @contextmenu="showDropdown"
      @touchstart="onTouchStart"
      @touchend="onTouchEnd"
      @touchmove="onTouchMove"
      @move="updateSelectedIds"
      @start="unselectAll">
      <DownloadedComicCard
        v-for="comic in currentPageComics"
        :key="comic.id"
        :data-key="comic.id"
        :class="['selectable mb-2', selectedIds.has(comic.id) ? 'selected shadow-md' : 'hover:bg-gray-1']"
        :comic="comic"
        :checkbox-checked="checkboxChecked"
        :handle-checkbox-click="handleCheckboxClick"
        :handle-context-menu="handleContextMenu" />
    </SelectionArea>

    <n-pagination
      class="box-border p-2 pt-0 mt-auto"
      :page-count="pageCount"
      :page="currentPage"
      @update:page="currentPage = $event" />

    <n-dropdown
      placement="bottom-start"
      trigger="manual"
      :x="dropdownX"
      :y="dropdownY"
      :options="dropdownOptions"
      :show="dropdownShowing"
      :on-clickoutside="() => (dropdownShowing = false)" />
  </div>
</template>

<style scoped>
.selection-container {
  @apply select-none overflow-auto;
  /* 触屏优化：垂直滚动优先于框选 */
  touch-action: pan-y;
  -webkit-overflow-scrolling: touch;
}

.selection-container .selected {
  @apply bg-[rgb(204,232,255)];
}
</style>