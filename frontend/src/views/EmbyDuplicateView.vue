<template>
    <div class="page-container">
        <div class="page-header">
            <h2 class="page-title">Emby 去重</h2>
            <div class="toolbar">
                <el-input
                    v-model="keyword"
                    placeholder="按番号 / 标题 / 路径过滤"
                    :disabled="busy"
                    clearable
                    @keyup.enter="load(false)"
                    @clear="load(false)"
                >
                    <template #prefix>
                        <el-icon><Search /></el-icon>
                    </template>
                </el-input>
                <el-button :loading="loading" :disabled="deleting" @click="load(true)">
                    <el-icon style="margin-right: 4px"><Refresh /></el-icon>
                    重新扫描
                </el-button>
            </div>
        </div>

        <el-alert
            class="mb-12"
            type="warning"
            :closable="false"
            show-icon
            title="删除会同时移除 Emby 条目和磁盘上的视频文件，无法恢复；请只勾选确实重复的文件。"
        />

        <div class="stat-row">
            <div class="stat-card">
                <div class="stat-value">{{ stats.total || 0 }}</div>
                <div class="stat-label">Emby 影片</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">{{ stats.groupCount || 0 }}</div>
                <div class="stat-label">重复分组</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">{{ stats.extraItems || 0 }}</div>
                <div class="stat-label">多余文件</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">{{ stats.reclaimableText || '0 B' }}</div>
                <div class="stat-label">可释放空间</div>
            </div>
        </div>

        <div class="bulk-bar">
            <span class="text-muted">
                已选 <b>{{ selectedItems.length }}</b> 个，共 {{ selectedSizeText }}
                <span v-if="!deleteAvailable && groups.length"> · 未配置永久删除</span>
                <span v-else-if="emptiedGroup"> · 每组至少保留一个条目</span>
            </span>
            <div class="toolbar">
                <el-button size="small" :disabled="busy || !deleteAvailable || !batchGroups.length" @click="selectAllExtra">
                    批量勾选高置信度候选
                </el-button>
                <el-button size="small" :disabled="busy || !selectedItems.length" @click="clearSelection">
                    清空选择
                </el-button>
                <el-button
                    size="small"
                    type="danger"
                    :disabled="busy || !deleteAvailable || !selectedItems.length || emptiedGroup"
                    :loading="deleting"
                    @click="onDelete"
                >
                    删除选中（{{ selectedItems.length }}）
                </el-button>
            </div>
        </div>

        <div v-loading="loading" class="group-list">
            <el-empty
                v-if="!loading && !groups.length"
                description="没有发现番号或标题重复的影片"
            />
            <el-card v-for="group in groups" :key="group.key" class="group-card" shadow="never">
                <template #header>
                    <div class="group-header">
                        <div class="group-title-wrap">
                            <span :class="['badge', group.reason === 'code' ? 'badge-code' : 'badge-title']">
                                {{ group.reason === 'code' ? '同番号' : '标题相似' }}
                            </span>
                            <span class="group-title" :title="group.title">{{ group.title }}</span>
                            <span v-if="!group.deletable" class="badge badge-count">仅查看</span>
                            <span v-else-if="!group.batchEligible" class="badge badge-count">需人工核对</span>
                            <span class="badge badge-count">{{ group.count }} 个文件</span>
                            <span class="text-muted">
                                合计 {{ group.totalSizeText }} · 可释放 {{ group.extraSizeText }}
                            </span>
                        </div>
                        <el-button v-if="group.batchEligible" size="small" text type="primary"
                            :disabled="busy || !deleteAvailable" @click="selectGroupExtra(group)">
                            选中多余（保留最大）
                        </el-button>
                    </div>
                </template>

                <div
                    v-for="item in group.items"
                    :key="item.id"
                    :class="['item-row', { picked: !!selected[item.id] }]"
                >
                    <el-checkbox v-model="selected[item.id]" class="item-check"
                        :disabled="busy || !deleteAvailable || !group.deletable" />
                    <el-image
                        class="cover-thumb"
                        :src="coverUrl(item.id)"
                        fit="cover"
                        lazy
                    >
                        <template #error>
                            <div class="cover-fallback">无图</div>
                        </template>
                    </el-image>
                    <div class="item-main">
                        <div class="item-name" :title="item.name">{{ item.name }}</div>
                        <div class="item-path" :title="item.path">{{ item.path }}</div>
                    </div>
                    <div class="item-meta">
                        <span class="badge badge-count">{{ item.sizeText }}</span>
                        <span v-if="item.resolution" class="meta-text">{{ item.resolution }}</span>
                        <span v-if="item.durationMinutes" class="meta-text">{{ item.durationMinutes }} 分钟</span>
                        <span v-if="item.year" class="meta-text">{{ item.year }} 年</span>
                        <span v-if="item.codeFrom === 'path'" class="meta-text">番号取自文件名</span>
                        <el-tag v-for="tag in item.tags" :key="tag" size="small" type="info" effect="plain">
                            {{ tag }}
                        </el-tag>
                    </div>
                </div>
            </el-card>
        </div>
    </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { javbusApi } from '@/api'

const loading = ref(false)
const deleting = ref(false)
const busy = computed(() => loading.value || deleting.value)
const deleteAvailable = ref(false)
const keyword = ref('')
const groups = ref([])
const stats = ref({})
const batchGroups = computed(() => groups.value.filter((group) => group.batchEligible))

/** itemId -> 是否勾选 */
const selected = reactive(Object.create(null))

const coverUrl = (id) => javbusApi.embyCoverUrl(id, 200, 280)

/** 已勾选的影片条目（含所属分组信息）。 */
const selectedItems = computed(() => {
    const list = []
    groups.value.forEach((group) => {
        group.items.forEach((item) => {
            if (selected[item.id]) {
                list.push(item)
            }
        })
    })
    return list
})

const selectedSizeText = computed(() => formatSize(selectedItems.value.reduce((sum, i) => sum + (i.size || 0), 0)))
const emptiedGroup = computed(() => groups.value.some(
    (group) => group.items.length && group.items.every((item) => selected[item.id])
))

function formatSize(bytes) {
    if (!bytes) {
        return '0 B'
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB']
    let value = bytes
    let unit = 0
    while (value >= 1024 && unit < units.length - 1) {
        value /= 1024
        unit += 1
    }
    return unit === 0 ? `${bytes} B` : `${value.toFixed(2)} ${units[unit]}`
}

function clearSelection() {
    Object.keys(selected).forEach((key) => delete selected[key])
}

/** 组内除最大文件外全选（items 已按大小倒序，第一条即最大）。 */
function selectGroupExtra(group) {
    if (busy.value || !deleteAvailable.value || !group.batchEligible) return
    const keep = group.items[0]
    group.items.forEach((item) => {
        if (keep && item.id === keep.id) {
            delete selected[item.id]
        } else {
            selected[item.id] = true
        }
    })
}

async function selectAllExtra() {
    try {
        await ElMessageBox.confirm(
            '仅为名称和文件名均匹配的高置信度分组勾选候选，每组保留最大文件。请逐组检查后再删除。',
            '批量勾选多余文件',
            { type: 'info', confirmButtonText: '勾选', cancelButtonText: '取消' }
        )
    } catch (e) {
        return
    }
    clearSelection()
    batchGroups.value.forEach((group) => selectGroupExtra(group))
}

async function load(refresh = false) {
    if (loading.value) return
    loading.value = true
    clearSelection()
    try {
        const res = await javbusApi.embyDuplicates(refresh, keyword.value)
        const data = res.data || {}
        groups.value = data.groups || []
        stats.value = data.stats || {}
        deleteAvailable.value = data.deleteAvailable === true
    } catch (e) {
        ElMessage.error(e.message || '扫描失败')
        groups.value = []
        stats.value = {}
        deleteAvailable.value = false
    } finally {
        loading.value = false
    }
}

async function onDelete() {
    const items = selectedItems.value
    if (busy.value || !deleteAvailable.value || !items.length || emptiedGroup.value) return
    const sizeText = selectedSizeText.value
    deleting.value = true
    let adminToken
    try {
        const { value } = await ElMessageBox.prompt(
            `即将从 Emby 删除 ${items.length} 个影片（共 ${sizeText}），磁盘文件会被永久删除且无法恢复。确定继续？`,
            '确认删除',
            {
                type: 'warning',
                confirmButtonText: '永久删除',
                confirmButtonClass: 'el-button--danger',
                cancelButtonText: '取消',
                inputType: 'password',
                inputPlaceholder: '管理员令牌',
                inputValidator: (token) => !!token?.trim() || '请输入管理员令牌'
            }
        )
        adminToken = value
    } catch (e) {
        deleting.value = false
        return
    }

    try {
        const res = await javbusApi.embyDelete(items.map((i) => i.id), adminToken)
        const data = res.data || {}
        const failed = data.failed || []
        if (failed.length) {
            ElMessage.warning(`${res.message}，请重新核对扫描结果`)
        } else {
            ElMessage.success(res.message || '删除完成')
        }
        await load(true)
        window.dispatchEvent(new Event('javspy:emby-changed'))
    } catch (e) {
        ElMessage.error(e.message || '删除失败')
        if (!e.status || e.status === 409 || e.status >= 500) {
            await load(true)
            window.dispatchEvent(new Event('javspy:emby-changed'))
        }
    } finally {
        deleting.value = false
    }
}

onMounted(() => load(false))
</script>

<style scoped>
.stat-row {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
    margin-bottom: 12px;
}

.stat-card {
    flex: 1 1 150px;
    background: #fff;
    border-radius: 8px;
    padding: 12px 16px;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.stat-value {
    font-size: 20px;
    font-weight: 600;
    color: #1e293b;
}

.stat-label {
    margin-top: 2px;
    font-size: 12px;
    color: #94a3b8;
}

.bulk-bar {
    position: sticky;
    top: 0;
    z-index: 10;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    background: #fff;
    border-radius: 8px;
    padding: 8px 14px;
    margin-bottom: 12px;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.group-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-height: 120px;
}

.group-card :deep(.el-card__header) {
    padding: 10px 14px;
}

.group-card :deep(.el-card__body) {
    padding: 8px 14px 12px;
}

.group-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
}

.group-title-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    min-width: 0;
}

.group-title {
    font-weight: 600;
    color: #1e293b;
    max-width: 560px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.badge-code {
    background: #fef3c7;
    color: #b45309;
}

.badge-title {
    background: #e0e7ff;
    color: #4338ca;
}

.item-row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px;
    border-radius: 6px;
    border: 1px solid transparent;
}

.item-row + .item-row {
    margin-top: 4px;
}

.item-row:hover {
    background: #f8fafc;
}

.item-row.picked {
    background: #fef2f2;
    border-color: #fecaca;
}

.item-check {
    flex: 0 0 auto;
}

.item-main {
    flex: 1 1 320px;
    min-width: 0;
}

.item-name {
    font-size: 13px;
    color: #1e293b;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.item-path {
    font-size: 12px;
    color: #94a3b8;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.item-meta {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    justify-content: flex-end;
}

.meta-text {
    font-size: 12px;
    color: #94a3b8;
}

@media (max-width: 768px) {
    .item-row {
        flex-wrap: wrap;
    }

    .item-meta {
        justify-content: flex-start;
        width: 100%;
        padding-left: 28px;
    }

    .group-title {
        max-width: 100%;
        white-space: normal;
    }
}
</style>
