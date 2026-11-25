package org.holic.javspy.javbusapi.controller;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.holic.javspy.javbusapi.client.JavbusApiClient;
import org.holic.javspy.javbusapi.mapper.JavbusApiMagnetMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiMovieMapper;
import org.holic.javspy.javbusapi.mapper.JavbusApiMovieSampleMapper;
import org.holic.javspy.javbusapi.model.JavbusApiMagnet;
import org.holic.javspy.javbusapi.model.JavbusApiMovie;
import org.holic.javspy.javbusapi.model.JavbusApiMovieSample;
import org.holic.javspy.javbusapi.service.JavbusApiService;
import org.holic.javspy.misc.ImageDownloadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JavbusApiControllerTest {

    private static final String CODE = "TEST-001";
    private static final String IMAGE_URL = "/javbus-api/movie/image";
    private static final String MAGNETS_URL = "/javbus-api/magnets";
    private static final String MAGNET_LINK = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567";
    private static final byte[] IMAGE = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aC1sAAAAASUVORK5CYII=");

    @TempDir
    Path imageDir;

    private final JavbusApiClient client = mock(JavbusApiClient.class);
    private final JavbusApiMovieMapper movies = mock(JavbusApiMovieMapper.class);
    private final JavbusApiMovieSampleMapper samples = mock(JavbusApiMovieSampleMapper.class);
    private final JavbusApiMagnetMapper magnets = mock(JavbusApiMagnetMapper.class);
    private final List<String> downloads = new ArrayList<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ImageDownloadService images = new ImageDownloadService();
        ReflectionTestUtils.setField(images, "imageStoragePath", imageDir.toString());
        ReflectionTestUtils.setField(images, "httpClient", new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath();
            downloads.add(path);
            boolean success = "https://www.javbus.com/".equals(chain.request().header("Referer"))
                    && !path.startsWith("/missing/");
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(success ? 200 : 403).message(success ? "OK" : "Forbidden")
                    .body(ResponseBody.create(IMAGE, MediaType.get("image/png"))).build();
        }).build());
        JavbusApiService service = new JavbusApiService(client, movies, magnets, null, samples,
                null, images, null, null);
        mvc = MockMvcBuilders.standaloneSetup(new JavbusApiController(service, null)).build();
    }

    @Test
    void downloadsAndCachesEachSampleSizeSeparately() throws Exception {
        JavbusApiMovieSample sample = new JavbusApiMovieSample();
        sample.setSrc("//images.example.test/full/example.png");
        sample.setThumbnail("/thumb/example.png");
        when(samples.findByCodeAndId(CODE, 7L)).thenReturn(sample);

        String full = cachedImage(mvc.perform(get(IMAGE_URL).param("code", CODE).param("sampleId", "7")));
        String thumb = cachedImage(mvc.perform(get(IMAGE_URL).param("code", CODE).param("sampleId", "7")
                .param("thumbnail", "true")));
        assertNotEquals(thumb, full);
        assertEquals(full, cachedImage(mvc.perform(get(IMAGE_URL).param("code", CODE).param("sampleId", "7"))));
        assertEquals(List.of("/full/example.png", "/thumb/example.png"), downloads);
        mvc.perform(get(IMAGE_URL).param("code", CODE).param("sampleId", "8"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fallsBackToAnExistingLocalCoverWhenTheLargeCoverFails() throws Exception {
        JavbusApiMovie movie = new JavbusApiMovie();
        movie.setCoverHd("/missing/example.png");
        movie.setCoverLocal("http://localhost:8084/pic/local cover.png");
        when(movies.findByCode(CODE)).thenReturn(movie);
        Files.write(imageDir.resolve("local cover.png"), IMAGE);

        mvc.perform(get(IMAGE_URL).param("code", CODE))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/pic/local%20cover.png"));
        assertEquals(List.of("/missing/example.png"), downloads);
    }

    @Test
    void showsAndRefreshesRemoteMagnetsWithoutImportingTheMovie() throws Exception {
        JavbusApiMovie remote = new JavbusApiMovie();
        remote.setCode(CODE);
        remote.setGid("123");
        remote.setUc("0");
        JavbusApiMagnet magnet = new JavbusApiMagnet();
        magnet.setMagnet(MAGNET_LINK);
        when(client.getMovieDetail(CODE)).thenReturn(remote);
        when(client.getMagnets(CODE, "123", "0")).thenReturn(List.of(magnet));

        mvc.perform(get(MAGNETS_URL).param("code", " test-001 "))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].magnet").value(MAGNET_LINK));
        mvc.perform(post(MAGNETS_URL + "/refresh").param("code", CODE))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.after").value(1))
                .andExpect(jsonPath("$.data.magnets[0].magnet").value(MAGNET_LINK));

        verify(client, times(2)).getMagnets(CODE, "123", "0");
        verify(movies, never()).insertMovie(any());
        verify(magnets, never()).insertBatch(any());
    }

    @Test
    void keepsIncrementalRefreshForMoviesAlreadyInTheLibrary() throws Exception {
        JavbusApiMovie movie = new JavbusApiMovie();
        movie.setCode(CODE);
        movie.setId(42L);
        movie.setGid("123");
        JavbusApiMagnet fresh = new JavbusApiMagnet();
        fresh.setMagnet(MAGNET_LINK);
        when(movies.findByCode(CODE)).thenReturn(movie);
        when(magnets.countByCode(CODE)).thenReturn(0, 1);
        when(client.getMagnets(CODE, "123", null)).thenReturn(List.of(fresh));

        mvc.perform(get(MAGNETS_URL).param("code", CODE)).andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(client);
        mvc.perform(post(MAGNETS_URL + "/refresh").param("code", CODE))
                .andExpect(jsonPath("$.data.added").value(1));
        verify(magnets).insertBatch(List.of(fresh));
        assertEquals(42L, fresh.getMovieId());
    }

    private String cachedImage(ResultActions request) throws Exception {
        String location = request.andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertTrue(location.startsWith("/pic/"));
        assertArrayEquals(IMAGE, Files.readAllBytes(imageDir.resolve(location.substring("/pic/".length()))));
        return location;
    }
}
