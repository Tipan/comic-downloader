import { ref, onUnmounted } from 'vue'

/**
 * 触屏长按菜单封装。
 *
 * 用法：
 *   const { onTouchStart, onTouchEnd, onTouchMove, pressing } = useLongPress((e) => {
 *     // 长按 500ms 触发，e 为 TouchEvent，可从 e.touches[0] 取坐标
 *   })
 *
 * 在模板上：@touchstart="onTouchStart" @touchend="onTouchEnd" @touchmove="onTouchMove"
 *
 * PC 端继续用 @contextmenu，移动端用长按，两者可绑定到同一个 dropdown 显示函数。
 */
export function useLongPress(onLongPress: (e: TouchEvent) => void, duration = 500) {
  const pressing = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null
  let startEvent: TouchEvent | null = null

  function clear() {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
    pressing.value = false
    startEvent = null
  }

  function onTouchStart(e: TouchEvent) {
    // 只响应单指
    if (e.touches.length !== 1) {
      clear()
      return
    }
    startEvent = e
    pressing.value = true
    timer = setTimeout(() => {
      if (startEvent) {
        onLongPress(startEvent)
      }
      pressing.value = false
      timer = null
    }, duration)
  }

  function onTouchMove() {
    // 移动即取消长按（避免和滚动/框选冲突）
    clear()
  }

  function onTouchEnd() {
    clear()
  }

  // 组件卸载时清理
  onUnmounted(clear)

  return { onTouchStart, onTouchEnd, onTouchMove, pressing }
}
