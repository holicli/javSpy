import { ref } from 'vue'

/**
 * 响应式移动端检测：宽度 < 768px 视为手机端。
 * 全局单例，任何组件调用 useMobile() 拿到同一个响应式引用。
 */
const mq = window.matchMedia('(max-width: 767px)')

const isMobile = ref(mq.matches)

mq.addEventListener('change', (e) => {
    isMobile.value = e.matches
})

export function useMobile() {
    return isMobile
}
