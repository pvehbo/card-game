package org.example.card.effect;

import org.example.card.model.Keyword;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 效果注册表：关键词 → 实现。法术三结算的前 3 个实现常驻，其余机制按需注册。 */
public final class EffectRegistry {

    private static final Map<Keyword, Effect> EFFECTS = new EnumMap<>(Keyword.class);

    static {
        register(new DamageEffect());
        register(new HealEffect());
        register(new DrawEffect());
    }

    private EffectRegistry() {
    }

    /** 注册新效果；重复注册直接 fail-fast（避免结算歧义）。 */
    public static void register(Effect effect) {
        Keyword key = effect.keyword();
        if (EFFECTS.containsKey(key)) {
            throw new IllegalStateException("重复注册效果: " + key);
        }
        EFFECTS.put(key, effect);
    }

    /** 查注册表结算一张法术；未注册 fail-fast（宁可启动/开局炸，不让对局中途行为未定义）。 */
    public static EffectResult resolve(SpellCard card, PlayerState self, PlayerState foe) {
        Keyword key;
        try {
            key = Keyword.valueOf(card.getKind().name());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("未注册的法术效果: " + card.getKind(), ex);
        }
        Effect effect = EFFECTS.get(key);
        if (effect == null) {
            throw new IllegalStateException("未注册的法术效果: " + card.getKind());
        }
        GameContext ctx = new GameContext(self, foe, card, null);
        if (!effect.appliesTo(ctx)) {
            return EffectResult.empty();
        }
        return effect.apply(ctx);
    }

    /** 已注册的效果（只读，供体检/文档用）。 */
    public static List<Effect> registered() {
        return List.copyOf(EFFECTS.values());
    }
}
