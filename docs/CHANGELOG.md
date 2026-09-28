# CHANGELOG

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
