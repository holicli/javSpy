<template>
    <el-dialog v-model="visible" title="演员详情" :width="dialogWidth" top="12vh" destroy-on-close>
        <div v-if="loading" class="body">
            <el-skeleton :rows="6" animated />
        </div>

        <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />

        <div v-else-if="!star" class="body empty">
            <el-empty :description="'未找到演员：' + (starName || '')" :image-size="90" />
        </div>

        <div v-else class="star-card">
            <el-image :src="star.avatar" fit="cover" class="star-avatar" lazy>
                <template #error>
                    <div class="avatar-fallback">无头像</div>
                </template>
            </el-image>
            <div class="star-info">
                <div class="info-row"><b>名字：</b>{{ star.name || '—' }}</div>
                <div class="info-row"><b>生日：</b>{{ star.birthday || '—' }}</div>
                <div class="info-row"><b>年龄：</b>{{ star.age || '—' }}</div>
                <div class="info-row"><b>身高：</b>{{ star.height || '—' }}</div>
                <div class="info-row">
                    <b>三围：</b>{{ star.bust || '—' }} / {{ star.waistline || '—' }} / {{ star.hipline || '—' }}
                </div>
                <div class="info-row"><b>出生地：</b>{{ star.birthplace || '—' }}</div>
                <div class="info-row"><b>爱好：</b>{{ star.hobby || '—' }}</div>
            </div>
        </div>

        <template #footer>
            <el-button @click="visible = false">关闭</el-button>
            <el-button
                v-if="star"
                type="primary"
                :loading="following"
                @click="follow"
            >
                关注演员
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { javbusApi } from '@/api'
import { useMobile } from '@/composables/useMobile'

const isMobile = useMobile()
const dialogWidth = computed(() => (isMobile.value ? '92%' : '560px'))

const props = defineProps({
    modelValue: { type: Boolean, default: false },
    starId: { type: String, default: '' },
    starName: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
    get: () => props.modelValue,
    set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const error = ref('')
const star = ref(null)
const following = ref(false)

watch(visible, (v) => {
    if (v) load()
})

async function load() {
    if (!props.starId) return
    loading.value = true
    error.value = ''
    star.value = null
    try {
        const res = await javbusApi.starDetail(props.starId, 'normal')
        star.value = (res.data || {}).star || null
    } catch (e) {
        error.value = e.message
    } finally {
        loading.value = false
    }
}

async function follow() {
    following.value = true
    try {
        await javbusApi.addFollowActor(star.value.name, '')
        ElMessage.success('已关注：' + star.value.name)
    } catch (e) {
        ElMessage.error('关注失败：' + e.message)
    } finally {
        following.value = false
    }
}
</script>

<style scoped>
.body {
    min-height: 140px;
}

.empty {
    display: flex;
    justify-content: center;
}

.star-card {
    display: flex;
    gap: 18px;
}

.star-avatar {
    width: 130px;
    height: 175px;
    border-radius: 10px;
    flex-shrink: 0;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.avatar-fallback {
    width: 130px;
    height: 175px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #eef1f4;
    color: #94a3b8;
    border-radius: 10px;
    font-size: 13px;
}

.star-info {
    flex: 1;
    min-width: 0;
}

.info-row {
    font-size: 13.5px;
    line-height: 2.2;
    color: #334155;
    word-break: break-all;
}

.info-row b {
    color: #1e293b;
}

/* 手机端适配 */
@media (max-width: 767px) {
    .star-card {
        flex-direction: column;
        align-items: center;
    }

    .star-avatar,
    .avatar-fallback {
        width: 110px;
        height: 148px;
    }
}
</style>
