<script setup lang="ts">
// This starter template is using Vue 3 <script setup> SFCs
// Check out https://vuejs.org/api/sfc-script-setup.html#script-setup
import AppContent from './AppContent.vue'
import { GlobalThemeOverrides } from 'naive-ui'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#FF7A00',
    primaryColorHover: '#FFB152',
    primaryColorPressed: '#D96200',
    primaryColorSuppl: '#FFB152',
    borderRadius: '4px',
    borderRadiusSmall: '3px',
    // 触屏优化：放大 medium 基准高度(原 32px)，让所有 medium 控件达到触控友好尺寸
    heightMedium: '40px',
  },
  Button: {
    paddingSmall: '0 12px',
    paddingMedium: '0 16px',
    paddingLarge: '0 20px',
  },
  Radio: {
    buttonColorActive: '#FF7A00',
    buttonTextColorActive: '#FFF',
  },
  Dropdown: {
    borderRadius: '5px',
    padding: '6px 2px',
    optionColorHover: '#FF7A00',
    optionTextColorHover: '#FFF',
    // 触屏优化：菜单项高度放大到 44px
    optionHeightMedium: '44px',
  },
}
</script>

<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-modal-provider>
      <n-notification-provider placement="bottom-right" :max="3">
        <n-message-provider>
          <app-content />
        </n-message-provider>
      </n-notification-provider>
    </n-modal-provider>
  </n-config-provider>
</template>
