---
name: dev-workflow
description: CardGame 项目的成体系开发流水线：需求识别 → todo 拆步 → 子代理并行实现 → 本地预检(mvn verify) → 提交/CI 三包 → 打 tag 发 Release；大需求挂 goal 跨轮推进。
whenToUse: 当用户对本项目提出开发需求（新功能/修复/重构/发布），或说「按 workflow / dev-workflow 做 X」「发个版本」时使用。
---

# CardGame 开发工作流（执行手册）

开始时向用户声明进入哪一阶段；按 S0→S6 顺序推进。**加粗句子是阶段门禁，不满足不得进入下一阶段。**

## S0 需求识别
- 小改动（预计 ≤2 个文件）：跳过 S1/S2，直接干完进 S3。
- 预计跨多轮、或超过 4 个独立步骤的大需求：先 `create_goal`（objective 写成**可验收**的完成定义，如“XX 机制上线 + 测试覆盖 + CI 三包绿 + CHANGELOG 更新”），后续每轮对着 goal 推进，防止对话中断失焦。
- 动手前读 `docs/PLAN.md`、`docs/PLAN-v1.5-v2.0.md`、`docs/BACKLOG.md`、`docs/BALANCE.md`，对齐版本规划，避免与排期打架。

## S1 拆步骤
- 用 `todo_write` 列出具体步骤（每步一个可检验的产物），随做随更新状态；进行中的步骤保持 in_progress。
- 把产物拆成可并行三轨：**改代码** / **补测试** / **文档图表**（CHANGELOG、README、BALANCE、archify 架构图）。

## S2 并行实现
- 独立轨道在同一条消息里发多个 `subagent_fork`（后台），主线程同时做集成/收口工作；子代理完成会自动通知，**不要轮询等待**。
- 委托前按 `subagent-model-routing` 策略选模型：简单机械活 `mimo-v2.5`；检索/流水线执行 `deepseek-v4-flash`；复杂逻辑与关键设计 `glm-5.3`。当前调用接口若不暴露模型参数则继承父模型并在汇报中说明。
- 三轨只许改各自负责的文件，接口约定写进 prompt，减少合并冲突。

## S3 本地预检（门禁一）
- 后台跑 `tools.bash({ command: "scripts/dev-verify.sh", run_in_background: true })`，记下 jobId；期间做 S1 剩余项，用 `job_output(wait: true)` 收结果。
- 失败就读取失败测试/编译错误，修复后**重跑整个脚本**。mvn verify 全绿之前，**不得 commit**。
- 涉及 JavaFX 界面：追加 `mvn -Dmaven.repo.local=.m2repo javafx:run` 冒烟或诊断截图。

## S4 提交与推送（门禁二）
- 提交信息对齐 git 历史惯例：`feat:` / `fix:` / `chore:` / `docs:` / `test:` / `release:` + 一句话目标。
- 一个提交只做一件事；`.m2repo/`、`target/`、`.idea/` 不入库。
- 推 `main`（remote 名为 `github`）后 CI 自动跑 pr-ci 单测门禁 + 三包 artifact（不出 Release）。可用 web_fetch 查 Actions 状态并向用户汇报。

## S5 发布（仅当用户明确要求“发版/出 Release”）
- 先在 `docs/CHANGELOG.md` 顶部补 `## vX.Y.Z（主题）` 小节，风格照现有条目。
- 跑 `scripts/release.sh <x.y.z> ["发布说明"]`：检查干净工作区 + 本地预检 + 打 `vX.Y.Z` annotated tag + push --follow-tags；推 tag 才触发 windows/unix-package 两条 workflow 发 GitHub Release。
- **版本号只跟着 tag 走；不要改 pom 的 1.0-SNAPSHOT** —— 打包 workflow 写死了产物 jar 名 `card-game-1.0-SNAPSHOT.jar`。
- **未获用户明确同意不得 push tag**。

## S6 收口
- 向用户汇报：测试通过数、CI 运行状态、产物/Release 链接。
- 若挂了 goal：全部验收点满足才 `update_goal(complete)`；否则如实说明剩余项。

## 边界（任何阶段都适用）
- 不 force push、不 rebase 已推送历史。
- 破坏性/外发操作（push、tag、删文件）先向用户确认。
- 声称“完成”必须附证据（命令输出、测试计数、CI 结论）。
