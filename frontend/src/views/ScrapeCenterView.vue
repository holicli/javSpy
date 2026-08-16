<template>
    <div class="page-container">
        <div class="page-header">
            <h2 class="page-title">刮削中心</h2>
        </div>

        <!-- 后台一键刮削 -->
        <el-card shadow="never" class="card">
            <template #header>
                <div class="card-header">
                    <span>后台一键刮削（按页抓取入库，直到命中 Emby 或数据库已有影片）</span>
                    <span :class="['badge', status.running ? 'badge-running' : 'badge-stopped']">
                        {{ status.running ? '运行中' : '已停止' }}
                    </span>
                </div>
            </template>

            <div class="scrape-status">
                <div class="status-grid">
                    <div class="status-item">
                        <span class="status-label">当前页</span>
                        <span class="status-value">{{ status.running || status.page ? status.page : '—' }}</span>
                    </div>
                    <div class="status-item">
                        <span class="status-label">已入库</span>
                        <span class="status-value">{{ status.count ?? '—' }}</span>
                    </div>
                    <div class="status-item">
                        <span class="status-label">停止原因</span>
                        <span class="status-value">{{ stopReasonText || '—' }}</span>
                    </div>
                </div>
                <div class="status-message">
                    <el-icon v-if="status.running" class="is-loading" style="margin-right: 6px"><Loading /></el-icon>
                    <span>{{ status.message || '未开始' }}</span>
                </div>
            </div>

            <template #footer>
                <div class="card-footer">
                    <el-button type="primary" :loading="starting" :disabled="status.running" @click="startScrape">
                        开始刮削
                    </el-button>
                    <span class="text-muted" style="font-size: 12px">
                        提示：刮削为单线程串行执行，命中 Emby 或数据库已有影片时自动停止，最多抓 500 页
                    </span>
                </div>
            </template>
        </el-card>

        <!-- 手动抓取 -->
        <el-card shadow="never" class="card">
            <template #header>
                <div class="card-header"><span>手动抓取</span></div>
            </template>

            <div class="manual-row">
                <span class="manual-label">按番号抓取</span>
                <el-input
                    v-model="codeInput"
                    placeholder="如 SSIS-406"
                    style="width: 220px"
                    clearable
                    @keyup.enter="scrapeByCode"
                />
                <el-button type="primary" :loading="codeLoading" @click="scrapeByCode">抓取入库</el-button>
                <span v-if="codeResult" class="text-muted" style="font-size: 12px">
                    {{ codeResult }}
                </span>
            </div>

            <el-divider style="margin: 14px 0" />

            <div class="manual-row">
                <span class="manual-label">按关键词搜索</span>
                <el-input
                    v-model="keywordInput"
                    placeholder="演员名 / 关键词"
                    style="width: 220px"
                    clearable
                    @keyup.enter="scrapeByKeyword"
                />
                <el-input-number v-model="keywordPages" :min="1" :max="10" style="width: 110px" />
                <el-button type="primary" :loading="keywordLoading" @click="scrapeByKeyword">
                    搜索抓取
                </el-button>
            </div>

            <el-table
                v-if="keywordRows.length"
                :data="keywordRows"
                size="small"
                border
                max-height="340px"
                style="margin-top: 12px"
            >
                <el-table-column prop="code" label="番号" width="120" />
                <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
                <el-table-column label="状态" width="90" align="center">
                    <template #default="{ row }">
                        <el-tag :type="row.status === 'INSERTED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'" size="small">
                            {{ row.status }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="磁力数" width="80" align="center">
                    <template #default="{ row }">{{ row.magnetCount ?? '—' }}</template>
                </el-table-column>
                <el-table-column prop="message" label="信息" min-width="180" show-overflow-tooltip />
            </el-table>
        </el-card>

        <!-- 关注演员管理 -->
        <el-card shadow="never" class="card">
            <template #header>
                <div class="card-header">
                    <span>关注演员</span>
                    <div>
                        <el-button type="primary" plain size="small" :loading="actorsLoading" @click="loadActors">
                            刷新
                        </el-button>
                        <el-button type="primary" size="small" @click="addVisible = true">
                            <el-icon style="margin-right: 4px"><Plus /></el-icon>添加
                        </el-button>
                    </div>
                </div>
            </template>

            <el-table
                v-loading="actorsLoading"
                :data="actors"
                size="default"
                border
                empty-text="暂无关注演员"
            >
                <el-table-column prop="actorName" label="演员名称" min-width="160" />
                <el-table-column prop="remark" label="备注" min-width="200">
                    <template #default="{ row }">{{ row.remark || '—' }}</template>
                </el-table-column>
                <el-table-column prop="createdAt" label="添加时间" width="180">
                    <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="110" align="center">
                    <template #default="{ row }">
                        <el-button size="small" type="danger" plain @click="removeActor(row)">取消关注</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>
    </div>

    <!-- 添加关注演员弹窗 -->
    <el-dialog v-model="addVisible" title="添加关注演员" :width="dialogWidth">
        <el-form :model="addForm" label-width="80px" @submit.prevent>
            <el-form-item label="演员名称" required>
                <el-input v-model="addForm.name" placeholder="演员名称" />
            </el-form-item>
            <el-form-item label="备注">
                <el-input v-model="addForm.remark" placeholder="可选备注" />
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="addVisible = false">取消</el-button>
            <el-button type="primary" :loading="addLoading" @click="submitAdd">确定</el-button>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { javbusApi } from '@/api'
import { useMobile } from '@/composables/useMobile'

const isMobile = useMobile()
const dialogWidth = computed(() => (isMobile.value ? '92%' : '440px'))

// ===== 后台一键刮削 =====
const status = ref({ running: false, page: 0, count: 0, message: '未开始', stopReason: null, stopCode: null })
const starting = ref(false)
let pollTimer = null

const stopReasonText = computed(() => {
    const map = {
        EMBY_MATCH: '命中 Emby 已有影片' + (status.value.stopCode ? '（' + status.value.stopCode + '）' : ''),
        DB_MATCH: '命中数据库已有影片' + (status.value.stopCode ? '（' + status.value.stopCode + '）' : ''),
        EMPTY: '页面无数据',
        MAX_PAGES: '抓满 500 页未命中',
        ERROR: '任务异常'
    }
    return map[status.value.stopReason] || ''
})

async function startScrape() {
    starting.value = true
    try {
        const res = await javbusApi.startScrapeUntilEmby()
        if (res.data) {
            ElMessage.success('刮削任务已启动')
        } else {
            ElMessage.warning('刮削任务已在运行中')
        }
        startPolling()
    } catch (e) {
        ElMessage.error('启动失败：' + e.message)
    } finally {
        starting.value = false
    }
}

async function pollStatus() {
    try {
        const res = await javbusApi.scrapeUntilEmbyStatus()
        status.value = res.data || status.value
        if (!status.value.running) {
            stopPolling()
            if (status.value.stopReason === 'EMBY_MATCH' || status.value.stopReason === 'DB_MATCH') {
                ElMessage.success('刮削完成：' + (status.value.message || '已命中'))
            }
        }
    } catch (e) {
        // 后端暂不可达时静默重试
    }
}

function startPolling() {
    stopPolling()
    pollTimer = setInterval(pollStatus, 2000)
    pollStatus()
}

function stopPolling() {
    if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
    }
}

onMounted(() => {
    pollStatus()
})
onBeforeUnmount(stopPolling)

// ===== 手动抓取 =====
const codeInput = ref('')
const codeLoading = ref(false)
const codeResult = ref('')

const keywordInput = ref('')
const keywordPages = ref(1)
const keywordLoading = ref(false)
const keywordRows = ref([])

async function scrapeByCode() {
    const code = codeInput.value.trim()
    if (!code) {
        ElMessage.warning('请输入番号')
        return
    }
    codeLoading.value = true
    codeResult.value = ''
    try {
        const res = await javbusApi.scrapeByCode(code)
        const movie = res.data?.movie
        if (!movie) {
            codeResult.value = '未找到该番号：' + code
            ElMessage.warning('未找到该番号：' + code)
        } else {
            const magnets = res.data?.magnets || []
            codeResult.value = `${movie.code} · ${movie.title || ''}（${movie.releaseDate || '?'}）磁力 ${magnets.length} 条`
            ElMessage.success('抓取入库完成：' + movie.code)
        }
    } catch (e) {
        codeResult.value = ''
        ElMessage.error('抓取失败：' + e.message)
    } finally {
        codeLoading.value = false
    }
}

async function scrapeByKeyword() {
    const keyword = keywordInput.value.trim()
    if (!keyword) {
        ElMessage.warning('请输入关键词')
        return
    }
    keywordLoading.value = true
    keywordRows.value = []
    try {
        const res = await javbusApi.scrapeByKeyword(keyword, keywordPages.value, 'exist')
        keywordRows.value = res.data || []
        ElMessage.success('搜索抓取完成，共处理 ' + keywordRows.value.length + ' 部')
    } catch (e) {
        ElMessage.error('搜索抓取失败：' + e.message)
    } finally {
        keywordLoading.value = false
    }
}

// ===== 关注演员 =====
const actors = ref([])
const actorsLoading = ref(false)
const addVisible = ref(false)
const addLoading = ref(false)
const addForm = ref({ name: '', remark: '' })

async function loadActors() {
    actorsLoading.value = true
    try {
        const res = await javbusApi.followActors()
        actors.value = res.data || []
    } catch (e) {
        ElMessage.error('加载失败：' + e.message)
    } finally {
        actorsLoading.value = false
    }
}

async function submitAdd() {
    const name = addForm.value.name.trim()
    if (!name) {
        ElMessage.warning('请输入演员名称')
        return
    }
    addLoading.value = true
    try {
        await javbusApi.addFollowActor(name, addForm.value.remark.trim())
        ElMessage.success('已添加关注演员')
        addVisible.value = false
        addForm.value = { name: '', remark: '' }
        loadActors()
    } catch (e) {
        ElMessage.error('添加失败：' + e.message)
    } finally {
        addLoading.value = false
    }
}

async function removeActor(row) {
    try {
        await ElMessageBox.confirm(`确定取消关注演员「${row.actorName}」？`, '提示', {
            type: 'warning'
        })
    } catch {
        return
    }
    try {
        await javbusApi.removeFollowActor(row.actorName)
        ElMessage.success('已取消关注')
        loadActors()
    } catch (e) {
        ElMessage.error('操作失败：' + e.message)
    }
}

function formatDate(value) {
    if (!value) return '—'
    return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadActors)
</script>

<style scoped>
.card {
    margin-bottom: 16px;
}

.card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
    font-size: 14px;
    color: #1e293b;
}

.scrape-status {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.status-grid {
    display: flex;
    gap: 40px;
    flex-wrap: wrap;
}

.status-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.status-label {
    font-size: 12px;
    color: #94a3b8;
}

.status-value {
    font-size: 16px;
    font-weight: 600;
    color: #1e293b;
}

.status-message {
    display: flex;
    align-items: center;
    font-size: 13px;
    color: #475569;
    background: #f8fafc;
    border: 1px solid #e9eef2;
    border-radius: 8px;
    padding: 8px 12px;
    word-break: break-all;
}

.card-footer {
    display: flex;
    align-items: center;
    gap: 14px;
}

.manual-row {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

.manual-label {
    width: 110px;
    font-size: 13px;
    color: #475569;
    flex-shrink: 0;
}

/* 手机端适配 */
@media (max-width: 767px) {
    .status-grid {
        gap: 20px;
    }

    .manual-label {
        width: auto;
    }

    .manual-row .el-input {
        flex: 1;
        min-width: 0;
    }
}
</style>
