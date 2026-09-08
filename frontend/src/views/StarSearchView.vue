<template>
    <div class="page-container">
        <!-- 搜索栏 -->
        <div class="page-header">
            <h2 class="page-title">{{ selectedStar ? selectedStar.name + ' 的影片' : '演员搜索' }}</h2>
            <div class="toolbar">
                <el-input
                    v-model="keyword"
                    placeholder="输入演员名搜索（如 三上、葵つかさ）"
                    clearable
                    style="width: 260px"
                    @keyup.enter="onSearch"
                    @clear="onSearch"
                >
                    <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
                <el-button type="primary" :loading="starLoading" @click="onSearch">搜索演员</el-button>
                <el-button v-if="selectedStar" plain @click="backToSearch">返回搜索结果</el-button>
            </div>
        </div>

        <!-- 演员结果 -->
        <div v-if="!selectedStar">
            <div v-loading="starLoading" class="star-grid-wrap">
                <div v-if="stars.length" class="star-grid">
                    <div
                        v-for="star in stars"
                        :key="star.id"
                        class="star-card"
                        @click="selectStar(star)"
                    >
                        <el-image
                            :src="star.avatarLocal || star.avatar"
                            fit="cover"
                            class="star-avatar"
                            lazy
                        >
                            <template #error>
                                <div class="star-avatar-fallback">{{ (star.name || '?').charAt(0) }}</div>
                            </template>
                        </el-image>
                        <div class="star-name" :title="star.name">{{ star.name }}</div>
                    </div>
                </div>
                <el-empty v-else-if="!starLoading && searched" description="未找到匹配的演员" :image-size="90" />
                <div v-else-if="!starLoading && !searched" class="star-empty-hint">
                    输入演员名（支持中文 / 日文），点击卡片查看该演员的全部入库影片
                </div>
            </div>
        </div>

        <!-- 选中演员的影片 -->
        <template v-else>
            <div v-if="!isMobile" class="movie-count-line">
                <span class="text-muted">已加载 {{ movies.length }} 部 · 第 {{ pageNum }} 页</span>
            </div>

            <!-- 桌面表格 -->
            <el-table
                v-if="!isMobile"
                v-loading="movieLoading"
                :data="movies"
                max-height="calc(100vh - 260px)"
                border
                stripe
                row-key="code"
                empty-text="该演员暂无影片"
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
                        <span
                            class="code-cell"
                            :class="{ 'staged-code': isStaged(row.code) }"
                            @click="openDetail(row.code)"
                            style="cursor: pointer"
                        >
                            {{ row.code }}
                        </span>
                    </template>
                </el-table-column>
                <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.title || '—' }}</template>
                </el-table-column>
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
                <el-table-column label="操作" width="215" align="center" fixed="right">
                    <template #default="{ row }">
                        <el-button size="small" type="primary" plain @click="openDetail(row.code)">详情</el-button>
                        <el-button size="small" type="success" plain @click="openMagnets(row.code)">磁力</el-button>
                        <el-button
                            size="small"
                            type="warning"
                            plain
                            :loading="refreshingCode === row.code"
                            @click="refreshMagnetsOf(row.code)"
                        >
                            更新磁力
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>

            <div v-if="!isMobile" class="pager">
                <el-button :loading="movieLoading" :disabled="!hasMore" @click="loadMore">
                    {{ hasMore ? '加载更多' : '已加载全部' }}
                </el-button>
            </div>

            <!-- 移动卡片列表 -->
            <template v-else>
                <div v-loading="movieLoading" class="mobile-card-grid">
                    <MobileMovieCard
                        v-for="row in movies"
                        :key="row.code"
                        :movie="row"
                        @open-detail="openDetail"
                    />
                </div>
                <div v-if="movies.length === 0 && !movieLoading" class="mobile-empty">
                    该演员暂无影片
                </div>
                <div class="mobile-more">
                    <el-button v-if="hasMore" link type="primary" :loading="movieLoading" @click="loadMore">
                        {{ movieLoading ? '加载中...' : '加载更多' }}
                    </el-button>
                    <span v-else class="text-muted">已加载 {{ movies.length }} 部</span>
                </div>
            </template>
        </template>
    </div>

    <MovieDetailDialog v-model="detailVisible" :code="detailCode" />
    <MagnetDialog v-model="magnetVisible" :code="magnetCode" />
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { javbusApi } from '@/api'
import MovieDetailDialog from '@/components/MovieDetailDialog.vue'
import MagnetDialog from '@/components/MagnetDialog.vue'
import MobileMovieCard from '@/components/MobileMovieCard.vue'
import { useMobile } from '@/composables/useMobile'
import { isStaged } from '@/store/staging'

const isMobile = useMobile()

const keyword = ref('')
const stars = ref([])
const starLoading = ref(false)
const searched = ref(false)
const followedLoaded = ref(false)

const selectedStar = ref(null)
const movies = ref([])
const movieLoading = ref(false)
const pageNum = ref(1)
const hasMore = ref(false)
/** 正在刷新磁力的番号（行内按钮 loading） */
const refreshingCode = ref('')

const detailVisible = ref(false)
const detailCode = ref('')
const magnetVisible = ref(false)
const magnetCode = ref('')

async function onSearch() {
    const kw = keyword.value.trim()
    if (!kw) {
        ElMessage.warning('请输入演员名')
        return
    }
    starLoading.value = true
    try {
        const res = await javbusApi.searchStars(kw)
        stars.value = Array.isArray(res.data) ? res.data : []
        searched.value = true
        selectedStar.value = null
        movies.value = []
    } catch (e) {
        ElMessage.error('搜索失败：' + e.message)
    } finally {
        starLoading.value = false
    }
}

function selectStar(star) {
    selectedStar.value = star
    pageNum.value = 1
    movies.value = []
    loadMovies(1)
}

function backToSearch() {
    selectedStar.value = null
    movies.value = []
}

function normalize(row) {
    return {
        code: row.code,
        title: row.title,
        coverUrl: row.coverUrl || row.cover || '',
        date: row.date || '',
        releaseDate: row.releaseDate || row.date || '',
        duration: row.duration,
        actors: row.actors,
        genres: row.genres,
        magnetCount: row.magnetCount,
        embyExists: row.embyExists,
        status: row.status || ''
    }
}

async function loadMovies(page = pageNum.value, append = false) {
    if (!selectedStar.value) return
    movieLoading.value = true
    try {
        const res = await javbusApi.moviesByStar(selectedStar.value.id, page, 'exist')
        const data = res.data || {}
        const list = (data.movies || []).map(normalize)
        if (append) {
            movies.value.push(...list)
        } else {
            movies.value = list
        }
        hasMore.value = !!data.hasNextPage
    } catch (e) {
        ElMessage.error('加载失败：' + e.message)
    } finally {
        movieLoading.value = false
    }
}

function loadMore() {
    if (!hasMore.value || movieLoading.value) return
    pageNum.value += 1
    loadMovies(pageNum.value, true)
}

function openDetail(code) {
    detailCode.value = code
    detailVisible.value = true
}

function openMagnets(code) {
    magnetCode.value = code
    magnetVisible.value = true
}

/** 行内更新磁力：重拉 javbus 增量入库，成功后刷新该行磁力数量。 */
async function refreshMagnetsOf(code) {
    if (refreshingCode.value) return
    refreshingCode.value = code
    try {
        const res = await javbusApi.refreshMagnets(code)
        if (res.success) {
            const after = Number(res.data?.after ?? 0)
            const row = movies.value.find((r) => r.code === code)
            if (row && !Number.isNaN(after)) row.magnetCount = after
            ElMessage.success(res.message || '更新磁力完成')
        } else {
            ElMessage.error(res.message || '更新磁力失败')
        }
    } catch (e) {
        ElMessage.error('更新磁力失败：' + e.message)
    } finally {
        refreshingCode.value = ''
    }
}
</script>

<style scoped>
.page-header .toolbar .el-input {
    width: 260px;
}

.star-grid-wrap {
    min-height: 120px;
}

.star-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
    gap: 12px;
}

.star-card {
    background: #fff;
    border-radius: 10px;
    overflow: hidden;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    cursor: pointer;
    transition: transform 0.15s, box-shadow 0.15s;
    text-align: center;
    padding-bottom: 6px;
}

.star-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
}

.star-card:active {
    transform: scale(0.97);
}

.star-avatar {
    width: 100%;
    aspect-ratio: 1;
    display: block;
    background: #eef1f4;
}

.star-avatar-fallback {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 30px;
    font-weight: 700;
    color: #94a3b8;
    background: #f1f5f9;
}

.star-name {
    padding: 6px 6px 2px;
    font-size: 12.5px;
    font-weight: 600;
    color: #334155;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.star-empty-hint {
    text-align: center;
    color: #94a3b8;
    font-size: 13px;
    padding: 48px 0;
}

.movie-count-line {
    margin-bottom: 10px;
    font-size: 13px;
}

.pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
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

.mobile-more {
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 14px 0;
    font-size: 13px;
}
</style>