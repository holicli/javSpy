package org.holic.javspy.javdb.mapper;

import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javdb.model.JavdbFollowActor;
import org.holic.javspy.javdb.model.JavdbMagnet;
import org.holic.javspy.javdb.model.JavdbMagnetExport;
import org.holic.javspy.javdb.model.JavdbMovie;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javdb 数据访问接口（javdb_movie / javdb_magnet 两张新表）。
 */
@Repository
public interface JavdbMapper {

    /** 按番号查询影片，不存在返回 null。 */
    JavdbMovie findByCode(@Param("code") String code);

    /** 批量按番号查询已存在的影片。 */
    List<JavdbMovie> findByCodes(@Param("codes") List<String> codes);

    /** 插入影片；已存在（番号冲突）时更新可更新字段。 */
    int insertMovie(JavdbMovie movie);

    /** 更新影片本地封面地址。 */
    int updateCoverLocal(@Param("code") String code, @Param("coverLocal") String coverLocal);

    /** 批量插入磁力链接；已存在的磁力链接由 service 层过滤。 */
    int insertMagnets(@Param("list") List<JavdbMagnet> magnets);

    /** 查询某部影片已入库的磁力链接。 */
    List<String> findMagnetLinksByDetailId(@Param("detailId") String detailId);

    /** 统计某部影片的磁力链接数量。 */
    int countMagnetsByCode(@Param("code") String code);

    /** 批量按番号查询磁力链接，用于导出磁链。 */
    List<JavdbMagnet> findMagnetsByCodes(@Param("codes") List<String> codes);

    /** 清空磁链导出表。 */
    int clearMagnetExports();

    /** 批量写入本次选中的磁链导出记录。 */
    int insertMagnetExports(@Param("list") List<JavdbMagnetExport> list);

    /** 按条件分页查询影片列表。 */
    List<JavdbMovie> searchMovies(@Param("code") String code,
                                  @Param("keyword") String keyword,
                                  @Param("releaseDate") String releaseDate);

    /** 查询全部关注演员。 */
    List<JavdbFollowActor> listFollowActors();

    /** 新增关注演员（重名时忽略），返回受影响行数。 */
    int insertFollowActor(@Param("actorName") String actorName,
                          @Param("remark") String remark);

    /** 删除关注演员，返回受影响行数。 */
    int deleteFollowActor(@Param("actorName") String actorName);
}
