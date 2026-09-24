package org.example.card.ai;

import java.util.function.Supplier;

/**
 * AI 难度（B-4）：简单随机 / 普通贪心 / 困难斩杀，一局内可切换。
 */
public enum AiLevel {
    EASY("简单", RandomAi::new),
    NORMAL("普通", SimpleAi::new),
    HARD("困难", HardAi::new);

    private final String label;
    private final Supplier<AiStrategy> factory;

    AiLevel(String label, Supplier<AiStrategy> factory) {
        this.label = label;
        this.factory = factory;
    }

    public String label() {
        return label;
    }

    public AiStrategy newAi() {
        return factory.get();
    }

    public static AiLevel fromId(String id) {
        if (id == null) {
            return NORMAL;
        }
        return switch (id.trim().toLowerCase()) {
            case "easy" -> EASY;
            case "hard" -> HARD;
            default -> NORMAL;
        };
    }
}
