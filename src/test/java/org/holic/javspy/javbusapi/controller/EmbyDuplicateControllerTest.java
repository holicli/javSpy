package org.holic.javspy.javbusapi.controller;

import org.holic.javspy.misc.EmbyDuplicateService;
import org.holic.javspy.misc.EmbyMovieChecker;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmbyDuplicateControllerTest {

    private static final String DELETE_URL = "/javbus-api/emby/delete";
    private static final String ADMIN_HEADER = "X-JavSpy-Admin-Token";
    private static final String FIXTURE_TOKEN = "fixture-admin-token";
    private static final String VALID_BODY = "{\"ids\":[\"1\"],\"confirm\":true}";
    private final EmbyDuplicateService service = mock(EmbyDuplicateService.class);
    private final EmbyMovieChecker checker = mock(EmbyMovieChecker.class);

    @Test
    void deniesRequestsWithoutTheConfiguredAdminToken() throws Exception {
        MockMvc mvc = mvc(FIXTURE_TOKEN);
        mvc.perform(post(DELETE_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(post(DELETE_URL).header(ADMIN_HEADER, "incorrect")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mvc("").perform(post(DELETE_URL).header(ADMIN_HEADER, FIXTURE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service, checker);
    }

    @Test
    void requiresExplicitConfirmationAndStringIds() throws Exception {
        MockMvc mvc = mvc(FIXTURE_TOKEN);
        mvc.perform(post(DELETE_URL).header(ADMIN_HEADER, FIXTURE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"1\"],\"confirm\":\"true\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(DELETE_URL).header(ADMIN_HEADER, FIXTURE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1],\"confirm\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, checker);
    }

    @Test
    void dispatchesConfirmedAdminRequest() throws Exception {
        when(service.delete(List.of("1"))).thenReturn(Map.of(
                "deleted", 1, "requested", 1, "failed", Collections.emptyList()));
        mvc(FIXTURE_TOKEN).perform(post(DELETE_URL).header(ADMIN_HEADER, FIXTURE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.deleted").value(1));
        verify(service).delete(List.of("1"));
    }

    private MockMvc mvc(String token) {
        return MockMvcBuilders.standaloneSetup(new EmbyDuplicateController(service, checker, token)).build();
    }
}
