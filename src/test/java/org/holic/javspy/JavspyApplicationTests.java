package org.holic.javspy;

import org.holic.javspy.model.JavTableConstants;
import org.holic.javspy.model.MovieResponse;
import org.holic.javspy.service.JavService;
import org.holic.javspy.service.MovieApiService;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.holic.javspy.model.JavTableConstants.TABLE_DIRECTOR;

@SpringBootTest
class JavspyApplicationTests {
    @Autowired
    private JavService javService;

    @Autowired
    private MovieApiService movieApiService;
    @Autowired
    private StringEncryptor encryptor;

    @Test
    void contextLoads() {
    }

    @Test
    void testNextPage() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            System.out.println("当前系统是 Windows");
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            System.out.println("当前系统是 Linux/Unix");
        }
    }
}
