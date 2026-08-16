<template>
    <div class="mobile-card" @click="$emit('open-detail', movie.code)">
        <div class="cover-wrap">
            <img v-if="movie.coverUrl" :src="movie.coverUrl" class="cover" loading="lazy" alt="" />
            <div v-else class="cover cover-fallback">无图</div>
            <span
                v-if="typeof movie.magnetCount === 'number' && movie.magnetCount > 0"
                class="tag tag-magnet"
            >
                {{ movie.magnetCount }}磁
            </span>
            <span
                v-if="typeof movie.embyExists === 'boolean' && movie.embyExists"
                class="tag tag-emby"
            >
                Emby
            </span>
        </div>
        <div class="info">
            <div class="code">{{ movie.code }}</div>
            <div class="title">{{ movie.title || '—' }}</div>
            <div class="meta">
                <span>{{ movie.releaseDate || '—' }}</span>
                <span v-if="movie.duration">{{ movie.duration }}分</span>
                <span v-if="movie.actors" class="actors" :title="movie.actors">{{ movie.actors }}</span>
            </div>
        </div>
    </div>
</template>

<script setup>
defineProps({
    movie: { type: Object, required: true }
})
defineEmits(['open-detail'])
</script>

<style scoped>
.mobile-card {
    background: #fff;
    border-radius: 12px;
    overflow: hidden;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    cursor: pointer;
    transition: transform 0.15s;
}

.mobile-card:active {
    transform: scale(0.97);
}

.cover-wrap {
    position: relative;
    aspect-ratio: 3 / 4;
    background: #eef1f4;
}

.cover {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
}

.cover-fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    color: #94a3b8;
    font-size: 12px;
}

.tag {
    position: absolute;
    top: 6px;
    left: 6px;
    padding: 1px 7px;
    border-radius: 20px;
    font-size: 11px;
    line-height: 18px;
    font-weight: 600;
    color: #fff;
}

.tag-magnet {
    background: rgba(5, 150, 105, 0.9);
}

.tag-emby {
    left: auto;
    right: 6px;
    background: rgba(21, 128, 61, 0.9);
}

.info {
    padding: 8px 10px 10px;
}

.code {
    font-size: 13px;
    font-weight: 700;
    color: #3b82f6;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.title {
    margin-top: 2px;
    font-size: 12px;
    line-height: 1.4;
    color: #334155;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.meta {
    margin-top: 5px;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    color: #94a3b8;
    white-space: nowrap;
    overflow: hidden;
}

.meta .actors {
    overflow: hidden;
    text-overflow: ellipsis;
}
</style>
