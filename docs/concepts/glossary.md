---
title: 术语表
description: 全部术语的中文名、英文名、白话定义和详细参考链接。
---

# 术语表

所有术语首次出现时都写成"中文名（英文名）"。JSON 字段名、目录名和命令不受影响，仍保持英文。

## 内容与流程

| 中文名 | 英文名 | 白话定义 | 详细参考 |
|---|---|---|---|
| 对话 | Dialogue | 一段完整的剧情内容：若干步骤、一个结尾、可选的进度条件 | [第一段对话](../start/first-dialogue.md) / [Dialogue JSON](../reference/dialogue-json.md) |
| 步骤 | Step | 对话里的一页文字，按顺序播放 | [步骤与推进](../dialogue/steps.md) |
| 结尾 | End | 对话的最后一页，必须带一个"怎么离开"的设置（exit） | [Dialogue JSON](../reference/dialogue-json.md) |
| 选项 | Option | 结尾处让玩家点击选择的条目，可以指向另一段对话、返回或关闭 | [选项与子对话](../dialogue/choices.md) |
| 说话者 | Speaker | 一个名字，显示在文字上方；可在步骤中随时切换或隐藏 | [显示 Speaker](../dialogue/speaker.md) |
| 进度节点 | ProgressNode | 玩家身上的一个标记（有或没有），用来控制对话和选项能不能访问 | [Progress 条件](../dialogue/progress.md) / [Progress 表达式](../reference/progress-expression.md) |
| 会话 | Session | 玩家从打开对话到界面关闭的整个过程 | [会话与导航](./session.md) |

## 画面与演出

| 中文名 | 英文名 | 白话定义 | 详细参考 |
|---|---|---|---|
| 演出配置 | Presentation | 一段对话的显示方案：用哪个主题、哪个场景、对话框放哪 | [Presentation JSON](../reference/presentation-json.md) |
| 演出配置定义 | PresentationDefinition | 存成独立文件的演出配置，多个对话可以用代号引用它 | [可复用 Presentation](../reference/presentation-definition-json.md) |
| 场景 | Scene | 一套可复用的画面组合：背景 + 视觉对象 + 滤镜 | [Scene JSON](../reference/scene-json.md) |
| 背景 | Background | 对话框后面的整张图片，可以有多张"差分"并在动画中切换 | [添加背景](../scene/background.md) |
| 视觉对象 | VisualObject | 画面上的单个图片对象（如一个立绘、一个道具图标），可移动、缩放、换图、显示或隐藏 | [添加 VisualObject](../scene/visual-objects.md) |
| 视觉资源 | VisualAsset | 给一组图片（差分）起代号的地方，视觉对象引用它来使用图片 | [VisualAsset JSON](../reference/visual-asset-json.md) |
| 场景动作 | SceneAction | 一段可复用的动画：让视觉对象移动、缩放、变透明、换差分，也可作用于背景和对话框 | [播放 SceneAction](../scene/actions.md) / [SceneAction JSON](../reference/scene-action-json.md) |
| 滤镜 | Filter | 对背景和视觉对象整体施加的画面效果（调色、CRT 屏幕效果） | [添加场景滤镜](../scene/filters.md) |
| 对话框 | DialogueBox | 屏幕下方显示文字、说话者、选项的方框 | [调整对话框布局](../scene/dialogue-box.md) |
| 主题 | Theme | 对话界面的外观设置：对话框颜色、边框、文字大小、选项按钮样式 | [制作 Theme](../scene/themes.md) / [Theme JSON](../reference/theme-json.md) |
| 差分 | Variant | 同一个视觉对象的不同图片（如不同表情），可在动画中切换 | [VisualAsset JSON](../reference/visual-asset-json.md) |

## 写法

| 中文名 | 英文名 | 白话定义 |
|---|---|---|
| 内联 | inline | 直接把配置写在对话文件里，不单独存文件 |
| 引用 | reference | 用代号指向一个独立文件（演出配置定义或外部动画） |
| 代号 | Resource ID | 资源的唯一名称，格式为"命名空间:路径"，如 `example:guide/welcome` | 

## 端与包

| 中文名 | 英文名 | 白话定义 |
|---|---|---|
| 数据包 | Data Pack | 放进服务端或世界存档的文件夹，存放对话的服务端副本 |
| 资源包 | Resource Pack | 放进客户端的文件夹，存放文字、画面和界面资源 |
| 双端 | 客户端 + 服务端 | 同一份对话 JSON 需要在两个包里各放一份 |
