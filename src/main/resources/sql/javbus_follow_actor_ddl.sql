-- 关注演员表
CREATE TABLE IF NOT EXISTS javbus_follow_actor (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    actor_name VARCHAR(128)    NOT NULL                COMMENT '演员名称',
    remark     VARCHAR(256)    DEFAULT NULL            COMMENT '备注',
    created_at DATETIME        DEFAULT NULL            COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_javbus_follow_actor_name (actor_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'javbus 关注演员表';
