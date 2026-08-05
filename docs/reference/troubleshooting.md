---
title: 故障排查
description: 按玩家看到的现象排查安装、双端资源、选项、图片、动画和进度问题。
---

# 故障排查

下文中的 `<资源包>` 表示已启用 Resource Pack 的根目录；完整映射见[资源路径与 ID](./resource-paths.md)。

## 先验证内置 Demo

```text
/maimai_dialogue open @s maimai_dialogue:debug/root
```

- Demo 也无法打开：优先检查 MOD、NeoForge、Modern UI、权限和日志。
- Demo 正常：问题通常位于自定义文件路径、JSON 或双端版本。

## 命令提示 Dialogue 不存在

依次检查：

1. Data Pack 是否出现在 `/datapack list enabled` 中；
2. 数据包内是否存在 `data/<namespace>/dialogues/<path>.json`；
3. 文件路径是否正确映射为命令中的 ID；
4. 是否执行了 `/reload`；
5. 服务端日志是否报告 JSON、字段或 Progress 表达式错误。

加载失败的资源会被跳过，所以“文件存在”不等于服务端已经加载。

## 命令成功但客户端没有显示

依次检查：

1. 客户端是否启用了对应 Resource Pack；
2. 资源包内是否存在同 ID 的 `assets/<namespace>/dialogues/<path>.json`；
3. 是否按过 `F3 + T`；
4. 玩家是否已经打开另一个 MaiMai Dialogue；
5. 客户端日志是否报告 Theme、Speaker、目标 Dialogue、Action 或图片缺失。

这通常表示服务端 Data Pack 与客户端 Resource Pack 不是同一个发布版本。

## 选项没有出现

- 使用 `/maimai_dialogue progress list <player>` 查看真实节点。
- 使用 `progress check` 检查单个节点。
- 按 `!`、`&&`、`||` 的优先级重新检查目标 Dialogue 的 `requires`。
- 确认服务端存在目标 Dialogue；不存在的目标也会被隐藏。
- 所有 Dialogue target 都被过滤时，当前结尾会采用 Return fallback。

## Speaker 显示成 ID

客户端缺少或无法解析：

```text
<资源包>/assets/<namespace>/speakers/<path>.json
```

检查 `speaker.id`、文件 ID 和非空 `name`。显示 ID 只是诊断回退。

## Theme 回退为默认样式

检查 `presentation.theme` 对应的：

```text
<资源包>/assets/<namespace>/themes/<path>.json
```

颜色格式与字段范围见 [Theme JSON](./theme-json.md)。修改后按 `F3 + T`。

## 图片不显示

JSON 中的：

```json
"example:dialogue/guide.png"
```

对应：

```text
<资源包>/assets/example/textures/dialogue/guide.png
```

检查 namespace、大小写、扩展名和 `initial_variant`。图片 ID 中不要写 `textures/`。

## SceneAction 没有播放

- 外部 Action 是否位于 `<资源包>/assets/<namespace>/actions/`；
- `target` 是否已经声明；
- `background` 是否只修改 `variant`；
- `dialogue` 是否只修改 `x`、`y`、`scale`、`opacity`；
- 同一步是否有多个 Action 写同一 target 的同一属性；
- 关键帧 `at` 是否位于 `[0,1]` 且严格递增；
- 相对值计算后的 scale 和 opacity 是否仍然有效。

## Progress 数据不可用

进度位于：

```text
<world>/data/maimai_dialogue/progress/<uuid>.dat
```

查看服务端日志中的读取、Schema、UUID、节点或保存错误。系统不会把损坏文件当作空进度覆盖。先备份原文件，不要在服务器运行时手工修改。
