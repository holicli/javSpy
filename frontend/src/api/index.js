import axios from 'axios'

/**
 * 统一的 axios 实例。
 * 后端所有接口返回 WebResult{success, data, message}，
 * 拦截器把 success=false 转成 Promise 拒绝，调用方直接拿 data。
 */
const http = axios.create({
    baseURL: '',
    timeout: 120000
})

http.interceptors.response.use(
    (res) => {
        const body = res.data
        if (body && typeof body === 'object' && 'success' in body) {
            if (body.success === false) {
                return Promise.reject(new Error(body.message || '请求失败'))
            }
            return body
        }
        return body
    },
    (err) => {
        const msg =
            err.response?.data?.message ||
            err.message ||
            '网络错误'
        return Promise.reject(new Error(msg))
    }
)

export const javbusApi = {
    // 连通性自检
    ping: () => http.get('/javbus-api/ping'),

    // 按番号抓取详情 + 磁力并入库
    scrapeByCode: (code) => http.get('/javbus-api/scrape/code', { params: { code } }),

    // 按关键词搜索并逐部入库
    scrapeByKeyword: (keyword, pages = 1, magnet = 'exist') =>
        http.get('/javbus-api/scrape/search', { params: { keyword, pages, magnet } }),

    // 按页抓取列表（withDetail=false 时会逐部入库）
    scrapePage: (page, magnet = 'exist', withDetail = false) =>
        http.get('/javbus-api/scrape/page', { params: { page, magnet, withDetail } }),

    // javbus API 关键字搜索并逐部完整入库（详情+磁力+封面）
    searchFromApi: (keyword, page = 1, magnet = 'exist') =>
        http.get('/javbus-api/search', { params: { keyword, page, magnet } }),

    // 分页查询已入库影片
    list: (params) => http.get('/javbus-api/list', { params }),

    // 最新入库影片
    newest: (params) => http.get('/javbus-api/newest', { params }),

    // 影片详情
    movieDetail: (code) => http.get('/javbus-api/movie', { params: { code } }),

    // 演员详情
    starDetail: (id, type = 'normal') => http.get('/javbus-api/star', { params: { id, type } }),

    // 影片磁力列表
    magnets: (code) => http.get('/javbus-api/magnets', { params: { code } }),

    // 保存磁力到单独表
    saveMagnet: (code, magnet) =>
        http.post('/javbus-api/magnets/save', null, { params: { code, magnet } }),

    // 关注演员
    followActors: () => http.get('/javbus-api/follow/actors'),
    addFollowActor: (name, remark) =>
        http.post('/javbus-api/follow/add', null, { params: { name, remark } }),
    removeFollowActor: (name) =>
        http.post('/javbus-api/follow/remove', null, { params: { name } }),

    // 后台一键刮削直到命中 Emby
    startScrapeUntilEmby: () => http.post('/javbus-api/scrape/until-emby'),
    scrapeUntilEmbyStatus: () => http.get('/javbus-api/scrape/until-emby/status')
}

/** 复制文本到剪贴板（带降级方案）。 */
export function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
        return navigator.clipboard.writeText(text)
    }
    return new Promise((resolve, reject) => {
        const ta = document.createElement('textarea')
        ta.value = text
        ta.style.position = 'fixed'
        ta.style.opacity = '0'
        document.body.appendChild(ta)
        ta.select()
        try {
            document.execCommand('copy')
            resolve()
        } catch (e) {
            reject(e)
        } finally {
            document.body.removeChild(ta)
        }
    })
}
