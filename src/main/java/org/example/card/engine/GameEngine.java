package org.example.card.engine;

import org.example.card.ai.AiStrategy;
import org.example.card.ai.GameView;
import org.example.card.ai.Target;
import org.example.card.effect.DrawEffect;
import org.example.card.effect.EffectResult;
import org.example.card.effect.GameContext;
import org.example.card.effect.TriggerSystem;
import org.example.card.data.Ruleset;
import org.example.card.model.Keyword;
import org.example.card.event.GameEvent;
import org.example.card.event.GameEventBus;
import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
 * 1. DRAW：抽 1 张；手牌满 10 张则烧掉；牌堆空则无事发生（标准模式：递增疲劳伤害）；
 * 2. MAIN：限打 1 张随从（场上未满 7 格）+ 1 张法术 + 1 只宠物；
 * 3. BATTLE：无召唤失调的己方随从可攻击——打在对方随从上（双方同时受伤，
 *    血量归零者阵亡）或直击敌方英雄；
 * 4. END：己方随从解除召唤失调。
 */
public class GameEngine {

    private AiStrategy ai;
    private final GameEventBus eventBus = new GameEventBus();
    private int turn;
    /** 规则集（批3）：标准 = 宠物 1 槽可替换、牌库见底递增疲劳；经典 = 历史行为。 */
    private Ruleset ruleset = Ruleset.CLASSIC;

    public GameEngine(AiStrategy ai) {
        this.ai = ai;
    }

    /** 规则集版（批3）：标准模式的结构规则（宠物槽/疲劳）由规则集驱动。 */
    public GameEngine(AiStrategy ai, Ruleset ruleset) {
        this.ai = ai;
        this.ruleset = ruleset == null ? Ruleset.CLASSIC : ruleset;
    }

    /** 本局规则集（只读）。 */
    public Ruleset ruleset() {
        return ruleset;
    }

    /** 切换 AI 策略（一局内可换，难度选择用）。 */
    public void setAiStrategy(AiStrategy ai) {
        this.ai = ai;
    }

    /** 事件总线：UI 订阅它做动效与刷新，引擎不关心谁在听。 */
    public GameEventBus eventBus() {
        return eventBus;
    }

    public int getTurn() {
        return turn;
    }

    // ============ AI 自动回合 ============

    /**
     * AI 打完整的一回合（抽卡→出牌→战斗→结束）。
     * 内部由下面这些可单步调用的方法组合而成，界面想「一个动作一个动作地演」时
     * 可以跳过本方法，直接按 beginAiTurn → aiSummon/aiSpell/aiPet → aiStrike → endAiTurn 驱动。
     */
    public void playTurn(PlayerState self, PlayerState foe, Consumer<String> log) {
        beginAiTurn(self, log);
        aiSummon(self, foe, log);
        aiSpell(self, foe, log);
        aiPet(self, log);
        for (MinionCard attacker : aiReadyAttackers(self)) {
            // 风怒 2 击：出手后重新判定是否还能出手；随从阵亡/对方倒下/被拒即止
            boolean proceeds = true;
            while (proceeds
                    && !foe.isDefeated()
                    && self.getField().contains(attacker)
                    && ActionValidator.isReadyToAttack(attacker)) {
                proceeds = aiStrike(self, foe, attacker, log);
            }
            if (foe.isDefeated()) {
                return;
            }
        }
        endAiTurn(self, foe, log);
    }

    /**
     * 后手补偿（B-后手补偿，张数走 Ruleset.secondMoveBonusDraws）：每张与 DRAW 一致
     * （满 10 张烧掉、空堆无事发生），并走 DRAW/BURN 事件。
     */
    public void grantSecondMoveBonus(PlayerState secondMover, Consumer<String> log) {
        grantSecondMoveBonus(secondMover, 1, log);
    }

    /** 可配张数版本：逐张走 drawPhase 管线；张数 0 时既不抽牌也不记日志。 */
    public void grantSecondMoveBonus(PlayerState secondMover, int draws, Consumer<String> log) {
        if (draws <= 0) {
            return;
        }
        log.accept(draws == 1 ? "后手补偿：后手多抽 1 张"
                : "后手补偿：后手多抽 " + draws + " 张");
        for (int i = 0; i < draws; i++) {
            drawPhase(secondMover, log);
        }
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

    /** AI 上场 1 张随从（触发器视域无敌方）。返回是否真的上了场。 */
    public boolean aiSummon(PlayerState self, Consumer<String> log) {
        return aiSummon(self, null, log);
    }

    /** AI 上场 1 张随从（带敌方视域）。返回是否真的上了场。 */
    public boolean aiSummon(PlayerState self, PlayerState foe, Consumer<String> log) {
        // 只在买得起的随从里选（买不起直接跳过，不占出牌次数）
        List<Card> affordable = self.getHand().stream()
                .filter(c -> !(c instanceof MinionCard) || self.canAfford(c.getCost()))
                .toList();
        Optional<MinionCard> summon = ai.chooseMinion(affordable);
        if (summon.isEmpty()) {
            return false;
        }
        MinionCard minion = summon.get();
        if (!ActionValidator.canPlay(self, minion)) {
            if (self.getField().size() >= PlayerState.MAX_FIELD) {
                log.accept("场上已满 7 格，本回合【AI】不上随从");
            }
            return false;
        }
        self.getHand().remove(minion);
        self.getField().add(minion);
        settleSummoned(minion, log);
        self.setMinionPlayed(true);
        self.spendMana(minion.getCost());
        String msg = "上场随从：" + minion;
        log.accept(msg);
        eventBus.publish(GameEvent.summon(self, minion, msg));
        fireSummonTriggers(self, foe, minion, log);
        return true;
    }

    /** AI 打出 1 张法术。返回是否真的打出。 */
    public boolean aiSpell(PlayerState self, PlayerState foe, Consumer<String> log) {
        if (!canPlaySpell(self)) {
            return false;
        }
        Optional<SpellCard> spell = ai.chooseSpell(GameView.snapshot(self, foe), self.getHand());
        if (spell.isEmpty()) {
            return false;
        }
        SpellCard card = spell.get();
        if (!ActionValidator.canAfford(self, card)) {
            return false;
        }
        self.getHand().remove(card);
        self.setSpellPlayed(true);
        self.spendMana(card.getCost());
        resolveSpell(card, self, foe, log);
        self.getGraveyard().add(card);
        return true;
    }

    /** AI 召唤 1 只宠物（常驻光环）。返回是否真的召唤。 */
    public boolean aiPet(PlayerState self, Consumer<String> log) {
        if (!canPlayPet(self)) {
            return false;
        }
        List<Card> affordable = self.getHand().stream()
                .filter(c -> !(c instanceof PetCard) || self.canAfford(c.getCost()))
                .toList();
        Optional<PetCard> pet = ai.choosePet(affordable);
        if (pet.isEmpty()) {
            return false;
        }
        PetCard card = pet.get();
        if (!ActionValidator.canAfford(self, card)) {
            return false;
        }
        self.getHand().remove(card);
        settlePetSlot(self, card, log);
        self.getPets().add(card);
        self.setPetPlayed(true);
        self.spendMana(card.getCost());
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
            if (ActionValidator.isReadyToAttack(m)) {
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
        if (!ActionValidator.canAttack(self, foe, attacker, target)) {
            return false;
        }
        performAttack(self, foe, attacker, target, log);
        attacker.registerAttack();
        return true;
    }

    /** AI 这次会打谁：空场（Optional.empty）表示打脸。目标血量由引擎算好，AI 只做偏好选择。 */
    public Optional<MinionCard> chooseAiTarget(PlayerState self, PlayerState foe) {
        GameView view = GameView.snapshot(self, foe);
        List<Target> legalTargets = ActionValidator.legalActions(self, foe).stream()
                .map(ActionValidator.Move::target)
                .filter(Objects::nonNull)
                .distinct()
                .map(t -> new Target(t, CombatResolver.currentHealth(foe, t)))
                .toList();
        return ai.chooseAttackTarget(view, legalTargets);
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
        return ActionValidator.canPlayMinion(self);
    }

    /** 打出这张牌的完整条件：次数限 + 费用（UI 置灰与结算共用）。 */
    public boolean canPlay(PlayerState self, Card card) {
        return ActionValidator.canPlay(self, card);
    }

    /** 玩家上场一张手牌随从（触发器视域无敌方）。返回是否成功。 */
    public boolean playMinion(PlayerState self, MinionCard minion, Consumer<String> log) {
        return playMinion(self, null, minion, log);
    }

    /** 玩家上场一张手牌随从（带敌方视域：战吼 spec DAMAGE 可打敌英雄）。返回是否成功。 */
    public boolean playMinion(PlayerState self, PlayerState foe, MinionCard minion,
                              Consumer<String> log) {
        Optional<String> reason = ActionValidator.playRejectReason(self, minion);
        if (reason.isPresent()) {
            log.accept(reason.get());
            return false;
        }
        if (!self.getHand().remove(minion)) {
            log.accept("无法上场：本回合已上过随从或场上已满 7 格");
            return false;
        }
        self.setMinionPlayed(true);
        self.spendMana(minion.getCost());
        self.getField().add(minion);
        settleSummoned(minion, log);
        String msg = "你上场了随从：" + minion;
        log.accept(msg);
        eventBus.publish(GameEvent.summon(self, minion, msg));
        fireSummonTriggers(self, foe, minion, log);
        return true;
    }

    /** 本回合还没打过法术。 */
    public boolean canPlaySpell(PlayerState self) {
        return ActionValidator.canPlaySpell(self);
    }

    /** 玩家打出一张手牌法术并立即结算。返回是否成功。 */
    public boolean playSpell(PlayerState self, PlayerState foe, SpellCard spell, Consumer<String> log) {
        Optional<String> reason = ActionValidator.playRejectReason(self, spell);
        if (reason.isPresent()) {
            log.accept(reason.get());
            return false;
        }
        if (!self.getHand().remove(spell)) {
            log.accept("无法打出：本回合已用过法术");
            return false;
        }
        self.setSpellPlayed(true);
        self.spendMana(spell.getCost());
        resolveSpell(spell, self, foe, log);
        self.getGraveyard().add(spell);
        return true;
    }

    /** 本回合还没召唤过宠物。 */
    public boolean canPlayPet(PlayerState self) {
        return ActionValidator.canPlayPet(self);
    }

    /** 玩家召唤一只手牌宠物（常驻光环）。返回是否成功。 */
    public boolean playPet(PlayerState self, PetCard pet, Consumer<String> log) {
        Optional<String> reason = ActionValidator.playRejectReason(self, pet);
        if (reason.isPresent()) {
            log.accept(reason.get());
            return false;
        }
        if (!self.getHand().remove(pet)) {
            log.accept("无法召唤：本回合已召唤过宠物");
            return false;
        }
        self.setPetPlayed(true);
        self.spendMana(pet.getCost());
        settlePetSlot(self, pet, log);
        self.getPets().add(pet);
        String msg = "你召唤了宠物：" + pet + "（常驻光环）";
        log.accept(msg);
        eventBus.publish(GameEvent.pet(self, pet, msg));
        return true;
    }

    /**
     * 宠物槽结算（批3标准规则）：宠物仅 1 槽——新宠入槽时旧宠离场（正常付费、无退费）。
     * 经典模式无槽位约束，保持可叠加常驻光环。
     */
    private void settlePetSlot(PlayerState self, PetCard incoming, Consumer<String> log) {
        if (ruleset != Ruleset.STANDARD || self.getPets().isEmpty()) {
            return;
        }
        PetCard outgoing = self.getPets().remove(self.getPets().size() - 1);
        String msg = "宠物替换：" + outgoing.getName() + " 离场，「" + incoming.getName() + "」进驻光环";
        log.accept(msg);
        eventBus.publish(GameEvent.pet(self, outgoing, msg));
    }

    /**
     * 玩家让己方随从攻击：target 为 null 时直击敌方英雄。
     * 受召唤失调限制。返回是否成功。
     */
    public boolean attack(PlayerState self, PlayerState foe, MinionCard attacker,
                          MinionCard target, Consumer<String> log) {
        Optional<String> reason = ActionValidator.rejectReason(self, foe, attacker, target);
        if (reason.isPresent()) {
            log.accept(reason.get());
            return false;
        }
        performAttack(self, foe, attacker, target, log);
        attacker.registerAttack();
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

    /**
     * 上场结算：冲锋随从立即解除召唤失调。
     * 关键词通用处理（非单卡特例），战吼/亡语走 TriggerSystem。
     */
    private void settleSummoned(MinionCard minion, Consumer<String> log) {
        if (minion.hasKeyword(Keyword.CHARGE) && minion.isSummoningSickness()) {
            minion.setSummoningSickness(false);
            log.accept(minion.getName() + " 有冲锋，本回合即可攻击");
        }
    }

    /**
     * 战吼派发：紧跟上场事件，触发消息同步记日志。
     * 上下文带敌方视域（spec DAMAGE 的战吼可打敌英雄）；老的无 foe overload 传 null。
     */
    private void fireSummonTriggers(PlayerState self, PlayerState foe, MinionCard minion,
                                    Consumer<String> log) {
        for (GameEvent e : TriggerSystem.fire(TriggerSystem.TriggerPoint.ON_SUMMON,
                new GameContext(self, foe, null, minion))) {
            log.accept(e.message());
            eventBus.publish(e);
        }
    }

    private void drawPhase(PlayerState self, Consumer<String> log) {
        // 抽一张走统一管线（满手烧牌发 BURN、否则进手牌发 DRAW、空堆返回 null）
        GameEvent drawn = DrawEffect.drawOne(self);
        if (drawn == null) {
            if (ruleset == Ruleset.STANDARD) {
                // 标准规则（确认③④）：牌库见底递增疲劳——第 N 次疲劳受 N 点伤害
                int damage = self.increaseFatigue();
                self.damage(damage);
                String msg = "疲劳：" + self.getName() + " 牌堆已空，受 " + damage + " 点疲劳伤害";
                log.accept(msg);
                eventBus.publish(GameEvent.damage(self, damage, msg));
            } else {
                log.accept("牌堆已空，无牌可抽");
            }
            return;
        }
        log.accept(drawn.message());
        eventBus.publish(drawn);
    }

    private void resolveSpell(SpellCard card, PlayerState self, PlayerState foe, Consumer<String> log) {
        EffectResult outcome = CombatResolver.resolveSpell(card, self, foe);
        outcome.logs().forEach(log);
        outcome.events().forEach(eventBus::publish);
    }

    /** 一次攻击/互撞的结算（数学与事件由 CombatResolver 产出，这里只负责发布）。 */
    private void performAttack(PlayerState self, PlayerState foe, MinionCard attacker,
                               MinionCard target, Consumer<String> log) {
        CombatResolver.Outcome outcome = CombatResolver.strike(self, foe, attacker, target);
        outcome.logs().forEach(log);
        outcome.events().forEach(eventBus::publish);
    }

    /** 己方回合结束：解除召唤失调，并清掉「本回合已攻击」标记（下回合才能再出手）。 */
    private void endPhase(PlayerState self, PlayerState foe, Consumer<String> log) {
        for (MinionCard minion : self.getField()) {
            minion.setSummoningSickness(false);
            minion.resetAttacks();
        }
    }
}