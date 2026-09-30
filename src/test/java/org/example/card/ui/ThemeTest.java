package org.example.card.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V3 双主题：枚举 ↔ 配置 id ↔ 样式表路径映射（无头可测，不起 Toolkit）。 */
class ThemeTest {

    @Test
    void idsAndLightFlags() {
        assertEquals("dark", Theme.DARK.id());
        assertEquals("light", Theme.LIGHT.id());
        assertFalse(Theme.DARK.isLight());
        assertTrue(Theme.LIGHT.isLight());
        assertNotEquals(Theme.DARK.cssPath(), Theme.LIGHT.cssPath());
    }

    @Test
    void toggleSwitchesBothWays() {
        assertEquals(Theme.LIGHT, Theme.DARK.toggle());
        assertEquals(Theme.DARK, Theme.LIGHT.toggle());
    }

    @Test
    void fromIdRoundTripAndFallback() {
        assertEquals(Theme.DARK, Theme.fromId("dark"));
        assertEquals(Theme.LIGHT, Theme.fromId("light"));
        assertEquals(Theme.DARK, Theme.fromId(null), "空 id 回退暗色");
        assertEquals(Theme.DARK, Theme.fromId(""), "空串回退暗色");
        assertEquals(Theme.DARK, Theme.fromId("no-such-theme"), "未知 id 回退暗色");
    }

    @Test
    void stylesheetsExistOnClasspath() {
        assertNotNull(Theme.class.getResource(Theme.DARK.cssPath()), "theme-dark.css 缺失");
        assertNotNull(Theme.class.getResource(Theme.LIGHT.cssPath()), "theme-light.css 缺失");
        assertNotNull(Theme.DARK.cssUrl());
        assertNotNull(Theme.LIGHT.cssUrl());
    }
}
