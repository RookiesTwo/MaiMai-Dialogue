---
layout: home
title: MaiMai Dialogue
titleTemplate: false

hero:
  name: MaiMai Dialogue
  text: 数据驱动的现代对话引擎
  tagline: 面向 Minecraft 1.21.1、NeoForge 与 Modern UI，为整合包和 MOD 提供 Dialogue、分支、进度条件与场景表现。
  actions:
    - theme: brand
      text: 快速入门
      link: /guide/quick-start
    - theme: alt
      text: 对话格式
      link: /content/dialogues

features:
  - title: 数据驱动
    details: 使用 JSON 定义对话、说话者、主题、场景对象和动画，无需为每段内容编写 Java 代码。
  - title: 服务端校验
    details: logical server 根据 ProgressNode 和 requires 权威决定玩家能否打开目标 Dialogue。
  - title: 可定制表现
    details: 支持 Markdown 正文、打字机、背景、多个 VisualObject、关键帧 Action、Theme 与场景滤镜。
---

## 选择你的入口

- 第一次制作内容：从[快速入门](./guide/quick-start.md)开始。
- 安装或发布整合包：阅读[安装与双端分发](./guide/installation.md)和[资源组织](./content/resources.md)。
- 编写对话：阅读[Dialogue 与流程](./content/dialogues.md)。
- 制作演出：阅读[场景表现](./content/presentation.md)、[PresentationAction](./content/actions.md)和[Theme](./content/themes.md)。
- 管理任务进度：阅读[ProgressNode 与访问条件](./content/progress.md)和[命令参考](./administration/commands.md)。
- 从其他 MOD 接入：阅读[Java API](./integration/java-api.md)。

::: warning Alpha 状态
当前版本为 `0.1.0-alpha`。数据格式、Java API 和表现行为仍可能发生不兼容变更，请先在测试环境验证并备份重要存档。
:::

MaiMai Dialogue 是专注的通用对话引擎，不绑定特定 NPC、实体或任务系统。它不会替代任务系统，也不会自动修改任务进度；由命令、数据包逻辑或第三方 MOD 决定何时打开对话及何时变更 ProgressNode。

