package org.example.card.qa;

import org.example.card.ai.SimpleAi;
import org.example.card.data.MiniJson;
import org.example.card.engine.GameEngine;
import org.example.card.engine.GameSession;
import org.example.card.event.GameEvent;
import org.example.card.event.Json;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.service.ReplayRecorder;
import org.example.card.service.SaveService;
import org.example.card.ui.fx.Sfx;
import org.example.card.ui.fx.SoundEngine;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 独立对抗性验证（QA 严过关）：不复述工程师自述，主动尝试证伪 R-01/02/03/04/05/10
 * 与「引擎侧零 JavaFX」红线。任何一条不成立即为源码缺陷，回传工程师。
 */
class AdversarialVerificationTest {

    private static PlayerState playerOf(List<Card> cards) {
        return new PlayerState("测试", new Deck(cards));
    }

    private static MinionCard minion(String name, int atk, int hp) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp);
    }

    // ==================== R-01 负攻击力 ====================

    /** 负攻随从直击「非满血」英雄：不得反向回血。 */
    @Test
    void negativeAttackFaceHitDoesNotHealAnAlreadyWoundedHero() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("弱攻", 0, 3);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        self.getPets().add(new PetCard("p-neg", "弱化兽", "光环", -3, 0)); // 有效攻击 = -3
        foe.damage(10);                                                   // 英雄被打到 10 血

        assertTrue(engine.attack(self, foe, attacker, null, new ArrayList<String>()::add));
        assertEquals(10, foe.getLifePoints(), "负攻直击非满血英雄不得回血");
    }

    /**
     * 负攻随从互撞：对方随从不得回血（MinionCard.takeDamage 有 max(0, amount)）。
     * 这是 R-01 修复「只改打脸分支」后必须确认没被改坏的另一条路径。
     */
    @Test
    void negativeAttackMinionVsMinionDoesNotHealDefender() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("弱攻", 0, 3);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        self.getPets().add(new PetCard("p-neg", "弱化兽", "光环", -3, 0)); // 有效攻击 = -3
        MinionCard defender = minion("守", 2, 5);
        defender.takeDamage(2);                                           // 已受 2 伤
        foe.getField().add(defender);

        assertTrue(engine.attack(self, foe, attacker, defender, new ArrayList<String>()::add));

        assertEquals(2, defender.getDamageTaken(), "负攻互撞不得让受方随从回血");
        assertEquals(PlayerState.START_LIFE, foe.getLifePoints(), "互撞不打英雄");
    }

    /** 双向钳制边界：过量伤害/治疗、负伤害、负治疗都不越界。 */
    @Test
    void heroLifeClampBoundaries() {
        PlayerState p = playerOf(new ArrayList<>());
        p.damage(Integer.MAX_VALUE);
        assertEquals(0, p.getLifePoints(), "过量伤害钳到 0");
        p.heal(999);
        assertEquals(PlayerState.START_LIFE, p.getLifePoints(), "过量治疗钳到上限");
        p.damage(-5);
        assertEquals(PlayerState.START_LIFE, p.getLifePoints(), "负伤害视为 0");
        p.damage(8);
        assertEquals(PlayerState.START_LIFE - 8, p.getLifePoints());
        p.damage(-5);
        assertEquals(PlayerState.START_LIFE - 8, p.getLifePoints(), "负伤害在中段也不回血");
        p.heal(-3);
        assertEquals(PlayerState.START_LIFE - 8, p.getLifePoints(), "负治疗视为 0");
    }

    // ==================== R-02 读档保留召唤失调 / 已出手 ====================

    private static GameSession sessionWithMinion(int attacksUsed, boolean sickness) {
        GameSession session = GameSession.newPveBattle(new Random(11));
        GameEngine engine = new GameEngine(new SimpleAi());
        List<String> logs = new ArrayList<>();
        engine.startPlayerTurn(session.getPlayer(), session.getAi(), logs::add);
        for (var card : new ArrayList<>(session.getPlayer().getHand())) {
            if (card instanceof MinionCard m) {
                engine.playMinion(session.getPlayer(), m, logs::add);
                break;
            }
        }
        MinionCard played = session.getPlayer().getField().get(0);
        played.setSummoningSickness(sickness);
        for (int i = 0; i < attacksUsed; i++) {
            played.registerAttack();
        }
        return session;
    }

    /** 非默认状态（sickness=false / attacksUsed=2）经存档往返必须逐字保留。 */
    @Test
    void saveLoadPreservesNonDefaultSicknessAndAttacks() {
        GameSession session = sessionWithMinion(2, false);
        GameSession restored = SaveService.load(SaveService.save(session));
        MinionCard before = session.getPlayer().getField().get(0);
        MinionCard after = restored.getPlayer().getField().get(0);
        assertFalse(after.isSummoningSickness(), "sickness=false 必须保留");
        assertEquals(2, after.getAttacksUsed(), "attacksUsed=2 必须保留");
        assertEquals(before.getDamageTaken(), after.getDamageTaken());
    }

    /** 召唤失调仍为 true 的随从也必须保留（默认值恰好一致，反向确认写读一致）。 */
    @Test
    void saveLoadPreservesSicknessTrueAndZeroAttacks() {
        GameSession session = sessionWithMinion(0, true);
        GameSession restored = SaveService.load(SaveService.save(session));
        MinionCard after = restored.getPlayer().getField().get(0);
        assertTrue(after.isSummoningSickness(), "sickness=true 必须保留");
        assertEquals(0, after.getAttacksUsed());
    }

    /** 老存档：手工删掉 sickness/attacksUsed 两个片段后仍可读，且默认「可攻击 / 未出手」。 */
    @Test
    void legacySaveWithoutNewFieldsLoadsWithDefaults() {
        GameSession session = sessionWithMinion(2, false);
        String legacy = SaveService.save(session)
                .replaceAll(",\"sickness\":\"(true|false)\"", "")
                .replaceAll(",\"attacksUsed\":\\d+", "");
        assertFalse(legacy.contains("sickness"), "构造的老存档应已无 sickness 字段");
        assertFalse(legacy.contains("attacksUsed"), "构造的老存档应已无 attacksUsed 字段");

        GameSession restored = SaveService.load(legacy);
        MinionCard after = restored.getPlayer().getField().get(0);
        assertFalse(after.isSummoningSickness(), "缺字段 → 默认可攻击");
        assertEquals(0, after.getAttacksUsed(), "缺字段 → 默认未出手");
    }

    /** 回放文件（同一快照格式）经磁盘往返也能读，且保留随从状态。 */
    @Test
    void replayFileRoundTripPreservesMinionState() throws Exception {
        GameSession session = sessionWithMinion(1, false);
        ReplayRecorder recorder = new ReplayRecorder();
        recorder.captureTurn(session);

        Path tmp = Files.createTempFile("qa-replay", ".txt");
        try {
            recorder.saveToFile(tmp);
            ReplayRecorder loaded = ReplayRecorder.loadFromFile(tmp);
            assertEquals(1, loaded.turns(), "回放文件应含 1 个回合快照");
            GameSession fromFile = loaded.snapshot(0);
            MinionCard after = fromFile.getPlayer().getField().get(0);
            assertFalse(after.isSummoningSickness(), "回放快照读回后召唤失调状态保留");
            assertEquals(1, after.getAttacksUsed(), "回放快照读回后已出手次数保留");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ==================== R-03 订阅去重 ====================

    /** 同一引擎 attach 100 次，订阅者数量必须恒定为 2（DRAW + any），不得增长。 */
    @Test
    void hundredSameEngineAttachesDoNotLeakSubscribers() {
        GameSession session = GameSession.newPveBattle(new Random(3));
        GameEngine engine = new GameEngine(new SimpleAi());
        org.example.card.ui.viewmodel.BoardViewModel vm =
                new org.example.card.ui.viewmodel.BoardViewModel();
        for (int i = 0; i < 100; i++) {
            vm.attach(session, engine);
        }
        assertEquals(2, engine.eventBus().subscriberCount(),
                "同一引擎反复 attach 后订阅者应恒为 2，不得线性增长");
    }

    // ==================== R-05 抽牌管线与文案 ====================

    /** drawPhase 文案逐字保留：正常抽牌 "抽卡：+"。 */
    @Test
    void drawPhaseKeepsDrawText() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState ai = playerOf(new ArrayList<>(List.of(minion("牌1", 2, 2))));
        List<String> logs = new ArrayList<>();
        engine.beginAiTurn(ai, logs::add);
        assertTrue(logs.stream().anyMatch(l -> l.startsWith("抽卡：+")),
                "drawPhase 正常抽牌文案应为 抽卡：+…，实际日志=" + logs);
    }

    /** drawPhase 文案逐字保留：牌堆空 "牌堆已空，无牌可抽"。 */
    @Test
    void drawPhaseKeepsEmptyDeckText() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState ai = playerOf(new ArrayList<>());
        List<String> logs = new ArrayList<>();
        engine.beginAiTurn(ai, logs::add);
        assertTrue(logs.contains("牌堆已空，无牌可抽"),
                "空堆文案应为 牌堆已空，无牌可抽，实际日志=" + logs);
    }

    /** drawPhase 文案逐字保留：满手 "手牌已满，烧掉：…"。 */
    @Test
    void drawPhaseKeepsBurnText() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState ai = playerOf(new ArrayList<>(List.of(minion("牌1", 2, 2))));
        for (int i = 0; i < PlayerState.MAX_HAND; i++) {
            ai.getHand().add(minion("占位" + i, 1, 1));
        }
        List<String> logs = new ArrayList<>();
        engine.beginAiTurn(ai, logs::add);
        assertTrue(logs.stream().anyMatch(l -> l.startsWith("手牌已满，烧掉：")),
                "满手文案应为 手牌已满，烧掉：…，实际日志=" + logs);
        assertEquals(PlayerState.MAX_HAND, ai.getHand().size(), "手牌不得超上限");
    }

    // ==================== R-10 JSON 转义闭环 ====================

    /** Json.escape 产出 → Json.Reader 读回：含 退格/换页/unicode 转义 往返无损。 */
    @Test
    void jsonQuoteReaderRoundTripWithControlChars() {
        String s = "退格\b换页\f引号\"反斜杠\\换行\n制表\t回车\rC0\u0001末";
        String quoted = Json.quote(s);
        for (int i = 0; i < quoted.length(); i++) {
            assertTrue(quoted.charAt(i) >= 0x20, "JSON 里不得出现裸控制字符（位置 " + i + "）");
        }
        String back = new Json.Reader(quoted).string();
        assertEquals(s, back, "Json 写读往返必须无损");
    }

    /** SaveService.quote（写入端）产出 → MiniJson（读取端）读回：闭环无损。 */
    @Test
    void saveServiceQuoteClosedLoopWithMiniJson() throws Exception {
        Method quote = SaveService.class.getDeclaredMethod("quote", String.class);
        quote.setAccessible(true);
        String s = "退格\b换页\f引号\"反斜杠\\C0\u0002末";
        String quoted = (String) quote.invoke(null, s);
        for (int i = 0; i < quoted.length(); i++) {
            assertTrue(quoted.charAt(i) >= 0x20, "quote 产出不得含裸控制字符（位置 " + i + "）");
        }
        String back = MiniJson.parse(quoted, "qa").asString().value();
        assertEquals(s, back, "SaveService.quote 的输出必须能被 MiniJson 读回（闭环）");
    }

    /** MiniJson 读取端对称：能解 退格/换页/unicode 转义。 */
    @Test
    void miniJsonDecodesControlEscapes() {
        String back = MiniJson.parse("\"A\\bB\\fC\\u0008D\"", "qa").asString().value();
        assertEquals("A\bB\fC\bD", back);
    }

    // ==================== R-04 SoundEngine ====================

    /** play() 在池未预热时不得阻塞调用线程（绝不在此线程合成）。 */
    @Test
    void playDoesNotBlockWhenPoolIsCold() {
        SoundEngine.close(); // 清空池，模拟未预热
        long start = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            for (Sfx sfx : Sfx.values()) {
                SoundEngine.play(sfx);
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        assumeTrue(true);
        assertTrue(ms < 3000, "冷池下 play() 5000 次应在 3 秒内返回，实际 " + ms + "ms（疑似调用线程合成阻塞）");
    }

    /** close() 幂等：连续调用不抛异常。 */
    @Test
    void closeIsIdempotent() {
        SoundEngine.close();
        SoundEngine.close();
        SoundEngine.close();
    }

    // ==================== 红线：引擎侧零 JavaFX 扫描有效性 ====================

    private static boolean bytecodeContainsJavafx(Class<?> type) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertNotNull(in, "找不到字节码：" + resource);
            String pool = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            return pool.contains("javafx/");
        }
    }

    /**
     * 证明 HeadlessGameTest 的字节码扫描不是空转：拿一个确定引用 JavaFX 的 UI 类，
     * 同一套扫描逻辑必须判为「含 javafx/」。若扫描失效，这里会假绿。
     * 同时确认 event.Json / Json.Reader 确实干净（即红线真实覆盖到它们）。
     */
    @Test
    void redLineScanIsEffectiveAndCoversJson() throws Exception {
        assertTrue(bytecodeContainsJavafx(org.example.card.ui.CardGameApp.class),
                "扫描逻辑必须能检出 javafx 引用，否则 HeadlessGameTest 红线是假绿");
        assertFalse(bytecodeContainsJavafx(Json.class), "event.Json 不得引用 JavaFX");
        assertFalse(bytecodeContainsJavafx(Json.Reader.class), "event.Json.Reader 不得引用 JavaFX");
    }

    /** 事件 JSON 往返仍走 event.Json（迁移后 GameEvent 无 net 依赖）。 */
    @Test
    void gameEventJsonStillRoundTrips() {
        GameEvent event = GameEvent.of(GameEvent.Type.BURN, "烧\b牌\f");
        GameEvent.JsonData parsed = GameEvent.fromJson(event.toJson());
        assertEquals("烧\b牌\f", parsed.message());
        assertEquals(GameEvent.Type.BURN, parsed.type());
    }
}
