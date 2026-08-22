import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import 'virtual:uno.css'

// Android 安全区避让：env(safe-area-inset-*) 在 Android WebView 上常返回 0，
// 用 screen.height - visualViewport.height 估算顶部状态栏高度，注入 CSS 变量。
// 这样顶部 tab/按钮不会被状态栏遮挡，底部内容不被导航栏遮挡。
function applySafeAreaInset() {
  try {
    const root = document.documentElement
    // 先检测 env() 是否有效：临时元素测量
    const probe = document.createElement('div')
    probe.style.cssText =
      'position:absolute;top:0;left:0;width:0;height:env(safe-area-inset-top);visibility:hidden'
    document.body.appendChild(probe)
    const envTop = probe.getBoundingClientRect().height
    document.body.removeChild(probe)

    if (envTop > 0) {
      // env() 有效，CSS 已用 var(--safe-top)=env(...)，无需 JS 介入
      return
    }

    // env() 无效(Android 常见)，用 screen vs viewport 差值估算
    const vv = window.visualViewport
    const screenHeight = vv ? vv.height : window.innerHeight
    const fullHeight = window.screen.height
    // 顶部状态栏 + 底部导航栏的总差值，粗略平分到上下
    const totalInset = Math.max(0, fullHeight - screenHeight)
    if (totalInset > 0) {
      // 顶部通常占约 1/3（状态栏），底部占 2/3（导航栏）—— 仅粗略估算
      const top = Math.round(totalInset / 3)
      const bottom = totalInset - top
      root.style.setProperty('--safe-top', `${top}px`)
      root.style.setProperty('--safe-bottom', `${bottom}px`)
    }
  } catch (e) {
    // ignore
  }
}
applySafeAreaInset()
window.addEventListener('resize', applySafeAreaInset)
window.addEventListener('orientationchange', applySafeAreaInset)

// 启动诊断标记：JS 一旦执行到这里，立即在屏幕上放一个可见标记并改 title。
// 若 WebView 加载了 index.html 但 JS 没跑，屏幕会停在 index.html 的空 #app(白屏)；
// 若 JS 跑了，这个标记会在 Vue mount 前就可见，用于区分"JS 没加载" vs "渲染失败"。
try {
  document.title = 'JM-Loading'
  const marker = document.createElement('div')
  marker.id = '__boot_marker'
  marker.textContent = '正在启动应用...'
  marker.style.cssText =
    'position:fixed;top:0;left:0;right:0;padding:12px;text-align:center;background:#FF7A00;color:#fff;font-size:14px;z-index:99999'
  document.body.appendChild(marker)
} catch (e) {
  // ignore
}

const pinia = createPinia()
const app = createApp(App)

app.use(pinia).mount('#app')

// mount 成功后移除启动标记
try {
  document.title = '漫画下载器'
  const m = document.getElementById('__boot_marker')
  if (m) m.remove()
} catch (e) {
  // ignore
}
