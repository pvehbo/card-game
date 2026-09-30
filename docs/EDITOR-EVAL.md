# B-11 评估：卡牌编辑器 / 排位天梯

> 结论先行：**两项都不在 v1.5 开工**，维持 BACKLOG「v2.0+，看留存再定」的判定。
> 本文把「开工前必须补的那一块」写清楚，将来真要动手时不用重新调研。
> 评估基准：v1.5.0 代码（`CardDatabase` 仍只读 classpath；`StatsService` 只做本地统计）。

## 一、卡牌编辑器

### 现状盘点（可直接复用的）

| 能力 | 位置 | 复用度 |
|---|---|---|
| 卡牌 JSON 解析（任意文本 → Card） | `data.CardDatabase.loadFrom(String json, String sourceName)` | 直接可用，已是公开 API |
| 严格校验（id 唯一 / 数值范围 / 关键词合法，报错到文件:行） | `data.CardDatabase` 的 fail-fast 校验 | 直接可用 —— 编辑器保存前跑一遍即可 |
| 数值体检（香草公式，超 3 分告警） | `data.BalanceCheck.check(List<Card>)` | 直接可用，编辑器可以实时显示告警 |
| 入堆份数 / 明星单卡 | `CardEntry` + `copies` 字段 | 直接可用 |
| 可写目录 | `service.UserData.file(String)`（`~/.cardgame`，`-Dcardgame.home` 可覆盖） | 直接可用 |
| 回退链（无图不炸） | `ui.Assets` | 直接可用，新卡没图也有程序化卡面 |

### 唯一的架构缺口

`CardDatabase.load()` 只读三份 classpath 资源：

```java
all.addAll(loadFile("/cards/minions.json", idSource));
all.addAll(loadFile("/cards/spells.json", idSource));
all.addAll(loadFile("/cards/pets.json", idSource));
```

打包进 jar 后资源是只读的，所以**玩家不可能在游戏里改到这三份文件**。编辑器要落地，
必须先加一层「用户卡叠加层」：

1. 新增 `UserData.dir().resolve("cards/")` 作为第四、第五、第六个来源（同名文件存在才读）；
2. id 冲突规则：用户卡与内置卡 id 相同时**以用户卡覆盖**（或反过来拒绝并提示，二选一，须写进文档）；
3. `CardDatabase` 的 id 唯一校验要在合并**之后**跑，保证覆盖语义不破坏 fail-fast；
4. 编辑器窗口保存时先 `loadFrom` 自检、再 `BalanceCheck`，两条都过才落盘。

### 若开工的 MVP 范围（估 3～4 天）

- 列表 + 表单（id/名称/费用/攻血/关键词/描述/copies），保存即写 `~/.cardgame/cards/*.json`
- 保存前双门禁：`loadFrom` 解析通过 + `BalanceCheck` 零 blocker
- 一键「导出/导入」整份 JSON（把创作成果发给别人）
- 明确不做的：卡图上传（走现有 resources 回退链）、脚本化效果（Effect 是新代码，不是数据）

### 风险与红线

- **红线：不许让编辑器绕过 `BalanceCheck` 与 `CardDatabase` 校验**，否则手滑写出超模卡会污染天梯数据。
- 「脚本化效果」看起来最诱人，但 `effect.Effect` 是 Java 接口，不存在安全的表达式沙箱；
  真要做等于自造 DSL + 解释器，量级从 4 天跳到 4 周，**不在 MVP 内**。
- 编辑器只对**开发者自用**也有价值（造卡速度提升），但对**玩家留存**的直接贡献最小 —— 这是它排在天梯之后的主因。

## 二、排位天梯

### 现状盘点

- `service.StatsService` 已有：总场次 / 胜场 / 分难度战绩 / 先手后手 / 连胜 + 11 个成就，落本地文件。
- 缺的只是「一个随胜负变动的分数」+ 分段展示。

### 关键结论：**本地天梯能马上做，真排位必须等联机**

| 形态 | 前置 | 量级 | 建议 |
|---|---|---|---|
| 单机「挑战等级」（对 AI 累积积分 + 分段名 + 段位徽章） | 无（纯 `StatsService` 扩展） | 1～2 天 | v1.6 可做，但**不叫排位**，避免承诺 PvP |
| 真排位（匹配 + 跨玩家积分） | **B-10 联机（v2.0，4 周起）** | ≥1 周（在联机之上） | 与 B-10 同批立项 |

纯单机积分有个天然上限：AI 是固定三档，分数最终只反映「你打了多少局困难 AI」，
会和成就在信息量上重叠。**所以它不值得单独排期，但可以蹭联机批次一起上。**

## 三、触发条件（什么时候回头看本文）

在 `~/.cardgame/stats` 上观察，满足**任意一条**再立项：

1. 累计对局 ≥ 50 局（说明有人在反复玩，编辑器造卡/天梯追求才有对象）；
2. 牌组多样性：出现 ≥ 3 套明显不同（费用曲线或关键词分布差异大）的自建牌组；
3. B-10 联机开工（则排位天梯**必须**同批做，否则联机没有长期动机）。

反之，对局数长期 < 20 局时，优先级应给「内容量」（新卡/新关键词），而不是工具链。

## 四、参考文献

- `docs/BACKLOG.md` B-10 / B-11 条目
- `docs/PLAN-v1.5-v2.0.md` v2.0 部分
- `docs/BALANCE.md`（编辑器实时告警的公式来源）
