# CardGame · 卡牌对战

一个炉石风格的卡牌对战游戏（玩家 vs AI），暗色奇幻美术风格，Java + JavaFX 实现。

![游戏截图](docs/screenshot.png)

## 下载游玩（Windows）

**➡️ [点此下载最新版](https://github.com/pvehbo/card-game/releases/latest)**

下载 `CardGame-<版本>-windows-x64.exe`，双击安装即可开始游戏。

- 仅支持 Windows 64 位
- **已内置运行环境，无需安装 Java**
- 安装后桌面和开始菜单会有快捷方式

## 玩法规则

双方英雄各有 **20 点生命**，先把对方打到 0 者获胜。

每回合可做这些事（每项各限 1 次）：

| 操作 | 说明 |
|---|---|
| **出 1 张随从** | 上场后可以攻击（刚上场当回合**不能**攻击，叫「召唤失调」） |
| **用 1 张法术** | 造成伤害 / 回复生命 / 抽牌 |
| **召唤 1 只宠物** | 不可攻击、不可被攻击，提供**常驻光环**：给己方全体随从加攻或加血 |

- 随从互相攻击时**双方同时受伤**，血量归零即阵亡
- 对方场上没有随从时，可以直接攻击对方英雄
- 场上最多 7 个随从，手牌上限 10 张

## 操作方法

1. 点 **「开始游戏」** 开局（随机决定先后手）
2. **出牌**：点击手牌区任意一张卡
3. **攻击**：先点自己的随从（会高亮），再点**对方的随从**或**对方的英雄头像**
4. **结束回合**：点「结束回合」按钮，交给 AI 行动

界面提示：
- 显示 `Zzz` 的随从表示召唤失调，本回合不能攻击
- 金色外圈 = 你的回合；红色外圈 = 可攻击目标

## AI 对手

内置的 AI 会：
- 上场攻击力最高的随从
- 生命值低于 10 时优先治疗
- 优先攻击你血量最低的随从，你场上没随从时直接打脸
- 发现能斩杀时立刻使用伤害法术

## 本地运行（开发）

需要 **JDK 17+** 和 **Maven**。

```bash
# 直接运行
mvn javafx:run

# 跑测试
mvn test
```

## 打包

**macOS / Linux：**
```bash
./package.sh
```
产物在 `target/pkg/output/`。

**Windows：**
```bat
package.bat
```
产物在 `target\pkg\output\`。

两个脚本都会用 `jlink` 构建**精简运行时**（只打包游戏需要的 10 个模块，而非整个 JDK），再交给 `jpackage` 生成免安装的应用程序。

## 自定义卡图与美术

游戏支持直接替换图片资源。把图片放进 `src/main/resources/images/` 对应目录即可自动生效，无需改代码：

```
images/
├── cards/         卡图（按卡牌 id 或名称命名，如 m1.png / 幼龙.png）
├── heroes/        英雄头像（player.png / ai.png）
└── backgrounds/   战场背景（board.jpg）
```

详细规格（尺寸、格式、命名）见 [`src/main/resources/images/README.md`](src/main/resources/images/README.md)。

**没有图片也能正常运行** —— 会自动回退到内置的程序化图形。

## 技术栈

- **Java 17** + **JavaFX 21**（界面）
- **Maven**（构建）
- **JUnit 5**（规则引擎单元测试）
- 架构分层：`model`（数据）/ `engine`（规则）/ `ai`（AI）/ `event`（事件总线）/ `ui`（界面）

引擎通过**事件总线**发布游戏事件（抽卡、上场、攻击、受伤、阵亡、回合切换），界面只订阅事件做表现，两者完全解耦 —— 便于后续持续扩展新玩法。

## 项目结构

```
src/main/java/org/example/card/
├── model/     卡牌与玩家数据模型（Card / MinionCard / SpellCard / PetCard / PlayerState / Deck）
├── engine/    游戏规则引擎（GameEngine 处理回合、出牌、战斗结算）
├── ai/        AI 决策（SimpleAi）
├── event/     事件总线（GameEvent / GameEventBus）
└── ui/        JavaFX 界面（CardGameApp / CardView / MinionView / HeroView / Assets / Fonts）
```
