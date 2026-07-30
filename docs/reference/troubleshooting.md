---
title: 故障排查
description: 排查 Dialogue 无法加载、无法打开、选项消失和表现资源错误。
---

# 故障排查

## 先验证内置 Demo

```text
/maimai_dialogue open @s maimai_dialogue:demo/root
```

如果 Demo 也无法打开，优先检查 MOD、NeoForge、Modern UI、命令权限和日志；如果 Demo 正常，问题通常位于自定义资源路径或 JSON。

## 命令提示 Dialogue 不存在

检查：

1. 服务端是否存在 `data/<namespace>/dialogues/<path>.json`；
2. 路径是否正确映射为 `<namespace>:<path>`；
3. `/reload` 后日志是否报告 JSON/Codec 错误；
4. 文件扩展名是否为 `.json`。

无效资源会被跳过，因此“文件存在”不等于成功加载。

## 服务端允许，但客户端不显示

检查客户端：

1. 是否存在同 ID 的 `assets/<namespace>/dialogues/<path>.json`；
2. 是否已按 `F3 + T` reload；
3. 是否已经打开另一个 MaiMai Dialogue UI；
4. 客户端日志是否报告 `missing Theme`、`missing Speaker`、目标 Dialogue 或图片缺失。

这是典型的双端内容不一致问题。

## 条件不满足或选项消失

- 使用 `/maimai_dialogue progress list <player>` 查看节点；
- 使用 `check` 验证单个节点；
- 按 `!`、`&&`、`||` 的优先级重新检查表达式；
- 确认服务端 `data` 副本中的 `requires` 与客户端副本一致；
- 目标 Dialogue 在服务端不存在时也会从选项中移除。

如果所有 Dialogue target 都被过滤，系统会使用 Return fallback。

## Speaker 显示成 ID

客户端缺少：

```text
assets/<namespace>/speakers/<path>.json
```

检查 `speaker.id`、文件 ID 和 `name` 是否非空。显示 ID 只是临时诊断回退。

## Theme 回退为默认样式

检查 `presentation.theme` 对应的：

```text
assets/<namespace>/dialogue_themes/<path>.json
```

颜色必须是 `#RRGGBB` 或 `#AARRGGBB`，数值必须在[Theme](../content/themes.md)规定范围内。

## 图片不显示

JSON 中：

```json
"example:dialogue/guide.png"
```

对应：

```text
assets/example/textures/dialogue/guide.png
```

检查 namespace、大小写、扩展名及 `textures/` 映射。`initial_variant` 必须存在于 `variants`。

## Action 没有播放

检查：

- 外部 Action 是否位于 `assets/<namespace>/presentation_actions/`；
- `target` 是否已声明；
- `background` 是否只修改 `variant`；
- `dialogue` 是否只修改 `opacity`；
- 同一步是否有多个 Action 写同一 target 的同一属性；
- 关键帧 `at` 是否位于 `[0,1]` 且严格递增；
- 相对值计算后的 scale/opacity 是否仍在合法范围。

## Progress 数据不可用

进度保存于：

```text
<world>/data/maimai_dialogue/progress/<uuid>.dat
```

查看服务端日志中的读取、Schema、UUID、节点或保存错误。系统不会把损坏文件当作空进度覆盖；请先备份原文件再处理，不要在服务器运行时手工修改。
