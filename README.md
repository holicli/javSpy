# JavSpy

使用 Java 11 构建后端，前端构建产物由 Spring Boot 提供。修改前端后在 `frontend` 运行
`npm run build`，然后在项目根目录运行 `mvn test` 或 `mvn package`。

## 部署配置

所有连接信息都通过环境变量注入，不要把真实值写入 Git。完整模板见 `.env.example`。

| 环境变量 | 用途 |
| --- | --- |
| `JAVSPY_TAG` | Docker 镜像标签，`latest` 或 `sha-xxxxxx`（回滚用） |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | MariaDB 地址、端口、数据库名 |
| `DB_USER` / `DB_PASSWORD` | 数据库账号和密码（建议使用专用账号，不要直接用 root） |
| `DB_POOL_SIZE` | Hikari 连接池上限，默认 20 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 |
| `EMBY_ENABLED` | 设为 `true` 启用 Emby；默认 `true` |
| `EMBY_URL` / `EMBY_API_KEY` | Emby 地址和 API Key |
| `EMBY_DELETE_ADMIN_TOKEN` | Emby 去重页“永久删除”的管理员令牌；留空时只能查看 |
| `JAVBUS_API_URL` / `JAVBUS_API_TOKEN` | 自托管 JavBus API 地址与鉴权 |
| `JAVBUS_API_PROXY_ENABLED` / `JAVBUS_API_PROXY_HOST` / `JAVBUS_API_PROXY_PORT` | 下载封面时的代理 |
| `JAVSPY_QBITTORRENT_URL` / `JAVSPY_QBITTORRENT_USERNAME` / `JAVSPY_QBITTORRENT_PASSWORD` | 磁力弹窗「下载」使用的 qBittorrent WebUI 连接 |
| `IMAGE_STORAGE_PATH` / `PIC_DIR` | 容器内图片目录 / 宿主机挂载目录 |
| `MYBATIS_LOG_IMPL` | SQL 日志实现；生产建议 `org.apache.ibatis.logging.nologging.NoLoggingImpl` |
| `JAVA_TOOL_OPTIONS` | JVM 参数 |

> `.env` 只会被 `docker compose` 自动读取。直接 `java -jar` 启动时不会加载 `.env`，
> 必须在 shell 里 `export`，或通过 systemd 的 `EnvironmentFile` 注入。
> 否则 `DB_PASSWORD` 为空，会出现
> `Access denied for user 'root'@'...' (using password: NO)`。

### Docker Compose

```bash
cp .env.example .env
vi .env   # 填 DB_PASSWORD / EMBY_API_KEY 等
docker compose up -d --force-recreate
docker compose logs -f javspy
```

修改 `.env` 后只执行 `docker restart` 不会重新注入环境变量，必须
`docker compose up -d --force-recreate`。

> `.env` 中的密码若包含 `#`、`$`、空格等字符，请用单引号包起来，例如
> `DB_PASSWORD='p@ss#word'`，否则可能被 shell/compose 截断。

### 直接运行 jar

```bash
export DB_HOST=192.168.0.108
export DB_PORT=33306
export DB_NAME=javspy
export DB_USER=javspy
export DB_PASSWORD='你的数据库密码'
export EMBY_API_KEY='你的 Emby Key'

java -jar target/javspy-0.0.1-SNAPSHOT.jar
```

长期运行建议用 systemd，把变量写入 `/etc/javspy.env` 并配置 `EnvironmentFile=`。

如果曾经提交过有效的数据库密码或 Emby 密钥，请先轮换，再用新凭据部署。

### qBittorrent 下载

在磁力弹窗点击「下载」后，JavSpy 会按影片番号匹配 qBittorrent 的任务名称和标签。已有同番号的任务
时提示「已存在」。新任务带番号标签，保存位置使用 qBittorrent 的默认设置。
Docker Compose 部署时，在 NAS 上的 `.env` 配置上表的三个 qBittorrent 环境变量。
本地运行也可以在启动目录放置 `application-qbittorrent-local.yml`：

```yaml
javspy:
    qbittorrent:
        url: http://192.168.0.108:8085
        username: admin
        password: "在此填入 WebUI 密码"
```

### Linux：生成并加载环境变量文件

从项目根目录运行脚本，用实际的数据库主机、库名和应用账号填写。密码和令牌输入时不回显，
配置文件权限为 `600`。脚本只写配置，不连接数据库或启动服务。

```bash
sudo bash scripts/configure-env.sh
```

默认写到 `/etc/javspy/javspy.env`。如果已有 `javspy.service`，用 `sudo systemctl edit javspy`
添加以下配置，与现有的启动命令配合使用：

```ini
[Service]
EnvironmentFile=/etc/javspy/javspy.env
```

然后执行 `sudo systemctl daemon-reload` 和 `sudo systemctl restart javspy`。

如果不用 systemd，可以把文件写到当前用户的配置目录，再由 Bash 加载并启动已构建的 JAR：

```bash
bash scripts/configure-env.sh "$HOME/.config/javspy/javspy.env"
(
    set -a
    source "$HOME/.config/javspy/javspy.env"
    set +a
    exec java -jar target/javspy-0.0.1-SNAPSHOT.jar
)
```

## Emby 去重

使用 HTTPS 访问 JavSpy。执行永久删除时输入 `EMBY_DELETE_ADMIN_TOKEN` 中配置的令牌；
页面不会保存它。只有名称和文件名番号明确一致的候选支持批量勾选。标为「需人工核对」的组
只能逐条选，标为「仅查看」的组不可删除。后端会在删除前重新扫描，且每组至少保留一个条目。
同一进程会串行处理删除；若部署多个后端实例，只在其中一个实例开启永久删除。
第一次验证请使用测试媒体库：Emby 的删除会移除磁盘文件，无法通过 JavSpy 撤销。
