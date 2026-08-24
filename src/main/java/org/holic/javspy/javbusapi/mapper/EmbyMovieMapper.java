package org.holic.javspy.javbusapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.EmbyMovie;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * emby_movie 表 mapper（Emby 影片番号缓存）。
 */
@Repository
public interface EmbyMovieMapper extends BaseMapper<EmbyMovie> {

    /** 查询全部缓存番号。 */
    List<String> selectAllCodes();

    /** 最近一次同步时间（MAX(updated_at)），无数据返回 null。 */
    Date selectLastSyncAt();

    /** 缓存数量。 */
    long countAll();

    /** 清空全部缓存（覆盖更新前调用）。 */
    int deleteAll();

    /** 批量插入缓存番号。 */
    int insertBatch(@Param("codes") List<String> codes);
}
