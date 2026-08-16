import { ref, computed } from 'vue'

/**
 * 磁力「暂存」仓库（纯前端，不写数据库）。
 * 状态保存在内存中并同步到 localStorage，刷新页面后仍在。
 */
const STORAGE_KEY = 'javspy.staged.magnets'

// 每条: { code, magnet, name, sizeText, shareDate, stagedAt }
const staged = ref([])

function load() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY)
        if (raw) {
            const arr = JSON.parse(raw)
            if (Array.isArray(arr)) {
                staged.value = arr.filter((m) => m && typeof m.magnet === 'string' && m.magnet.trim())
            }
        }
    } catch (e) {
        // localStorage 不可用时忽略
    }
}

function persist() {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(staged.value))
    } catch (e) {
        // 忽略
    }
}

load()

/** 暂存一条磁力；重复（按 magnet 全文）返回 false。 */
export function stageMagnet(item) {
    if (!item || !item.magnet) return false
    const magnet = String(item.magnet).trim()
    if (!magnet) return false
    if (staged.value.some((m) => m.magnet === magnet)) return false
    staged.value.push({
        code: item.code || '',
        magnet,
        name: item.name || '',
        sizeText: item.sizeText || '',
        shareDate: item.shareDate || '',
        stagedAt: Date.now()
    })
    persist()
    return true
}

/** 批量暂存，返回本次新增条数。 */
export function stageMagnets(list) {
    let added = 0
    ;(list || []).forEach((item) => {
        if (stageMagnet(item)) added++
    })
    return added
}

/** 按索引移除一条。 */
export function removeStaged(index) {
    if (index >= 0 && index < staged.value.length) {
        staged.value.splice(index, 1)
        persist()
    }
}

/** 清空全部暂存。 */
export function clearStaged() {
    staged.value = []
    persist()
}

/** 暂存列表（响应式）。 */
export const stagedMagnets = staged

/** 暂存数量。 */
export const stagedCount = computed(() => staged.value.length)

/** 回车分割的磁力文本（用于展示/复制）。 */
export const stagedMagnetText = computed(() => staged.value.map((m) => m.magnet).join('\n'))
