package org.holic.javspy.javbusapi.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.holic.javspy.javbusapi.controller.MagnetDownloadController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.holic.javspy.javbusapi.client.QbittorrentClient.AddResult.ADDED;
import static org.holic.javspy.javbusapi.client.QbittorrentClient.AddResult.ALREADY_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QbittorrentClientTest {

    private static final String CODE = "TEST-001";
    private static final String MAGNET = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Example";
    private static final String TORRENT_INFO = "[{\"name\":\"[Group] test_001 - 1080p\"}]";
    private static final String QBIT_URL = "http://qbittorrent.example.test:8085";
    private static final String DOWNLOAD_PATH = "/javbus-api/magnets/download";
    private static final String DOWNLOADING_PATH = "/javbus-api/magnets/downloading";

    private final List<Request> requests = new ArrayList<>();
    private final Deque<Response.Builder> responses = new ArrayDeque<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QbittorrentClient client = new QbittorrentClient(QBIT_URL, "test-user", "test-password", objectMapper,
            new OkHttpClient.Builder().addInterceptor(chain -> {
                requests.add(chain.request());
                return responses.removeFirst().request(chain.request()).build();
            }).build());

    @Test
    void reportsOnlyRequestedCodesWithDownloadsInProgress() throws Exception {
        reply(200, "Ok.").addHeader("Set-Cookie", "SID=fixture; Path=/");
        reply(200, "["
                + "{\"name\":\"[Group] test_001 - 1080p\",\"state\":\"downloading\"},"
                + "{\"name\":\"Untitled torrent\",\"tags\":\"archive, TAG-002\",\"state\":\"metaDL\"},"
                + "{\"name\":\"PAUSE-003\",\"state\":\"pausedDL\"},"
                + "{\"name\":\"DONE-004\",\"state\":\"uploading\"},"
                + "{\"name\":\"WAIT-005\",\"state\":\"queuedDL\"},"
                + "{\"name\":\"PEERS-006\",\"state\":\"stalledDL\"},"
                + "{\"name\":\"TEST-0100\",\"state\":\"forcedDL\"},"
                + "{\"name\":\"OTHER-007\",\"state\":\"downloading\"}"
                + "]");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MagnetDownloadController(client)).build();
        List<String> codes = List.of("test-001", "TAG-002", "PAUSE-003", "DONE-004", "WAIT-005",
                "PEERS-006", "TEST-010", "test-001");

        mvc.perform(post(DOWNLOADING_PATH)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(codes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", contains("test-001", "TAG-002", "PEERS-006")));

        assertEquals(2, requests.size());
        assertEquals("GET", requests.get(1).method());
        assertEquals("/api/v2/torrents/info", requests.get(1).url().encodedPath());
    }

    @Test
    void checksForAnExactCodeBeforeSubmittingToTheDefaultLocation() throws Exception {
        reply(200, "Ok.").addHeader("Set-Cookie", "QBT_SID_8085=fixture; Path=/; HttpOnly");
        reply(200, "[{\"name\":\"TEST-0010\",\"tags\":\"TEST-001A, XTEST-001\"}]");
        reply(200, "Ok.");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MagnetDownloadController(client)).build();

        mvc.perform(post(DOWNLOAD_PATH)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", " test_001 ", "magnet", MAGNET))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ADDED"));

        Request login = requests.get(0);
        assertEquals("/api/v2/auth/login", login.url().encodedPath());
        assertEquals(QBIT_URL + "/", login.header("Referer"));
        FormBody credentials = (FormBody) login.body();
        assertEquals("test-user", credentials.value(0));
        assertEquals("test-password", credentials.value(1));

        Request lookup = requests.get(1);
        assertEquals("GET", lookup.method());
        assertEquals("/api/v2/torrents/info", lookup.url().encodedPath());
        assertNull(lookup.url().query());
        assertEquals("QBT_SID_8085=fixture", lookup.header("Cookie"));

        Request add = requests.get(2);
        assertEquals("/api/v2/torrents/add", add.url().encodedPath());
        assertEquals(QBIT_URL + "/", add.header("Referer"));
        assertEquals("QBT_SID_8085=fixture", add.header("Cookie"));
        MultipartBody multipart = (MultipartBody) add.body();
        assertEquals(2, multipart.size());
        assertEquals("form-data; name=\"urls\"", multipart.part(0).headers().get("Content-Disposition"));
        Buffer content = new Buffer();
        multipart.part(0).body().writeTo(content);
        assertEquals(MAGNET, content.readUtf8());
        assertEquals("form-data; name=\"tags\"", multipart.part(1).headers().get("Content-Disposition"));
        multipart.part(1).body().writeTo(content);
        assertEquals(CODE, content.readUtf8());
    }

    @Test
    void returnsAlreadyExistsWhenATaskNameContainsTheSameCode() throws Exception {
        reply(200, "Ok.").addHeader("Set-Cookie", "SID=fixture; Path=/");
        reply(200, TORRENT_INFO);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MagnetDownloadController(client)).build();

        mvc.perform(post(DOWNLOAD_PATH)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", CODE, "magnet", MAGNET))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("已存在"));
        assertEquals(2, requests.size());
        assertNull(requests.get(1).url().query());
    }

    @Test
    void findsATaskByItsCodeTagEvenWhenItsNameDoesNotContainTheCode() {
        reply(204, "").addHeader("Set-Cookie", "SID=fixture; Path=/");
        reply(200, "[{\"name\":\"Untitled torrent\",\"tags\":\"archive, TEST-001\"}]");

        assertEquals(ALREADY_EXISTS, client.addMagnet(CODE, MAGNET));
        assertEquals(2, requests.size());
    }

    @Test
    void recognizesCommonFc2NamingVariants() {
        reply(204, "").addHeader("Set-Cookie", "SID=fixture; Path=/");
        reply(200, "[{\"name\":\"[Group] ＦＣ２.１２３４５６.1080p\"}]");

        assertEquals(ALREADY_EXISTS, client.addMagnet("FC2-PPV-123456", MAGNET));
        assertEquals(2, requests.size());
    }

    @Test
    void confirmsThatAConflictMeansTheTorrentWasAddedConcurrently() {
        reply(204, "").addHeader("Set-Cookie", "SID=fixture; Path=/");
        reply(200, "[]");
        reply(409, "Conflict");
        reply(200, TORRENT_INFO);

        assertEquals(ALREADY_EXISTS, client.addMagnet(CODE, MAGNET));
        assertEquals("/api/v2/torrents/info", requests.get(3).url().encodedPath());
    }

    @Test
    void doesNotSubmitWhenTheExistenceCheckFails() {
        reply(204, "").addHeader("Set-Cookie", "SID=fixture; Path=/");
        reply(502, "Bad gateway");

        assertThrows(IllegalStateException.class, () -> client.addMagnet(CODE, MAGNET));
        assertEquals(2, requests.size());
    }

    @Test
    void reusesTheSessionAndSignsInAgainIfItExpires() {
        reply(204, "").addHeader("Set-Cookie", "SID=first; Path=/");
        reply(200, "[]");
        reply(200, "{\"success_count\":1,\"pending_count\":0}");
        reply(403, "Forbidden");
        reply(204, "").addHeader("Set-Cookie", "SID=second; Path=/");
        reply(200, "[]");
        reply(202, "{\"success_count\":0,\"pending_count\":1}");

        assertEquals(ADDED, client.addMagnet(CODE, MAGNET));
        assertEquals(ADDED, client.addMagnet(CODE, MAGNET));

        assertEquals(7, requests.size());
        assertEquals("SID=first", requests.get(3).header("Cookie"));
        assertEquals("/api/v2/auth/login", requests.get(4).url().encodedPath());
        assertEquals("SID=second", requests.get(5).header("Cookie"));
    }

    @Test
    void detectsLegacyErrorsEvenWhenTheServerReturnsHttp200() {
        reply(200, "Fails.");
        assertThrows(IllegalStateException.class, () -> client.addMagnet(CODE, MAGNET));
        assertEquals(1, requests.size());

        reply(200, "Ok.").addHeader("Set-Cookie", "SID=fixture; Path=/");
        reply(200, "[]");
        reply(200, "Fails.");
        reply(200, "[]");
        assertThrows(IllegalStateException.class, () -> client.addMagnet(CODE, MAGNET));
        assertTrue(responses.isEmpty());
    }

    @Test
    void rejectsInvalidCodesAndMagnetsBeforeCallingTheWebUi() {
        assertThrows(IllegalArgumentException.class, () -> client.addMagnet(CODE, "https://example.test/file.torrent"));
        assertThrows(IllegalArgumentException.class, () -> client.addMagnet(CODE, MAGNET + "\n" + MAGNET));
        assertThrows(IllegalArgumentException.class, () -> client.addMagnet(CODE, "magnet:?"));
        assertThrows(IllegalArgumentException.class, () -> client.addMagnet(null, MAGNET));
        assertThrows(IllegalArgumentException.class, () -> client.addMagnet("TEST-001,extra-tag", MAGNET));
        assertTrue(requests.isEmpty());
    }

    private Response.Builder reply(int code, String body) {
        Response.Builder response = new Response.Builder().protocol(Protocol.HTTP_1_1)
                .code(code).message("Fixture")
                .body(ResponseBody.create(body, MediaType.get("text/plain")));
        responses.add(response);
        return response;
    }
}
