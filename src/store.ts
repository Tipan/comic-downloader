import { defineStore } from 'pinia'
import { CurrentTabName, ProgressData } from './types.ts'
import { Comic, Config, GetFavoriteResult, GetUserProfileRespData, GetWeeklyResult, SearchResult } from './bindings.ts'
import { ref } from 'vue'
import { ProgressesPaneTabName } from './panes/ProgressesPane/ProgressesPane.vue'

export const useStore = defineStore('store', () => {
  const config = ref<Config>()
  // 应用初始化错误(如 getConfig 失败)。非空时前端显示错误界面而非白屏。
  const initError = ref<string>('')
  const userProfile = ref<GetUserProfileRespData>()
  const pickedComic = ref<Comic>()
  const currentTabName = ref<CurrentTabName>('search')
  const progresses = ref<Map<number, ProgressData>>(new Map())
  const getFavoriteResult = ref<GetFavoriteResult>()
  const searchResult = ref<SearchResult>()
  const progressesPaneTabName = ref<ProgressesPaneTabName>('uncompleted')
  const getWeeklyResult = ref<GetWeeklyResult>()

  return {
    config,
    initError,
    userProfile,
    pickedComic,
    currentTabName,
    progresses,
    getFavoriteResult,
    searchResult,
    progressesPaneTabName,
    getWeeklyResult,
  }
})
