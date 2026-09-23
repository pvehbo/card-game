package org.example.card.ai;

import org.example.card.engine.GameEngine;
import org.example.card.model.PlayerState;

import java.util.List;

/**
 * 只读对局快照（值对象，无活引用）：
 * 把 AI 决策需要的对局信息一次性拷成不可变快照，只留数字与名字，
 * 不暴露 Deck / PlayerState / Card 等可变实体——既防副作用，也方便换真 AI。
 *
 * 随从当前血量一律委托 {@link GameEngine#currentHealth} 计算（含宠物光环），
 * 本类不算光环、不重算规则。
 */
public record GameView(int selfLife, int selfHandSize, int foeLife, List<MinionInfo> foeMinions) {

    /** 敌方单个随从的快照：名字 + 当前血量（含宠物光环）。 */
    public record MinionInfo(String name, int currentHealth) {
    }

    /** 构造时拷贝成不可变列表，getter 拿不到活引用，也改不动快照。 */
    public GameView {
        foeMinions = List.copyOf(foeMinions);
    }

    /** 拍一张不可变快照；随从血量用 GameEngine.currentHealth（含光环），不在这里重算。 */
    public static GameView snapshot(PlayerState self, PlayerState foe) {
        List<MinionInfo> foeMinions = foe.getField().stream()
                .map(m -> new MinionInfo(m.getName(), GameEngine.currentHealth(foe, m)))
                .toList();
        return new GameView(self.getLifePoints(), self.getHand().size(), foe.getLifePoints(), foeMinions);
    }
}