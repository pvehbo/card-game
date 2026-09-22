package org.example.card.engine;

import org.example.card.ai.SimpleAi;
import org.example.card.event.GameEvent;
import org.example.card.event.GameEventBus;
import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 回合引擎（炉石式 v1，无费规则）。
 *
 * 两种驱动方式：
 * - AI 自动回合：playTurn() 一次跑完；界面想逐动作演出时改为
 *   beginAiTurn() → aiSummon()/aiSpell()/aiPet() → aiStrike() → endAiTurn()；
 * - 玩家手动回合：startPlayerTurn() 开局，playMinion()/playSpell()/playPet()/attack()
 *   逐操作执行，endPlayerTurn() 收尾。
 *
 * 规则：
 * 1. DRAW：抽 1 张；手牌满 10 张则烧掉；牌堆空则无事发生；
 * 2. MAIN：限打 1 张随从（场上未满 7 格）+ 1 张法术 + 1 只宠物；
 * 3. BATTLE：无召唤失调的己方随从可攻击——打在对方随从上（双方同时受伤，
 *    血量归零者阵亡）或直击敌方英雄；
 * 4. END：己方随从解除召唤失调。
 */
public class GameEngine {

    private final SimpleAi ai;
    private final GameEventBus eventBus = new GameEventBus();
    private int turn;

    public GameEngine(SimpleAi ai) {
        this.ai = ai;
    }

    /** 事件总线：UI 订阅它做动效与刷新，引擎不关心谁在听。 */
    public GameEventBus eventBus() {
        return eventBus;
    }

    public int getTurn() {
        return turn;
    }

    /** 宠物光环叠加后的随从攻击。 */
    public static int effectiveAttack(PlayerState owner, MinionCard minion) {
        int bonus = owner.getPets().stream().mapToInt(PetCard::getAttackBonus).sum();
        return minion.getAttack() + bonus;
    }

    /** 宠物光环叠加后的随从血量上限。 */
    public static int effectiveMaxHealth(PlayerState owner, MinionCard minion) {
        int bonus = owner.getPets().stream().mapToInt(PetCard::getHealthBonus).sum();
        return minion.getMaxHealth() + bonus;
    }

    /** 随从当前血量 = 光环上限 - 已受伤害。 */
    public static int currentHealth(PlayerState owner, MinionCard minion) {
        return effectiveMaxHealth(owner, minion) - minion.getDamageTaken();
    }

    // ============ AI 自动回合 ============

    /**
     * AI 打完整的一回合（抽卡→出牌→战斗→结束）。
     * 内部由下面这些可单步调用的方法组合而成，界面想「一个动作一个动作地演」时
     * 可以跳过本方法，直接按 beginAiTurn → aiSummon/aiSpell/aiPet → aiStrike → endAiTurn 驱动。
     */
    public void playTurn(PlayerState self, PlayerState foe, Consumer<String> log) {
        beginAiTurn(self, log);
        aiSummon(self, log);
        aiSpell(self, foe, log);
        aiPet(self, log);
        for (MinionCard attacker : aiReadyAttackers(self)) {
            if (foe.isDefeated()) {
                return;
            }
            aiStrike(self, foe, attacker, log);
        }
        endAiTurn(self, foe, log);
    }

    /** AI 回合起手：回合数 +1、重置出牌次数、发布 TURN_START、抽 1 张。 */
    public void beginAiTurn(PlayerState self, Consumer<String> log) {
        turn++;
        String msg = "—— 第 " + turn + " 回合：" + self.getName() + "（AI）——";
        log.accept(msg);
        self.resetTurnFlags();
        eventBus.publish(GameEvent.turn(GameEvent.Type.TURN_START, self, msg));
        drawPhase(self, log);
    }

    /** AI 回合收尾：解除召唤失调、发布 TURN_END。 */
    public void endAiTurn(PlayerState self, PlayerState foe, Consumer<String> log) {
        endPhase(self, foe, log);
        String endMsg = "回合结束：" + self + " | " + foe;
        log.accept(endMsg);
        eventBus.publish(GameEvent.turn(GameEvent.Type.TURN_END, self, endMsg));
    }

    /** AI 上场 1 张随从。返回是否真的上了场。 */
    public boolean aiSummon(PlayerState self, Consumer<String> log) {
        if (self.isMinionPlayed()) {
            return false;
        }
        if (self.getField().size() >= PlayerState.MAX_FIELD) {
            log.accept("场上已满 7 格，本回合【AI】不上随从");
            return false;
        }
        Optional<MinionCard> summon = ai.chooseMinion(self.getHand());
        if (summon.isEmpty()) {
            return false;
        }
        MinionCard minion = summon.get();
        self.getHand().remove(minion);
        self.getField().add(minion);
        self.setMinionPlayed(true);
        String msg = "上场随从：" + minion;
        log.accept(msg);
        eventBus.publish(GameEvent.summon(self, minion, msg));
        return true;
    }

    /** AI 打出 1 张法术。返回是否真的打出。 */
    public boolean aiSpell(PlayerState self, PlayerState foe, Consumer<String> log) {
        if (!canPlaySpell(self)) {
            return false;
        }
        Optional<SpellCard> spell = ai.chooseSpell(self.getHand(), self, foe);
        if (spell.isEmpty()) {
            return false;
        }
        SpellCard card = spell.get();
        self.getHand().remove(card);
        self.setSpellPlayed(true);
        resolveSpell(card, self, foe, log);
        self.getGraveyard().add(card);
        return true;
    }

    /** AI 召唤 1 只宠物（常驻光环）。返回是否真的召唤。 */
    public boolean aiPet(PlayerState self, Consumer<String> log) {
        if (!canPlayPet(self)) {
            return false;
        }
        Optional<PetCard> pet = ai.choosePet(self.getHand());
        if (pet.isEmpty()) {
            return false;
        }
        PetCard card = pet.get();
        self.getHand().remove(card);
        self.getPets().add(card);
        self.setPetPlayed(true);
        String msg = "召唤宠物：" + card + "（常驻光环）";
        log.accept(msg);
        eventBus.publish(GameEvent.pet(self, card, msg));
        return true;
    }

    /**
     * 本回合可以出手的 AI 随从快照（排除召唤失调与本回合已出手过的）。
     * 之所以返回快照，是因为界面要一个动作一个动作地演，中途随从可能阵亡。
     */
    public List<MinionCard> aiReadyAttackers(PlayerState self) {
        List<MinionCard> ready = new ArrayList<>();
        for (MinionCard m : self.getField()) {
            if (!m.isSummoningSickness() && !m.isAttackedThisTurn()) {
                ready.add(m);
            }
        }
        return ready;
    }

    /** AI 让指定随从出手一次（自行选择目标）。返回是否真的打出了这一击。 */
    public boolean aiStrike(PlayerState self, PlayerState foe, MinionCard attacker, Consumer<String> log) {
        return aiStrike(self, foe, attacker, chooseAiTarget(self, foe).orElse(null), log);
    }

    /**
     * 指定目标的出手。
     * 界面先问 {@link #chooseAiTarget} 拿到目标、把突刺动画演给玩家看，再调用本方法结算。
     */
    public boolean aiStrike(PlayerState self, PlayerState foe, MinionCard attacker,
                            MinionCard target, Consumer<String> log) {
        if (!self.getField().contains(attacker) || attacker.isSummoningSickness()
                || attacker.isAttackedThisTurn()) {
            return false;
        }
        if (target != null && !foe.getField().contains(target)) {
            return false;
        }
        performAttack(self, foe, attacker, target, log);
        attacker.setAttackedThisTurn(true);
        return true;
    }

    /** AI 这次会打谁：空场（Optional.empty）表示打脸。 */
    public Optional<MinionCard> chooseAiTarget(PlayerState self, PlayerState foe) {
        return ai.chooseAttackTarget(self, foe);
    }

    // ============ 玩家手动回合 ============

    /** 开始玩家回合：回合数 +1、重置出牌次数、抽 1 张。 */
    public void startPlayerTurn(PlayerState self, PlayerState foe, Consumer<String> log) {
        turn++;
        self.resetTurnFlags();
        String msg = "—— 第 " + turn + " 回合：你的回合 ——";
        log.accept(msg);
        eventBus.publish(GameEvent.turn(GameEvent.Type.TURN_START, self, msg));
        drawPhase(self, log);
        log.accept("你的状态：" + self + " | 对手：" + foe);
    }

    /** 本回合还能上随从、且场上未满 7 格。 */
    public boolean canPlayMinion(PlayerState self) {
        return !self.isMinionPlayed() && self.getField().size() < PlayerState.MAX_FIELD;
    }

    /** 玩家上场一张手牌随从。返回是否成功。 */
    public boolean playMinion(PlayerState self, MinionCard minion, Consumer<String> log) {
        if (!canPlayMinion(self) || !self.getHand().remove(minion)) {
            log.accept("无法上场：本回合已上过随从或场上已满 7 格");
            return false;
        }
        self.setMinionPlayed(true);
        self.getField().add(minion);
        String msg = "你上场了随从：" + minion;
        log.accept(msg);
        eventBus.publish(GameEvent.summon(self, minion, msg));
        return true;
    }

    /** 本回合还没打过法术。 */
    public boolean canPlaySpell(PlayerState self) {
        return !self.isSpellPlayed();
    }

    /** 玩家打出一张手牌法术并立即结算。返回是否成功。 */
    public boolean playSpell(PlayerState self, PlayerState foe, SpellCard spell, Consumer<String> log) {
        if (!canPlaySpell(self) || !self.getHand().remove(spell)) {
            log.accept("无法打出：本回合已用过法术");
            return false;
        }
        self.setSpellPlayed(true);
        resolveSpell(spell, self, foe, log);
        self.getGraveyard().add(spell);
        return true;
    }

    /** 本回合还没召唤过宠物。 */
    public boolean canPlayPet(PlayerState self) {
        return !self.isPetPlayed();
    }

    /** 玩家召唤一只手牌宠物（常驻光环）。返回是否成功。 */
    public boolean playPet(PlayerState self, PetCard pet, Consumer<String> log) {
        if (!canPlayPet(self) || !self.getHand().remove(pet)) {
            log.accept("无法召唤：本回合已召唤过宠物");
            return false;
        }
        self.setPetPlayed(true);
        self.getPets().add(pet);
        String msg = "你召唤了宠物：" + pet + "（常驻光环）";
        log.accept(msg);
        eventBus.publish(GameEvent.pet(self, pet, msg));
        return true;
    }

    /**
     * 玩家让己方随从攻击：target 为 null 时直击敌方英雄。
     * 受召唤失调限制。返回是否成功。
     */
    public boolean attack(PlayerState self, PlayerState foe, MinionCard attacker,
                          MinionCard target, Consumer<String> log) {
        if (!self.getField().contains(attacker)) {
            log.accept("该随从已不在场上");
            return false;
        }
        if (attacker.isSummoningSickness()) {
            log.accept(attacker.getName() + " 召唤失调，本回合还不能攻击");
            return false;
        }
        if (attacker.isAttackedThisTurn()) {
            log.accept(attacker.getName() + " 本回合已经攻击过了");
            return false;
        }
        if (target != null && !foe.getField().contains(target)) {
            log.accept("攻击目标已不在场上");
            return false;
        }
        performAttack(self, foe, attacker, target, log);
        attacker.setAttackedThisTurn(true);
        return true;
    }

    /** 玩家结束回合：解除召唤失调，打印战况。 */
    public void endPlayerTurn(PlayerState self, PlayerState foe, Consumer<String> log) {
        endPhase(self, foe, log);
        String msg = "回合结束：" + self + " | " + foe;
        log.accept(msg);
        eventBus.publish(GameEvent.turn(GameEvent.Type.TURN_END, self, msg));
    }

    // ============ 内部实现 ============

    private void drawPhase(PlayerState self, Consumer<String> log) {
        self.getDeck().draw().ifPresentOrElse(
                card -> {
                    if (self.getHand().size() >= PlayerState.MAX_HAND) {
                        self.getGraveyard().add(card);
                        String msg = "手牌已满，烧掉：" + card;
                        log.accept(msg);
                        eventBus.publish(GameEvent.of(GameEvent.Type.BURN, msg));
                    } else {
                        self.getHand().add(card);
                        String msg = "抽卡：+" + card;
                        log.accept(msg);
                        eventBus.publish(GameEvent.draw(self, card, msg));
                    }
                },
                () -> log.accept("牌堆已空，无牌可抽"));
    }

    private void resolveSpell(SpellCard card, PlayerState self, PlayerState foe, Consumer<String> log) {
        switch (card.getKind()) {
            case DAMAGE -> {
                foe.damage(card.getAmount());
                String msg = "法术：" + card.getName() + " 对敌方英雄 -" + card.getAmount();
                log.accept(msg);
                eventBus.publish(GameEvent.spell(self, card, card.getAmount(), msg));
                eventBus.publish(GameEvent.damage(foe, card.getAmount(), msg));
            }
            case HEAL -> {
                self.heal(card.getAmount());
                String msg = "法术：" + card.getName() + " 回复己方英雄 +" + card.getAmount();
                log.accept(msg);
                eventBus.publish(GameEvent.spell(self, card, card.getAmount(), msg));
            }
            case DRAW -> {
                for (int i = 0; i < card.getAmount(); i++) {
                    self.getDeck().draw().ifPresentOrElse(
                            drawn -> {
                                if (self.getHand().size() >= PlayerState.MAX_HAND) {
                                    self.getGraveyard().add(drawn);
                                } else {
                                    self.getHand().add(drawn);
                                }
                            },
                            () -> {
                            });
                }
                String msg = "法术：" + card.getName() + " 抽 " + card.getAmount() + " 张牌";
                log.accept(msg);
                eventBus.publish(GameEvent.spell(self, card, card.getAmount(), msg));
            }
        }
    }

    /** 一次攻击/互撞的结算。 */
    private void performAttack(PlayerState self, PlayerState foe, MinionCard attacker,
                               MinionCard target, Consumer<String> log) {
        int atk = effectiveAttack(self, attacker);
        if (target == null) {
            foe.damage(atk);
            String msg = attacker.getName() + " 直击敌方英雄 -" + atk;
            log.accept(msg);
            eventBus.publish(GameEvent.attack(self, foe, attacker, null, atk, msg));
            eventBus.publish(GameEvent.damage(foe, atk, msg));
        } else {
            int defAtk = effectiveAttack(foe, target);
            target.takeDamage(atk);
            attacker.takeDamage(defAtk);
            String msg = attacker.getName() + "(" + atk + ") 与 "
                    + target.getName() + "(" + defAtk + ") 互撞";
            log.accept(msg);
            eventBus.publish(GameEvent.attack(self, foe, attacker, target, atk, msg));
            // 随从受伤：带上受害者随从，界面才能把飘字/粒子锚在随从身上
            eventBus.publish(GameEvent.damage(foe, target, atk, msg));
            eventBus.publish(GameEvent.damage(self, attacker, defAtk, msg));
            removeDead(foe, target, log);
            if (currentHealth(self, attacker) <= 0) {
                removeDead(self, attacker, log);
            }
        }
        if (foe.isDefeated()) {
            String msg = foe.getName() + " 生命归零！";
            log.accept(msg);
            eventBus.publish(GameEvent.gameOver(self, msg));
        }
    }

    private void removeDead(PlayerState owner, MinionCard minion, Consumer<String> log) {
        if (currentHealth(owner, minion) <= 0 && owner.getField().remove(minion)) {
            owner.getGraveyard().add(minion);
            String msg = minion.getName() + " 阵亡";
            log.accept(msg);
            eventBus.publish(GameEvent.death(owner, minion, msg));
        }
    }

    /** 己方回合结束：解除召唤失调，并清掉「本回合已攻击」标记（下回合才能再出手）。 */
    private void endPhase(PlayerState self, PlayerState foe, Consumer<String> log) {
        for (MinionCard minion : self.getField()) {
            minion.setSummoningSickness(false);
            minion.setAttackedThisTurn(false);
        }
    }
}