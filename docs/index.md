---
layout: home
title: MaiMai Dialogue
titleTemplate: false

hero:
  name: MaiMai Dialogue
  text: 从第一句话开始制作 Minecraft 对话
  tagline: 用 JSON 编写正文、分支、任务条件、立绘、动画和主题，不必为每段内容编写 Java 代码。
  actions:
    - theme: brand
      text: 从安装开始
      link: /start/installation
    - theme: alt
      text: 创建第一段对话
      link: /start/first-dialogue

features:
  - title: 写内容，不写界面代码
    details: Dialogue、Speaker、背景、视觉对象、动画和主题都由资源文件定义。
  - title: 做出有条件的分支
    details: 使用选项、子 Dialogue 和 ProgressNode，根据玩家进度决定哪些内容可以进入。
  - title: 同一套内容用于单人与服务器
    details: “能不能看”由数据包决定，“看到什么”由资源包决定；文档会带你正确准备并分发双端资源。
---

## 它适合谁

这套文档主要写给整合包作者、服务器内容作者和剧情内容作者。你只需要会创建 Resource Pack、Data Pack、编辑 JSON，并能在测试世界中执行命令；不需要先理解 MaiMai Dialogue 的内部代码。

如果你负责服务器管理，可以直接查看[命令与管理](./publish/commands.md)。如果你正在开发另一个 NeoForge MOD，可以从 [Java API](./integration/java-api.md) 接入。

## 四步开始

1. [安装 MaiMai Dialogue](./start/installation.md)，准备一个 Minecraft 1.21.1 测试实例。
2. [认识 Dialogue](./start/dialogue.md)，理解对话文件能做什么，以及为什么每段对话有两份。
3. [创建内容包](./start/content-project.md)，建立配套的 Resource Pack 与 Data Pack。
4. [制作第一段对话](./start/first-dialogue.md)，进入世界亲自打开它。

完成后，沿左侧目录逐章加入 Speaker、Markdown、选项、Progress、背景、立绘、动画和主题。所有教程都会继续使用同一个 `example` 示例工程。

## 常用资源

- [常见任务速查](./reference/quick-recipes.md)：想做某件事时，该改哪个文件、用哪个字段
- [概念总览](./concepts/overview.md)：所有资源类型和它们的关系，一张图看懂
- [术语表](./concepts/glossary.md)：全部术语的白话解释
- [故障排查](./reference/troubleshooting.md)：打不开、不显示时按现象排查
- 不想手打示例？直接下载[示例资源包](/MaiMai-Dialogue/examples/example_dialogue_resources-1.0.0.zip)与[示例数据包](/MaiMai-Dialogue/examples/example_dialogue_data-1.0.0.zip)

<!-- TODO(截图): hero 区域或本页下方放一张内置 Demo 打开后的截图 -->

::: warning 当前版本
MaiMai Dialogue 当前为 `0.1.0-alpha`。正式发布内容包前，请在测试环境走完所有对话路径，并备份重要存档。
:::
