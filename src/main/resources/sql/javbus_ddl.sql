-- ============================================================
-- javbus 刮削功能建表脚本（手动执行）
-- 数据库：与项目 datasource 同一个库（默认 avbook）
-- 执行方式：mysql -uroot -p avbook < javbus_ddl.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS javbus_movie (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code         VARCHAR(64)     NOT NULL                COMMENT '番号，如 SSIS-123',
    title        VARCHAR(512)    DEFAULT NULL            COMMENT '标题',
    cover_url    VARCHAR(1024)   DEFAULT NULL            COMMENT '封面图片地址',
    release_date VARCHAR(32)     DEFAULT NULL            COMMENT '发售日期 yyyy-MM-dd',
    duration     INT             DEFAULT NULL            COMMENT '时长（分钟）',
    director     VARCHAR(128)    DEFAULT NULL            COMMENT '导演',
    studio       VARCHAR(256)    DEFAULT NULL            COMMENT '制作商',
    publisher    VARCHAR(256)    DEFAULT NULL            COMMENT '发行商',
    series       VARCHAR(256)    DEFAULT NULL            COMMENT '系列',
    actors       TEXT            DEFAULT NULL            COMMENT '演员，逗号分隔',
    genres       TEXT            DEFAULT NULL            COMMENT '类型/标签，逗号分隔',
    detail_url   VARCHAR(1024)   DEFAULT NULL            COMMENT 'javbus 详情页地址',
    raw_html     MEDIUMTEXT      DEFAULT NULL            COMMENT '详情页原始 HTML（排查用）',
    created_at   DATETIME        DEFAULT NULL            COMMENT '入库时间',
    updated_at   DATETIME        DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_javbus_movie_code (code),
    KEY idx_javbus_movie_release_date (release_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 刮削影片信息';

CREATE TABLE IF NOT EXISTS javbus_magnet (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code       VARCHAR(64)     NOT NULL                COMMENT '所属影片番号',
    detail_id  VARCHAR(128)    DEFAULT NULL            COMMENT 'javbus 详情页 id',
    magnet     TEXT            NOT NULL                COMMENT 'magnet:?xt=... 完整链接',
    name       VARCHAR(1024)   DEFAULT NULL            COMMENT '磁力资源名称',
    size_text  VARCHAR(128)    DEFAULT NULL            COMMENT '文件大小（原始字符串）',
    size_bytes BIGINT          DEFAULT NULL            COMMENT '文件大小字节数',
    share_date VARCHAR(32)     DEFAULT NULL            COMMENT '分享日期',
    hd         TINYINT         DEFAULT 0               COMMENT '是否高清 1/0',
    subtitle   TINYINT         DEFAULT 0               COMMENT '是否有中文字幕 1/0',
    created_at DATETIME        DEFAULT NULL            COMMENT '入库时间',
    PRIMARY KEY (id),
    KEY idx_javbus_magnet_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 磁力链接';
