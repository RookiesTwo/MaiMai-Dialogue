---
title: 常见任务速查
description: 按"我想做什么"查找该改哪个文件、用哪个字段。
---

# 常见任务速查

按"我想做什么"查找该动哪个文件。文件路径与 ID 规则见[资源路径与 ID](./resource-paths.md)，完整字段见左侧参考资料各页。

## 对话内容

| 我想做 | 改哪个文件 | 关键字段 | 教程 |
|---|---|---|---|
| 写一段新对话 | 数据包 + 资源包 `dialogues/...`（两份） | `steps`、`end`、`exit` | [第一段对话](../start/first-dialogue.md) |
| 只有满足条件才能打开 | 同上 | `requires` | [Progress 条件](../dialogue/progress.md) |
| 一页随机显示几句中的一句 | 同上 | `text: [ ... ]` | [步骤与推进](../dialogue/steps.md) |
| 加速 / 放慢打字机 | 同上 | `typewriter_interval_ms` | [步骤与推进](../dialogue/steps.md) |
| 显示说话者名字 | 资源包 `speakers/...` + 步骤 | `name`；`speaker: {type: set}` | [显示 Speaker](../dialogue/speaker.md) |
| 正文里用标题、粗体、列表 | 对话文件 | `text`（支持 Markdown） | [Markdown 正文](../dialogue/markdown.md) |
| 结尾给玩家选项 | 对话文件 | `exit: {type: options}` | [选项与子对话](../dialogue/choices.md) |
| 点选项时执行命令 | 对话文件 | Option 的 `command` | [选项与子对话](../dialogue/choices.md) |
| 直接关掉整个对话 | 对话文件 | `target: {type: close}` | [选项与子对话](../dialogue/choices.md) |
| 播完自动进入下一段 | 对话文件 | `exit: {type: dialogue}` | [选项与子对话](../dialogue/choices.md) |
| 跳过后让玩家确认 | 对话文件 | `skip_summary` | [Dialogue JSON](./dialogue-json.md) |

## 画面与动画

| 我想做 | 改哪个文件 | 关键字段 | 教程 |
|---|---|---|---|
| 换一张背景 | 资源包 `scenes/...`，或对话 `presentation.background` | `background` | [添加背景](../scene/background.md) |
| 放一张立绘 / 道具图 | 资源包 `visual_assets/...` + `scenes/...` | `asset`、`visual_objects` | [添加 VisualObject](../scene/visual-objects.md) |
| 立绘换表情（切换差分） | 视觉资源 / 场景 + 动画 | `variants`、Action 的 `variant` | [播放 SceneAction](../scene/actions.md) |
| 播放一段动画 | 资源包 `actions/...`，或直接写在步骤里 | 步骤 `actions`、`reference` / `inline` | [播放 SceneAction](../scene/actions.md) |
| 用 MOD 自带的动画 | 不改文件，直接引用 | `maimai_dialogue:presets/<名称>` | [内置预设 SceneAction](./preset-actions.md) |
| 调整对话框位置 / 宽度 | 资源包 `presentations/...` | `dialogue_box` | [调整对话框布局](../scene/dialogue-box.md) |
| 给画面加滤镜 | 同上 | `filter` | [添加场景滤镜](../scene/filters.md) |
| 改对话框配色 / 字号 | 资源包 `themes/...` | `box`、`text`、`option` | [制作 Theme](../scene/themes.md) |
| 多个对话共用一套画面 | 资源包 `presentations/...`，对话里引用 | `presentation: {type: reference}` | [添加 VisualObject](../scene/visual-objects.md) |

## 条件与运营

| 我想做 | 改哪个文件 | 关键字段 | 教程 |
|---|---|---|---|
| 给玩家加 / 删剧情节点 | 不改文件，用命令 | `/maimai_dialogue progress add/remove` | [命令与管理](../publish/commands.md) |
| 在命令方块里判断节点 | 不改文件 | `progress check` | [命令与管理](../publish/commands.md) |
| 把内容发布给服务器和玩家 | 打包两个 ZIP | 检查清单 | [双端发布](../publish/client-server.md) |
| 排查"打不开 / 不显示" | 不改文件 | 按现象查 | [故障排查](./troubleshooting.md) |

## 下一步

- 完整字段参考：[Dialogue JSON](./dialogue-json.md)、[Presentation JSON](./presentation-json.md)、[SceneAction JSON](./scene-action-json.md)
- 所有术语的含义：[术语表](../concepts/glossary.md)
