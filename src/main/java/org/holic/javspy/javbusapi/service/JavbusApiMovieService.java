package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.holic.javspy.javbusapi.mapper.JavbusApiMovieMapper;
import org.holic.javspy.javbusapi.model.JavbusApiMovie;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * javbus_movie 表服务。
 */
@Service
public class JavbusApiMovieService extends ServiceImpl<JavbusApiMovieMapper, JavbusApiMovie> {

    /** 分页查询已入库的影片。 */
    public PageInfo<JavbusApiMovie> searchMovies(String code, String keyword,
                                                 String releaseDate, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        PageHelper.startPage(safePage, safeSize);
        List<JavbusApiMovie> list = baseMapper.searchMovies(code, keyword, releaseDate);
        return new PageInfo<>(list);
    }

    /** 回写本地封面地址。 */
    public boolean updateCoverLocal(String code, String coverLocal) {
        return baseMapper.updateCoverLocal(code, coverLocal) > 0;
    }
}
