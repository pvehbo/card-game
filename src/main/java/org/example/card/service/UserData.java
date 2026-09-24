package org.example.card.service;

import java.nio.file.Path;

/**
 * 用户数据目录（B-6）：默认 ~/.cardgame，诊断可用 -Dcardgame.home 覆盖
 * （受限环境家目录不可写时指到别处）。
 */
public final class UserData {

    private UserData() {
    }

    public static Path dir() {
        String override = System.getProperty("cardgame.home");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".cardgame");
    }

    public static Path file(String name) {
        return dir().resolve(name);
    }
}
