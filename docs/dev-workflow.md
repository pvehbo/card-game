# CardGame 开发工作流（成体系）

这套流程有两个消费者：**人**（照 SOP 操作）和 **Agent**（DSH 会话读取
[`.dsh/skills/dev-workflow/SKILL.md`](../.dsh/skills/dev-workflow/SKILL.md) 后照章执行）。
项目级 skill 在仓库里，随 git 共享；DSH 监视该目录，改动即时生效、无需重启。

## 全景

| 阶段 | 干什么 | 工具 / 门禁 |
|---|---|---|
| S0 需求识别 | 判断小改动 or 大需求；大需求挂 **goal** 跨轮推进 | `create_goal`；读 PLAN/BACKLOG 对齐排期 |
| S1 拆步骤 | 拆成可跟踪的步骤 + 三轨产物（代码/测试/文档） | `todo_write` |
| S2 并行实现 | 三轨同时干；按模型路由省额度 | `subagent_fork`（配合 subagent-model-routing） |
| S3 本地预检 | 编译 + 全量单测（+ 可选 Qodana） | `scripts/dev-verify.sh`；**不过不 commit** |
| S4 提交推送 | 惯例提交信息，推 main | `feat:/fix:/chore:/docs:/test:/release:` |
| CI | PR/main 单测门禁；main 三包 artifact | pr-ci.yml / qodana / windows-、unix-package.yml |
| S5 发版 | 补 CHANGELOG → 打 tag → 自动 Release | `scripts/release.sh <x.y.z>`；**需人工确认** |

## 常用命令

```bash
scripts/dev-verify.sh            # 预检（mvn verify，与 CI 同仓库路径）
scripts/dev-verify.sh --fast     # 只跑单测
scripts/release.sh 1.5.0 "说明"  # 发版：tag v1.5.0 并推送 → Release
```

## CI/CD 触发矩阵

| 事件 | 跑什么 | 产物 |
|---|---|---|
| 提 PR 到 main | pr-ci.yml（mvn verify）+ Qodana | 测试报告（失败挡合并） |
| push main | pr-ci.yml + Windows/Unix Package | 三包 artifact（不发 Release） |
| push v* tag | Windows/Unix Package | exe/dmg/deb → **GitHub Release** |
| 手动 | 三条打包流均可 workflow_dispatch | 按需 |

## 约定

- **版本号只跟 tag 走**：workflow 里 jar 名写死 `card-game-1.0-SNAPSHOT.jar`，不要动 pom 版本。
- remote 名为 `github`；只在 `main` 上发版；release.sh 自带干净工作区 / 重复 tag 检查。
- 每个版本发布前先在 [docs/CHANGELOG.md](CHANGELOG.md) 顶部补 `## vX.Y.Z（主题）` 小节。

## 怎么让 Agent 跑这套流程

在 DSH 会话里直接说：

> 按 dev-workflow 实现：<需求描述>

Agent 会加载项目 skill，从 S0 开始按门禁推进：大需求挂 goal、拆 todo、派子代理并行、
后台跑预检，收口时汇报测试数与 CI 状态。
