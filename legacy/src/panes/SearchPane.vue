<script setup lang="ts">
import { computed, ref } from 'vue'
import { commands, SearchSort } from '../bindings.ts'
import { useMessage } from 'naive-ui'
import ComicCard from '../components/ComicCard.vue'
import FloatLabelInput from '../components/FloatLabelInput.vue'
import { PhMagnifyingGlass } from '@phosphor-icons/vue'
import { useStore } from '../store.ts'

const store = useStore()

const message = useMessage()

const searchInput = ref<string>('')
const searching = ref<boolean>(false)
const sortSelected = ref<SearchSort>('Latest')
const searchPage = ref<number>(1)

const searchPageCount = computed(() => {
  const PAGE_SIZE = 80
  if (store.searchResult === undefined) {
    return 0
  }
  const total = store.searchResult.total
  return Math.ceil(total / PAGE_SIZE)
})

async function search(keyword: string, page: number, sort: SearchSort) {
  if (searching.value) {
    message.warning('有搜索正在进行，请稍后再试')
    return
  }

  searching.value = true
  console.log(keyword, page, sort)
  searchPage.value = page

  const result = await commands.search(keyword, page, sort)
  if (result.status === 'error') {
    console.error(result.error)
    searching.value = false
    return
  }
  const searchResultVariant = result.data
  if ('SearchResult' in searchResultVariant) {
    const respData = searchResultVariant.SearchResult
    if (respData.content.length === 0) {
      message.warning('什么都没有搜到，请尝试其他关键词')
      searching.value = false
      return
    }
    store.searchResult = respData
    console.log(respData)
  } else if ('Comic' in searchResultVariant) {
    const comic = searchResultVariant.Comic
    store.pickedComic = comic
    console.log(comic)
    store.currentTabName = 'chapter'
  }

  searching.value = false
}
</script>

<template>
  <div class="h-full flex flex-col gap-2">
    <n-input-group class="box-border px-2 pt-2">
      <FloatLabelInput
        label="关键词(jm号也可以)"
        size="medium"
        v-model:value="searchInput"
        clearable
        @keydown.enter="search(searchInput.trim(), 1, sortSelected)" />
      <n-button
        :loading="searching"
        type="primary"
        size="medium"
        class="w-auto min-w-12"
        @click="search(searchInput.trim(), 1, sortSelected)">
        <template #icon>
          <n-icon size="22">
            <PhMagnifyingGlass />
          </n-icon>
        </template>
      </n-button>
    </n-input-group>
    <div class="px-2">
      <n-radio-group v-model:value="sortSelected" size="medium" @update-value="search(searchInput.trim(), 1, $event)">
        <n-radio-button value="Latest">最新</n-radio-button>
        <n-radio-button value="View">最多点击</n-radio-button>
        <n-radio-button value="Picture">最多图片</n-radio-button>
        <n-radio-button value="Like">最多爱心</n-radio-button>
      </n-radio-group>
    </div>

    <div v-if="store.searchResult !== undefined" class="flex flex-col gap-row-2 overflow-auto box-border px-2 flex-1 min-h-0">
      <ComicCard
        v-for="comicInSearch in store.searchResult.content"
        :key="comicInSearch.id"
        :comic-id="comicInSearch.id"
        :comic-title="comicInSearch.name"
        :comic-author="comicInSearch.author"
        :comic-category="comicInSearch.category"
        :comic-category-sub="comicInSearch.categorySub"
        :comic-downloaded="comicInSearch.isDownloaded"
        :comic-download-dir="comicInSearch.comicDownloadDir" />
    </div>

    <n-pagination
      v-if="searchPageCount > 0"
      class="box-border p-2 pt-0 mt-auto"
      :page-count="searchPageCount"
      :page="searchPage"
      @update:page="search(searchInput.trim(), $event, sortSelected)" />
  </div>
</template>
