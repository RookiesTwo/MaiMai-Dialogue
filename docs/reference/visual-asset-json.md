---
title: VisualAsset JSON
description: 定义可复用的 VisualObject 差分与图片采样方式。
---

# VisualAsset JSON

视觉资源（VisualAsset）把角色或道具的差分图片集中定义一次。这里的"差分（variant）"指同一个对象的不同图片——比如同一个角色的不同表情。Dialogue 中的 VisualObject 只负责该次出场的位置、缩放、透明度与初始差分。

## 文件位置

VisualAsset 只属于客户端 Resource Pack：

```text
assets/<namespace>/visual_assets/<path>.json
```

例如：

```text
assets/example/visual_assets/characters/guide.json
                              ↓
                 example:characters/guide
```

它不需要复制到 Data Pack。

## 完整示例

```json
{
  "variants": {
    "neutral": "example:characters/guide/neutral.png",
    "happy": "example:characters/guide/happy.png",
    "sad": "example:characters/guide/sad.png"
  },
  "sampling": "linear"
}
```

| 字段 | 必填 | 默认值/约束 |
|---|---:|---|
| `variants` | 是 | 非空图片映射；key 使用 `[a-z0-9_-]+` |
| `sampling` | 否 | `linear`；像素图可用 `nearest` |

图片 ID 不包含 `textures/`。例如 `example:characters/guide/happy.png` 对应：

```text
assets/example/textures/characters/guide/happy.png
```

## 在 Dialogue 中引用

```json
{
  "presentation": {
    "theme": "maimai_dialogue:default",
    "visual_objects": {
      "guide": {
        "asset": "example:characters/guide",
        "initial_variant": "neutral",
        "x": 0.8,
        "y": 1.0,
        "anchor": "bottom_center",
        "scale": 1.0,
        "opacity": 1.0,
        "visible": true,
        "z_index": 10
      }
    }
  }
}
```

`initial_variant` 必须存在于被引用 VisualAsset 的 `variants` 中。VisualObject 可以额外声明 `sampling` 来覆盖 VisualAsset 的设置；省略时继承 VisualAsset。

VisualAsset 只复用图片集合与 sampling，不保存位置等场景状态。因此同一角色可以在不同 Dialogue 或 [Scene](./scene-json.md) 中使用不同位置、大小、初始表情和层级。

## Inline 兼容写法

旧内容仍可直接在 VisualObject 中写 `variants`。一个 VisualObject 必须且只能提供 `asset` 或 `variants` 其中之一：

```json
{
  "variants": {
    "default": "example:dialogue/marker.png"
  },
  "initial_variant": "default"
}
```

经常复用的角色、道具建议建立 VisualAsset；只在单个 Dialogue 使用的简单对象可以保留 inline 写法。
