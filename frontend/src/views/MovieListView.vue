<template>
    <div class="page-container">
        <!-- 桌面工具栏 -->
        <div v-if="!isMobile" class="page-header">
            <h2 class="page-title">影片库</h2>
            <div class="toolbar">
                <el-input
                    v-model="keyword"
                    placeholder="搜索番号 / 标题关键词（留空浏览 javbus 最新页）"
                    clearable
                    @keyup.enter="onSearch"
                    @clear="onSearch"
                >
                    <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
                <el-button type="primary" :loading="searching" @click="onSearch">搜索</el-button>
                <el-button type="warning" plain :loading="scrapingPage" :disabled="loading || isSearchMode" @click="onScrapePage">
                    抓取并入库当前页
                </el-button>
                <el-button plain @click="resetAndLoad">刷新</el-button>
                <span class="text-muted">{{ modeText }}</span>
            </div>
        </div>

        <!-- 移动工具栏 -->
        <div v-else class="mobile-toolbar">
            <el-input
                v-model="keyword"
                placeholder="搜索番号 / 标题关键词"
                clearable
                @keyup.enter="onSearch"
                @clear="onSearch"
            >
                <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" :loading="searching" @click="onSearch">搜索</el-button>
        </div>
        <div v-if="isMobile" class="mobile-mode-tip">{{ modeText }}</div>

        <!-- 桌面表格 -->
        <el-table
            v-if="!isMobile"
            ref="tableRef"
            v-loading="loading"
            :data="rows"
            :max-height="tableMaxHeight"
            border
            stripe
            size="default"
            row-key="code"
            empty-text="暂无数据，滚动或点击搜索加载"
        >
            <el-table-column label="封面" width="72" align="center">
                <template #default="{ row }">
                    <el-tooltip placement="right" :show-after="150" :hide-after="0">
                        <template #content>
                            <img v-if="row.coverUrl" :src="row.coverUrl" class="cover-hover-img" alt="预览" />
                            <span v-else class="text-muted">无封面</span>
                        </template>
                        <img
                            v-if="row.coverUrl"
                            :src="row.coverUrl"
                            class="cover-thumb"
                            alt="cover"
                            loading="lazy"
                            @click="openDetail(row.code)"
                        />
                        <div v-else class="cover-fallback">无图</div>
                    </el-tooltip>
                </template>
            </el-table-column>
            <el-table-column label="番号" width="120">
                <template #default="{ row }">
                    <span class="code-cell" @click="openDetail(row.code)" style="cursor: pointer">
                        {{ row.code }}
                    </span>
                </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">{{ row.title || '—' }}</template>
            </el-table-column>
            <el-table-column prop="actors" label="演员" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.actors || '—' }}</template>
            </el-table-column>
            <el-table-column label="时长" width="80" align="center">
                <template #default="{ row }">{{ row.duration ? row.duration + ' 分' : '—' }}</template>
            </el-table-column>
            <el-table-column prop="releaseDate" label="日期" width="105">
                <template #default="{ row }">{{ row.releaseDate || '—' }}</template>
            </el-table-column>
            <el-table-column prop="genres" label="类型" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.genres || '—' }}</template>
            </el-table-column>
            <el-table-column label="磁力" width="70" align="center">
                <template #default="{ row }">
                    <span v-if="typeof row.magnetCount === 'number'" class="badge badge-count">
                        {{ row.magnetCount }}
                    </span>
                    <span v-else class="text-muted">—</span>
                </template>
            </el-table-column>
            <el-table-column label="Emby" width="72" align="center">
                <template #default="{ row }">
                    <span v-if="typeof row.embyExists === 'boolean'" :class="['badge', row.embyExists ? 'badge-emby-yes' : 'badge-emby-no']">
                        {{ row.embyExists ? '存在' : '无' }}
                    </span>
                    <span v-else class="text-muted">—</span>
                </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                    <el-tag v-if="row.status === 'DB'" size="small" type="success" effect="plain">已入库</el-tag>
                    <el-tag v-else-if="row.status === 'INSERTED'" size="small" type="success">新入库</el-tag>
                    <el-tag v-else-if="row.status === 'FAILED'" size="small" type="danger">失败</el-tag>
                    <span v-else class="text-muted">—</span>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center" fixed="right">
                <template #default="{ row }">
                    <el-button size="small" type="primary" plain @click="openDetail(row.code)">详情</el-button>
                    <el-button size="small" type="success" plain @click="openMagnets(row.code)">磁力</el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 移动卡片列表 -->
        <div v-else v-loading="loading" class="mobile-card-grid">
            <MobileMovieCard
                v-for="row in rows"
                :key="row.code"
                :movie="row"
                @open-detail="openDetail"
            />
        </div>

        <div v-if="isMobile && rows.length === 0 && !loading" class="mobile-empty">
            暂无数据，输入关键词搜索，或下拉加载更多
        </div>

        <div class="list-footer">
            <span class="text-muted">
                已加载 {{ rows.length }} 部 · {{ hasMore ? (isMobile ? '上滑加载更多' : '滚动到底部加载更多') : '已加载全部' }}
            </span>
            <el-button v-if="hasMore && isMobile" link type="primary" :loading="loading" @click="loadMore">
                {{ loading ? '加载中...' : '加载更多' }}
            </el-button>
        </div>
    </div>

    <!-- 详情弹窗 -->
    <MovieDetailDialog v-model="detailVisible" :code="detailCode" />
    <MagnetDialog v-model="magnetVisible" :code="magnetCode" />
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { javbusApi } from '@/api'
import MovieDetailDialog from '@/components/MovieDetailDialog.vue'
import MagnetDialog from '@/components/MagnetDialog.vue'
import MobileMovieCard from '@/components/MobileMovieCard.vue'
import { useMobile } from '@/composables/useMobile'

const isMobile = useMobile()

const keyword = ref('')
const rows = ref([])
const loading = ref(false)
const searching = ref(false)
const scrapingPage = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)
const tableRef = ref(null)

const detailVisible = ref(false)
const detailCode = ref('')
const magnetVisible = ref(false)
const magnetCode = ref('')

const tableMaxHeight = 'calc(100vh - 225px)'
const isSearchMode = computed(() => keyword.value.trim().length > 0)
const modeText = computed(() =>
    isSearchMode.value ? '搜索模式（javbus 接口搜索并逐部入库）' : '浏览模式（自动入库）'
)

/** 规范化后端两种接口返回的影片行。 */
function normalize(row) {
    return {
        code: row.code,
        title: row.title,
        coverUrl: row.coverUrl || row.cover || '',
        releaseDate: row.releaseDate || row.date || '',
        duration: row.duration,
        actors: row.actors,
        genres: row.genres,
        magnetCount: row.magnetCount,
        embyExists: row.embyExists,
        status: row.status || ''
    }
}

async function fetchPage(page) {
    if (loading.value) return
    loading.value = true
    try {
        let list = []
        if (isSearchMode.value) {
            // 搜索：直接调 javbus API 搜索并逐部入库
            const res = await javbusApi.searchFromApi(keyword.value.trim(), page, 'exist')
            list = (res.data || []).map(normalize)
        } else {
            const res = await javbusApi.scrapePage(page, 'exist', false)
            list = (res.data || []).map(normalize)
        }
        if (list.length === 0) {
            hasMore.value = false
        } else {
            // 按 code 去重追加
            const seen = new Set(rows.value.map((r) => r.code))
            const added = list.filter((r) => r.code && !seen.has(r.code))
            rows.value.push(...added)
            if (added.length < list.length) {
                // 本页都是重复数据，视为已到结尾
                hasMore.value = false
            }
        }
    } catch (e) {
        ElMessage.error('加载失败：' + e.message)
        if (page === 1) hasMore.value = false
    } finally {
        loading.value = false
        nextTick(checkAndLoadMoreIfNeeded)
    }
}

function resetAndLoad() {
    rows.value = []
    currentPage.value = 1
    hasMore.value = true
    fetchPage(1)
}

function onSearch() {
    if (searching.value) return
    searching.value = true
    resetAndLoad()
    setTimeout(() => (searching.value = false), 300)
}

async function loadMore() {
    if (!hasMore.value || loading.value) return
    currentPage.value += 1
    await fetchPage(currentPage.value)
}

/** 抓取并入库当前页（滚动浏览时已自动入库，此按钮用于补抓 + 刷新）。 */
async function onScrapePage() {
    if (scrapingPage.value || loading.value) return
    scrapingPage.value = true
    try {
        const res = await javbusApi.scrapePage(currentPage.value, 'exist', false)
        const count = (res.data || []).length
        ElMessage.success('当前页处理完成，共 ' + count + ' 部影片')
        resetAndLoad()
    } catch (e) {
        ElMessage.error('抓取失败：' + e.message)
    } finally {
        scrapingPage.value = false
    }
}

// ===== 桌面端无限滚动（监听 el-table 内部滚动容器） =====
let wrapEl = null

function onWrapScroll() {
    if (isMobile.value || !hasMore.value || loading.value) return
    const el = wrapEl
    if (el && el.scrollTop + el.clientHeight >= el.scrollHeight - 60) {
        loadMore()
    }
}

function attachScroll() {
    const el = tableRef.value?.$el?.querySelector('.el-scrollbar__wrap')
    if (el && el !== wrapEl) {
        wrapEl?.removeEventListener('scroll', onWrapScroll)
        wrapEl = el
        el.addEventListener('scroll', onWrapScroll)
    }
}

function checkAndLoadMoreIfNeeded() {
    if (isMobile.value) {
        checkMobileAutoLoad()
        return
    }
    attachScroll()
    if (!hasMore.value || loading.value || !wrapEl) return
    if (wrapEl.scrollHeight <= wrapEl.clientHeight + 60) {
        loadMore()
    }
}

// ===== 移动端无限滚动（监听 window 滚动） =====
function onWindowScroll() {
    if (!isMobile.value || !hasMore.value || loading.value) return
    const doc = document.documentElement
    if (doc.scrollTop + window.innerHeight >= doc.scrollHeight - 120) {
        loadMore()
    }
}

function checkMobileAutoLoad() {
    if (!isMobile.value || !hasMore.value || loading.value) return
    const doc = document.documentElement
    if (doc.scrollHeight <= window.innerHeight + 80) {
        loadMore()
    }
}

onMounted(() => {
    attachScroll()
    fetchPage(1)
    window.addEventListener('scroll', onWindowScroll)
})

onBeforeUnmount(() => {
    wrapEl?.removeEventListener('scroll', onWrapScroll)
    window.removeEventListener('scroll', onWindowScroll)
})

function openDetail(code) {
    detailCode.value = code
    detailVisible.value = true
}

function openMagnets(code) {
    magnetCode.value = code
    magnetVisible.value = true
}
</script>

<style scoped>
.list-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 10px;
    font-size: 13px;
}

/* 移动端 */
.mobile-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
}

.mobile-toolbar .el-input {
    flex: 1;
}

.mobile-mode-tip {
    font-size: 12px;
    color: #94a3b8;
    margin-bottom: 10px;
}

.mobile-card-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
    min-height: 120px;
}

.mobile-empty {
    text-align: center;
    color: #94a3b8;
    font-size: 13px;
    padding: 48px 0;
}
</style>
