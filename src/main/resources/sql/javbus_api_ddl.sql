-- ============================================================
-- javbus API 刮削功能数据库设计（规范化）
-- 依据 https://github.com/ovnrain/javbus-api README 的 JSON 结构
-- 数据库：与项目 datasource 同一个库（默认 avbook）
-- 执行方式：mariadb -uroot -p avbook < javbus_api_ddl.sql
-- ============================================================

-- 1. 影片表
CREATE TABLE IF NOT EXISTS javbus_movie (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code         VARCHAR(64)     NOT NULL                COMMENT '番号，如 SSIS-406',
    title        VARCHAR(1024)   DEFAULT NULL            COMMENT '标题',
    cover_url    VARCHAR(1024)   DEFAULT NULL            COMMENT '封面图（列表缩略图）',
    cover_hd     VARCHAR(1024)   DEFAULT NULL            COMMENT '高清封面图（详情大图）',
    cover_local  VARCHAR(1024)   DEFAULT NULL            COMMENT '本地封面图地址',
    cover_width  INT             DEFAULT NULL            COMMENT '封面大图宽度',
    cover_height INT             DEFAULT NULL            COMMENT '封面大图高度',
    release_date VARCHAR(32)     DEFAULT NULL            COMMENT '发售日期 yyyy-MM-dd',
    duration     INT             DEFAULT NULL            COMMENT '时长（分钟）',
    director_id  VARCHAR(64)     DEFAULT NULL            COMMENT '导演 ID（javbus_director.id）',
    studio_id    VARCHAR(64)     DEFAULT NULL            COMMENT '制作商 ID（javbus_studio.id）',
    publisher_id VARCHAR(64)     DEFAULT NULL            COMMENT '发行商 ID（javbus_publisher.id）',
    series_id    VARCHAR(64)     DEFAULT NULL            COMMENT '系列 ID（javbus_series.id）',
    detail_url   VARCHAR(1024)   DEFAULT NULL            COMMENT 'javbus 详情页地址',
    gid          VARCHAR(64)     DEFAULT NULL            COMMENT 'gid（磁力接口参数）',
    uc           VARCHAR(16)     DEFAULT NULL            COMMENT 'uc（磁力接口参数）',
    raw_json     MEDIUMTEXT      DEFAULT NULL            COMMENT '详情页原始 JSON（排查用）',
    created_at   DATETIME        DEFAULT NULL            COMMENT '入库时间',
    updated_at   DATETIME        DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_javbus_movie_code (code),
    KEY idx_javbus_movie_release_date (release_date),
    KEY idx_javbus_movie_director (director_id),
    KEY idx_javbus_movie_studio (studio_id),
    KEY idx_javbus_movie_publisher (publisher_id),
    KEY idx_javbus_movie_series (series_id),
    KEY idx_javbus_movie_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 影片信息';

-- 若表已存在，执行以下语句补字段：
-- ALTER TABLE javbus_movie ADD COLUMN cover_local VARCHAR(1024) DEFAULT NULL COMMENT '本地封面图地址' AFTER cover_hd;

-- 2. 磁力链接表
CREATE TABLE IF NOT EXISTS javbus_magnet (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    magnet_id   VARCHAR(128)    DEFAULT NULL            COMMENT '磁力 ID（API 返回的 id）',
    movie_id    BIGINT UNSIGNED DEFAULT NULL            COMMENT '影片主键（javbus_movie.id）',
    code        VARCHAR(64)     NOT NULL                COMMENT '所属影片番号',
    link        TEXT            NOT NULL                COMMENT 'magnet:?xt=... 完整链接',
    title       VARCHAR(1024)   DEFAULT NULL            COMMENT '磁力资源名称',
    is_hd       TINYINT         DEFAULT 0               COMMENT '是否高清 1/0',
    size_text   VARCHAR(64)     DEFAULT NULL            COMMENT '文件大小文本（如 6.57GB）',
    size_bytes  BIGINT          DEFAULT NULL            COMMENT '文件大小字节数',
    share_date  VARCHAR(32)     DEFAULT NULL            COMMENT '分享日期 yyyy-MM-dd',
    has_subtitle TINYINT        DEFAULT 0               COMMENT '是否包含字幕 1/0',
    created_at  DATETIME        DEFAULT NULL            COMMENT '入库时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_javbus_magnet_link (link(255)),
    KEY idx_javbus_magnet_movie (movie_id),
    KEY idx_javbus_magnet_code (code),
    KEY idx_javbus_magnet_code_share (code, share_date, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 磁力链接';

-- 3. 演员表
CREATE TABLE IF NOT EXISTS javbus_star (
    id         VARCHAR(64)     NOT NULL                COMMENT '演员 ID（API 返回的 id）',
    name       VARCHAR(128)    NOT NULL                COMMENT '演员名称',
    avatar     VARCHAR(1024)   DEFAULT NULL            COMMENT '头像地址',
    birthday   VARCHAR(32)     DEFAULT NULL            COMMENT '生日 yyyy-MM-dd',
    age        VARCHAR(16)     DEFAULT NULL            COMMENT '年龄',
    height     VARCHAR(32)     DEFAULT NULL            COMMENT '身高',
    bust       VARCHAR(32)     DEFAULT NULL            COMMENT '胸围',
    waistline  VARCHAR(32)     DEFAULT NULL            COMMENT '腰围',
    hipline    VARCHAR(32)     DEFAULT NULL            COMMENT '臀围',
    birthplace VARCHAR(128)    DEFAULT NULL            COMMENT '出生地',
    hobby      VARCHAR(512)    DEFAULT NULL            COMMENT '爱好',
    created_at DATETIME        DEFAULT NULL            COMMENT '创建时间',
    updated_at DATETIME        DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_javbus_star_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 演员';

-- 4. 导演表
CREATE TABLE IF NOT EXISTS javbus_director (
    id         VARCHAR(64)  NOT NULL COMMENT '导演 ID',
    name       VARCHAR(128) NOT NULL COMMENT '导演名称',
    created_at DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 导演';

-- 5. 制作商表
CREATE TABLE IF NOT EXISTS javbus_studio (
    id         VARCHAR(64)  NOT NULL COMMENT '制作商 ID',
    name       VARCHAR(256) NOT NULL COMMENT '制作商名称',
    created_at DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 制作商';

-- 6. 发行商表
CREATE TABLE IF NOT EXISTS javbus_publisher (
    id         VARCHAR(64)  NOT NULL COMMENT '发行商 ID',
    name       VARCHAR(256) NOT NULL COMMENT '发行商名称',
    created_at DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 发行商';

-- 7. 系列表
CREATE TABLE IF NOT EXISTS javbus_series (
    id         VARCHAR(64)  NOT NULL COMMENT '系列 ID',
    name       VARCHAR(256) NOT NULL COMMENT '系列名称',
    created_at DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 系列';

-- 8. 类别表
CREATE TABLE IF NOT EXISTS javbus_genre (
    id         VARCHAR(64)  NOT NULL COMMENT '类别 ID',
    name       VARCHAR(128) NOT NULL COMMENT '类别名称',
    created_at DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 类别';

-- 9. 影片-演员关联表（多对多）
CREATE TABLE IF NOT EXISTS javbus_movie_star (
    movie_id BIGINT UNSIGNED NOT NULL COMMENT '影片主键',
    star_id  VARCHAR(64)     NOT NULL COMMENT '演员 ID',
    PRIMARY KEY (movie_id, star_id),
    KEY idx_javbus_movie_star_star (star_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 影片-演员关联';

-- 10. 影片-类别关联表（多对多）
CREATE TABLE IF NOT EXISTS javbus_movie_genre (
    movie_id BIGINT UNSIGNED NOT NULL COMMENT '影片主键',
    genre_id VARCHAR(64)     NOT NULL COMMENT '类别 ID',
    PRIMARY KEY (movie_id, genre_id),
    KEY idx_javbus_movie_genre_genre (genre_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 影片-类别关联';

-- 11. 影片预览图表
CREATE TABLE IF NOT EXISTS javbus_movie_sample (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    movie_id   BIGINT UNSIGNED NOT NULL COMMENT '影片主键',
    sample_id  VARCHAR(128)    DEFAULT NULL COMMENT '预览图 ID（API 返回的 id）',
    alt        VARCHAR(1024)   DEFAULT NULL COMMENT '预览图 alt',
    src        VARCHAR(1024)   DEFAULT NULL COMMENT '预览图大图地址',
    thumbnail  VARCHAR(1024)   DEFAULT NULL COMMENT '预览图缩略图地址',
    created_at DATETIME        DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_javbus_movie_sample_movie (movie_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 影片预览图';

-- 12. 影片-相似影片关联表
CREATE TABLE IF NOT EXISTS javbus_movie_similar (
    movie_id      BIGINT UNSIGNED NOT NULL COMMENT '影片主键',
    similar_code  VARCHAR(64)     NOT NULL COMMENT '相似影片番号',
    similar_title VARCHAR(1024)   DEFAULT NULL COMMENT '相似影片标题',
    similar_img   VARCHAR(1024)   DEFAULT NULL COMMENT '相似影片封面',
    created_at    DATETIME        DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (movie_id, similar_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 影片-相似影片关联';

-- 13. 磁力保存表（单独保存用户选中的磁力链接）
CREATE TABLE IF NOT EXISTS javbus_magnet_save (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code       VARCHAR(64)     NOT NULL                COMMENT '影片番号',
    magnet     TEXT            NOT NULL                COMMENT '磁力链接',
    saved_date DATE            DEFAULT NULL            COMMENT '插入日期 yyyy-MM-dd',
    PRIMARY KEY (id),
    KEY idx_javbus_magnet_save_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 磁力保存表';
