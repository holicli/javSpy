# 部署说明：GitHub Actions → GHCR → 飞牛 OS（x86）

整体链路：

```
本地 push master
   → GitHub Actions 构建多阶段镜像（前端 Vite + Maven jar）
   → 推送 ghcr.io/holicli/javspy:latest / :sha-xxxxxx
   → NAS 上 docker compose pull && docker compose up -d
```

新增文件一览：

| 文件 | 作用 |
| --- | --- |
| `Dockerfile` | 三阶段构建：node 构建前端 → maven 打 jar → JRE 11 运行 |
| `.dockerignore` | 构建上下文排除项 |
| `docker-compose.yml` | NAS 上的运行编排（app 容器） |
| `.env.example` | 运行配置模板（复制为 `.env` 使用） |
| `.github/workflows/build-push-image.yml` | push 后自动构建并推送镜像 |
| `src/main/resources/application.yml` | 改为环境变量占位，仓库不再保存任何口令 |
| `src/main/resources/application-local.example.yml` | 本地开发 profile 模板 |

---

## 一、GitHub 侧首次准备

1. 推送代码到 `master` 后，Actions 会自动执行 `Build and Push Image`。
   仓库是公开的，Actions 免费额度不受限；到仓库 `Actions` 页确认构建绿色通过。
2. 构建完成后，仓库首页右侧 `Packages`（或个人主页 → Packages）能看到 `javspy` 镜像，
   标签有 `latest` 和 `sha-<commit>`。
3. **镜像可见性**：GHCR 包默认是私有的，二选一：
   - 保持私有：NAS 上执行 `docker login ghcr.io`（见下一节）；
   - 改为公开：Package 页面 → `Package settings` → 底部 `Change visibility` → `Public`，
     之后 NAS 拉取不需要登录。
4. 私有包需要的 token：GitHub → `Settings` → `Developer settings` →
   `Personal access tokens`，classic token 勾选 `read:packages`，
   或 fine-grained token 授予 `Packages: Read`。

> ⚠️ 安全提醒：这个仓库此前是公开的，历史提交里出现过数据库密码、Emby API Key。
> 推代码前请先轮换这些凭据；`application.yml` 现在已不再保存任何口令。

---

## 二、NAS 部署（飞牛 OS）

飞牛 OS 基于 Debian，自带 Docker。先确认 SSH 已开启（控制台 → 终端与 SSH）。

### 1. 准备目录和配置

```bash
# 路径按飞牛实际存储空间调整（/vol1/1000/... 是常见默认位置）
mkdir -p /vol1/1000/docker/javspy
cd /vol1/1000/docker/javspy

# 把仓库里的 docker-compose.yml 和 .env.example 拷到该目录（scp / 文件管理器均可）
cp .env.example .env
vi .env   # 填真实值：数据库密码、Emby API Key、图片目录等
```

### 2. 登录 GHCR（仅私有包需要）

```bash
echo "<你的PAT>" | docker login ghcr.io -u holicli --password-stdin
```

### 3. 启动

```bash
cd /vol1/1000/docker/javspy
docker compose up -d
docker compose ps
docker compose logs -f javspy
```

浏览器访问 `http://<NAS的局域网IP>:8084`。

### 4. 日常更新

```bash
cd /vol1/1000/docker/javspy
docker compose pull
docker compose up -d
```

想自动化更新，二选一：

- 飞牛「计划任务」（cron）：`0 4 * * * cd /vol1/1000/docker/javspy && docker compose pull && docker compose up -d`
- 部署 Watchtower 容器，监控 `ghcr.io/holicli/javspy:latest`，检测到新镜像自动重启。

### 5. 回滚

每次构建都有 `sha-<commit>` 标签。把 `.env` 里的 `JAVSPY_TAG` 改成对应值后：

```bash
docker compose pull && docker compose up -d
```

---

## 三、`.env` 配置说明

| 变量 | 说明 |
| --- | --- |
| `JAVSPY_TAG` | 镜像标签，`latest` 或 `sha-xxxxxx`（回滚用） |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | MariaDB 连接 |
| `DB_POOL_SIZE` | Hikari 连接池上限，默认 20（NAS 上不要用原来的 500） |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 |
| `EMBY_ENABLED` / `EMBY_URL` / `EMBY_API_KEY` | Emby 影片存在性检查 |
| `JAVBUS_API_URL` / `JAVBUS_API_TOKEN` | 自托管 javbus-api 地址 |
| `JAVBUS_API_PROXY_ENABLED` 等 | 下载 javbus 图片需要代理时才打开 |
| `IMAGE_STORAGE_PATH` | 容器内图片目录，固定 `/pic` |
| `PIC_DIR` | 宿主机图片目录，挂载到容器 `/pic` |
| `MYBATIS_LOG_IMPL` | 生产建议 `org.apache.ibatis.logging.nologging.NoLoggingImpl`，排查问题时改回 stdout |
| `JAVA_TOOL_OPTIONS` | JVM 参数，NAS 上建议 `-Xms256m -Xmx512m` |

注意：容器里的 `127.0.0.1` 指容器自身。数据库 / Redis / Emby / javbus-api 要填
NAS 的局域网 IP（或改用 `host.docker.internal`，需给 compose 加
`extra_hosts: ["host.docker.internal:host-gateway"]`）。

---

## 四、本地开发

前端：

```bash
cd frontend
npm ci
npm run dev        # dev server 会把 /javbus-api、/pic 代理到 127.0.0.1:8084
```

后端（IDEA）：复制 `src/main/resources/application-local.example.yml` 为
`application-local.yml` 并填值（该文件已被 gitignore），运行配置里把
`Active profiles` 设为 `local`，或直接给运行配置加环境变量。

也可以完全不用 profile，在 IDEA 运行配置里填环境变量：
`DB_HOST`、`DB_PASSWORD`、`EMBY_API_KEY`、`IMAGE_STORAGE_PATH` 等，
变量名与 `.env.example` 相同。

---

## 五、备份与排查

- 备份：MariaDB 定时 `mysqldump`、`PIC_DIR` 图片目录、`.env` 文件（例如用飞牛的备份任务）。
- 前端产物：`src/main/resources/static` 里提交的构建产物会被 Docker 构建过程覆盖，
  以 Dockerfile 中 `npm run build` 的结果为准，不影响使用；想精简仓库也可以后续
  把这些产物从 git 移除并加进 `.gitignore`。
- 构建慢：首次会下载 Maven / npm 依赖，之后由 BuildKit 层缓存和 GHA 缓存加速。
- 端口冲突：改 `docker-compose.yml` 的 `ports` 左侧端口。
