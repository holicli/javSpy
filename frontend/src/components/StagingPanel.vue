<template>
    <el-dialog v-model="visible" title="磁力暂存" :width="dialogWidth" top="8vh" destroy-on-close>
        <div class="panel">
            <div class="panel-toolbar">
                <span class="text-muted">
                    共 {{ stagedCount }} 条 · 回车分割 · 仅暂存在本机浏览器，不写入数据库
                </span>
                <div>
                    <el-button
                        size="small"
                        type="primary"
                        plain
                        :disabled="stagedCount === 0"
                        @click="copyAll"
                    >
                        复制全部
                    </el-button>
                    <el-button
                        size="small"
                        type="danger"
                        plain
                        :disabled="stagedCount === 0"
                        @click="clearAll"
                    >
                        清空
                    </el-button>
                </div>
            </div>

            <el-input
                :model-value="stagedMagnetText"
                type="textarea"
                :rows="12"
                readonly
                resize="none"
                placeholder="暂存区为空，可在磁力弹窗中点击「暂存」添加"
            />

            <div v-if="stagedMagnets.length" class="panel-list">
                <div class="panel-list-title">暂存明细（点击右侧按钮可移除单条）</div>
                <div v-for="(m, i) in stagedMagnets" :key="i" class="list-row">
                    <span class="list-text" :title="m.magnet">
                        {{ m.code || '—' }} · {{ m.name || m.magnet }}
                    </span>
                    <span class="list-meta">{{ m.sizeText || '' }}</span>
                    <el-button size="small" link type="danger" @click="removeOne(i)">移除</el-button>
                </div>
            </div>
        </div>

        <template #footer>
            <el-button @click="visible = false">关闭</el-button>
        </template>
    </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { stagedMagnets, stagedCount, stagedMagnetText, removeStaged, clearStaged } from '@/store/staging'
import { copyText } from '@/api'
import { useMobile } from '@/composables/useMobile'

const isMobile = useMobile()
const dialogWidth = computed(() => (isMobile.value ? '96%' : '720px'))

const props = defineProps({
    modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
    get: () => props.modelValue,
    set: (v) => emit('update:modelValue', v)
})

async function copyAll() {
    try {
        await copyText(stagedMagnetText.value)
        ElMessage.success('已复制 ' + stagedCount.value + ' 条磁力（回车分割）')
    } catch (e) {
        ElMessage.error('复制失败：' + e.message)
    }
}

async function clearAll() {
    try {
        await ElMessageBox.confirm('确定清空全部暂存磁力？', '提示', { type: 'warning' })
    } catch {
        return
    }
    clearStaged()
    ElMessage.success('已清空')
}

function removeOne(index) {
    removeStaged(index)
}
</script>

<style scoped>
.panel-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    margin-bottom: 10px;
}

.panel-list {
    margin-top: 12px;
}

.panel-list-title {
    font-size: 12px;
    color: #94a3b8;
    margin-bottom: 6px;
}

.list-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 0;
    border-bottom: 1px dashed #edf2f6;
    font-size: 12.5px;
}

.list-text {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: #334155;
}

.list-meta {
    color: #94a3b8;
    flex-shrink: 0;
}
</style>
