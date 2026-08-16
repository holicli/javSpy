<template>
    <div class="page-container">
        <div class="page-header">
            <h2 class="page-title">最新入库</h2>
            <div class="toolbar">
                <el-button type="primary" plain :loading="loading" @click="reload">刷新</el-button>
                <span class="text-muted">共 {{ total }} 部</span>
            </div>
        </div>

        <!-- 桌面表格 -->
        <template v-if="!isMobile">
            <el-table
                v-loading="loading"
                :data="rows"
                max-height="calc(100vh - 225px)"
                border
                stripe
                row-key="code"
                empty-text="暂无数据"
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
                <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
                <el-table-column prop="actors" label="演员" min-width="150" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.actors || '—' }}</template>
                </el-table-column>
                <el-table-column label="时长" width="80" align="center">
                    <template #default="{ row }">{{ row.duration ? row.duration + ' 分' : '—' }}</template>
                </el-table-column>
                <el-table-column prop="releaseDate" label="日期" width="110" />
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
                <el-table-column label="操作" width="130" align="center" fixed="right">
                    <template #default="{ row }">
                        <el-button size="small" type="primary" plain @click="openDetail(row.code)">详情</el-button>
                        <el-button size="small" type="success" plain @click="openMagnets(row.code)">磁力</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <div class="pager">
                <el-pagination
                    v-model:current-page="pageNum"
                    v-model:page-size="pageSize"
                    background
                    layout="total, sizes, prev, pager, next"
                    :total="total"
                    :page-sizes="[15, 30]"
                    @current-change="load"
                    @size-change="onSizeChange"
                />
            </div>
        </template>

        <!-- 移动卡片列表 -->
        <template v-else>
            <div v-loading="loading" class="mobile-card-grid">
                <MobileMovieCard
                    v-for="row in rows"
                    :key="row.code"
                    :movie="row"
                    @open-detail="openDetail"
                />
            </div>
            <div v-if="rows.length === 0 && !loading" class="mobile-empty">暂无数据</div>
            <div class="mobile-more">
                <el-button v-if="hasMore" link type="primary" :loading="loading" @click="loadMore">
                    {{ loading ? '加载中...' : '加载更多' }}
                </el-button>
                <span v-else class="text-muted">已加载全部（共 {{ total }} 部）</span>
            </div>
        </template>
    </div>

    <MovieDetailDialog v-model="detailVisible" :code="detailCode" />
    <MagnetDialog v-model="magnetVisible" :code="magnetCode" />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { javbusApi } from '@/api'
import MovieDetailDialog from '@/components/MovieDetailDialog.vue'
import MagnetDialog from '@/components/MagnetDialog.vue'
import MobileMovieCard from '@/components/MobileMovieCard.vue'
import { useMobile } from '@/composables/useMobile'

const isMobile = useMobile()

const rows = ref([])
const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(30)
const hasMore = ref(false)

const detailVisible = ref(false)
const detailCode = ref('')
const magnetVisible = ref(false)
const magnetCode = ref('')

function normalize(row) {
    return {
        code: row.code,
        title: row.title,
        coverUrl: row.coverUrl || '',
        releaseDate: row.releaseDate,
        duration: row.duration,
        actors: row.actors,
        genres: row.genres,
        magnetCount: row.magnetCount,
        embyExists: row.embyExists
    }
}

async function load(page = pageNum.value, append = false) {
    loading.value = true
    try {
        const res = await javbusApi.newest({ pageNum: page, pageSize: pageSize.value })
        const pageInfo = res.data || {}
        total.value = pageInfo.total || 0
        const list = (pageInfo.list || []).map(normalize)
        if (append) {
            rows.value.push(...list)
        } else {
            rows.value = list
        }
        hasMore.value = page * pageSize.value < total.value
    } catch (e) {
        ElMessage.error('加载失败：' + e.message)
    } finally {
        loading.value = false
    }
}

function loadMore() {
    if (!hasMore.value || loading.value) return
    pageNum.value += 1
    load(pageNum.value, true)
}

function reload() {
    pageNum.value = 1
    load(1, false)
}

function onSizeChange() {
    pageNum.value = 1
    load(1, false)
}

function openDetail(code) {
    detailCode.value = code
    detailVisible.value = true
}

function openMagnets(code) {
    magnetCode.value = code
    magnetVisible.value = true
}

onMounted(() => load(1))
</script>

<style scoped>
.pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
}

/* 移动端 */
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

.mobile-more {
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 14px 0;
    font-size: 13px;
}
</style>
