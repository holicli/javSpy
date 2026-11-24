#!/usr/bin/env bash
# Write a private environment file for systemd or a Bash launcher.
set +x
set -euo pipefail
umask 077

die() {
    printf '错误：%s\n' "$1" >&2
    exit 1
}

ask() {
    local field=$1 label=$2
    printf '%s：' "$label" >&2
    if [[ ${3:-} == secret ]]; then
        IFS= read -rs "$field" || die '输入中断，配置文件未修改。'
        printf '\n' >&2
    else
        IFS= read -r "$field" || die '输入中断，配置文件未修改。'
    fi
}

write_var() {
    local key=$1 value=$2
    [[ $value != *$'\r'* && $value != *$'\n'* ]] || die "$key 必须是单行值。"

    # Within double quotes, Bash and systemd EnvironmentFile recognize the same escapes here.
    value=${value//\\/\\\\}
    value=${value//\"/\\\"}
    value=${value//\$/\\\$}
    value=${value//\`/\\\`}
    printf '%s="%s"\n' "$key" "$value"
}

if [[ ${1:-} == -h || ${1:-} == --help ]]; then
    printf '用法：bash %s [配置文件的绝对路径]\n默认：/etc/javspy/javspy.env\n' "$0"
    exit 0
fi
[[ $# -le 1 ]] || die "用法：bash $0 [配置文件的绝对路径]"

env_file=${1-/etc/javspy/javspy.env}
[[ $env_file == /* && $env_file != */ ]] || die '请指定配置文件的绝对路径。'
replace=false
if [[ -e $env_file || -L $env_file ]]; then
    [[ -f $env_file && ! -L $env_file ]] || die '目标不是普通文件，拒绝写入。'
    printf '配置文件已存在：%s\n' "$env_file" >&2
    ask answer '是否重新填写并替换整个文件？留空取消 [y/N]'
    case $answer in
        y|Y|yes|YES) replace=true ;;
        *) printf '已取消，原文件未修改。\n'; exit 0 ;;
    esac
fi

ask db_url 'MariaDB JDBC 地址（jdbc:mariadb://主机:端口/库名）'
[[ $db_url =~ ^jdbc:mariadb(:[a-z-]+)?://[^[:space:]]+$ ]] || die '请输入有效格式的 MariaDB JDBC 地址。'
ask db_user '数据库账号'
[[ $db_user =~ [^[:space:]] ]] || die '数据库账号不能为空。'
ask db_password '数据库密码（不回显）' secret

emby_enabled=false
emby_url=''
emby_key=''
emby_delete_token=''
ask answer '是否启用 Emby [y/N]'
case $answer in
    y|Y|yes|YES)
        emby_enabled=true
        ask emby_url 'Emby 地址（http:// 或 https://）'
        [[ $emby_url =~ ^https?://[^[:space:]]+$ ]] || die '请输入有效格式的 Emby 地址。'
        ask emby_key 'Emby API 密钥（不回显）' secret
        [[ $emby_key =~ [^[:space:]] ]] || die '启用 Emby 时必须填写 API 密钥。'
        ask emby_delete_token 'Emby 删除管理员令牌（不回显，建议留空禁用永久删除）' secret
        ;;
esac
ask javbus_token 'JavBus API 令牌（不回显，不需要就留空）' secret

env_dir=$(dirname -- "$env_file")
mkdir -p -- "$env_dir"
tmp_file=$(mktemp -- "$env_dir/.javspy-env.XXXXXXXX")
trap 'rm -f -- "$tmp_file"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
chmod 600 -- "$tmp_file"
[[ $(stat -c '%a' -- "$tmp_file") == 600 ]] || die '无法保证配置文件权限为 600，请选择支持 Linux 文件权限的目录。'

{
    write_var JAVSPY_DB_URL "$db_url"
    write_var JAVSPY_DB_USERNAME "$db_user"
    write_var JAVSPY_DB_PASSWORD "$db_password"
    write_var JAVSPY_EMBY_ENABLED "$emby_enabled"
    write_var JAVSPY_EMBY_URL "$emby_url"
    write_var JAVSPY_EMBY_API_KEY "$emby_key"
    write_var JAVSPY_EMBY_DELETE_ADMIN_TOKEN "$emby_delete_token"
    write_var JAVSPY_JAVBUS_TOKEN "$javbus_token"
} > "$tmp_file"

if [[ $replace == true ]]; then
    mv -fT -- "$tmp_file" "$env_file"
else
    mv -nT -- "$tmp_file" "$env_file" || die '无法创建配置文件。'
    [[ ! -e $tmp_file ]] || die '目标文件已存在，未覆盖。'
fi
trap - EXIT INT TERM
printf '配置已写入 %s（权限 600）。\n' "$env_file"
