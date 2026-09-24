package org.example.card.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-6 用户数据目录：-Dcardgame.home 覆盖 + 配置读写往返。 */
class UserDataTest {

    @Test
    void configRoundTripsInOverriddenHome(@TempDir Path home) {
        String previous = System.getProperty("cardgame.home");
        System.setProperty("cardgame.home", home.toString());
        try {
            ConfigService config = new ConfigService();
            config.load(); // 空目录：默认值，不抛
            assertTrue(config.isSoundEnabled());

            config.setSoundEnabled(false);
            config.save();
            assertTrue(java.nio.file.Files.isRegularFile(home.resolve("config.properties")));

            ConfigService reloaded = new ConfigService();
            reloaded.load();
            assertFalse(reloaded.isSoundEnabled());

            GameLog.info("userdata-test");
            assertTrue(java.nio.file.Files.isRegularFile(home.resolve("game.log")));
            assertEquals(home, UserData.dir());
        } finally {
            if (previous == null) {
                System.clearProperty("cardgame.home");
            } else {
                System.setProperty("cardgame.home", previous);
            }
        }
    }
}
