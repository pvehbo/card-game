package org.example.card.service;

import org.example.card.ui.Theme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** V3 主题持久化：默认值/空值回退 + ui.theme 落盘往返。 */
class ConfigThemeTest {

    @Test
    void defaultIsDarkAndBlankFallsBack() {
        ConfigService config = new ConfigService();
        assertEquals("dark", config.getThemeId());
        config.setThemeId(null);
        assertEquals("dark", config.getThemeId());
        config.setThemeId("   ");
        assertEquals("dark", config.getThemeId());
        config.setThemeId("light");
        assertEquals("light", config.getThemeId());
    }

    @Test
    void themeRoundTripsThroughFile(@TempDir Path home) {
        String previous = System.getProperty("cardgame.home");
        System.setProperty("cardgame.home", home.toString());
        try {
            ConfigService config = new ConfigService();
            config.load();
            config.setThemeId(Theme.LIGHT.id());
            config.save();

            ConfigService reloaded = new ConfigService();
            reloaded.load();
            assertEquals("light", reloaded.getThemeId());
            assertEquals(Theme.LIGHT, Theme.fromId(reloaded.getThemeId()));
        } finally {
            if (previous == null) {
                System.clearProperty("cardgame.home");
            } else {
                System.setProperty("cardgame.home", previous);
            }
        }
    }
}
