---
title: 资源组织
description: 组织 Dialogue、Speaker、Theme、Action 和纹理资源。
---

# 资源组织

## 目录与 ID

| 资源 | 路径 | ID 示例 |
|---|---|---|
| 服务端 Dialogue | `data/<namespace>/dialogues/<path>.json` | `example:intro/start` |
| 客户端 Dialogue | `assets/<namespace>/dialogues/<path>.json` | `example:intro/start` |
| Speaker | `assets/<namespace>/speakers/<path>.json` | `example:npc/guide` |
| Theme | `assets/<namespace>/dialogue_themes/<path>.json` | `example:dark` |
| SceneAction | `assets/<namespace>/presentation_actions/<path>.json` | `example:entrance/fade` |
| 图片 | `assets/<namespace>/textures/<path>` | `example:dialogue/guide.png` |

例如 `data/example/dialogues/intro/start.json` 和 `assets/example/dialogues/intro/start.json` 都映射为 `example:intro/start`。

图片比较特殊：JSON 中的 `"example:dialogue/guide.png"` 会查找 `assets/example/textures/dialogue/guide.png`，不要在 ID 中重复写 `textures/`。

## 推荐的内容 MOD 结构

```text
src/main/resources/
├─ assets/example/
│  ├─ dialogues/
│  ├─ speakers/
│  ├─ dialogue_themes/
│  ├─ presentation_actions/
│  └─ textures/dialogue/
└─ data/example/
   └─ dialogues/
```

同一个 Dialogue 的客户端与服务端 JSON 应保持一致。当前实现不会自动比较两份文件：服务端读取自己的 `requires`，客户端使用自己的正文与表现，因此差异可能产生难以定位的行为。

## 发布方式

推荐使用包含 `assets` 和 `data` 的附属内容 MOD，并在服务端与客户端安装同一版本。也可以分别制作 Data Pack 和 Resource Pack，但必须确保：

- 服务端拥有所有可访问 Dialogue 的 `data` 副本；
- 每个客户端拥有相同 ID 的 `assets` 副本；
- 客户端拥有所有引用的 Speaker、Theme、Action 和图片。

## 命名约束

- namespace 与资源路径遵循 Minecraft `ResourceLocation` 规则。
- VisualObject、variant 和 Action target 的局部 ID 使用 `[a-z0-9_-]+`。
- `background` 和 `dialogue` 是保留的 Action target，不能用作 VisualObject ID。

## Reload 与校验

客户端 reload 会集中校验缺失的 Theme、Speaker、目标 Dialogue、Action、Action target/variant 和图片。非法 JSON 或 Codec 字段会使当前资源跳过加载，其他有效资源仍可使用。

开发时建议先查看日志，再使用内置 Demo 对照：

```text
/maimai_dialogue open @s maimai_dialogue:demo/root
```
