package org.holic.javspy.misc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbyDuplicateServiceTest {

    @Mock
    private EmbyMovieChecker checker;
    @Mock
    private EmbyMovieService movieService;
    private EmbyDuplicateService service;

    @BeforeEach
    void setUp() {
        service = new EmbyDuplicateService(checker, movieService);
    }

    @Test
    void doesNotTreatWordsFollowedByAYearAsCodes() {
        assertNull(EmbyDuplicateService.extractCode("Love Story 2020"));
        assertNull(EmbyDuplicateService.extractCode("A Different Story 2020"));
        assertNull(EmbyDuplicateService.extractCode("Story 2020"));
        assertEquals("FC2-1234567", EmbyDuplicateService.extractCode("FC2-PPV-1234567"));
    }

    @Test
    void conflictingNameAndFileCodesAreNotCandidates() {
        EmbyMediaItem conflict = item("1", "ABC-123", "/a/DEF-123.mkv", 2020, 10L);
        EmbyMediaItem matching = item("2", "ABC-123", "/b/ABC-123.mkv", 2020, 20L);
        assertEquals("conflict", conflict.getCodeFrom());
        assertTrue(EmbyDuplicateService.groupItems(List.of(conflict, matching)).isEmpty());
    }

    @Test
    void differentYearRemakesAreNeverGroupedByTitle() {
        EmbyMediaItem a = item("1", "Cinderella", "/a/Cinderella.mkv", 1950, 10L);
        EmbyMediaItem b = item("2", "Cinderella", "/b/Cinderella.mkv", 2015, 20L);
        assertTrue(EmbyDuplicateService.groupItems(List.of(a, b)).isEmpty());
    }

    @Test
    void weakCodesAndTitleMatchesRequireManualReview() {
        EmbyMediaItem a = item("1", "ABC123", "/a/ABC123.mkv", 2020, 10L);
        EmbyMediaItem b = item("2", "ABC123", "/b/ABC123.mkv", 2020, 20L);
        EmbyMediaItem c = item("3", "Cinderella", "/a/Cinderella.mkv", 1950, 10L);
        EmbyMediaItem d = item("4", "Cinderella", "/b/Cinderella.mkv", 1950, 20L);
        List<EmbyDuplicateService.Group> groups = EmbyDuplicateService.groupItems(List.of(a, b, c, d));

        assertEquals(2, groups.size());
        assertTrue(groups.stream().allMatch(EmbyDuplicateService.Group::isDeletable));
        assertTrue(groups.stream().noneMatch(EmbyDuplicateService.Group::isBatchEligible));
    }

    @Test
    void filteredScanIncludesBothFilesInTheMatchingGroup() {
        when(movieService.isEnabled()).thenReturn(true);
        when(checker.getAllMovieItems()).thenReturn(pair());
        Map<String, Object> scan = service.duplicates(false, "/small/");
        List<?> groups = (List<?>) scan.get("groups");

        assertEquals(1, groups.size());
        EmbyDuplicateService.Group group = (EmbyDuplicateService.Group) groups.get(0);
        assertEquals(2, group.getCount());
        assertTrue(group.isBatchEligible());
        assertEquals("2", group.getItems().get(0).getId());
    }

    @Test
    void refusesDeletesFromStaleScans() {
        when(movieService.isEnabled()).thenReturn(true);
        List<EmbyMediaItem> original = pair();
        when(checker.getAllMovieItems()).thenReturn(original, original.subList(0, 1));
        service.duplicates(false, null);

        assertThrows(IllegalArgumentException.class, () -> service.delete(List.of("1")));
        verify(checker, times(2)).getAllMovieItems();
        verify(checker, never()).deleteItems(anyList());
    }

    @Test
    void rejectsRemovingTheEntireGroup() {
        when(movieService.isEnabled()).thenReturn(true);
        when(checker.getAllMovieItems()).thenReturn(pair());

        assertThrows(IllegalArgumentException.class, () -> service.delete(List.of("1", "2")));
        verify(checker, never()).deleteItems(anyList());
    }

    @Test
    void doesNotTreatSharedPathsOrMultiVersionItemsAsSeparateFiles() {
        when(movieService.isEnabled()).thenReturn(true);
        List<EmbyMediaItem> sharedPath = pair();
        sharedPath.get(1).setPath(sharedPath.get(0).getPath());
        List<EmbyMediaItem> multipleSources = pair();
        multipleSources.get(0).setMediaSourceCount(2);
        when(checker.getAllMovieItems()).thenReturn(sharedPath, multipleSources);

        assertThrows(IllegalArgumentException.class, () -> service.delete(List.of("1")));
        assertThrows(IllegalArgumentException.class, () -> service.delete(List.of("1")));
        verify(checker, never()).deleteItems(anyList());
    }

    @Test
    void onlyDispatchesValidatedIdsAndInvalidatesTheCache() {
        when(movieService.isEnabled()).thenReturn(true);
        when(checker.getAllMovieItems()).thenReturn(pair());
        when(checker.deleteItems(List.of("1"))).thenReturn(Collections.emptyMap());

        Map<String, Object> result = service.delete(List.of("1", "1"));
        assertEquals(1, result.get("requested"));
        assertEquals(1, result.get("deleted"));
        verify(checker).deleteItems(List.of("1"));
        verify(movieService).invalidate();
    }

    @Test
    void invalidatesEvenWhenEmbyDoesNotConfirmResults() {
        when(movieService.isEnabled()).thenReturn(true);
        when(checker.getAllMovieItems()).thenReturn(pair());
        when(checker.deleteItems(List.of("1"))).thenReturn(Map.of("1", "未确认"));

        assertEquals(0, service.delete(List.of("1")).get("deleted"));
        verify(movieService).invalidate();
    }

    @Test
    void refusesScanAndDeleteWhenDisabled() {
        assertThrows(IllegalStateException.class, () -> service.duplicates(true, null));
        assertThrows(IllegalStateException.class, () -> service.delete(List.of("1")));
        verifyNoInteractions(checker);
    }

    private static List<EmbyMediaItem> pair() {
        return List.of(
                item("1", "ABC-123", "/small/ABC-123-U.mkv", 2020, 10L),
                item("2", "ABC-123", "/large/ABC-123.mkv", 2020, 20L));
    }

    private static EmbyMediaItem item(String id, String name, String path, int year, long size) {
        EmbyMediaItem item = EmbyMediaItem.builder().id(id).name(name).path(path)
                .year(year).size(size).mediaSourceCount(1).build();
        EmbyDuplicateService.resolveCode(item);
        return item;
    }
}
