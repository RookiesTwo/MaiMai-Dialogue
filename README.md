# MaiMai Dialogue

MaiMai Dialogue 是面向 Minecraft 1.21.1、NeoForge 与 Modern UI 的数据驱动对话引擎，主要服务于整合包作者、服务器内容作者和需要接入对话功能的 MOD 开发者。

你可以通过 Resource Pack 与 Data Pack，使用 JSON 制作 Dialogue、Speaker、选项分支、Progress 条件、背景、VisualObject、SceneAction 和 Theme。

## 开始使用

完整的中文教程与参考资料：

**https://rookiestwo.github.io/MaiMai-Dialogue/**

MOD 下载：**https://github.com/RookiesTwo/MaiMai-Dialogue/releases**

安装内置开发版本后，可在测试世界执行：

```text
/maimai_dialogue open @s maimai_dialogue:debug/root
```

该命令默认需要 permission level 2。

## 兼容版本

- Minecraft 1.21.1
- NeoForge 21.x
- Modern UI 3.13.0.1 或更高版本（客户端）
- MaiMai Dialogue 0.1.0-alpha

当前仍是 alpha 版本，数据格式、Java API 和表现行为可能发生不兼容变化。请先在测试环境验证，不建议直接用于无法恢复的正式存档。

> [!WARNING]
> **AI 生成声明：本项目的大多数代码由 AI 生成。** 项目维护者负责需求定义、架构决策、代码审查与测试。

## 开发与附属编辑器

根项目继续构建主 MOD；`editor/` 为独立的客户端编辑器附属 MOD，目前提供可调整分区的工作台和本地项目管理，内容编辑、预览和导出分阶段实现。

在 IDEA 重新加载 Gradle 后，可通过 `Build Main`、`Build Editor`、`Build All` 分别构建；开发启动统一使用 `Client`，同时加载主 MOD 和编辑器。主 MOD 与编辑器分别生成独立 JAR，服务器与普通玩家无需安装编辑器。

## License

MIT License
