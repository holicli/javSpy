# GitHub Actions 构建镜像并推送到 GHCR 教程

> 适用于本项目 `holicli/javSpy`：推送 `master` → Actions 自动构建 Docker 镜像 →
> 推送到 GitHub Container Registry（GHCR）→ NAS / 任何机器 `docker pull`。
> 对应工作流文件：`.github/workflows/build-push-image.yml`。

---

## 0. 几个概念先分清

| 名词 | 是什么 | 谁用它 |
| --- | --- | --- |
| **GitHub Actions** | GitHub 提供的 CI/CD，按 workflow 文件在云端（ubuntu-latest）跑任务 | 构建镜像时用 |
| **GITHUB_TOKEN** | 每次 workflow 运行自动生成的临时令牌，用完即弃 | workflow 里登录 GHCR 用 |
| **PAT**（Personal Access Token） | 你手动创建的长期令牌，可设权限和有效期 | NAS 拉私有镜像时用 |
| **GHCR** | GitHub 的容器镜像仓库，地址 `ghcr.io/<用户名>/<镜像名>` | 存镜像 |
| **docker/build-push-action** | 官方封装好的构建 + 推送 action | workflow 里构建镜像用 |

关键点：

- 工作流文件必须放在仓库的 `.github/workflows/` 目录，扩展名 `.yml` 或 `.yaml`；
- 镜像路径**必须全小写**（`ghcr.io/holicli/javspy`，不能是 `javSpy`）；
- 用 `GITHUB_TOKEN` 推送镜像时，workflow 必须声明 `permissions: packages: write`；
- GHCR 包**默认私有**，NAS 拉取需要 `docker login`，或者把包改成 public。

---

## 1. 前置检查

1. 仓库已有 workflow 文件（本项目已有 `.github/workflows/build-push-image.yml`）。
2. 仓库 Settings → Actions → General：确认 Actions 允许运行。
   公开仓库默认开启，一般不用改。
3. 不需要提前创建任何 Secret —— `secrets.GITHUB_TOKEN` 是自动注入的。

---

## 2. 工作流逐段解释

```yaml
name: Build and Push Image            # workflow 名称，Actions 页面显示

on:
  push:
    branches: [master]                # push 到 master 自动触发
  workflow_dispatch:                  # 也允许在 Actions 页面手动点“Run workflow”

permissions:
  contents: read                      # 读代码
  packages: write                     # 关键：允许 GITHUB_TOKEN 推送镜像到 GHCR

concurrency:
  group: build-image-${{ github.ref }}
  cancel-in-progress: true            # 连续 push 时取消旧任务，省额度

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ghcr.io/holicli/javspy  # 镜像地址，必须小写

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4              # 1. 拉代码到 runner
      - uses: docker/setup-buildx-action@v3    # 2. 启用 BuildKit（支持缓存）
      - uses: docker/login-action@v3           # 3. 登录 GHCR
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}        # 触发人，也就是仓库所有者
          password: ${{ secrets.GITHUB_TOKEN }}# 自动令牌，无需手动配置
      - id: meta
        uses: docker/metadata-action@v5        # 4. 生成 tags 和 labels
        with:
          images: ${{ env.IMAGE_NAME }}
          tags: |
            type=raw,value=latest,enable={{is_default_branch}}  # master 上打 latest
            type=sha,format=short                               # 每次提交打 sha-xxxx
      - uses: docker/build-push-action@v6      # 5. 构建 + 推送
        with:
          context: .
          push: true
          platforms: linux/amd64               # 目标架构；ARM NAS 再加 linux/arm64
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}  # 含镜像来源，自动关联到仓库
          cache-from: type=gha
          cache-to: type=gha,mode=max          # 用 GitHub 缓存加速后续构建
```

---

## 3. 第一次运行

```bash
# 本地提交并推送（workflow 文件要在 master 上才会生效）
git add .github/workflows/build-push-image.yml Dockerfile .dockerignore docker-compose.yml .env.example DEPLOY.md
git commit -m "ci: build and push image to GHCR"
git push origin master
```

推送后：

1. 打开仓库 → **Actions** 标签页，能看到 `Build and Push Image` 正在运行；
2. 点进去看每一步日志：`Checkout` → `Set up Buildx` → `Log in to GHCR` →
   `Extract metadata` → `Build and push`；
3. 绿色勾表示成功，整个流程通常 3~8 分钟（首次会下载 Maven/npm 依赖，之后有缓存）。

手动触发（不推代码也能重新构建）：

- 页面：Actions → 左侧选 `Build and Push Image` → 右侧 `Run workflow` → 选 `master` → Run；
- 命令行（需装 GitHub CLI）：
  ```bash
  gh workflow run build-push-image.yml --ref master
  gh run list --workflow=build-push-image.yml
  gh run watch
  ```

---

## 4. 查看镜像

构建成功后，镜像出现在两个地方：

- 仓库首页右侧 **Packages** 里（或 个人主页 → Packages → `javspy`）；
- 直接访问：`https://github.com/holicli/javSpy/pkgs/container/javspy`

页面里能看到 tags：

| tag | 说明 |
| --- | --- |
| `latest` | 默认分支最新构建 |
| `sha-abc1234` | 某次提交对应的镜像，回滚用 |

---

## 5. 包可见性（决定 NAS 要不要登录）

GHCR 包**默认私有**，两种做法：

### 方案 A：保持私有（推荐，镜像内容不外泄）

NAS 上先登录，再 pull：

```bash
# 在 GitHub 创建 PAT：
# Settings → Developer settings → Personal access tokens → Tokens (classic)
# 勾选 read:packages（如需写也勾 write:packages）
echo "<你的PAT>" | docker login ghcr.io -u holicli --password-stdin
docker pull ghcr.io/holicli/javspy:latest
```

### 方案 B：改成公开（NAS 无需登录）

GitHub → 个人主页 → Packages → `javspy` → **Package settings** →
滚到底部 **Danger Zone** → `Change visibility` → `Public`。

之后任何机器都能直接 `docker pull ghcr.io/holicli/javspy:latest`，
无需 login（公开包免费，私有包占存储配额，一般也够用）。

---

## 6. NAS 上使用

```bash
# 私有包先登录（公开包跳过）
echo "<PAT>" | docker login ghcr.io -u holicli --password-stdin

# 拉取并运行（在 docker-compose.yml 所在目录）
docker compose pull
docker compose up -d

# 查看
docker compose ps
docker compose logs -f javspy
```

日常更新：`docker compose pull && docker compose up -d`。

回滚：把 `.env` 里的 `JAVSPY_TAG` 改成要回滚的 `sha-xxxxxxx`：

```bash
docker compose pull && docker compose up -d
```

（`latest` 会被覆盖，`sha-` 标签是固定的，适合定位到具体提交。）

---

## 7. 排错清单

| 现象 | 原因 / 解决 |
| --- | --- |
| Actions 页面没有运行记录 | 分支名不是 `master`；或 workflow 文件没提交到默认分支；或仓库 Actions 被关闭（Settings → Actions） |
| `denied: permission_denied` / `403 Forbidden` 推送失败 | workflow 缺少 `permissions: packages: write`；或 YAML 里 permissions 位置写错 |
| `invalid reference format` | 镜像地址含大写字母，必须是全小写 |
| `docker login` 成功但 pull 401 | PAT 没有 `read:packages` 权限；或包私有但没登录；token 过期 |
| 改了包可见性仍拉不下来 | NAS 上重新 `docker login`；或先 `docker logout ghcr.io` 再登录 |
| `npm ci` 报 `Cannot find module @rollup/rollup-linux-x64-*` | lockfile 缺少 Linux 平台原生依赖（Windows 生成 lockfile 的常见问题）。用 `npm install` 重新生成 lockfile 后提交，或 Dockerfile 里改用 `npm install` |
| Maven 依赖下载超时 | 重试即可；或给 Maven 配镜像仓库（阿里云） |
| 构建特别慢 | 首次正常；后续靠 `cache-from/cache-to: type=gha`；可适当精简前端依赖 |
| 推送后 NAS 拉到的还是旧镜像 | compose 默认不会自动更新，需 `docker compose pull`；或配置 Watchtower/cron |
| workflow 显示 "This workflow has no runs yet" | 该文件刚加还没触发过；push 一次或手动 Run workflow |

---

## 8. 可选进阶

- **PR 只构建不推送**：再加一个 `pull_request` 触发的 job，把 `push: false`，用于验证 PR 能编译通过。
- **多架构**：NAS 是 ARM 时把 `platforms: linux/amd64` 改成 `linux/amd64,linux/arm64`（构建时间翻倍）。
- **版本标签**：`type=semver,pattern={{version}}`（配 git tag `v*` 触发）。
- **自动清理旧镜像**：GitHub 不自动删包，可在 Package settings 里配置保留策略，或加
  `actions/delete-package-versions`。
- **推送国内仓库**：如果 NAS 拉 GHCR 慢，可再加一步推送到阿里云 ACR 等国内仓库并让 NAS 从那里拉。

---

## 参考

- GitHub Actions 文档：<https://docs.github.com/actions>
- GHCR 文档：<https://docs.github.com/packages/working-with-a-github-packages-registry/working-with-the-container-registry>
- docker/build-push-action：<https://github.com/docker/build-push-action>
- docker/metadata-action：<https://github.com/docker/metadata-action>
