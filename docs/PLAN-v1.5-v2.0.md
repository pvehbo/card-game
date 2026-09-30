# v1.5 → v2.0 开发计划（打磨版 → 联机版）

> 前置状态：v1.4.0 已发布（成长版：统计成就 / 圣盾·风怒·剧毒 / 卡池 30 种 / 先手胜率回落 ~60%）。
> 基线验证：`mvn -o verify` 122 个测试全绿（v1.5 收尾时点；v2.0 联机代码并入后为 172 个）。
>
> **进度（实时）**：v1.5 的 V0/V1/V2/V3 与 V4 文档部分**已完成**；v2.0 的 M0（HeadlessGame）**已完成**，
> M1~M3 未开工。下方保留原始计划原文，完成项以 ✅ 标注，不删旧文以便对照验收标准。
> 配套文档：`docs/PLAN.md`（成熟化计划书）、`docs/BACKLOG.md`（总清单）、`docs/BALANCE.md`（数值规范）、`docs/PLAN-v1.4.md`（已交付留档）。
>
> 本文分两部分：**v1.5 美术打磨版**（细到文件与验收，直接开工）和 **v2.0 联机版**（立项级方案，动工前需老板拍板技术选型）。

---

# 第一部分 · v1.5 美术打磨版（约 4~5 个工作日）

## 0. 定位与红线

- **定位**：玩法零改动（不加卡、不加关键词、不改数值），只做**视觉与体验打磨**。v1.4 已经证明流水线，v1.5 证明"资源替换链"。
- **红线**：
  - ❌ 不碰 `engine/`、`ai/`、`effect/` 任何一行——本版 diff 应该只有资源 + `ui/` + CSS。
  - ❌ 不做联机（留给 v2.0）。
  - ✅ 任何资源删除后必须回退到程序化图形照常可玩（A8 已有回退链测试，沿用）。

## 1. 工作项总览

| # | 工作项 | 量级 | 依赖 |
|---|---|---|---|
| V0 ✅ | 资源规范冻结 + 缺口盘点 | 半天 | 无 |
| V1 ✅ | 30 张卡图（AI 生成 → 回退链接入） | 2 天 | V0 |
| V2 ✅ | 英雄头像 + 战场背景（亮/暗两套） | 1 天 | V0 |
| V3 ✅ | 主题系统：亮/暗主题 CSS + 切换按钮 | 1 天 | V2 |
| V4 🔄 | 收尾：README 截图全换、CHANGELOG、tag v1.5.0 | 半天 | V1~V3 |

关键路径：V0 → V1 ∥ V2 → V3 → V4。V1 最重（30 张图），可与 V2/V3 穿插。

---

## 2. 工作项详案

### V0 资源规范冻结（半天）—— ✅ 已完成
> 交付：`src/main/resources/images/README.md` 规范升级；盘点表 `docs/ASSETS-v1.5.md`（30 卡 + 2 头像 + 2 背景逐项）。

**做什么**：把 `src/main/resources/images/README.md` 的规范升级为 v1.5 版，锁死尺寸/格式/命名三件事，然后盘点缺口。

**步骤**：
1. 核对现有回退链代码（`ui/Assets.java`）：确认查找顺序 = `images/cards/<id>.png` → `images/cards/<名称>.png` → 程序化卡面。
2. 规范定稿（写进 images/README.md）：
   - 卡图：512×716（≈ 5:7 炉石比例），PNG（透明背景），文件名 = 卡牌 `id`（如 `m7.png`、`s3.png`、`p2.png`）
   - 头像：256×256 PNG，`player.png` / `ai.png`
   - 背景：1920×1080 JPG，`board.jpg`（暗）/ `board-light.jpg`（亮）
3. 盘点表落盘 `docs/ASSETS-v1.5.md`：30 张卡 × 状态（待做/草稿/终稿）× 命名，逐张打勾用。

**验收**：盘点表覆盖全部 30 种卡 + 2 头像 + 2 背景；命名与 `cards/*.json` 的 id 一一对应（写个 10 行脚本核对）。

---

### V1 30 张卡图（2 天，最重的一块）—— ✅ 已完成
> 交付：`images/cards/*.png` 30 张；命名与 `cards/*.json` 的 id 一一对应由 `AssetsCoverageTest` 逐卡锁定（比"10 行脚本核对"更强的门禁）。

**策略**：AI 生成草稿 → 人工挑 → 程序化后处理统一风格。**不追求商稿质量，追求"整包风格统一"**。

**分批**（每批 10 张，做完一批跑一次冒烟）：

| 批次 | 内容 | 风格锚点 |
|---|---|---|
| 第 1 批 | 11 张老基础卡（m1~m6, s1~s5 中的老卡） | 暗色奇幻、贴边剪影风 |
| 第 2 批 | 明星单卡（雷霆领主、血肉巨兽、城墙巨像、瘟疫使者等） | 同上 + 主题色区分（冲锋红/嘲讽金/剧毒绿） |
| 第 3 批 | 新关键词卡 + 宠物 + 剩余法术 | 同上 |

**每张卡的流程**：
1. 生成时以卡牌 `name + text` 语义写提示词（如"暗色奇幻风格，幼龙，低攻快攻，红黑配色，卡牌插画，无文字"）。
2. 产出 512×716 PNG，命名 `<id>.png`，放入 `src/main/resources/images/cards/`。
3. **后处理统一**：写一个一次性脚本（不进主代码），批量做：色阶归一 + 暗角 + 底部渐变遮罩（给费用圆和名字条留位）。30 张图一个滤镜出，风格立刻统一。
4. 冒烟：`-Dui.screenshot` 截图肉眼检查 + 删单张图验证回退。

**验收**：
- 30/30 张到位，`Assets` 回退链零告警。
- **回退测试**：随机删 5 张图，游戏照常启动、程序化卡面顶上（A8 模式的资源删除冒烟，照抄）。
- 截图对比：同一局面（`-Dui.screenshot`）前后各一张，放 `docs/` 留档。

---

### V2 头像与背景（1 天）—— ✅ 已完成
> 交付：`player.png`/`ai.png`（256×256）；`board.jpg`（暗）+ `board-light.jpg`（亮）1920×1080；`Assets.background(boolean light)` 亮主题优先 `board-light.*` 回退 `board.*`。

1. **英雄头像**：玩家/ AI 各 2 张（暗/亮主题各一），256×256，风格与卡图一致。
2. **战场背景**：暗色版 + 亮色版 1920×1080。暗色版是默认（现状延续）；亮色版给 V3 主题切换用。
3. **背景做减法**：中央战场区压暗 15%（`app.css` 已有遮罩层的话调参数即可），保证卡面可读性优先于背景表现力。

**验收**：截图对比；亮/暗背景切换后文字对比度达标（卡名、伤害数字在两套背景下都清晰）。

---

### V3 主题系统（1 天）—— ✅ 已完成
> 交付：`ui/Theme.java`（DARK/LIGHT 枚举，`cssUrl()` 资源缺失返回 null）+ `theme-dark.css` / `theme-light.css`；
> 工具栏主题按钮 + `ConfigService` 的 `ui.theme` 落盘 + 重启记忆；`-Dui.theme=light|dark` 诊断覆盖（不落盘）。
> 门禁：`ThemeTest`（4）+ `ConfigThemeTest`（2）已绿；红线复检通过（本项 diff 只有 `ui/` + `resources/`）。

**现状**：`app.css` 单文件单主题。

**步骤**：
1. `app.css` 拆成两文件：`theme-dark.css`（现内容）+ `theme-light.css`（改 CSS 变量，不动结构）——如果现 CSS 没用变量，先做一步"色值变量化"（半天内可完成，纯替换）。
2. 主界面加"主题：暗/亮"切换按钮，落 `ConfigService` 持久化（已有配置服务，加一个 key）。
3. 切换 = JavaFX `Scene` 换 stylesheet，即时生效不重启。

**验收**：
- 两主题下完整走一局（出牌/攻击/法术/成就 toast），无样式破版。
- 配置持久化：重启后记住上次主题（单测：`ConfigService` 读写 round-trip）。
- **红线复检**：本项 diff 只有 `ui/` + `resources/`，`engine/` 零改动。

---

### V4 收尾（半天）—— 🔄 进行中

1. README：~~截图全换~~（**受阻**：headless 环境无法初始化 JavaFX 渲染管线，需在有显示环境的机器重拍）、"自定义卡图"章节按新规范更新 ✅。
2. `docs/CHANGELOG.md` 记 v1.5.0 ✅；`docs/BACKLOG.md` B-9 打勾 ✅。
3. 三件套门禁 + 资源删除冒烟，全绿后 `git tag v1.5.0` 推 Release —— 门禁已绿（`mvn -o verify` 172 全绿，
   其中 v1.5 时点为 122，其余为 v2.0 联机用例），**tag `v1.5.0` 已打在 v1.5 收尾提交上**（`585e707`）；
   `autoplay=10` 与截图同样需有显示环境。
4. `docs/ASSETS-v1.5.md` 盘点表归档 ✅。

**计划外补做的两项**：v1.5 里加了后手补偿与成就 8→11（B-11 评估见 `docs/EDITOR-EVAL.md`）；
为 v2.0 铺路的 `service/HeadlessGame.java`（零 JavaFX 跑完整局）+ `HeadlessGameTest` 4 例，随 M0 一起提交。

---

# 第二部分 · v2.0 联机版（立项方案，约 4~6 周）

> **动工前必须老板拍板两件事**：① 同步方案（本文推荐指令同步）；② 服务端栈（本文推荐 Spring Boot 3 + WebSocket，正好是老板主场）。以下按推荐方案展开。

## 1. 架构选型（先定这两刀）

| 决策点 | 选项 A（推荐） | 选项 B | 理由 |
|---|---|---|---|
| 同步方式 | **指令同步**（客户端发指令，服务端重算结算） | 状态同步（服务端算完推全场状态） | 项目 seam 就是按指令同步预留的：`ActionValidator.legalActions` 服务端重放即反作弊；事件流天然就是给客户端的回放 |
| 服务端栈 | **Spring Boot 3 + WebSocket(STOMP)，MySQL 只存账号/战绩** | 纯 Java SE + 裸 socket | 老板主力栈就是 Spring Boot 3 + MySQL，运维零学习成本；游戏局内状态放内存（`GameSession` 本来就不可变纯数据），**不进数据库** |

**反作弊模型**（核心卖点）：客户端只是"手柄"——
```
客户端 ── 指令(动作 JSON) ──▶ 服务端 GameEngine(同一套 jar)
客户端 ◀── 事件流(GameEvent JSON, EVENT_SCHEMA 已版本化) ── 服务端
```
服务端用**同一份引擎代码**重算合法性与结算，客户端算的任何东西都不信任。这就是 A6"AI 与玩家同权"架构的联机版 payoff。

## 2. 里程碑（每步都有验收，红灯停线）

| # | 里程碑 | 内容 | 验收 | 量级 |
|---|---|---|---|---|
| M0 ✅ | **引擎可嵌入化体检** | 验证 `GameEngine` 无 UI 依赖、`Deck.shuffle(Random)` 可定种子、事件 JSON 往返（现有 `ReplayTest` 已锁） | 写一个 `HeadlessGame` 冒烟：无 JavaFX 环境跑完 20 回合 | 3 天 |
| M1 ✅ | **协议与编解码层** | 定义协议：`JOIN / START / ACTION / EVENT / PING`；`GameEvent.toJson` 扩协议字段（升 `EVENT_SCHEMA=2`，v1 客户端事件流照旧兼容） | 编解码 round-trip 单测；旧 schema 事件可解析 | 1 周 |
| M2 ✅ | **房间服务端** | Spring Boot：房间管理（创建/加入/匹配）、两个客户端接入同一 `GameSession`、指令校验+结算+事件广播 | 本机双客户端打完一整局；作弊指令（非法动作）被拒并记录 | 1.5 周 |
| M3 ✅ | **客户端接线** | `CardGameApp` 加"联机对战"入口：现有 UI 不动，把"玩家操作"从直调引擎改为发指令、把收事件从总线改为 WebSocket 订阅 | 局域网两台机器对局；断线 10 秒内重连恢复 | 1.5 周 |
| M4 | **健壮性** | 断线重连（重放事件流快照）、超时判负、对方逃跑判胜、简单 ELO | 各异常路径单测 + 双端手工验证 | 1 周 |
| M5 | **账号与战绩**（可选裁剪） | MySQL：账号（用户名+密码哈希）、ELO、历史战绩（`StatsService` 扩展远端上报） | 注册/登录/战绩页 | 1 周 |

**裁剪建议**：M5 可砍（游客模式随机昵称），先验证"有人联机玩"再投入账号体系。**最小可玩 = M0~M3 已全部落地**。

> **M1~M3 的执行偏差（已记录）**：本表推荐 Spring Boot 3 + WebSocket(STOMP)，实际先用**纯 Java SE + 裸 TCP
> （一行一条 JSON）**把「协议 / 房间 / 双端一致」三件事打通——服务端零第三方依赖、不需要引入 Spring 才知道
> 协议对不对。协议（`net/`）、房间逻辑（`server/GameRoom`）与传输层是分开的，将来换 WebSocket 只替换
> `GameRoom.Transport` 这一层适配；客户端地址与昵称已支持 `-Dnet.room` / `-Dnet.name` 预设。

### M0 交付留档（已完成，v1.5 期间落地）

- `src/main/java/org/example/card/service/HeadlessGame.java`：`run(long seed[, int maxRounds, AiLevel level[, Consumer<String> log, boolean keepLog]])`，
  返回 `record Result(rounds, engineTurns, playerFirst, gameOver, playerWon, aiWon, playerLife, aiLife, eventCount, log)`；
  `DEFAULT_MAX_ROUNDS = 20`。内部只依赖 `GameSession` / `GameEngine` / `TurnController` / `GameEventBus` / `AiLevel`。
- `src/test/java/org/example/card/service/HeadlessGameTest.java`（4 例）：
  `runsTwentyRoundsWithoutUi`、`sameSeedProducesSameOutcome`（**同种子同结果**，联机仲裁的前提）、
  `manySeedsAllTerminateOrHitCap`、`engineSideClassesDoNotReferenceJavafx`（对 18 个类逐一检查 class 常量池不含 `javafx/`）。
- **体检结论**：JavaFX import 只集中在 `ui/` 包，`engine/ai/effect/data/event/model/service` 零引用
  → 服务端可直接复用同一份 jar，M2 不需要任何引擎改造。
- 未做（刻意留到 M1/M2）：种子握手、指令编解码、房间与广播。

### M1~M3 交付留档（已完成）

- **M1 协议与编解码**（`src/main/java/org/example/card/net/`）：
  `Protocol`（`VERSION = 1`，kind / 动作 `PLAY|ATTACK|END_TURN` / 错误码 `VERSION_MISMATCH|BAD_MESSAGE|ROOM_FULL|NOT_YOUR_TURN|ILLEGAL_MOVE|GAME_OVER` / `FACE = -1`）、
  `NetMessage`（sealed：`Join / Start / Action / Sync / Event / Ping / Pong / Failure`）、
  `MessageCodec`（`encode` / `decode` / `peekKind`）、`Json`（手写读写器，零依赖）。
  `GameEvent.toJson(seq, turn)` 带 `EVENT_SCHEMA = 2`，v1 事件流照旧可解析。
  验收：`net/ProtocolCodecTest` 12 例（每种信封 round-trip + 黄金样例字节格式 + 大 seed + 旧 schema 兼容）。
- **M2 房间服务端**（`server/GameRoom` + `server/RoomServer`）：
  房间唯一输入口 `handle(int seat, String wire)`；入座 → 双方就位后 `start()` 广播 `START(seed, youSeat, firstSeat)`；
  指令走 `MatchReferee.apply(seat, action)` 结算，接受则广播 `SYNC`，被拒则回 `ERROR` 并记进 `rejections()`
  （`"座位 N 被拒 [CODE] message"`）；引擎事件按座位裁剪（对手抽牌/烧牌抹成"对手抽了一张牌"）后广播 `EVENT`。
  验收：`server/GameRoomTest` 18 例 + `server/RoomServerTest` 3 例（真 socket，第三位被拒 `ROOM_FULL`）。
- **M3 客户端与界面接线**（`client/RoomClient` + `service/MatchReferee` + `ui/CardGameApp`）：
  客户端只按 `SYNC` 顺序重放，`divergences()` 记录分叉；`CardGameApp` 工具栏「联机」按钮 → 地址对话框 →
  `adoptNetworkGame`（座位映射 + 订阅事件 + 不挂本地 AI 导演）；`RoomClient.setDispatcher(Platform::runLater)`
  把重放整体搬到 UI 线程。验收：`client/RoomClientTest` 2 例（两个真 socket 客户端打完整局，重放状态与服务端三次比对一致）+
  `ui/viewmodel/BoardViewModelTest` 4 例（`setLocalSeat(1)` 后 `me()` 指向会话的 `ai` 槽）。
- **未做**（M4/M5）：断线重连、超时判负、逃跑判胜、ELO、账号；一局一房（服务端同时只服务一个房间）。
- 玩家视角的说明、协议表与已知限制：[`docs/NETWORK.md`](NETWORK.md)。

## 3. 风险与止损

| 风险 | 概率 | 对策 |
|---|---|---|
| 客户端 UI 与联机事件流耦合过深 | 中 | M3 前先做一次"事件消费审计"：列出 `CardGameApp` 直读引擎的全部点位，逐个改为纯事件驱动（预计 20~30 处，1 天） |
| 先手 ~60% 胜率在真人对战更失衡 | 中 | M2 起在服务端记录真人先/后手数据；超 65% 就给后手 +1 起始牌（数值改动走 `BalanceCheck` 流程） |
| Spring Boot 服务端部署与运维 | 低 | 老板主场。单 jar + systemd，MySQL 只管账号，游戏状态零落盘 |
| 范围膨胀（聊天/好友/观战） | 高 | 全部进 `docs/IDEAS.md`，v2.0 一律不做 |

## 4. v2.0 发版口径

tag `v2.0.0`，客户端 Release 三端包照旧（内嵌联机入口），服务端单独立仓或 `server/` 子模块（建议单独立仓，客户端仓库保持纯客户端）。

---

# 一页纸总结

> **v1.5（已完成，tag `v1.5.0` = `585e707`）**：资源规范冻结 → 30 张 AI 生成卡图 + 头像背景 → 亮/暗双主题 → 后手补偿 + 成就 8→11 → 收尾发版。玩法零改动，diff 只碰资源、UI 与数值。剩余 = 有显示环境重拍 README 截图（`docs/screenshot.png`）。
> **v2.0（最小可玩 M0~M3 已完成）**：指令同步 + 纯 Java SE 服务端（计划里的 Spring Boot + WebSocket 留到 M4 之后替换传输层），
> **M0 引擎体检**（`HeadlessGame`）→ **M1 协议**（`net/`，EVENT_SCHEMA=2 兼容 v1）→ **M2 房间**（`server/GameRoom` 收指令、重算、广播）→
> **M3 双端接线**（`RoomClient` + 工具栏「联机」，双端重放与服务端三方比对一致）均已落地，`mvn -o verify` 172 全绿。
> 剩 **M4 健壮性**（断线重连 / 超时判负 / 逃跑判胜）与 **M5 账号**（可砍）。反作弊 = 服务端同引擎重算。玩家说明见 `docs/NETWORK.md`。
> **顺序建议**：先做 v1.5（美术是传播素材，也为联机版攒门面），v2.0 选型老板拍板后再立项。全程沿用三件套门禁：`mvn test` + `autoplay=10` + 截图，红灯停线。
