# MaiMai Dialogue

MaiMai Dialogue 是一个面向 Minecraft 1.21.1、基于 NeoForge 与 Modern UI 的通用对话引擎 MOD。

它主要为整合包作者和其他 MOD 开发者提供数据驱动的现代化对话能力，不与特定 NPC、实体或任务系统绑定。对话内容可以通过资源包与数据包定义，并通过命令或 Java API 打开。

> [!WARNING]
> **AI 生成声明：本项目的大多数代码由 AI 生成。** 项目维护者负责需求定义、架构决策、代码审查与测试。当前项目仍处于早期开发阶段，使用前请充分测试。

## 主要功能

- 使用 JSON 定义 Dialogue、Speaker、Theme、场景和表现动画；
- 支持 Markdown 正文、打字机效果、选项分支与会话历史；
- 支持背景、多个画面对象、差分切换、关键帧动画及场景滤镜；
- 使用 ProgressNode 与条件表达式控制对话访问；
- 提供服务端命令和 Java API，方便整合包与其他 MOD 接入；
- 客户端负责表现和播放，logical server 负责权威访问校验。

## 当前状态

当前版本为 **0.1.0-alpha**。核心原型已经可以运行，但数据格式、Java API 和视觉表现仍可能发生不兼容变更，暂不建议直接用于正式存档。

项目当前面向：

- Minecraft 1.21.1
- NeoForge 21.x
- Modern UI 3.13.0.1 或更高版本
- Java 21（开发与构建）

## 开发与体验

构建项目：

```powershell
.\gradlew.bat build
```

启动开发客户端：

```powershell
.\gradlew.bat runClient
```

进入世界后，可以通过内置示例体验主要功能：

```mcfunction
/maimai_dialogue open @s maimai_dialogue:demo/root
```

该命令默认需要 permission level 2。

## License

本项目声明使用 MIT License。
