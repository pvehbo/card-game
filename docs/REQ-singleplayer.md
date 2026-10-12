# REQ-singleplayer · CardGame 纯单机化增量需求（轻量版）

> 状态：需求冻结候选 · 作者：许清楚（PM） · 面向：架构师（高见远）
> 上游依据：用户三条前提 + 决策 A/B、`docs/PLAN-v2.0-hardening.md`（缺陷审计，已逐条回代码核实）
> 定位：**既有项目范围收缩**，非新产品。竞品分析 / 市场调研 / 象限图**不适用**，本文不写。
> 前提：①简单小巧 ②纯单机 ③死守「卡牌对战」边界。

---

## 1. 本次目标（TL;DR）

一句话：**把 CardGame 收敛成「小巧玲珑的单机卡牌对战」——移除全部联机代码，修掉会算错数/丢状态的缺陷，顺手清理代码卫生；功能面只减不增。**

---

## 2. 范围边界

### 2.1 保留（本轮不动）
| 能力 | 说明 |
|---|---|
| 单机对战（玩家 vs AI） | 引擎 / 效果 / AI / 界面全保留 |
| 存档 · 读档 · 回放 | 主推功能，仅修 S4/M1，不重构 |
| 牌组构筑 · 战绩成就 · 双主题 · 后手补偿 | 已发布内容，原样保留 |
| 卡库 JSON 化（加卡不改代码） | 保留，仅确保不再触发 S3 |

### 2.2 移除（决策 A）
| 对象 | 位置 |
|---|---|
| 联机协议 | `net/`（Json / MessageCodec / NetMessage / Protocol，4 文件） |
| 房间服务端 | `server/`（GameRoom / RoomServer，2 文件） |
| 联机客户端 | `client/`（RoomClient，1 文件） |
| 联机裁判 | `service/MatchReferee.java` |
| 界面入口 | `ui/CardGameApp.java` 工具栏「联机」按钮、连接线程、`adoptNetworkGame` 等 |
| 联机测试 | `test/.../net`、`server`、`client`（4 文件 35 用例）+ `MatchRefereeTest`（2 用例） |
| 联机文档 | `docs/NETWORK.md`；README / CHANGELOG / BACKLOG / PLAN 中联机章节 |

### 2.3 本轮修复
见 §3 需求池（P0 + P1 + P2 全部）。

### 2.4 明确不做（呼应前提③）
| 不做项 | 为什么现在不做 |
|---|---|
| 卡牌编辑器 | 需先加「用户卡叠加层」，MVP 3~4 天；已超「卡牌对战」边界 |
| 账号 / ELO / 天梯 | 依赖联机；单机积分与成就信息量重叠，不值得单独排期 |
| 社交 / 好友 / 聊天 | 需联网，直接违背前提② |
| 本地双人 | 已取消（BACKLOG B-5），偏离「玩家 vs AI」定位 |
| 联机 M4 / M5 | 决策 A 整体移除，不再推进 |
| 新卡 / 新关键词 / 新玩法 | 本轮是收缩，功能面只减不增 |

---

## 3. 需求池

优先级：**P0 = 会算错数 / 丢状态；P1 = 体验受损；P2 = 代码卫生。**

| ID | 需求 | 优先级 | 一句话验收 | 预估改动面 |
|---|---|---|---|---|
| R-00 | 移除全部联机代码（决策 A） | P0 | §4 全部口径通过 | ~8 主文件 + 5 测试文件 + 4 文档 |
| R-01 | 负攻击力打脸不再回血（S3） | P0 | 负攻随从直击对手英雄，对手生命不增；生命恒在 `[0,20]` | 2~3（CombatResolver / PlayerState + 测试） |
| R-02 | 读档保留召唤失调 / 已出手（S4） | P0 | 存档落 `summoningSickness`/`attacksUsed`；读档后当回合随从可攻击 | 2~3（SaveService / MinionCard + 测试） |
| R-03 | 视图模型订阅可注销（M1） | P1 | 进出回放 N 次后监听器数量不增长 | 2（BoardViewModel / CardGameApp + 测试） |
| R-04 | 音效池线程安全 + 资源可关（M3） | P1 | 预热/播放并发无泄漏；Clip 与 AudioInputStream 均关闭 | 1~2（SoundEngine + 测试） |
| R-05 | 过牌法术发 DRAW / BURN 事件（M6） | P1 | 过牌有抽牌音效；满手时发 BURN，不再静默烧牌 | 1~2（DrawEffect + 测试） |
| R-06 | 置灰卡牌真正拦截点击 | P1 | 点置灰卡无任何反应（不只加样式） | 1（CardView） |
| R-07 | 硬编码颜色 / 字号迁入主题 CSS（L4） | P1 | 亮色主题下卡名、伤害数字对比度达标 | 2~3（MinionView / CardGameApp + CSS） |
| R-08 | 清理死代码与空转配置（L1） | P2 | 删 `Phase` / `requireBool` / `shownLife`；`ai.level`/`ai.stepGapMs` 接线或删除 | 4~5 |
| R-09 | 线程收口（L2） | P2 | 非 daemon 线程移除或 `setDaemon(true)`；线程表不增长 | 1（CardGameApp） |
| R-10 | 单机侧 JSON 转义缺口（L3） | P2 | `MiniJson` / `SaveService.quote` 覆盖 `\b \f` 及 C0 控制字符 | 1~2 |
| R-11 | 同归于尽判定明确定义 | P2 | 若单机路径存在该分支则显式定义；否则随 R-00 消失 | 待确认（见 §6） |

> 说明：原审计的 **M2 / M4 / M5**（端口非法致线程静默死亡、事件总线吞异常、可变状态暴露）**均属联机路径**，随 R-00 一并消失，不单独立项。

---

## 4. 联机移除的验收口径

「干净」= 以下**全部**满足：

| # | 口径 | 验证方式 |
|---|---|---|
| 1 | `src/main` 无 `net`/`server`/`client` 包，无 `MatchReferee` | `find` + `grep import` 均为空 |
| 2 | `src/test` 无 `net`/`server`/`client` 包，无 `MatchRefereeTest` | 同上 |
| 3 | `CardGameApp` 无联机字段 / 按钮 / 线程（`RoomClient`、`netButton`、`net.*` 属性） | `grep` 为空 |
| 4 | `mvn -o verify` 全绿（走 `scripts/dev-verify.sh`） | exit = 0 |
| 5 | `autoplay=10` 整局冒烟 exit = 0 | 命令见 dev-workflow |
| 6 | 文档同步：删 `docs/NETWORK.md`；README/CHANGELOG/BACKLOG/PLAN-v1.5-v2.0 联机引用标注「已移除」 | 人工核对 |
| 7 | `pom.xml` 无多余依赖（不新增；`javafx-swing` 仍为截图所需，保留） | `diff pom.xml` |
| 8 | 无 `net.*` 系统属性残留（`net.name`/`net.host`/`net.port`） | `grep` 为空 |

> 门禁三件套遵循 `docs/dev-workflow.md`：编译 + 全量单测 + 整局冒烟；**红灯停线**。

---

## 5. 「小巧玲珑」量化口径

| 指标 | 现状 | 目标 | 依据 |
|---|---|---|---|
| `src/main` 源文件数 | 68 | **≤ 60** | 移除 net4 + server2 + client1 + MatchReferee = 8 |
| `src/test` 测试文件数 | 33 | **≤ 29** | 移除 4 个联机测试文件 + MatchRefereeTest = 5 |
| `@Test` 用例数 | 172 | **135 ~ 150** | 减 37 条联机用例；加约 8~12 条回归用例 |
| `ui/CardGameApp.java` 行数 | 1668 | **≤ 1450** | 联机接线（按钮 / 连接线程 / adoptNetworkGame）约 200 行 |
| 新增第三方依赖 | 0 | **0** | 收缩不引依赖 |
| 新增功能面 | — | **0** | 本轮只减不增 |

> 目标为「收缩下界」，非硬性红线；若架构师发现某文件无法压到目标，需在设计文档说明原因。

---

## 6. 待确认问题（≤4）

1. **R-11 同归于尽**：单机（非联机）路径是否也存在「双方同时 0 血」的胜负判定分支？若有，按什么规则判（平局 / 先手负 / 特定卡胜）？——决定 R-11 是否立项。
2. **R-07 主题范围**：硬编码颜色只迁「卡名 / 伤害数字」等关键可读项，还是要求硬编码色值零残留？——影响改动面与验收严格度。
3. **R-08 空转配置**：`ai.level` / `ai.stepGapMs` 选择「接线使其生效」还是「直接删除」？——即用户是否需要运行时调 AI 难度 / 速度。
4. **发版口径**：本次收缩作为 v2.1 发版，还是并入 v2.0 正式版（撤销 M0–M3 预览）？
