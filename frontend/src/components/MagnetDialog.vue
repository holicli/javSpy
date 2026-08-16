<template>
    <el-dialog v-model="visible" :title="code + ' 磁力链接'" :width="dialogWidth" top="6vh" destroy-on-close>
        <div v-if="loading" class="body">
            <el-skeleton :rows="5" animated />
        </div>

        <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />

        <div v-else-if="magnets.length === 0" class="body empty">
            <el-empty description="该影片暂无磁力链接" :image-size="90" />
        </div>

        <template v-else>
            <div class="toolbar-line">
                <span class="text-muted">共 {{ magnets.length }} 条磁力</span>
                <div>
                    <el-button size="small" type="warning" plain :disabled="magnets.length === 0" @click="stageAll">
                        暂存全部
                    </el-button>
                    <el-button size="small" type="primary" plain @click="copyAll">
                        复制全部
                    </el-button>
                </div>
            </div>
            <el-table :data="magnets" size="small" max-height="50vh" border>
                <el-table-column type="index" label="#" width="48" />
                <el-table-column label="名称" min-width="220" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.name || row.magnet }}</template>
                </el-table-column>
                <el-table-column prop="sizeText" label="大小" width="90">
                    <template #default="{ row }">{{ row.sizeText || '—' }}</template>
                </el-table-column>
                <el-table-column prop="shareDate" label="分享日期" width="105">
                    <template #default="{ row }">{{ row.shareDate || '—' }}</template>
                </el-table-column>
                <el-table-column label="高清" width="60" align="center">
                    <template #default="{ row }">{{ row.hd ? '是' : '—' }}</template>
                </el-table-column>
                <el-table-column label="字幕" width="60" align="center">
                    <template #default="{ row }">{{ row.subtitle ? '是' : '—' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="200" align="center" fixed="right">
                    <template #default="{ row }">
                        <el-button size="small" type="warning" plain @click="stage(row)">
                            暂存
                        </el-button>
                        <el-button size="small" type="primary" plain @click="copyOne(row)">
                            复制
                        </el-button>
                        <el-button size="small" type="success" plain @click="save(row)">
                            保存
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { javbusApi, copyText } from '@/api'
import { stageMagnet, stageMagnets } from '@/store/staging'
import { useMobile } from '@/composables/useMobile'

const isMobile = useMobile()
const dialogWidth = computed(() => (isMobile.value ? '96%' : '820px'))

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
const magnets = ref([])

watch(visible, (v) => {
    if (v) load()
})

async function load() {
    loading.value = true
    error.value = ''
    magnets.value = []
    try {
        const res = await javbusApi.magnets(props.code)
        magnets.value = Array.isArray(res.data) ? res.data : []
    } catch (e) {
        error.value = e.message
    } finally {
        loading.value = false
    }
}

async function copyOne(row) {
    try {
        await copyText(row.magnet)
        ElMessage.success('已复制磁力链接')
    } catch (e) {
        ElMessage.error('复制失败：' + e.message)
    }
}

async function copyAll() {
    const text = magnets.value.map((m) => m.magnet).join('\n')
    try {
        await copyText(text)
        ElMessage.success('已复制全部 ' + magnets.value.length + ' 条磁力')
    } catch (e) {
        ElMessage.error('复制失败：' + e.message)
    }
}

/** 暂存单条磁力（前端本地暂存，不写数据库）。 */
function stage(row) {
    if (stageMagnet({ code: props.code, magnet: row.magnet, name: row.name, sizeText: row.sizeText, shareDate: row.shareDate })) {
        ElMessage.success('已暂存到本地')
    } else {
        ElMessage.info('该磁力已在暂存区')
    }
}

/** 暂存当前影片的全部磁力。 */
function stageAll() {
    const added = stageMagnets(
        magnets.value.map((m) => ({
            code: props.code,
            magnet: m.magnet,
            name: m.name,
            sizeText: m.sizeText,
            shareDate: m.shareDate
        }))
    )
    ElMessage.success(added > 0 ? '已暂存 ' + added + ' 条磁力' : '全部磁力已在暂存区')
}

async function save(row) {
    try {
        await javbusApi.saveMagnet(props.code, row.magnet)
        ElMessage.success('磁力已保存')
    } catch (e) {
        ElMessage.error('保存失败：' + e.message)
    }
}
</script>

<style scoped>
.body {
    min-height: 160px;
}

.empty {
    display: flex;
    justify-content: center;
    padding: 10px 0;
}

.toolbar-line {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
}
</style>
