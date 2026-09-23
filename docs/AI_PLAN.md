# AI 可执行计划书（粘贴即开工）

> 用法：每次取**一个步骤**，把该步骤的「AI 提示词」全文粘贴给 AI（本项目助手、或其他 AI 编程 agent 均可），
> AI 做完后你只跑「验证命令」，绿了才进入下一步。不要一次贴多个步骤。
> 背景文档：`docs/PLAN.md`（架构思想）、`docs/BACKLOG.md`（全景清单）。

---

## 0. 全局上下文（每个提示词里已内置，AI 不需再问）

- 项目：`/Users/danghaobo/Java/test`，Java 17 + JavaFX 21.0.5 + Maven，炉石式 PvE 卡牌对战。
- 包根：`src/main/java/org/example/card/`，包：`model/`（6 类）`engine/`（`GameEngine` 416 行 + `Phase`）`ai/`（`SimpleAi`）`event/`（`GameEvent` record + `GameEventBus`）`ui/`（`CardGameApp` 1094 行 + 4 View + `fx/`）`ui/fx/`（`Fx/ParticleLayer/SoundEngine/Sfx`）。
- 测试：`src/test/java/org/example/card/engine/` 下 `GameEngineTest/AiStepTurnTest/BattleDemoTest/ManualTurnTest`。
- 常用命令（本机 `~/.m2` 不可写，一律加 `-Dmaven.repo.local=.m2repo`，与 `package.sh`/CI 一致）：
  - `mvn -Dmaven.repo.local=.m2repo test`
  - `mvn -Dmaven.repo.local=.m2repo javafx:run`
  - `mvn -o -Dmaven.repo.local=.m2repo dependency:build-classpath -Dmdep.outputFile=target/cp.txt`（一次即可）
  - `java -Dui.autoplay=10 -cp "target/classes:$(cat target/cp.txt)" org.example.card.ui.Main`
  - `java -Dui.screenshot=docs/check.png -cp "target/classes:$(cat target/cp.txt)" org.example.card.ui.Main`
- 当前玩法常量：英雄 20 血、手牌上限 10、战场 7 格、每回合限 1 随从+1 法术+1 宠物、召唤失调、每随从每回合攻 1 次。

## 铁律（所有步骤通用，违反即打回）

1. 先读后写：动手前必须用 read 读完步骤指定的文件，禁止凭印象改代码。
2. 小步提交：一步只做一件事，完工一个 `git commit`，信息格式 `refactor:/feat:/test: ...`。
3. 行为锁死：凡标“不改玩法”的步骤，diff 里不许出现伤害/血量/费用/回合数字变化，只允许搬家。
4. 内核零 JavaFX：`model/engine/ai/event/data/effect/service` 包下禁止出现 `import javafx.*`。
5. 测试先行：新类必须带新测试；改旧类必须先跑旧测试，红了先修再改。
6. 每步收尾三件套：`mvn test` 全绿 + `autoplay=10` 正常退出 + 无新增编译警告。

---

## S0 · 基线冻结（半天）

**AI 提示词（全文复制）：**

```
在 /Users/danghaobo/Java/test 做基线冻结：
1. 读 README.md、pom.xml，确认 JDK17+Maven 环境可用。
2. 跑 mvn test，把完整输出保存到 docs/baseline-test.log（用 mvn test > docs/baseline-test.log 2>&1；仍要在终端确认 BUILD SUCCESS）。
   日志含本机绝对路径，提交前必须脱敏：`sed -i '' "s|$HOME|~|g" docs/baseline-test.log docs/baseline-autoplay.log`。
3. 跑 mvn -o dependency:build-classpath -Dmdep.outputFile=target/cp.txt，然后
   java -Dui.autoplay=10 -cp "target/classes:$(cat target/cp.txt)" org.example.card.ui.Main，
   确认控制台输出 10 回合战报且进程正常退出，把战报尾 20 行贴到 docs/baseline-autoplay.log。
4. 跑 java -Dui.screenshot=docs/baseline.png -cp "target/classes:$(cat target/cp.txt)" org.example.card.ui.Main，
   确认 docs/baseline.png 生成且肉眼可见战场/手牌/双方头像。
5. 以上全过则 git add docs/baseline-test.log docs/baseline-autoplay.log docs/baseline.png && git commit -m "chore: freeze baseline" && git tag baseline-v1。
铁律：只许新增 docs/ 下 3 个文件，不许改 src/ 一行；任何命令失败就停下报告，不许跳过。
```

**验证**：`git show --stat HEAD` 只有 3 个 docs 文件；`git tag` 有 baseline-v1。

---

## S1 · 抽 ActionValidator（1 天，不改玩法）

**AI 提示词：**

```
在 /Users/danghaobo/Java/test 做重构（不改玩法）：
1. 先读：src/main/java/org/example/card/engine/GameEngine.java（全文）、model/PlayerState.java、
   model/MinionCard.java、ai/SimpleAi.java、全部 4 个测试。
2. 新建 engine/ActionValidator.java（final 工具类，零 JavaFX 引用），把 GameEngine 里所有“能不能”
   判断搬进去，至少：canPlayMinion/canPlaySpell/canPlayPet（次数+场满7格）、canAttack（召唤失调/
   本回合已出手/攻击者在场/目标在场）。再加：public static List<Move> legalActions(PlayerState self,
   PlayerState foe) 返回本方所有合法动作；Move 用 engine 包内小 record（含种类+攻击者+目标，
   target==null 表打脸）。public static Optional<String> rejectReason(...) 给界面提示用。
3. GameEngine 原判断方法保留签名、内部转调 ActionValidator（标 @Deprecated 的不动，只转调）。
4. SimpleAi 选目标改走 legalActions 过滤（行为保持：优先最低血随从、无随从打脸）。
5. 新增测试 engine/LegalActionsTest：召唤失调不可攻、已出手不可再攻、场满7格不可上、
   legalActions 与 canXxx 一致。四旧测试一行不许改。
6. 收尾三件套。
铁律：diff 不许出现数字变化；新文件禁 javafx import；测试先跑后改。
```

**验证**：`mvn test` 全绿；`git diff --stat` 只有新增 2 文件 + 转调行。

---

## S2 · 抽 CombatResolver（1 天，不改玩法）

**AI 提示词：**

```
在 /Users/danghaobo/Java/test 做重构（不改玩法，前置：S1 已合入）：
1. 先读：engine/GameEngine.java、engine/ActionValidator.java、event/GameEvent.java、
   event/GameEventBus.java、model/ 全部、BattleDemoTest/GameEngineTest。
2. 新建 engine/CombatResolver.java（final，纯函数：输入双方 PlayerState+动作，返回 List<GameEvent>，
   内部不 publish，由调用方 publish；零 JavaFX）。搬入互撞结算（含光环 effectiveAttack/
   effectiveMaxHealth/currentHealth 三个静态方法一并搬入）、直击结算、阵亡移除、法术三结算
   （DAMAGE/HEAL/DRAW）、胜负判定事件。GameEngine.performAttack/resolveSpell/removeDead 改为
   转调（@Deprecated 保留）。
3. 事件顺序必须与原来 publish 顺序逐条一致（对照原方法逐行搬）。
4. 不新增玩法测试，但要新增 engine/CombatEventOrderTest：一次互撞产出 ATTACK→DAMAGE(随从)→
   DAMAGE(随从)→DEATH? 顺序断言，锁定事件契约。
5. 四旧测试一行不许改；收尾三件套。
铁律：数值/顺序零变化；新文件禁 javafx。
```

**验证**：`mvn test` 全绿；`autoplay=10` 战报与 `docs/baseline-autoplay.log` 同语义（回合数/胜负一致）。

---

## S3 · 抽 TurnController + GameSession（1 天，不改玩法）

**AI 提示词：**

```
在 /Users/danghaobo/Java/test 做重构（不改玩法，前置：S1、S2）：
1. 先读：engine/GameEngine.java、engine/ActionValidator.java、engine/CombatResolver.java、
   ui/CardGameApp.java 开局与回合相关段（startNewGame/endYourTurn/AI 调度/gameGeneration）、model/Deck.java。
2. 新建 engine/GameSession.java：持 player/ai 双方 PlayerState、turn、currentSide、mode 枚举
   （PVE/LOCAL_PVP，先只实现 PVE）、gameGeneration（开局+1，旧 AI 回调凭代数丢弃）。
   新建 engine/TurnController.java：startPlayerTurn/beginAiTurn/endTurn 四阶段推进
   （DRAW→MAIN→BATTLE→END），抽牌烧牌规则原样搬（手牌满10烧、牌堆空无事）。
   CardGameApp 只保留接线（新局组装 Session→VM→View），回合推进全调 TurnController。
3. Deck 构造加 static standardDeck()（牌表先硬编码原样，后续 S5 才 JSON 化，本步不许改牌表内容）。
4. 旧测试不许改；新增 engine/SessionTest：先后手随机开局 20 次都有终局、gameGeneration+1 后旧回调丢弃。
5. 收尾三件套 + 截图与 docs/baseline.png 肉眼对比无回归。
铁律：不改牌表、不改数值；CardGameApp 只删不增逻辑。
```

**验证**：行数 `wc -l ui/CardGameApp.java` 下降；`mvn test` 全绿。

---

## S4 · Effect 注册表（1 天，加行为但老行为不变）

**AI 提示词：**

```
在 /Users/danghaobo/Java/test 做功能（前置：S2、S3）：
1. 先读：engine/CombatResolver.java、model/SpellCard.java、model/MinionCard.java、event/GameEvent.java。
2. 新建 effect/Effect.java（接口：String keyword(); boolean appliesTo(GameContext ctx);
   List<GameEvent> apply(GameContext ctx)）、effect/EffectRegistry.java（register/resolve，
   未注册 keyword 启动 fail-fast）、effect/GameContext.java（双方状态+触发点+只读视图）。
   新建 model/Keyword.java 枚举（先 DAMAGE/HEAL/DRAW/CHARGE 占位，CHARGE 本步可不接线）。
3. 把法术三结算改成 DamageEffect/HealEffect/DrawEffect 三个实现类，CombatResolver.resolveSpell
   只查注册表派发，不写 switch（switch 删掉）。
4. 触发点：CombatResolver 上场处与阵亡处各加一行 TriggerSystem.fire(ON_SUMMON/ON_DEATH)，
   无监听时零开销直通。
5. 新增 effect/EffectRegistryTest（注册/派发/未注册 fail-fast）+ TriggerFireTest（触发与不触发各1例）。
   旧测试全绿；收尾三件套。
铁律：本步是唯一允许新增行为的内核步骤，但老三法术数值不许变；禁单卡特例 if。
```

**验证**：删掉 switch 的 diff；`mvn test` 全绿。

---

## S5 · 卡牌 JSON 化（1 天，老卡行为不变）

**AI 提示词：**

```
在 /Users/danghaobo/Java/test 做功能（前置：S3、S4）：
1. 先读：model/Deck.java、model/ 各卡类、data 包（无则新建）、effect/EffectRegistry.java。
2. 新建 src/main/resources/cards/minions.json、spells.json、pets.json，格式：
   {"schemaVersion":1,"cards":[{"id":"m1","name":"幼龙","attack":2,"health":3,"cost":0,
   "keywords":[],"text":"..."}]}。把当前硬编码牌表逐张翻译进 JSON（数值一个不许变，
   无费用老卡 cost 全 0）。
3. 新建 data/CardDatabase.java：加载+校验（id 唯一、attack/health/cost 非负、keywords 必须已注册），
   失败 fail-fast 打印“文件名:行号:原因”；static standardDeck() 供 GameSession 用，
   替换 S3 的硬编码牌表。
4. 新增 data/CardDatabaseTest：缺字段/重复 id/未知 keyword 三例 fail-fast；全量加载张数与旧牌表一致断言。
5. 旧测试全绿；收尾三件套。
铁律：数值零变化；JSON 只 UTF-8 无 BOM。
```

**验证**：`git diff` 无 Java 数值改动；故意改坏一张卡能复现 fail-fast。

---

## S6 · AI 接口化（半天，行为不变）

**AI 提示词：**

```
在 /Users/danghaobo/Java/test 做重构（前置：S1、S3）：
1. 先读：ai/SimpleAi.java、engine/ActionValidator.java（含 Move/legalActions）、engine/GameEngine.java
   的 AI 相关方法、AiStepTurnTest。
2. 新建 ai/GameView.java（只读快照：双方生命/手牌数/战场攻血快照，不暴露 Deck/牌堆顺序/对手手牌内容）、
   ai/AiStrategy.java（chooseMinion/chooseSpell/chooseMove，参数全用 GameView）。
   SimpleAi implements AiStrategy，内部逻辑逐行保留（最高攻/10血回血/最低血目标/斩杀），
   数据来源改为 GameView + legalActions。
3. GameEngine.chooseAiTarget/aiStrike 改走 legalActions 校验（非法直接 return false）。
4. 新增 ai/AiNoCheatTest：AI 侧拿不到 Deck 引用、改快照不影响真实状态；旧 AI 测试不许改。
5. 收尾三件套。
铁律：AI 走子行为零变化（autoplay 战报语义一致）；GameView 零 setter。
```

**验证**：`mvn test` 全绿；`autoplay=10` 语义一致。

---

## S7 · 界面 MVVM 拆分（1～2 天，行为不变）

**AI 提示词：**

```
在 /Users/danghaobo/Java/test 做重构（前置：S1～S6 全绿）：
1. 先读：ui/CardGameApp.java 全文（1094行）、ui/CardView/MinionView/HeroView.java、
   ui/fx/Fx.java、engine/GameSession.java、engine/ActionValidator.java、event/GameEventBus.java。
2. 新建 ui/viewmodel/BoardViewModel.java：唯一真相源，持有 yourTurn/selectedAttacker/
   双方只读展示数据、按钮可用性（调 ActionValidator），订阅事件总线刷新自身；
   CardGameApp 的 player/ai/yourTurn/selectedAttacker/pendingDrawAnim/aiSteps 状态全部搬入 VM，
   CardGameApp 瘦身为组装器（new Session→new VM→new View→接事件，只许接线代码）。
3. ui/CardView/MinionView/HeroView 搬入 ui/view/ 包（包名改，类行为不动），渲染只读 VM，
   不许直读 PlayerState 可变列表；动画调用点由 CardGameApp 改为 VM 触发，fx/ 内代码不动。
4. 不新增逻辑测试；验收为行为测试：mvn javafx:run 手点全流程（开始→出牌→攻击→结束→AI回合→终局），
   加 autoplay=10 + 截图对比基线无回归。
5. 收尾三件套。
铁律：fx/ 内文件一行不许改；CardGameApp 行数必须减半以下；本步不许加新玩法。
```

**验证**：`wc -l` 对比；截图肉眼无回归；`mvn test` 全绿。

---

## S8 · 基础服务 + 回退链测试（1 天）

**AI 提示词：**

```
在 /Users/danghaobo/Java/test 做功能（前置：S3、S5、S7）：
1. 先读：engine/GameSession.java、ui/Assets.java、ui/fx/SoundEngine.java、
   src/main/resources/images/README.md。
2. 新建 service/SaveService.java（saveVersion 必填，当前版本 1；save/load/ migrate：
   高版本拒绝+提示升级，低版本逐级 migrate+警告日志，未知字段忽略）、service/ConfigService.java
   （音效开关/难度/AI 步间隔持久化）、service/GameLog.java（文件日志 rolling）、
   resources/i18n/messages_zh.properties + messages.properties（先 20 个 key：开始游戏/结束回合/胜利…）。
3. 存档样例 src/test/resources/saves/v1-sample.json；新增 service/SaveCompatTest
   （存取一致/未知字段忽略/高版本拒绝）。
4. 新增回退测试：备份后清空 src/main/resources/images/ 再跑 autoplay=5 与 screenshot，
   全过则恢复资源（用 git checkout -- src/main/resources/images 恢复，游戏自带程序化图形兜底）。
5. 收尾三件套。
```

**验证**：样例存档测试绿；删资源测试绿。

---

## S9 · 事件版本化与回放（半天）

**AI 提示词：**

```
在 /Users/danghaobo/Java/test 做功能（前置：S2、S8）：
1. 先读：event/GameEvent.java、event/GameEventBus.java、engine/CombatResolver.java。
2. GameEvent 加 int schemaVersion（常量 EVENT_SCHEMA=1）与 toJson/fromJson（手写字符串拼接即可，
   不引入新依赖；字段：type/actor/target/cardId/attackerId/defenderId/amount/message）。
3. 新增 event/ReplayTest：autoplay 一局收集事件流 → 新 Session 重放 apply → 终局双方生命与原局一致。
4. 旧测试全绿；收尾三件套。
铁律：不引入 JSON 第三方库（保持零依赖）；事件字段只增不改。
```

**验证**：重放测试绿。

---

## S10 · 三端 CI + 发版（半天）

**AI 提示词：**

```
在 /Users/danghaobo/Java/test 做发布（前置：S0～S9）：
1. 先读：.github/workflows/windows-package.yml、package.sh、package.bat、README.md 下载章节。
2. 复用 package.sh 给 .github/workflows 加 macos-latest、ubuntu-latest 矩阵（jlink 模块列表不动，
   只换 jpackage --type：dmg/deb）；每端 job 尾加 autoplay=5 冒烟（复用 S0 命令）。
3. README 下载表补三端链接占位；新建 docs/CHANGELOG.md（v1.1.0：框架重构条目逐条列 Bain：A1～A9，
   注明“玩法零变化”）。
4. 本地验证 mvn test 全绿；git tag v1.1.0（不 push，等你确认）。
铁律：不许升级 JavaFX 版本；CI 脚本只加 job 不改现有 Windows job。
```

**验证**：`git diff --stat` 只有 workflow/README/CHANGELOG；`mvn test` 绿。

---

## 附：总验收表（S10 完成后逐项打勾）

- [ ] `mvn test` 全绿（≥10 个测试类）
- [ ] `autoplay=10` 正常退出，战报语义与基线一致
- [ ] 截图与 `docs/baseline.png` 无回归
- [ ] `engine/`、`ui/` 无 400+ 行神类（`wc -l` 检查）
- [ ] `grep -r "import javafx" src/main/java --include=*.java -l` 无 `model/engine/ai/event/data/effect/service` 文件
- [ ] 删 `images/` 后冒烟照过
- [ ] 上版本存档读入测试绿
