package org.holic.javspy.javbusapi.mapper;

import org.apache.ibatis.annotations.Param;
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;
import org.holic.javspy.javbusapi.model.JavbusApiMovie;
import org.holic.javspy.javbusapi.model.JavbusApiMovieSample;
import org.holic.javspy.javbusapi.model.JavbusApiSimilarMovie;
import org.holic.javspy.javbusapi.model.JavbusApiStar;
import org.holic.javspy.javbusapi.model.JavbusFollowActor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * javbus API 数据访问接口（规范化表结构）。
 */
@Repository
public interface JavbusApiMapper {

    // ---------- 影片 ----------

    /** 按番号查询影片，不存在返回 null。 */
    JavbusApiMovie findByCode(@Param("code") String code);

    /** 批量按番号查询已存在的影片。 */
    List<JavbusApiMovie> findByCodes(@Param("codes") List<String> codes);

    /** 插入影片；已存在（番号冲突）时更新可更新字段。 */
    int insertMovie(JavbusApiMovie movie);

    /** 回写本地封面地址。 */
    int updateCoverLocal(@Param("code") String code, @Param("coverLocal") String coverLocal);

    /** 按条件分页查询影片列表。 */
    List<JavbusApiMovie> searchMovies(@Param("code") String code,
                                      @Param("keyword") String keyword,
                                      @Param("releaseDate") String releaseDate);

    // ---------- 磁力 ----------

    /** 批量插入磁力链接（按 link 去重）。 */
    int insertMagnets(@Param("list") List<JavbusApiMagnet> magnets);

    /** 查询某部影片已入库的磁力链接数量。 */
    int countMagnetsByCode(@Param("code") String code);

    // ---------- 实体表 ----------

    /** 插入或更新演员（按 id 去重）。 */
    int upsertStar(JavbusApiStar star);

    /** 批量插入或更新演员。 */
    int upsertStars(@Param("list") List<JavbusApiStar> stars);

    /** 插入或更新导演（按 id 去重）。 */
    int upsertDirector(@Param("id") String id, @Param("name") String name);

    /** 插入或更新制作商（按 id 去重）。 */
    int upsertStudio(@Param("id") String id, @Param("name") String name);

    /** 插入或更新发行商（按 id 去重）。 */
    int upsertPublisher(@Param("id") String id, @Param("name") String name);

    /** 插入或更新系列（按 id 去重）。 */
    int upsertSeries(@Param("id") String id, @Param("name") String name);

    /** 插入或更新类别（按 id 去重）。 */
    int upsertGenre(@Param("id") String id, @Param("name") String name);

    // ---------- 关联表 ----------

    /** 插入影片-演员关联。 */
    int insertMovieStars(@Param("movieId") Long movieId,
                         @Param("list") List<JavbusApiStar> stars);

    /** 插入影片-类别关联。 */
    int insertMovieGenres(@Param("movieId") Long movieId,
                          @Param("list") List<JavbusApiStar> genres);

    /** 插入影片预览图。 */
    int insertMovieSamples(@Param("movieId") Long movieId,
                           @Param("list") List<JavbusApiMovieSample> samples);

    /** 插入影片-相似影片关联。 */
    int insertMovieSimilars(@Param("movieId") Long movieId,
                            @Param("list") List<JavbusApiSimilarMovie> similars);

    // ---------- 查询（详情展示用） ----------

    /** 按影片 code 查询预览图列表。 */
    List<JavbusApiMovieSample> findSamplesByCode(@Param("code") String code);

    /** 按影片 code 查询演员列表（关联 javbus_movie_star / javbus_star）。 */
    List<JavbusApiStar> findStarsByCode(@Param("code") String code);

    /** 按影片 code 查询系列列表（关联 javbus_movie_star） */
    List<String> findGenreByCode(@Param("code") String code);

    /** 按演员 ID 查询演员详情。 */
    JavbusApiStar findStarById(@Param("id") String id);

    // ---------- 关注演员 ----------

    /** 查询全部关注演员。 */
    List<JavbusFollowActor> listFollowActors();

    /** 新增关注演员（重名时忽略），返回受影响行数。 */
    int insertFollowActor(@Param("actorName") String actorName,
                          @Param("remark") String remark);

    /** 删除关注演员，返回受影响行数。 */
    int deleteFollowActor(@Param("actorName") String actorName);
}
