import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { javbusApi } from '@/api'

export function useDownloadStatus(rows) {
    const codes = computed(() => [...new Set(rows.value.map((row) => row.code).filter(Boolean))])
    const downloading = ref(new Set())
    let timer
    let active = false
    let inFlight = false
    let refreshPending = false

    async function refresh() {
        if (!active) return
        if (codes.value.length === 0) {
            downloading.value = new Set()
            return
        }
        if (inFlight) {
            refreshPending = true
            return
        }
        inFlight = true
        try {
            const { data } = await javbusApi.downloadingCodes(codes.value)
            if (active && codes.value.length > 0) downloading.value = new Set(data)
        } catch {
            if (active) downloading.value = new Set()
        } finally {
            inFlight = false
            if (refreshPending) {
                refreshPending = false
                refresh()
            }
        }
    }

    watch(codes, refresh)
    onMounted(() => {
        active = true
        refresh()
        timer = window.setInterval(refresh, 15000)
        window.addEventListener('javspy:downloads-changed', refresh)
    })
    onUnmounted(() => {
        active = false
        window.clearInterval(timer)
        window.removeEventListener('javspy:downloads-changed', refresh)
    })

    return (code) => downloading.value.has(code)
}
