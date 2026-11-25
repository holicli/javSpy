# JavSpy

使用 Java 11 构建后端，前端构建产物由 Spring Boot 提供。修改前端后在 `frontend` 运行
`npm run build`，然后在项目根目录运行 `mvn test` 或 `mvn package`。

## 部署配置

请在启动 Spring Boot 的环境中设置以下变量，也可以用部署平台的秘密存储注入。不要把真实值写入 Git。

| 环境变量 | 用途 |
| --- | --- |
| `JAVSPY_DB_URL` | MariaDB JDBC 连接地址 |
| `JAVSPY_DB_USERNAME` / `JAVSPY_DB_PASSWORD` | 数据库账号和密码 |
| `JAVSPY_EMBY_ENABLED` | 设为 `true` 启用 Emby；默认关闭 |
| `JAVSPY_EMBY_URL` / `JAVSPY_EMBY_API_KEY` | Emby 地址和凭据 |
| `JAVSPY_EMBY_DELETE_ADMIN_TOKEN` | 独立的随机管理员令牌；不设置时只能查看 |
| `JAVSPY_JAVBUS_TOKEN` | 自托管 JavBus API 要求鉴权时使用 |

如果曾经提交过有效的数据库密码或 Emby 密钥，请先轮换，再用新凭据部署。

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

如果已在 Windows 的 IDEA `JavspyApplication` 运行配置里填过变量，可以直接导出，不用重新输入：

```powershell
.\scripts\export-idea-env.ps1
```

脚本只读取该运行配置中的 `JAVSPY_*` 设置，写入 Git 忽略的 `target/javspy.env`，默认不导出
Emby 永久删除令牌。通过 SSH 等安全方式把文件传到 Linux 后，收紧上传文件权限并安装为服务配置：

```bash
chmod 600 "$HOME/javspy.env"
sudo install -D -m 600 "$HOME/javspy.env" /etc/javspy/javspy.env
sudo systemctl restart javspy
```

`install` 会替换已有配置，运行前请确认。数据库和 Emby 地址仍须能从 Linux 服务器访问。

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

使用 HTTPS 访问 JavSpy。执行永久删除时输入独立的管理员令牌；页面不会保存它。
只有名称和文件名番号明确一致的候选支持批量勾选。标为「需人工核对」的组只能逐条选，
标为「仅查看」的组不可删除。后端会在删除前重新扫描，且每组至少保留一个条目。
同一进程会串行处理删除；若部署多个后端实例，只在其中一个实例开启永久删除。
第一次验证请使用测试媒体库：Emby 的删除会移除磁盘文件，无法通过 JavSpy 撤销。
