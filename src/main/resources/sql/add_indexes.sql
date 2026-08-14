-- ============================================================
-- 为已有数据库补充性能索引（可重复执行）
-- 执行方式：mariadb -uroot -p avbook < add_indexes.sql
-- ============================================================
SET @db = DATABASE();

-- javbus_movie.created_at：最新入库页按入库时间倒序
SET @idx1 = IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @db AND table_name = 'javbus_movie')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @db AND table_name = 'javbus_movie'
                     AND index_name = 'idx_javbus_movie_created_at'),
    'ALTER TABLE javbus_movie ADD INDEX idx_javbus_movie_created_at (created_at)',
    'SELECT 1');
PREPARE stmt1 FROM @idx1;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

-- javbus_magnet(code, share_date, id)：按番号查磁力并按分享日期倒序
SET @idx2 = IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @db AND table_name = 'javbus_magnet')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @db AND table_name = 'javbus_magnet'
                     AND index_name = 'idx_javbus_magnet_code_share'),
    'ALTER TABLE javbus_magnet ADD INDEX idx_javbus_magnet_code_share (code, share_date, id)',
    'SELECT 1');
PREPARE stmt2 FROM @idx2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
