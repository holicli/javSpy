package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.github.pagehelper.PageInfo;
import org.holic.javspy.javbusapi.model.JavbusApiMovie;

/**
 * javbus_movie 表服务。
 */
public interface JavbusApiMovieService extends IService<JavbusApiMovie> {

    /** 分页查询已入库的影片。 */
    PageInfo<JavbusApiMovie> searchMovies(String code, String keyword,
                                          String releaseDate, int pageNum, int pageSize);

    /** 回写本地封面地址。 */
    boolean updateCoverLocal(String code, String coverLocal);
}
