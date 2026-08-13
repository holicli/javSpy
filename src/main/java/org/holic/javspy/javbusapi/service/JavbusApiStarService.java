package org.holic.javspy.javbusapi.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.holic.javspy.javbusapi.model.JavbusApiStar;

import java.util.List;

/**
 * javbus_star 表服务。
 */
public interface JavbusApiStarService extends IService<JavbusApiStar> {

    /** 插入或更新演员。 */
    boolean upsertStar(JavbusApiStar star);

    /** 批量插入或更新演员。 */
    boolean upsertStars(List<JavbusApiStar> stars);

    /** 按影片 code 查询演员列表。 */
    List<JavbusApiStar> listByMovieCode(String code);
}
