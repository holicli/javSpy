-- ============================================================
-- 为已有数据库补充性能索引（可重复执行）
-- 执行方式：mariadb -uroot -p avbook < add_indexes.sql
-- ============================================================
SET @db = DATABASE();

-- javbus_movie.created_at：备用（当前 /newest 实际按 release_date 排序，见下方 idx3）
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

-- javbus_movie(release_date, code)：/newest 实际排序为 ORDER BY release_date DESC, code
-- （searchNewest 的排序字段；countNewest 为全表 COUNT，无需索引）
SET @idx3 = IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = @db AND table_name = 'javbus_movie')
    AND NOT EXISTS(SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = @db AND table_name = 'javbus_movie'
                     AND index_name = 'idx_javbus_movie_release_code'),
    'ALTER TABLE javbus_movie ADD INDEX idx_javbus_movie_release_code (release_date, code)',
    'SELECT 1');
PREPARE stmt3 FROM @idx3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- ------------------------------------------------------------
-- 可选（手动，先确认数据库支持 ngram 解析器）：
-- 关键词搜索目前是 title LIKE '%关键词%'，无法走普通索引。
-- 若影片表很大且关键词搜索慢，可改用全文索引 + MATCH...AGAINST：
--   MySQL 5.7+/8.0、MariaDB 10.0.5+：
--   ALTER TABLE javbus_movie ADD FULLTEXT INDEX ft_javbus_movie_title (title) WITH PARSER ngram;
-- 注意：FULLTEXT 是“分词匹配”，与 LIKE 的“子串匹配”语义不同，
-- 且代码里的 SQL 需要同步改成 MATCH(title) AGAINST(#{keyword} IN BOOLEAN MODE)
-- 才能生效，因此这里默认不自动创建，避免只加索引不生效（反而增加写入开销）。
-- ------------------------------------------------------------
