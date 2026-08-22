<script setup lang="tsx">
import { events } from '../../../bindings.ts'
import { onMounted, ref, watchEffect, nextTick } from 'vue'
import { NIcon, DropdownOption } from 'naive-ui'
import { PhChecks, PhTrash } from '@phosphor-icons/vue'
import { SelectionArea, SelectionEvent } from '@viselect/vue'
import ExportProgress from './ExportProgress.vue'
import { useLongPress, clampDropdownPos } from '../../../composables/useLongPress'

type ProgressState = 'Processing' | 'Error' | 'End'

export interface ProgressData {
  uuid: string
  exportType: 'cbz' | 'pdf'
  state: ProgressState
  comicTitle: string
  current: number
  total: number
  percentage: number
  indicator: string
  chapterExportDir?: string
}

const selectedIds = ref<Set<string>>(new Set())
const { dropdownX, dropdownY, dropdownShowing, dropdownOptions, showDropdown, showDropdownTouch } = useDropdown()
const { onTouchStart, onTouchEnd, onTouchMove } = useLongPress(showDropdownTouch)

const progresses = ref<Map<string, ProgressData>>(new Map())

watchEffect(() => {
  // 保证selectedIds中的uuid在progresses中存在
  const uuids = new Set(progresses.value.keys())
  for (const uuid of selectedIds.value) {
    if (!uuids.has(uuid)) {
      selectedIds.value.delete(uuid)
    }
  }
})

// 处理导出CBZ事件
async function handleExportCbzEvents() {
  await events.exportCbzEvent.listen(async ({ payload: exportEvent }) => {
    if (exportEvent.event === 'Start') {
      const { uuid, comicTitle, total } = exportEvent.data
      progresses.value.set(uuid, {
        uuid,
        exportType: 'cbz',
        state: 'Processing',
        comicTitle,
        current: 0,
        total,
        percentage: 0,
        indicator: 'CBZ创建CBZ中',
      })
    } else if (exportEvent.event === 'Progress') {
      const { uuid, current } = exportEvent.data
      const progressData = progresses.value.get(uuid)
      if (progressData !== undefined) {
        progressData.state = 'Processing'
        progressData.current = current
        progressData.percentage = (current / progressData.total) * 100
        progressData.indicator = `CBZ创建中 ${current}/${progressData.total}`
      }
    } else if (exportEvent.event === 'Error') {
      const { uuid } = exportEvent.data
      const progressData = progresses.value.get(uuid)
      if (progressData !== undefined) {
        progressData.state = 'Error'
        progressData.indicator = 'CBZ创建失败'
      }
    } else if (exportEvent.event === 'End') {
      const { uuid, chapterExportDir } = exportEvent.data
      const progressData = progresses.value.get(uuid)
      if (progressData !== undefined) {
        progressData.state = 'End'
        progressData.chapterExportDir = chapterExportDir
        progressData.indicator = 'CBZ创建完成'
      }
    }
  })
}

// 处理导出PDF事件
async function handleExportPdfEvents() {
  await events.exportPdfEvent.listen(async ({ payload: exportEvent }) => {
    if (exportEvent.event === 'CreateStart') {
      const { uuid, comicTitle, total } = exportEvent.data
      progresses.value.set(uuid, {
        uuid,
        exportType: 'pdf',
        state: 'Processing',
        comicTitle,
        current: 0,
        total,
        percentage: 0,
        indicator: 'PDF创建中',
      })
    } else if (exportEvent.event === 'CreateProgress') {
      const { uuid, current } = exportEvent.data
      const progressData = progresses.value.get(uuid)
      if (progressData !== undefined) {
        progressData.state = 'Processing'
        progressData.current = current
        progressData.percentage = (current / progressData.total) * 100
        progressData.indicator = `PDF创建中 ${current}/${progressData.total}`
      }
    } else if (exportEvent.event === 'CreateError') {
      const { uuid } = exportEvent.data
      const progressData = progresses.value.get(uuid)
      if (progressData !== undefined) {
        progressData.state = 'Error'
        progressData.indicator = '创建PDF失败'
      }
    } else if (exportEvent.event === 'CreateEnd') {
      const { uuid, chapterExportDir } = exportEvent.data
      const progressData = progresses.value.get(uuid)
      if (progressData !== undefined) {
        progressData.state = 'End'
        progressData.chapterExportDir = chapterExportDir
        progressData.indicator = 'PDF创建完成'
      }
    } else if (exportEvent.event === 'MergeStart') {
      const { uuid, comicTitle } = exportEvent.data
      progresses.value.set(uuid, {
        uuid,
        exportType: 'pdf',
        state: 'Processing',
        comicTitle,
        current: 0,
        total: 1,
        percentage: 0,
        indicator: 'PDF合并中',
      })
    } else if (exportEvent.event === 'MergeError') {
      const { uuid } = exportEvent.data
      const progressData = progresses.value.get(uuid)
      if (progressData !== undefined) {
        progressData.state = 'Error'
        progressData.indicator = 'PDF合并失败'
      }
    } else if (exportEvent.event === 'MergeEnd') {
      const { uuid, chapterExportDir } = exportEvent.data
      const progressData = progresses.value.get(uuid)
      if (progressData !== undefined) {
        progressData.state = 'End'
        progressData.current = progressData.total
        progressData.percentage = 100
        progressData.chapterExportDir = chapterExportDir
        progressData.indicator = 'PDF合并完成'
      }
    }
  })
}

// 监听导出事件
onMounted(async () => {
  await handleExportCbzEvents()
  await handleExportPdfEvents()
})

function extractIds(elements: Element[]): string[] {
  return elements
    .map((element) => element.getAttribute('data-key'))
    .filter(Boolean)
    .filter((uuid) => uuid !== null)
}

function updateSelectedIds({
  store: {
    changed: { added, removed },
  },
}: SelectionEvent) {
  extractIds(added).forEach((uuid) => selectedIds.value.add(uuid))
  extractIds(removed).forEach((uuid) => selectedIds.value.delete(uuid))
}

function unselectAll({ event, selection }: SelectionEvent) {
  if (!event?.ctrlKey && !event?.metaKey) {
    selection.clearSelection()
    selectedIds.value.clear()
  }
}

function handleProgressContextMenu(p: ProgressData) {
  if (selectedIds.value.has(p.uuid)) {
    return
  }
  selectedIds.value.clear()
  selectedIds.value.add(p.uuid)
}

function useDropdown() {
  const dropdownX = ref<number>(0)
  const dropdownY = ref<number>(0)
  const dropdownShowing = ref<boolean>(false)
  const dropdownOptions: DropdownOption[] = [
    {
      label: '全选',
      key: 'check all',
      icon: () => (
        <NIcon size="20">
          <PhChecks />
        </NIcon>
      ),
      props: {
        onClick: () => {
          progresses.value.forEach((_, uuid) => selectedIds.value.add(uuid))
          dropdownShowing.value = false
        },
      },
    },
    {
      label: '删除',
      key: 'delete',
      icon: () => (
        <NIcon size="20">
          <PhTrash />
        </NIcon>
      ),
      props: {
        onClick: () => {
          selectedIds.value.forEach((uuid) => progresses.value.delete(uuid))
          dropdownShowing.value = false
        },
      },
    },
  ]

  async function showDropdown(e: MouseEvent) {
    if ('ontouchstart' in window) return
    dropdownShowing.value = false
    await nextTick()
    dropdownShowing.value = true
    const pos = clampDropdownPos(e.clientX, e.clientY)
    dropdownX.value = pos.x
    dropdownY.value = pos.y
  }

  // 触屏长按：从 TouchEvent 取坐标，找到触摸点的 item 并选中
  async function showDropdownTouch(e: TouchEvent) {
    const touch = e.touches[0] || e.changedTouches[0]
    if (!touch) return
    // 用坐标找到触摸的 item(.selectable[data-key])
    const el = document.elementFromPoint(touch.clientX, touch.clientY)
    const selectable = el?.closest('.selectable')
    if (selectable) {
      const uuid = selectable.getAttribute('data-key')
      if (uuid) {
        // 长按时选中当前 item(单选，替换之前的选中)
        selectedIds.value.clear()
        selectedIds.value.add(uuid)
      }
    }
    dropdownShowing.value = false
    await nextTick()
    dropdownShowing.value = true
    const pos = clampDropdownPos(touch.clientX, touch.clientY)
    dropdownX.value = pos.x
    dropdownY.value = pos.y
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
  <SelectionArea
    class="h-full flex flex-col selection-container px-2"
    :options="{ selectables: '.selectable', features: { deselectOnBlur: true, touch: false } }"
    @contextmenu="showDropdown"
    @touchstart="onTouchStart"
    @touchend="onTouchEnd"
    @touchmove="onTouchMove"
    @move="updateSelectedIds"
    @start="unselectAll">
    <div class="flex">
      <span class="ml-auto animate-pulse text-red">长按打开菜单</span>
    </div>

    <ExportProgress
      v-for="[uuid, p] in progresses"
      :key="uuid"
      :data-key="uuid"
      :class="['selectable ', selectedIds.has(uuid) ? 'selected shadow-md' : 'hover:bg-gray-1']"
      :p="p"
      :handle-context-menu="handleProgressContextMenu" />

    <n-dropdown
      placement="bottom-start"
      trigger="manual"
      :x="dropdownX"
      :y="dropdownY"
      :options="dropdownOptions"
      :show="dropdownShowing"
      :on-clickoutside="() => (dropdownShowing = false)" />
  </SelectionArea>
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
