import { createRouter, createWebHashHistory } from 'vue-router'
import MovieListView from '@/views/MovieListView.vue'
import NewestView from '@/views/NewestView.vue'
import ScrapeCenterView from '@/views/ScrapeCenterView.vue'
import StarSearchView from '@/views/StarSearchView.vue'

// 使用 hash 模式，避免部署到 Spring Boot 静态目录时需要服务端路由回退配置
const router = createRouter({
    history: createWebHashHistory(),
    routes: [
        { path: '/', name: 'movies', component: MovieListView, meta: { title: '影片库' } },
        { path: '/newest', name: 'newest', component: NewestView, meta: { title: '最新入库' } },
        { path: '/star', name: 'star', component: StarSearchView, meta: { title: '演员搜索' } },
        { path: '/scrape', name: 'scrape', component: ScrapeCenterView, meta: { title: '刮削中心' } }
    ]
})

router.afterEach((to) => {
    document.title = (to.meta?.title ? to.meta.title + ' · ' : '') + 'JavSpy'
})

export default router
