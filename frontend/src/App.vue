<template>
    <!-- 桌面端布局 -->
    <el-container v-if="!isMobile" class="app-shell">
        <el-aside width="210px" class="sidebar">
            <div class="brand">
                <el-icon :size="22" color="#3b82f6"><VideoCameraFilled /></el-icon>
                <span>JavSpy</span>
            </div>
            <el-menu router :default-active="$route.path" class="side-menu">
                <el-menu-item index="/">
                    <el-icon><Collection /></el-icon>
                    <span>影片库</span>
                </el-menu-item>
                <el-menu-item index="/newest">
                    <el-icon><Clock /></el-icon>
                    <span>最新入库</span>
                </el-menu-item>
                <el-menu-item index="/scrape">
                    <el-icon><MagicStick /></el-icon>
                    <span>刮削中心</span>
                </el-menu-item>
            </el-menu>
            <div class="sidebar-footer">
                <el-button link type="info" @click="onPing" :loading="pinging">
                    <el-icon style="margin-right: 4px"><Connection /></el-icon>
                    {{ pingText }}
                </el-button>
            </div>
        </el-aside>
        <el-main class="main-area">
            <router-view />
        </el-main>
    </el-container>

    <!-- 移动端布局 -->
    <div v-else class="mobile-shell">
        <header class="mobile-header">
            <div class="mobile-brand">
                <el-icon :size="18" color="#3b82f6"><VideoCameraFilled /></el-icon>
                <span>JavSpy</span>
            </div>
            <el-button link :loading="pinging" @click="onPing" :title="pingText">
                <el-icon :size="17"><Connection /></el-icon>
            </el-button>
        </header>
        <main class="mobile-main">
            <router-view />
        </main>
        <nav class="mobile-tabbar">
            <router-link to="/" class="tab" exact-active-class="tab-active">
                <el-icon :size="20"><Collection /></el-icon>
                <span>影片库</span>
            </router-link>
            <router-link to="/newest" class="tab" exact-active-class="tab-active">
                <el-icon :size="20"><Clock /></el-icon>
                <span>最新</span>
            </router-link>
            <router-link to="/scrape" class="tab" exact-active-class="tab-active">
                <el-icon :size="20"><MagicStick /></el-icon>
                <span>刮削</span>
            </router-link>
        </nav>
    </div>

    <!-- 磁力暂存：全局悬浮按钮 -->
    <el-badge
        :value="stagedCount"
        :hidden="stagedCount === 0"
        :max="99"
        class="staging-fab-badge"
        :class="{ 'is-mobile': isMobile }"
    >
        <el-button
            class="staging-fab"
            type="primary"
            circle
            size="large"
            @click="stagingVisible = true"
        >
            <el-icon :size="20"><Files /></el-icon>
        </el-button>
    </el-badge>
    <StagingPanel v-model="stagingVisible" />
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { javbusApi } from '@/api'
import { stagedCount } from '@/store/staging'
import StagingPanel from '@/components/StagingPanel.vue'
import { useMobile } from '@/composables/useMobile'

const isMobile = useMobile()

const pinging = ref(false)
const pingText = ref('后端连通性检测')
const stagingVisible = ref(false)

async function onPing() {
    pinging.value = true
    try {
        const res = await javbusApi.ping()
        const ok = res.success && (res.data || '').startsWith('OK')
        pingText.value = ok ? '后端正常' : '后端异常'
        ElMessage[ok ? 'success' : 'error'](res.data || '未知结果')
    } catch (e) {
        pingText.value = '后端异常'
        ElMessage.error('后端不可达：' + e.message)
    } finally {
        pinging.value = false
    }
}
</script>

<style scoped>
.app-shell {
    height: 100vh;
}

.sidebar {
    background: #fff;
    border-right: 1px solid #e9eef2;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

.brand {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 18px 20px;
    font-size: 18px;
    font-weight: 700;
    color: #1e293b;
    border-bottom: 1px solid #f1f5f9;
}

.side-menu {
    border-right: none;
    flex: 1;
    overflow-y: auto;
    padding-top: 8px;
}

.sidebar-footer {
    padding: 14px 16px;
    border-top: 1px solid #f1f5f9;
}

.main-area {
    background: #f5f7fa;
    padding: 20px 24px;
    overflow-y: auto;
}

/* ===== 移动端 ===== */
.mobile-shell {
    min-height: 100vh;
    padding-top: 54px;
    padding-bottom: 72px;
}

.mobile-header {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    height: 52px;
    z-index: 2000;
    background: #fff;
    border-bottom: 1px solid #e9eef2;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 16px;
}

.mobile-brand {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 17px;
    font-weight: 700;
    color: #1e293b;
}

.mobile-main {
    padding: 12px;
}

.mobile-tabbar {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 2000;
    height: 58px;
    padding-bottom: env(safe-area-inset-bottom);
    background: #fff;
    border-top: 1px solid #e9eef2;
    display: flex;
    align-items: stretch;
}

.tab {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    color: #64748b;
    text-decoration: none;
    font-size: 11px;
    -webkit-tap-highlight-color: transparent;
}

.tab-active {
    color: #3b82f6;
}

/* 磁力暂存悬浮按钮 */
.staging-fab-badge {
    position: fixed;
    right: 24px;
    bottom: 28px;
    z-index: 3000;
}

.staging-fab-badge.is-mobile {
    right: 16px;
    bottom: 78px;
}

.staging-fab {
    box-shadow: 0 6px 18px rgba(37, 99, 235, 0.35);
}
</style>
