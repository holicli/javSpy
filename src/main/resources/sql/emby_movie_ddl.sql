-- Emby 影片缓存表：存 Emby 中全部影片番号，每日全量覆盖更新
CREATE TABLE IF NOT EXISTS emby_movie (
    code       VARCHAR(64) NOT NULL                COMMENT 'Emby 中影片番号（统一大写）',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次同步时间',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近同步时间',
    PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Emby 影片番号缓存表';
