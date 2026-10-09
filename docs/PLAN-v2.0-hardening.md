# v2.0 架构加固与任务分工（M4 前置）

> ⚠️ **本文已作废**。本文（v2.0 联机架构加固与任务分工方案）已被
> [`docs/REQ-singleplayer.md`](REQ-singleplayer.md)（纯单机化增量需求）与
> [`docs/DESIGN-singleplayer.md`](DESIGN-singleplayer.md)（增量设计）**取代**。
>
> **作废原因**：项目定位调整为**纯单机**，联机子系统**整体移除**——本文描述的联机加固（如 WS-2 联机权威与协议）
> 及其中 **S1 同名分叉** / **S2 慢客户端冻房**等缺陷，全部属于联机路径，随联机代码一并**消失**。
>
> **保留原文仅作历史留档**，以下内容不再执行。

> **阶段定位**：v2.0 联机 M0–M3 已落地（`mvn -o verify` **172 用例全绿**），M4（断线重连/超时判负/逃跑判胜）
> 与 M5（账号/ELO）属**新功能**，不在本文范围。本文只做一件事：**在加功能之前，先把架构里的裂缝补上**，
> 否则 M4 的重连要建立在「同名即分叉」「慢客户端冻房」的地基上。
>
> **依据**：全仓只读审计（逐条回到代码核实，附文件:行号）。**不含**未复现的猜测。
> **配套**：`docs/BACKLOG.md`（B-10）、`docs/PLAN-v1.5-v2.0.md`（M0–M3 交付留档）、`docs/NETWORK.md`（协议表，已随联机移除一并删除）、`docs/dev-workflow.md`（S0–S5 流程）。

---

## 一、现状架构（as-is）

### 1.1 分层与依赖方向

```
                    ┌─────────────────────────────────────────┐
                    │  ui/   JavaFX 界面（MVVM + 演出导演）      │  ← 唯一允许 import javafx 的层
                    └───────────────┬─────────────────────────┘
                                    │ 只读快照 + 事件订阅
     ┌──────────────┬───────────────┼───────────────┬──────────────┐
     ▼              ▼               ▼               ▼              ▼
  client/        server/         service/         ai/          data/
 联机客户端     联机服务端     存档/配置/回放    AI 三档      卡库/体检
     │              │               │               │              │
     └──────────────┴───────┬───────┴───────────────┴──────────────┘
                            ▼
                   ┌──────────────────┐
                   │ engine/  effect/ │  规则内核（零 JavaFX）
                   │ event/  model/   │  事件总线解耦
                   └──────────────────┘
                            ▲
                   ┌──────────────────┐
                   │ net/  协议与编解码 │  零第三方依赖，两端共用
                   └──────────────────┘
```

### 1.2 四条已成立的红线（加固期间必须继续成立）

| # | 红线 | 门禁 |
|---|---|---|
| R1 | `javafx` 只许出现在 `ui/` | `HeadlessGameTest` 字节码扫描 |
| R2 | `net/` `server/` 零第三方依赖 | 无新依赖引入 |
| R3 | 客户端不判规则，只重放服务端接受过的指令 | `RoomClientTest` 双端一致性 |
| R4 | 引擎通过事件总线对外，UI 只订阅不反向依赖 | 分层 import 检查 |

**架构评价**：分层干净、seam 合理——M0 的「引擎可嵌入」体检证明服务端可直接复用同一份 jar，
这是本项目最大的架构资产。裂缝**不在分层，而在四条横切关注点**：不变量、并发、错误传播、标识权威。

---

## 二、架构诊断（已核实，按五类归因）

> 严重度：**S** = 崩溃/数据错误/联机分叉；**M** = 体验明显受损；**L** = 代码卫生。
> 「当前可触发」列标注该缺陷是否需要额外条件才暴露。

### 2.1 类别 A · 不变量没有单一出口

| ID | 问题 | 位置 | 影响 | 级别 | 当前可触发 |
|---|---|---|---|---|---|
| **S3** | 负攻击力打脸变成给对手**回血** | `engine/CombatResolver.java:60-66` → `model/PlayerState.java:101` | `damage()` 只 `max(0, life-amount)`，无上限钳制；`atk<0` 时反向加血、可超 20 | **S** | 是——`CardDatabase.java:176` 允许 `attackBonus∈[-5,5]`，按 README 加卡即触发 |
| **S4** | 读档丢失「召唤失调/已出手」 | `service/SaveService.java:80-85`、`model/MinionCard.java:15` | 存档不落 `summoningSickness`/`attacksUsed`，重建默认 `true` → 读档当回合己方随从全不能攻击 | **S** | 是——「读档续玩」主推功能直接受损 |

**归因**：伤害的钳制规则散落在 `PlayerState.damage` / `MinionCard.takeDamage` / `CombatResolver` 三处，
且**打脸路径与随从互撞路径不一致**（互撞有 `Math.max(0,...)` 兜底，打脸没有）。

### 2.2 类别 B · 并发模型：锁与阻塞 IO 混在一起

| ID | 问题 | 位置 | 影响 | 级别 | 当前可触发 |
|---|---|---|---|---|---|
| **S2** | 持有房间锁做阻塞 socket 写 | `server/GameRoom.java:158`（`synchronized handle`）→ `server/RoomServer.java:122-128`（`flush()`） | 任一对端不读 → 发送缓冲填满 → `flush` 阻塞并**持有 GameRoom 监视器** → 另一位玩家动作全部卡死，服务端假死 | **S** | 是——窗口卡死/断点即可复现 |
| **M3** | `SoundEngine` Clip 池 check-then-act 竞态 + Clip 从不关闭 | `ui/fx/SoundEngine.java:62-76,119-137` | 预热线程与 UI 线程可同时通过 `containsKey` 检查，后写 `put` 孤立先前的 `Deque<Clip>`；`AudioInputStream` 从不关闭 | **M** | 是——启动后立刻交互 |
| **M5** | 对外返回可变 `PlayerState` | `client/RoomClient.java:172-179` | 类注释承诺「状态读取都带锁」，实际只锁了「取引用」；重放期间非 FX 调用方遍历 `getField()` 会 CME | **M** | 是——非 FX 嵌入方 |

**归因**：`GameRoom` 用「一把大锁 + 同步写」换取简单性，在单机/测试下正确，
但在真实网络下把「网络延迟」放大成「全局阻塞」。锁的职责应是**保护状态**，不是**等待网络**。

### 2.3 类别 C · 错误被静默吞掉

| ID | 问题 | 位置 | 影响 | 级别 |
|---|---|---|---|---|
| **M4** | 事件总线吞订阅者异常 | `event/GameEventBus.java:40-46` | `catch(RuntimeException)` 只打印并继续；联机路径 `GameRoom.onEvent` 就是订阅者之一，若其内部抛错则**广播缺条却不报错**，两端悄悄分叉 | **M** |
| **M2** | 联机端口非法 → 线程静默死亡 | `ui/CardGameApp.java:603-608,657-669` | 只 `catch IOException`；`new Socket(host,port)` 对 `port>65535` 抛 `IllegalArgumentException` → 状态栏永久停在「连接…」 | **M** |

**归因**：项目在「引擎异常」上做得很好（fail-fast 到文件:行），但在「UI/网络边界」上默认了
「异常=噪音」。联机场景下，**静默 = 分叉**，必须升级为「可见 + 可断言」。

### 2.4 类别 D · 权威标识靠推断而非传递

| ID | 问题 | 位置 | 影响 | 级别 | 当前可触发 |
|---|---|---|---|---|---|
| **S1** | 同名玩家 → 所有 SYNC 重放归座位 0 | `client/RoomClient.java:372`；`server/GameRoom.java:198` | 座位靠「actor 是否等于 `seatNames[0]`」反推；服务端不校验重名，两端同用 `-Dnet.name=小明` 时对手指令被判 `NOT_YOUR_TURN`，`divergences()` 非空、棋盘静默走散 | **S** | 是——README 仅口头警告「别重名」 |

**归因**：`START` 已经带了权威的 `youSeat`，但 `SYNC` 只带 `actor`（名字），
客户端只好**用名字反推座位**——这是把「身份」降级成「字符串相等」的经典错误。

### 2.5 类别 E · 生命周期与一致性缺口

| ID | 问题 | 位置 | 级别 |
|---|---|---|---|
| **M1** | `BoardViewModel.attach` 反复订阅同一引擎 | `ui/viewmodel/BoardViewModel.java:37-49`；`ui/CardGameApp.java:1276,1341` | **M**（回放步进 N 帧 → 多挂 ~2N 个监听器 → 后续每个事件触发 N 次刷新） |
| **M6** | 过牌法术不发 DRAW/BURN 事件 | `effect/DrawEffect.java:23-38` vs `engine/GameEngine.java:375-391` | **M**（打「奥术研读」无抽牌音效/动画；满手静默烧牌） |
| **L1** | 死代码 / 空转配置 / 硬编码 | `engine/Phase.java` 全类无引用；`ConfigService.java:37-38` 读了不生效；`AiTurnDirector` 步进间隔硬编码 | **L** |
| **L2** | 非 daemon 线程 / workers 只增不减 | `ui/CardGameApp.java:1086-1098`；`server/RoomServer.java:39,96-98` | **L** |
| **L3** | JSON 转义不全（`\b \f` 及 C0 控制字符） | `net/Json.java:21-35` | **L**（昵称经 `-Dnet.name` 可注入） |
| **L4** | 硬编码颜色/字号不走主题 CSS | `ui/view/MinionView.java:36-37`、`ui/CardGameApp.java:1449,1481` | **L**（亮色主题对比度无保证） |

---

## 三、目标架构调整（把裂缝规则化）

加固不是「改几行」，而是**给四类横切关注点各立一条可检查的规则**：

| 规则 | 内容 | 落地方式 | 对应缺陷 |
|---|---|---|---|
| **N1 · 不变量单出口** | 「伤害/治疗」只允许经 `CombatResolver` 的钳制入口；`PlayerState` 的生命变更必须同时满足 `[0, START_LIFE]` | 在 `CombatResolver` 引入 `applyDamage`/`applyHeal` 唯一入口，`damage()` 加双向钳制 | S3 |
| **N2 · 锁不等待 IO** | `GameRoom` 的锁只保护状态；广播移出锁（快照出站报文，锁外写） | 出站队列 + 单独写线程；`handle` 内只入队 | S2 |
| **N3 · 权威标识随协议传递** | 座位号由服务端在 `SYNC` 显式携带，客户端**不做任何推断** | `Protocol` 升 `VERSION=2`，`NetMessage.Sync` 加 `seat` 字段，旧版本明确拒绝 | S1 |
| **N4 · 边界异常必须可见** | 网络/UI 边界禁止空 catch；订阅者异常进入「分叉账本」而非 `stderr` | `GameEventBus` 异常上抛或记入可查询列表；UI 连接线程 catch `Exception` 并回写状态栏 | M2, M4 |
| **N5 · 订阅与资源成对** | 订阅必须可注销；`AutoCloseable` 覆盖 Socket/Clip/线程 | `BoardViewModel.detach()`；`SoundEngine.close()`；线程 `setDaemon(true)` | M1, M3, L2 |
| **N6 · 快照不可变** | 跨线程只传不可变视图，不传活集合 | `RoomClient.me()` 返回只读视图或快照 | M5 |

**版本与兼容**：N3 会动协议，`Protocol.VERSION 1→2`。按现有约定（`EVENT_SCHEMA` 升版仍兼容 v1 事件流），
本次对**联机协议**采取**硬升版**（M3 预览尚未发布、无线上客户端），事件 schema 不动。

---

## 四、任务分工

### 4.1 轨道划分（5 条 + 1 个收口角色）

按**改动面**而非按缺陷切轨，保证同一文件只被一条轨触碰，避免并行冲突。

| 轨道 | 负责角色 | 范围（独占文件） | 三轨产物 |
|---|---|---|---|
| **WS-1 引擎不变量与存档** | 引擎工程师 | `engine/CombatResolver`、`model/PlayerState`、`service/SaveService`、`effect/DrawEffect` | 代码 + `CombatClampTest`/`SaveCompatTest` 扩展 + CHANGELOG |
| **WS-2 联机权威与协议** | 联机工程师 | `net/*`、`server/GameRoom`、`client/RoomClient` | 代码 + `ProtocolCodecTest`/`GameRoomTest`/`RoomClientTest` 扩展 + `NETWORK.md` |
| **WS-3 并发与资源** | 并发工程师 | `server/RoomServer`、`ui/fx/SoundEngine`、`ui/viewmodel/BoardViewModel`、`event/GameEventBus` | 代码 + `RoomServerTest` 慢客户端用例 + 泄漏验证脚本 |
| **WS-4 体验打磨** | UI 工程师 | `ui/CardGameApp`、`ui/view/*`、`resources/theme-*.css` | 代码 + 双主题截图对比 + README |
| **WS-5 验证与门禁** | QA | `src/test/**`（新增用例）、`scripts/dev-verify.sh` | 测试 + 门禁脚本 + 验证报告 |
| **收口 · 架构与合并** | 架构负责人 | 接口冻结、code review、合并、发版 | 评审记录 + 发版 |

> **独占原则**：`ui/CardGameApp.java` 只归 WS-4；`GameEventBus` 只归 WS-3。跨轨依赖走**接口冻结**，
> 不互相改文件——这是并行不冲突的前提。

### 4.2 任务清单

#### WS-1 · 引擎不变量与存档

| # | 任务 | 对应 | 验收 |
|---|---|---|---|
| 1.1 | 伤害/治疗唯一入口 + 生命双向钳制 `[0, START_LIFE]` | S3 | 新增 `CombatClampTest`：负攻打脸不回血；治疗不超上限 |
| 1.2 | 存档补 `summoningSickness`/`attacksUsed`（旧档缺字段走默认，`SAVE_VERSION` 保持 1） | S4 | `SaveCompatTest` 增断言：读档后随从可攻击 |
| 1.3 | 过牌法术改走统一抽牌管线，发 DRAW/BURN 事件 | M6 | 新用例：满手时过牌发 BURN |
| 1.4 | 同归于尽的胜负判定明确定义（而非恒判 AI 胜） | 审计 L | 单测覆盖双方同时 0 血 |

#### WS-2 · 联机权威与协议

| # | 任务 | 对应 | 验收 |
|---|---|---|---|
| 2.1 | `Protocol.VERSION 1→2`；`NetMessage.Sync` 增 `seat`；`MessageCodec` 同步 | S1 | `ProtocolCodecTest` 黄金样例更新 + 旧版本被拒 |
| 2.2 | `RoomClient.replay` 改用显式 `seat`，删除名字推断 | S1 | 新增用例：**同名两端**打完一局，`divergences()` 为空 |
| 2.3 | 服务端 `join` 增加重名检测，重名回 `ERROR`（防御性，配合 2.1） | S1 | `GameRoomTest`：重名被拒 |
| 2.4 | 同步更新 `NETWORK.md` 协议表与错误码 | 文档 | 表格与实际字段一致 |

#### WS-3 · 并发与资源

| # | 任务 | 对应 | 验收 |
|---|---|---|---|
| 3.1 | `GameRoom` 广播移出锁：出站队列 + 写线程；`handle` 只入队 | S2 | 新用例：**一个客户端不读**时，另一位指令仍在 1s 内被处理 |
| 3.2 | `GameEventBus` 订阅者异常不再静默（记入可查询账本 + 可选上抛） | M4 | 新用例：订阅者抛异常可被检测到 |
| 3.3 | `SoundEngine` 池加锁 + `close()`；`AudioInputStream` 用 try-with-resources | M3 | 预热/播放并发冒烟无泄漏 |
| 3.4 | `BoardViewModel.detach()`，`CardGameApp` 进出回放成对调用 | M1 | 新用例：步进 N 帧后监听器数量不增长 |
| 3.5 | `RoomClient.me()/opponent()` 返回不可变快照 | M5 | 新用例：重放期间遍历不抛 CME |

#### WS-4 · 体验打磨

| # | 任务 | 对应 | 验收 |
|---|---|---|---|
| 4.1 | 连接线程 catch `Exception`；非法端口/主机即时回写状态栏并复位按钮 | M2 | 手工：输入 `127.0.0.1:99999` 有明确报错 |
| 4.2 | 置灰卡牌拦截点击（不只是加样式） | 审计 L | 手工：点置灰卡无反应 |
| 4.3 | 硬编码颜色/字号迁入主题 CSS；亮色主题对比度复检 | L4 | 双主题截图，卡名/伤害数字可读 |
| 4.4 | `ConfigService` 的 `ai.level`/`ai.stepGapMs` 接线，或删除空转配置项 | L1 | 配置改动实际生效（或字段移除） |
| 4.5 | 删除死代码（`Phase`、`requireBool`、`shownLife`） | L1 | 编译通过 + 全绿 |

#### WS-5 · 验证与门禁

| # | 任务 | 验收 |
|---|---|---|
| 5.1 | 为每条缺陷补回归用例（**先红后绿**） | 每个修复都有对应用例 |
| 5.2 | 新增「慢客户端」与「同名双端」两个集成用例 | 覆盖 S1/S2 的真实触发条件 |
| 5.3 | 门禁三件套：`mvn -o verify` + `autoplay=10` + 双主题截图 | 全绿 |
| 5.4 | 汇总验证报告（缺陷 ID → 用例 → 结论） | 文档 |

### 4.3 依赖与批次

```
批次 1（可并行，无跨轨依赖）
  WS-1 1.1  1.2  1.3  1.4        WS-3 3.2  3.3  3.4  3.5        WS-4 4.1~4.5
                │
批次 2（依赖批次 1 的接口冻结）
  WS-2 2.1 → 2.2 → 2.3 → 2.4      WS-3 3.1（依赖 3.2 的异常策略）
                │
批次 3（收口）
  WS-5 全量回归 → 架构负责人 review → CHANGELOG → 发版判定
```

**关键路径**：`3.2（异常策略）→ 3.1（广播移出锁）→ 5.2（慢客户端集成）`。
WS-1 与 WS-4 完全独立，可全程并行。

**接口冻结点**：批次 1 结束时冻结三处——`CombatResolver` 的伤害入口签名、`GameEventBus` 的异常契约、
`Protocol` 的版本号与 `Sync` 字段。冻结后 WS-2/WS-3 不得再改他人文件。

---

## 五、门禁与验收

沿用 `docs/dev-workflow.md` 的 S3 门禁，逐条加严：

| 门禁 | 命令 | 通过标准 |
|---|---|---|
| 编译 + 全量单测 | `scripts/dev-verify.sh` | 用例数 ≥ 172 + 新增数，**零失败** |
| 整局冒烟 | `java -Dui.autoplay=10 -cp ... Main` | exit=0，打满回合或提前终局 |
| 双主题截图 | `java -Dui.screenshot=docs/screenshot.png ...` + `-Dui.theme=light` | 无破版，文字可读 |
| 红线复检 | `HeadlessGameTest.engineSideClassesDoNotReferenceJavafx` | 绿 |
| 双端一致性 | `RoomClientTest`（含新增同名用例） | `divergences()` 为空 |

**完成定义（DoD）**：每条缺陷 = 一条**先红后绿**的回归用例 + 修复 + `CHANGELOG` 一笔。
没有用例的修复不算完成。

---

## 六、风险与止损

| 风险 | 概率 | 对策 |
|---|---|---|
| 协议升版（N3）波及 `GameRoomTest`/`RoomClientTest` 大量断言 | 高 | 批次 1 结束即冻结协议；一次性改完，不留半版本状态 |
| 广播移出锁（N2）引入新的乱序/丢包 | 中 | 出站队列按序单线程写；加「慢客户端」集成用例钉死 |
| 并行轨互相改文件导致冲突 | 中 | 独占原则 + 接口冻结；冲突即停线，由收口角色裁定 |
| 加固期引入回归 | 低 | 三件套门禁 + 每缺陷独立用例；红灯停线不合并 |

---

## 七、一页纸总结

> **架构判断**：分层与 seam 是健康的（引擎零 JavaFX、事件总线解耦、指令同步反作弊），
> 裂缝全在**四条横切关注点**——不变量单出口、锁不等待 IO、权威标识随协议传递、边界异常可见。
>
> **最高优先级**（违反核心承诺，必须先修）：
> **S1 同名分叉**（破坏「客户端状态 = 服务端接受序列」）、**S2 慢客户端冻房**（服务端唯一抗压短板）、
> **S3 负攻回血**（README 承诺的加卡流程即可触发）、**S4 读档丢召唤失调**（主推功能直接受损）。
>
> **分工**：5 条独占文件域的轨道并行 + 1 个架构收口角色；批次 1（WS-1/3/4）→ 批次 2（WS-2 + 3.1）→ 批次 3（回归发版）。
> **门禁**：`dev-verify.sh` + `autoplay=10` + 双主题截图 + 红线复检 + 双端一致性，**红灯停线**。
> **完成定义**：一缺陷 = 一回归用例（先红后绿）+ 修复 + CHANGELOG。
