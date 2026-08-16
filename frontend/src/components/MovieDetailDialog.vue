<template>
    <el-dialog v-model="visible" :title="dialogTitle" :width="dialogWidth" top="5vh" destroy-on-close>
        <!-- 加载中 -->
        <div v-if="loading" class="detail-body">
            <el-skeleton :rows="8" animated />
        </div>

        <!-- 加载失败 -->
        <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon>
            <template #default>
                <el-button size="small" type="primary" style="margin-top: 8px" @click="load">重试</el-button>
            </template>
        </el-alert>

        <!-- 未入库 -->
        <div v-else-if="!data || !data.found" class="detail-body not-found">
            <el-empty description="该影片尚未抓取入库">
                <el-button type="primary" @click="scrapeAndReload" :loading="scraping">
                    立即抓取入库
                </el-button>
            </el-empty>
        </div>

        <!-- 正常详情 -->
        <template v-else>
            <div class="detail-body">
                <div class="movie-main">
                    <div class="movie-cover">
                        <el-image :src="movie.coverUrl" fit="cover" class="movie-cover-img" lazy>
                            <template #error>
                                <div class="cover-fallback-lg">无封面</div>
                            </template>
                        </el-image>
                    </div>
                    <div class="movie-meta">
                        <div class="meta-row"><span class="meta-label">番号</span>{{ movie.code }}</div>
                        <div class="meta-row"><span class="meta-label">标题</span>{{ movie.title || '—' }}</div>
                        <div class="meta-row"><span class="meta-label">发售日期</span>{{ movie.releaseDate || '—' }}</div>
                        <div class="meta-row"><span class="meta-label">时长</span>{{ movie.duration ? movie.duration + ' 分钟' : '—' }}</div>
                        <div class="meta-row"><span class="meta-label">导演</span>{{ movie.director || '—' }}</div>
                        <div class="meta-row"><span class="meta-label">片商</span>{{ movie.studio || '—' }}</div>
                        <div class="meta-row"><span class="meta-label">发行商</span>{{ movie.publisher || '—' }}</div>
                        <div class="meta-row"><span class="meta-label">系列</span>{{ movie.series || '—' }}</div>
                        <div class="meta-row">
                            <span class="meta-label">Emby</span>
                            <span :class="['badge', movie.embyExists ? 'badge-emby-yes' : 'badge-emby-no']">
                                {{ movie.embyExists ? '已存在' : '不存在' }}
                            </span>
                        </div>
                    </div>
                </div>

                <div class="section">
                    <div class="section-title">演员（点击查看详情）</div>
                    <div v-if="data.stars && data.stars.length" class="star-chips">
                        <el-tag
                            v-for="s in data.stars"
                            :key="s.id"
                            class="star-chip"
                            effect="plain"
                            round
                            @click="openStar(s.id, s.name)"
                        >
                            {{ s.name }}
                        </el-tag>
                    </div>
                    <div v-else class="text-muted">暂无演员信息</div>
                </div>

                <div class="section">
                    <div class="section-title">预览图（点击查看大图）</div>
                    <div v-if="data.samples && data.samples.length" class="sample-grid">
                        <el-image
                            v-for="(s, i) in data.samples"
                            :key="i"
                            :src="s.thumbnail || s.src"
                            :preview-src-list="sampleSrcList"
                            :initial-index="i"
                            fit="cover"
                            class="sample-img"
                            lazy
                            preview-teleported
                        />
                    </div>
                    <div v-else class="text-muted">暂无预览图</div>
                </div>
            </div>
        </template>

        <template #footer>
            <el-button @click="visible = false">关闭</el-button>
            <el-button v-if="data && data.found" type="primary" @click="openMagnets">
                <el-icon style="margin-right: 4px"><Link /></el-icon>
                磁力链接（{{ movie.magnetCount ?? '—' }}）
            </el-button>
        </template>
    </el-dialog>

    <!-- 子弹窗 -->
    <MagnetDialog v-model="magnetVisible" :code="code" />
    <StarDetailDialog v-model="starVisible" :star-id="starId" :star-name="starName" />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { javbusApi } from '@/api'
import MagnetDialog from './MagnetDialog.vue'
import StarDetailDialog from './StarDetailDialog.vue'
import { useMobile } from '@/composables/useMobile'

const isMobile = useMobile()
const dialogWidth = computed(() => (isMobile.value ? '96%' : '880px'))

const props = defineProps({
    modelValue: { type: Boolean, default: false },
    code: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
    get: () => props.modelValue,
    set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const error = ref('')
const data = ref(null)
const scraping = ref(false)

const magnetVisible = ref(false)
const starVisible = ref(false)
const starId = ref('')
const starName = ref('')

const movie = computed(() => (data.value && data.value.movie) || {})
const dialogTitle = computed(() =>
    movie.value.code ? movie.value.code + ' 影片详情' : '影片详情'
)
const sampleSrcList = computed(() =>
    (data.value?.samples || []).map((s) => s.src || s.thumbnail).filter(Boolean)
)

watch(visible, (v) => {
    if (v) load()
})

async function load() {
    if (!props.code) return
    loading.value = true
    error.value = ''
    data.value = null
    try {
        const res = await javbusApi.movieDetail(props.code)
        data.value = res.data
    } catch (e) {
        error.value = e.message
    } finally {
        loading.value = false
    }
}

async function scrapeAndReload() {
    scraping.value = true
    try {
        await javbusApi.scrapeByCode(props.code)
        await load()
    } catch (e) {
        error.value = e.message
    } finally {
        scraping.value = false
    }
}

function openStar(id, name) {
    starId.value = id
    starName.value = name
    starVisible.value = true
}

function openMagnets() {
    magnetVisible.value = true
}
</script>

<style scoped>
.detail-body {
    min-height: 200px;
}

.movie-main {
    display: flex;
    gap: 20px;
    margin-bottom: 18px;
}

.movie-cover-img {
    width: 165px;
    height: 230px;
    border-radius: 8px;
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}

.cover-fallback-lg {
    width: 165px;
    height: 230px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #eef1f4;
    color: #94a3b8;
    border-radius: 8px;
}

.movie-meta {
    flex: 1;
    min-width: 0;
}

.meta-row {
    font-size: 13.5px;
    line-height: 2.1;
    color: #334155;
    word-break: break-all;
}

.meta-label {
    display: inline-block;
    width: 70px;
    color: #64748b;
    font-weight: 500;
    flex-shrink: 0;
}

.section {
    margin-top: 6px;
    margin-bottom: 18px;
}

.section-title {
    font-size: 13px;
    font-weight: 600;
    color: #475569;
    margin-bottom: 10px;
}

.star-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.star-chip {
    cursor: pointer;
    font-size: 13px;
}

.sample-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 10px;
}

.sample-img {
    width: 100%;
    height: 100px;
    border-radius: 8px;
    border: 1px solid #e2e8f0;
    cursor: zoom-in;
}

.not-found {
    display: flex;
    justify-content: center;
    padding: 20px 0;
}

/* 手机端适配 */
@media (max-width: 767px) {
    .movie-main {
        flex-direction: column;
        align-items: center;
    }

    .movie-cover-img,
    .cover-fallback-lg {
        width: 150px;
        height: 210px;
    }

    .movie-meta {
        width: 100%;
    }

    .sample-grid {
        grid-template-columns: repeat(3, 1fr);
    }

    .sample-img {
        height: 90px;
    }
}
</style>
