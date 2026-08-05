---
title: Scene JSON
description: 定义可复用的背景、VisualObject 与 Scene Filter。
---

# Scene JSON

Scene 是可复用的视觉舞台。它集中保存背景、视觉对象和滤镜；Dialogue 的 Presentation 继续决定 Theme 与 DialogueBox 布局。

## 文件位置

Scene 只属于客户端 Resource Pack：

```text
assets/<namespace>/scenes/<path>.json
```

例如：

```text
assets/example/scenes/village/square.json
                     ↓
        example:village/square
```

Scene 文件不需要复制到 Data Pack，但引用它的 Dialogue 仍然需要保持双端一致。

## 完整示例

```json
{
  "background": {
    "variants": {
      "day": "example:backgrounds/village/day.png",
      "night": "example:backgrounds/village/night.png"
    },
    "initial_variant": "day",
    "fit": "cover",
    "opacity": 1.0
  },
  "visual_objects": {
    "guide": {
      "asset": "example:characters/guide",
      "initial_variant": "neutral",
      "x": 0.8,
      "y": 1.0,
      "anchor": "bottom_center",
      "z_index": 10
    }
  },
  "filter": {
    "type": "color_adjust",
    "saturation": 0.9
  }
}
```

| 字段 | 必填 | 默认值 |
|---|---:|---|
| `background` | 否 | 无背景 |
| `visual_objects` | 否 | `{}` |
| `filter` | 否 | 无滤镜 |

Scene 中的 Background、VisualObject 和 Filter 与 [Presentation JSON](./presentation-json.md) 使用相同结构。VisualObject 推荐引用 [VisualAsset](./visual-asset-json.md)。

## 在 Dialogue 中引用

```json
{
  "presentation": {
    "theme": "maimai_dialogue:default",
    "scene": "example:village/square"
  }
}
```

解析顺序为：

1. 加载 Scene 的背景、对象与滤镜；
2. 合并 Dialogue Presentation 中的局部场景字段；
3. 解析所有 VisualAsset；
4. 准备 SceneAction。

## 局部覆盖规则

Dialogue 仍可在引用 Scene 后声明局部内容：

```json
{
  "presentation": {
    "theme": "maimai_dialogue:default",
    "scene": "example:village/square",
    "background": {
      "variants": {
        "default": "example:backgrounds/village/rain.png"
      }
    },
    "visual_objects": {
      "guide": {
        "asset": "example:characters/guide",
        "initial_variant": "sad",
        "x": 0.25,
        "y": 1.0,
        "anchor": "bottom_center"
      }
    }
  }
}
```

- 局部 `background` 整体替换 Scene 的 Background。
- `visual_objects` 按对象 ID 合并；同名对象由 Dialogue 局部定义整体替换。
- 局部 `filter` 整体替换 Scene 的 Filter。
- `theme` 与 `dialogue_box` 始终属于 Dialogue Presentation，不进入 Scene。

Scene 不继承其他 Scene，避免形成循环引用。需要另一种舞台组合时，创建新的 Scene 文件。

