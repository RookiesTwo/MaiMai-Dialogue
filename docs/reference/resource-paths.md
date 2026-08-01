---
title: 资源路径与 ID
description: 查询 Dialogue、Speaker、Theme、SceneAction 和图片的目录映射规则。
---

# 资源路径与 ID

## Pack 根目录

教程中的两个占位符分别表示：

```text
<资源包> = <游戏实例>/resourcepacks/example_dialogue_resources
<数据包> = <世界目录>/datapacks/example_dialogue_data
```

两个目录根部都需要 `pack.mcmeta`。Minecraft 1.21.1 的 Resource Pack 使用 `pack_format: 34`，Data Pack 使用 `pack_format: 48`。

## 路径映射

| 资源 | 文件路径 | ID 示例 |
|---|---|---|
| 服务端 Dialogue | `<数据包>/data/<namespace>/dialogues/<path>.json` | `example:guide/root` |
| 客户端 Dialogue | `<资源包>/assets/<namespace>/dialogues/<path>.json` | `example:guide/root` |
| Speaker | `<资源包>/assets/<namespace>/speakers/<path>.json` | `example:guide` |
| Theme | `<资源包>/assets/<namespace>/dialogue_themes/<path>.json` | `example:parchment` |
| SceneAction | `<资源包>/assets/<namespace>/presentation_actions/<path>.json` | `example:guide/enter` |
| 图片 | `<资源包>/assets/<namespace>/textures/<path>` | `example:dialogue/guide.png` |

例如下面两个文件都映射为 `example:guide/root`：

```text
<资源包>/assets/example/dialogues/guide/root.json
<数据包>/data/example/dialogues/guide/root.json
```

图片 ID 不包含 `textures/`。`example:dialogue/guide.png` 对应：

```text
<资源包>/assets/example/textures/dialogue/guide.png
```

## 双端职责

- 服务端从启用的 Data Pack 读取 `data` Dialogue，检查 ID、`requires` 和访问权限。
- 客户端从启用的 Resource Pack 读取 `assets` Dialogue，显示正文、场景和 UI。
- Speaker、Theme、SceneAction 与图片只由客户端加载。
- 同一 Dialogue 的两份 JSON 应保持一致。

## 命名规则

- namespace 与资源路径遵循 Minecraft `ResourceLocation` 规则，使用小写字符。
- VisualObject、variant 和 SceneAction target 的局部 ID 使用 `[a-z0-9_-]+`。
- `background` 和 `dialogue` 是保留的 Action target，不能作为 VisualObject ID。
- ProgressNode 使用点分名称；详细规则见[Progress 表达式](./progress-expression.md)。

## Reload

- `/reload`：重新加载服务端 `data` 中的 Dialogue。
- `F3 + T`：重新加载客户端 Dialogue、Speaker、Theme、SceneAction 和图片。

修改 Dialogue 时通常需要同时执行两种 reload。客户端 reload 后会集中报告缺失 Theme、Speaker、目标 Dialogue、Action、target、variant 和图片。
