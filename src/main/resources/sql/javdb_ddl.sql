-- ============================================================
-- javdb 刮削功能建表脚本（手动执行）
-- 数据库：与项目 datasource 同一个库（默认 avbook）
-- 执行方式：mariadb -uroot -p avbook < javdb_ddl.sql
-- 老库升级：先执行 javdb_cover_local_alter.sql 补充新字段，再启动新版应用
-- 说明：这两张表是新增的独立表，不影响已有业务表。
-- ============================================================

-- 影片主表
CREATE TABLE IF NOT EXISTS javdb_movie (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code         VARCHAR(64)     NOT NULL                COMMENT '番号，如 SSIS-123',
    title        VARCHAR(512)    DEFAULT NULL            COMMENT '标题',
    cover_url    VARCHAR(1024)   DEFAULT NULL            COMMENT '封面图片地址',
    cover_local  VARCHAR(1024)   DEFAULT NULL            COMMENT '本地封面图片地址',
    release_date VARCHAR(32)     DEFAULT NULL            COMMENT '发售日期 yyyy-MM-dd',
    duration     INT             DEFAULT NULL            COMMENT '时长（分钟）',
    director     VARCHAR(128)    DEFAULT NULL            COMMENT '导演',
    studio       VARCHAR(256)    DEFAULT NULL            COMMENT '制作商',
    publisher    VARCHAR(256)    DEFAULT NULL            COMMENT '发行商',
    series       VARCHAR(256)    DEFAULT NULL            COMMENT '系列',
    actors       TEXT            DEFAULT NULL            COMMENT '演员，逗号分隔',
    genres       TEXT            DEFAULT NULL            COMMENT '类型/标签，逗号分隔',
    description  TEXT            DEFAULT NULL            COMMENT '简介',
    detail_url   VARCHAR(1024)   DEFAULT NULL            COMMENT 'javdb 详情页地址',
    raw_html     MEDIUMTEXT      DEFAULT NULL            COMMENT '详情页原始 HTML（排查用）',
    created_at   DATETIME        DEFAULT NULL            COMMENT '入库时间',
    updated_at   DATETIME        DEFAULT NULL            COMMENT '更新时间',
    cover_local  VARCHAR(1024)   DEFAULT NULL            COMMENT '本地封面图片地址' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_javdb_movie_code (code),
    KEY idx_javdb_movie_release_date (release_date),
    KEY idx_javdb_movie_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javdb 刮削影片信息';

-- 磁力链接表
CREATE TABLE IF NOT EXISTS javdb_magnet (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code       VARCHAR(64)     NOT NULL                COMMENT '所属影片番号',
    detail_id  VARCHAR(128)    DEFAULT NULL            COMMENT 'javdb 详情页 id',
    magnet     TEXT            NOT NULL                COMMENT 'magnet:?xt=... 完整链接',
    name       VARCHAR(1024)   DEFAULT NULL            COMMENT '磁力资源名称',
    size_text  VARCHAR(128)    DEFAULT NULL            COMMENT '文件大小（原始字符串）',
    size_bytes BIGINT          DEFAULT NULL            COMMENT '文件大小字节数',
    share_date VARCHAR(32)     DEFAULT NULL            COMMENT '分享日期',
    hd         TINYINT         DEFAULT 0               COMMENT '是否高清 1/0',
    subtitle   TINYINT         DEFAULT 0               COMMENT '是否有中文字幕 1/0',
    created_at DATETIME        DEFAULT NULL            COMMENT '入库时间',
    PRIMARY KEY (id),
    KEY idx_javdb_magnet_code (code),
    KEY idx_javdb_magnet_detail_id (detail_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javdb 磁力链接';

-- 关注演员表
CREATE TABLE IF NOT EXISTS javdb_follow_actor (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    actor_name VARCHAR(128)    NOT NULL                COMMENT '演员名称',
    remark     VARCHAR(256)    DEFAULT NULL            COMMENT '备注',
    created_at DATETIME        DEFAULT NULL            COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_javdb_follow_actor_name (actor_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javdb 关注演员表';

-- 选中影片磁链导出表（保存页面按钮写入的磁链，无磁链时记录番号）
CREATE TABLE IF NOT EXISTS javdb_magnet_export (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code       VARCHAR(64)     NOT NULL                COMMENT '影片番号',
    magnet     TEXT            DEFAULT NULL            COMMENT '选中的磁力链接，无磁链时为 NULL',
    name       VARCHAR(1024)   DEFAULT NULL            COMMENT '磁力资源名称',
    size_text  VARCHAR(128)    DEFAULT NULL            COMMENT '文件大小文本',
    share_date VARCHAR(32)     DEFAULT NULL            COMMENT '磁力分享日期',
    status     VARCHAR(32)     NOT NULL DEFAULT 'OK'   COMMENT 'OK=已保存磁链，NO_MAGNET=无磁链',
    created_at DATETIME        DEFAULT NULL            COMMENT '导出时间',
    PRIMARY KEY (id),
    KEY idx_javdb_magnet_export_code (code),
    KEY idx_javdb_magnet_export_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javdb 选中影片磁链导出表';
