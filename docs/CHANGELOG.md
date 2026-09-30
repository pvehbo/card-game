# CHANGELOG

## v2.0.0（联机版 · M0–M3 预览，未发布）

口号：两个人，同一盘棋。服务端用同一份引擎重算，客户端只是手柄。

### 联机最小可玩（M0 → M3）
- **协议与编解码（M1）**：新增 `net/` 包（`Protocol` / `NetMessage` / `MessageCodec` / `Json`）——
  8 种信封（JOIN / START / ACTION / SYNC / EVENT / PING / PONG / ERROR），裸 TCP 一行一条 JSON，
  手写 JSON 读写器**零第三方依赖**；`GameEvent` 升 `EVENT_SCHEMA = 2`（带 `seq` / `turn`），v1 事件流照旧能读
- **房间服务端（M2）**：`server/GameRoom` 是房间唯一输入口 `handle(seat, wire)`（解析 → 校验 → 结算 → 广播），
  `server/RoomServer` 负责 TCP 接受循环；启动方式
  `java -cp target/classes org.example.card.server.RoomServer [端口] [seed]`（默认 7788）。
  非法指令回 `ERROR` 并记在 `rejections()` 账上（验收要求「被拒并记录」）
- **客户端与界面接线（M3）**：`client/RoomClient`（发指令、按 SYNC 回声重放、收事件、`divergences()` 查分叉）+
  `service/MatchReferee`（服务端与客户端共用的座位无关裁判器）+ 工具栏「联机」按钮与地址对话框；
  入站报文整体派发到 JavaFX 线程（`RoomClient.setDispatcher(Platform::runLater)`），订阅手柄可直接碰控件
- **反作弊模型**：客户端不判任何规则——本地只按服务端**接受过的指令**顺序重放，
  「客户端状态 = 服务端指令序列的函数」，两端不可能分叉

### 关键设计：座位与暗牌
- `START` 带 `youSeat` / `firstSeat`：发牌按座位顺序，摆反了两端起手牌会互换、第一回合起就对不上；
  界面靠 `BoardViewModel.setLocalSeat(seat)` 切换 `me()/foe()`（`BoardViewModelTest` 把这条映射钉死）
- 服务端按座位裁剪事件：对手的抽牌 / 烧牌抹掉牌面，文案换成「对手抽了一张牌」，
  服务端自己的日志保留完整牌面（审计看得见，网络上看不见）
- 联机局不挂本地 AI 导演、不写单机存档 / 回放 / 战绩；联机时「构筑 / 读档 / 回放」只提示不生效

### 其他
- 测试 122 → 172 用例，全绿（联机新增：`ProtocolCodecTest` 12 / `GameRoomTest` 18 / `RoomServerTest` 3 /
  `RoomClientTest` 2 / `MatchRefereeTest` 2 / `BoardViewModelTest` 4；M0 体检 `HeadlessGameTest` 4）
- `service/HeadlessGame` + `HeadlessGameTest`：整局对局在零 JavaFX 环境下跑完，并用字节码扫描
  钉死「`javafx` 只许出现在 `ui` 包」这条红线
- 协议表、启动方式、代码地图与已知限制见 [`docs/NETWORK.md`](NETWORK.md)
- 未做：断线重连、超时判负 / 逃跑判胜（M4）、账号与 ELO（M5，可砍）

## v1.5.0（双主题版）

口号：一套内容，两种皮肤；先手不再稳赢。本版主线是美术资源 + 主题系统，另附一条平衡调整。

### 双主题系统（V3）
- 暗 / 亮两套 CSS：`theme-dark.css`（默认）/ `theme-light.css`（羊皮纸配色），
  工具栏一键切换，选择落盘（`ConfigService.ui.theme`），下次启动即恢复
- `ui/Theme` 枚举是唯一真相源（id / cssPath / isLight / cssUrl / toggle / fromId）：
  资源缺失时 `cssUrl()` 返回 null 直接回退暗主题，未知 id 也回退暗主题，不会白屏
- 亮主题背景优先 `backgrounds/board-light.jpg`，缺图回退 `board.jpg`；再缺回退程序化图形
- 诊断开关 `-Dui.theme=light|dark` 可预设主题（只覆盖本次运行，不落盘）
- `ThemeTest` + `ConfigThemeTest` 锁定切换、持久化与容错路径

### 美术资源包（V0 / V1 / V2）
- 30 张卡图（512×716）+ 玩家/AI 头像（256×256）+ 亮暗双背景（1920×1080），逐张 `sips` 核验尺寸
- 规格冻结在 [`docs/ASSETS-v1.5.md`](ASSETS-v1.5.md)；`AssetsCoverageTest` 逐卡 id 锁定资源存在性
- 查找链与命名规范见 `resources/images/README.md`：**替换即生效，删除即回退**

### 平衡：后手补偿
- 后手开局多抽 1 张（规则与普通抽牌一致：满 10 张烧掉并发 BURN 事件、空牌堆无事发生）
- 桌面演示/截图模式（`deferFirstTurn`）不补牌，避免打乱摆好的演示局面
- 天梯先手优势从 ~60% 继续回落，不再出现「先手稳赢」；`SecondMoveBonusTest` 覆盖三种边界

### 成就 8 → 11
- 新增「后发制人」（后手获胜）/「金身不破」（满血获胜）/「速战速决」（≤5 回合获胜）
- `GameResult` 字段复用，界面与存档格式零改动

### 其他
- 测试 110 → 122 用例，全绿

## v1.4.0（成长版）

口号：看得见成长，打不腻的牌。零新系统，全部复用现有流水线。

- 战绩成就：`service.StatsService`（总场/胜场/分难度/先手后手/连胜）+ 8 成就
  （首胜/三连/十连/空手/清场/反杀/困难/马拉松）+ 战绩窗 + 解锁 toast；自动对局不计入
- 第二批关键词：圣盾（单次豁免）/ 风怒（2 连击）/ 剧毒（见血封喉，圣盾可挡）
- 卡池 20 → 30 种：10 张新卡按费用曲线铺（1 费剧毒/冲锋、风怒、圣盾墙、双解场、大哥），
  `BalanceCheck` 零告警；10 张新卡 5 局内全部被实际打出过
- 天梯回归：先手优势从 ~80% 回落到 ~60%，无倒挂（同座位困难 ≥ 贪心 > 随机）
- README 重写（规则/AI/构筑/存档/加卡教程），截图更新

## v1.3.0（内容版，纯单机）

- 存档/读档：每玩家回合自动存档，“读档”续玩（`service.SaveService` + 界面接线）
- 快照回放查看器：上一步/播放暂停/下一步/退出，退出恢复实况（`service.ReplayRecorder`）
- 牌组构筑器：30 张、同名 ≤2（明星单卡 ≤1）、搜索、费用曲线、非法禁开局（`data.DeckBuilder` + 构筑窗口，牌组存配置）
- 牌池 20 种：新增熔火犬（3 费 4/3 冲锋）、奥术研读（2 费抽 2）
- `-Dcardgame.home` 可覆盖数据目录（受限环境用）

## v1.2.0（策略版）

- 费用体系：1 费开局、+1/回合、10 封顶；次数+费用合并校验；费用圆数字、法力条、置灰
- 关键词：冲锋/嘲讽真机制，战吼/亡语走触发管线；卡面缀关键词，嘲讽金边
- 扩展包：8 明星单卡（超模配副作用）+ `copies` 机制 + `BalanceCheck` 零告警；`docs/BALANCE.md`
- AI 三档：随机/贪心/斩杀，下拉切换 + `-Dai.level`；`AiLadderTest` 天梯基准（发现先手约 80% 胜率，已记录）
- 修复：徽章拉伸遮挡（锁尺寸）、嘲讽打脸死循环（合法动作推进 + 出手前预检）

## v1.1.0（框架版）—— 玩法零变化的一次

口号：外表不变，骨头换了。本版所有重构提交均满足“diff 无数值变化”，
15 个基线测试零修改全绿；新行为只有数据驱动、AI 接口、存档、事件回放等扩展点。

### 内核拆分
- `engine/ActionValidator`：合法动作唯一入口（出牌/攻击/拒绝原因/合法动作表），UI 与 AI 共用
- `engine/CombatResolver`：互撞/直击/阵亡/法术结算纯函数化，事件发布顺序锁死（`CombatEventOrderTest`）
- `engine/GameSession` + `TurnController`：开局发牌/先后手/代数作废/回合交接搬出界面
- `effect/` 包：`Effect` 接口 + `EffectRegistry`（三法术为前 3 实现，`switch` 删除）
  + `TriggerSystem`（战吼 `ON_SUMMON` / 亡语 `ON_DEATH`，无监听零开销）
- `ai/` 包：`AiStrategy` 接口 + 只读 `GameView` 快照 + `Target`；`SimpleAi` 实现接口，
  不再接触 `PlayerState`/`GameEngine`（反射防作弊测试锁定）
- `data/` 包：卡牌 JSON 化（`resources/cards/*.json`）+ `CardDatabase` 严格校验（fail-fast 到文件:行）
  + `MiniJson` 零依赖解析器；牌堆即走 JSON

### 界面
- `ui/viewmodel/BoardViewModel`：会话/选择/可玩判断/变更通知，界面唯一真相源
- `ui/view/BoardView`：四区刷新 + 动效锚点查询；`CardView/MinionView/HeroView` 搬入 `ui/view`
- `ui/AiTurnDirector`：AI 逐步演出队列（起手→出牌→逐个出手→收尾）
- `CardGameApp` 1072 → 925 行，只剩组装、动效、自动对局

### 服务与兼容
- `service/` 包：版本化存档（`SaveService`，高版本拒绝/未知字段忽略）
  + 文件配置（`ConfigService`）+ 文件日志（`GameLog`）
- 中英 key（`resources/i18n/`）；删 `images/` 全目录冒烟照过（资源回退链）
- `GameEvent.EVENT_SCHEMA=1` + `toJson/fromJson`，事件流可重放还原终局（`ReplayTest`）

### 发布
- CI 三矩阵：Windows（已有）+ macOS/Linux（`unix-package.yml`，`mvn test` 门禁 + `autoplay=5` 冒烟）
- 发版证据：基线截图 `docs/baseline.png` + 每步 `autoplay` 战报

### 已知限制（v1.2 做）
- 费用体系：JSON 已占位 `cost`，校验器还是次数限
- 关键词只有 DAMAGE/HEAL/DRAW；`ON_SUMMON` 上下文暂不带敌方
- 只有 PvE；联机/编辑器/天梯另立项
