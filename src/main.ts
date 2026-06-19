import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import 'virtual:uno.css'

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
