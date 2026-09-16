package org.holic.javspy.misc;

import org.holic.javspy.javbusapi.mapper.EmbyMovieMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link EmbyMovieService#syncFromEmby()} 及其公开入口（syncNow / getCodes / status / exists）的单元测试。
 *
 * <p>测试策略：</p>
 * <ul>
 *     <li>{@code syncFromEmby()} 是私有方法，用反射直接调用，断言它的返回值、写库参数、
 *     事务边界以及异常语义；</li>
 *     <li>其余用例走公开入口，验证 {@code syncFromEmby()} 被正确编排（失败不破坏旧数据、
 *     当日缓存不重复同步等）；</li>
 *     <li>{@code EmbyMovieChecker}（真正发 HTTP 的部分）与 {@code EmbyMovieMapper} 全部 mock，
 *     不依赖 Emby 服务、数据库、Redis，也不需要启动 Spring 上下文；</li>
 *     <li>{@code enabled} 字段来自 {@code @Value("${conf.emby.enabled:true}")}，脱离 Spring 时
 *     boolean 默认是 false，所以每个用例里显式注入。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbyMovieService")
class EmbyMovieServiceTest {

    @Mock
    private EmbyMovieChecker embyMovieChecker;
    @Mock
    private EmbyMovieMapper embyMovieMapper;
    @Mock
    private PlatformTransactionManager transactionManager;

    private EmbyMovieService service;

    /** 私有方法 syncFromEmby 的反射句柄。 */
    private Method syncFromEmby;

    @BeforeEach
    void setUp() throws Exception {
        service = new EmbyMovieService(embyMovieChecker, embyMovieMapper, transactionManager);
        ReflectionTestUtils.setField(service, "enabled", true);
        syncFromEmby = EmbyMovieService.class.getDeclaredMethod("syncFromEmby");
        syncFromEmby.setAccessible(true);
    }

    /** 直接调用私有 syncFromEmby()，并把包装异常拆出来抛原始异常。 */
    private int invokeSyncFromEmby() throws Throwable {
        try {
            return (Integer) syncFromEmby.invoke(service);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    /** 固定时间：今天（用于「当日已同步」分支）。 */
    private static Date today() {
        return new Date();
    }

    /** 固定时间：N 天前（用于「缓存过期」分支）。 */
    private static Date daysAgo(int days) {
        return new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days));
    }

    @Nested
    @DisplayName("syncFromEmby（私有，反射直测）")
    class SyncFromEmby {

        @Test
        @DisplayName("去空 / trim / 转大写 / 去重后写库，返回去重条数")
        void normalizesDedupsAndUppercases() throws Throwable {
            when(embyMovieChecker.getAllMovieFromEmby()).thenReturn(Arrays.asList(
                    "ssis-406", "SSIS-406", "  ssis-406  ", "abc-123", "", "   ", null));

            int count = invokeSyncFromEmby();

            assertEquals(2, count, "只有 SSIS-406 / ABC-123 两个有效番号");
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(embyMovieMapper).insertBatch(captor.capture());
            assertEquals(2, captor.getValue().size());
            assertTrue(captor.getValue().contains("SSIS-406"));
            assertTrue(captor.getValue().contains("ABC-123"));
        }

        @Test
        @DisplayName("先清空再插入，且整个过程在一个事务里")
        void clearsThenInsertsInsideOneTransaction() throws Throwable {
            when(embyMovieChecker.getAllMovieFromEmby()).thenReturn(Collections.singletonList("ssis-406"));

            invokeSyncFromEmby();

            InOrder inOrder = inOrder(transactionManager, embyMovieMapper);
            inOrder.verify(transactionManager).getTransaction(any());
            inOrder.verify(embyMovieMapper).deleteAll();
            inOrder.verify(embyMovieMapper).insertBatch(anyList());
            inOrder.verify(transactionManager).commit(any());
            verify(embyMovieMapper, times(1)).deleteAll();
        }

        @Test
        @DisplayName("Emby 返回空清单：仍然清空旧缓存，但不 insert，返回 0")
        void emptyEmbyClearsCacheWithoutInsert() throws Throwable {
            when(embyMovieChecker.getAllMovieFromEmby()).thenReturn(new ArrayList<>());

            int count = invokeSyncFromEmby();

            assertEquals(0, count);
            verify(embyMovieMapper).deleteAll();
            verify(embyMovieMapper, never()).insertBatch(anyList());
        }

        @Test
        @DisplayName("Emby 返回 null（拉取失败）：抛 IllegalStateException，不清空旧数据、不开事务")
        void nullEmbyResultThrowsAndKeepsOldData() {
            when(embyMovieChecker.getAllMovieFromEmby()).thenReturn(null);

            IllegalStateException ex = assertThrows(IllegalStateException.class, this::invokeSyncFromEmbyForTest);
            assertTrue(ex.getMessage().contains("Emby 拉取失败"), "异常信息应说明拉取失败：" + ex.getMessage());
            verify(embyMovieMapper, never()).deleteAll();
            verify(embyMovieMapper, never()).insertBatch(anyList());
            verifyNoInteractions(transactionManager);
        }

        @Test
        @DisplayName("插入失败：异常向上抛，事务回滚（旧数据不会被清空成空缓存）")
        void rollsBackWhenInsertFails() {
            when(embyMovieChecker.getAllMovieFromEmby()).thenReturn(Collections.singletonList("ssis-406"));
            when(embyMovieMapper.insertBatch(anyList())).thenThrow(new RuntimeException("db down"));

            RuntimeException ex = assertThrows(RuntimeException.class, this::invokeSyncFromEmbyForTest);
            assertEquals("db down", ex.getMessage());
            verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
        }

        /** assertThrows 的回调适配：把 Throwable 收敛掉，避免每个用例重复 try/catch。 */
        private void invokeSyncFromEmbyForTest() {
            try {
                invokeSyncFromEmby();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    @Nested
    @DisplayName("syncNow（手动同步入口）")
    class SyncNow {

        @Test
        @DisplayName("成功：返回条数与最近同步时间")
        void successReturnsCountAndLastSyncAt() {
            Date lastSyncAt = today();
            when(embyMovieChecker.getAllMovieFromEmby())
                    .thenReturn(Arrays.asList("SSIS-406", "ABC-123", "abc-123"));
            when(embyMovieMapper.selectLastSyncAt()).thenReturn(lastSyncAt);

            EmbyMovieService.SyncResult result = service.syncNow();

            assertTrue(result.success);
            assertEquals(2, result.count);
            assertSame(lastSyncAt, result.lastSyncAt);
            assertTrue(result.message.contains("2"), "提示信息应带上条数：" + result.message);
            verify(embyMovieMapper).deleteAll();
        }

        @Test
        @DisplayName("拉取失败：返回失败结果，不动数据库旧数据")
        void failureKeepsOldData() {
            when(embyMovieChecker.getAllMovieFromEmby()).thenReturn(null);

            EmbyMovieService.SyncResult result = service.syncNow();

            assertFalse(result.success);
            assertEquals(0, result.count);
            assertNull(result.lastSyncAt);
            assertTrue(result.message.contains("Emby 拉取失败"), "失败原因应透出：" + result.message);
            verify(embyMovieMapper, never()).deleteAll();
            verify(embyMovieMapper, never()).selectLastSyncAt();
        }

        @Test
        @DisplayName("功能关闭：直接返回未启用，不碰 Emby 与数据库")
        void disabledShortCircuits() {
            ReflectionTestUtils.setField(service, "enabled", false);

            EmbyMovieService.SyncResult result = service.syncNow();

            assertFalse(result.success);
            assertTrue(result.message.contains("未启用"), "提示信息应说明未启用：" + result.message);
            verifyNoInteractions(embyMovieChecker, embyMovieMapper, transactionManager);
        }
    }

    @Nested
    @DisplayName("getCodes（查询缓存，必要时自动同步）")
    class GetCodes {

        @Test
        @DisplayName("当日已同步：直接读缓存，并做 trim + 大写归一化，不请求 Emby")
        void usesCacheWhenSyncedToday() {
            when(embyMovieMapper.selectLastSyncAt()).thenReturn(today());
            when(embyMovieMapper.selectAllCodes()).thenReturn(Arrays.asList("ssis-406", " ABC-123 "));

            Set<String> codes = service.getCodes();

            assertEquals(2, codes.size());
            assertTrue(codes.contains("SSIS-406"));
            assertTrue(codes.contains("ABC-123"));
            verify(embyMovieChecker, never()).getAllMovieFromEmby();
            verify(embyMovieMapper, never()).deleteAll();
        }

        @Test
        @DisplayName("缓存过期：先自动同步一次，再返回同步后的缓存")
        void staleCacheTriggersSync() {
            when(embyMovieMapper.selectLastSyncAt()).thenReturn(daysAgo(2));
            when(embyMovieChecker.getAllMovieFromEmby()).thenReturn(Collections.singletonList("SSIS-406"));
            when(embyMovieMapper.selectAllCodes()).thenReturn(Collections.singletonList("SSIS-406"));

            Set<String> codes = service.getCodes();

            verify(embyMovieChecker).getAllMovieFromEmby();
            verify(embyMovieMapper).deleteAll();
            verify(embyMovieMapper).insertBatch(anyList());
            assertEquals(Collections.singleton("SSIS-406"), codes);
        }

        @Test
        @DisplayName("缓存从未同步（lastSyncAt 为 null）：同样触发一次同步")
        void nullLastSyncTriggersSync() {
            when(embyMovieMapper.selectLastSyncAt()).thenReturn(null);
            when(embyMovieChecker.getAllMovieFromEmby()).thenReturn(Collections.singletonList("ABC-123"));
            when(embyMovieMapper.selectAllCodes()).thenReturn(Collections.singletonList("ABC-123"));

            Set<String> codes = service.getCodes();

            verify(embyMovieChecker).getAllMovieFromEmby();
            assertEquals(Collections.singleton("ABC-123"), codes);
        }

        @Test
        @DisplayName("过期且同步失败：不抛异常，降级使用数据库旧数据")
        void syncFailureFallsBackToOldData() {
            when(embyMovieMapper.selectLastSyncAt()).thenReturn(daysAgo(3));
            when(embyMovieChecker.getAllMovieFromEmby()).thenReturn(null);
            when(embyMovieMapper.selectAllCodes()).thenReturn(Arrays.asList("OLD-001", "old-002"));

            Set<String> codes = service.getCodes();

            assertEquals(2, codes.size());
            assertTrue(codes.contains("OLD-001"));
            assertTrue(codes.contains("OLD-002"));
            verify(embyMovieMapper, never()).deleteAll();
        }

        @Test
        @DisplayName("功能关闭：返回空集合，不查库")
        void disabledReturnsEmptySet() {
            ReflectionTestUtils.setField(service, "enabled", false);

            assertTrue(service.getCodes().isEmpty());
            verifyNoInteractions(embyMovieChecker, embyMovieMapper, transactionManager);
        }
    }

    @Nested
    @DisplayName("exists / status")
    class ExistsAndStatus {

        @Test
        @DisplayName("exists：命中时忽略大小写与首尾空格，空白/未命中返回 false")
        void existsNormalizesInput() {
            when(embyMovieMapper.selectLastSyncAt()).thenReturn(today());
            when(embyMovieMapper.selectAllCodes()).thenReturn(Collections.singletonList("SSIS-406"));

            assertTrue(service.exists(" ssis-406 "));
            assertFalse(service.exists("ABC-123"));
            assertFalse(service.exists("   "));
            assertFalse(service.exists(null));
        }

        @Test
        @DisplayName("status：当日已同步时 todaySynced=true 并带出数量与时间")
        void statusReportsCountAndTodayFlag() {
            Date lastSyncAt = today();
            when(embyMovieMapper.countAll()).thenReturn(5L);
            when(embyMovieMapper.selectLastSyncAt()).thenReturn(lastSyncAt);

            Map<String, Object> status = service.status();

            assertEquals(Boolean.TRUE, status.get("enabled"));
            assertEquals(5L, status.get("count"));
            assertSame(lastSyncAt, status.get("lastSyncAt"));
            assertEquals(Boolean.TRUE, status.get("todaySynced"));
        }

        @Test
        @DisplayName("status：缓存过期或为空时 todaySynced=false")
        void statusReportsNotSyncedToday() {
            when(embyMovieMapper.countAll()).thenReturn(0L);
            when(embyMovieMapper.selectLastSyncAt()).thenReturn(daysAgo(1));

            Map<String, Object> status = service.status();

            assertEquals(Boolean.FALSE, status.get("todaySynced"));
            assertNotNull(status.get("lastSyncAt"));
        }

        @Test
        @DisplayName("status：功能关闭时给出零值，不查库")
        void statusDisabled() {
            ReflectionTestUtils.setField(service, "enabled", false);

            Map<String, Object> status = service.status();

            assertEquals(Boolean.FALSE, status.get("enabled"));
            assertEquals(0, status.get("count"));
            assertNull(status.get("lastSyncAt"));
            assertEquals(Boolean.FALSE, status.get("todaySynced"));
            verifyNoInteractions(embyMovieMapper);
        }
    }
}
