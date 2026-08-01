---
title: Presentation JSON
description: Theme、背景、对话框布局、VisualObject 和 Filter 的字段参考。
---

# Presentation JSON

## 顶层字段

| 字段 | 必填 | 默认值 |
|---|---:|---|
| `theme` | 是 | — |
| `background` | 否 | 无背景 |
| `dialogue_box` | 否 | 默认底部布局 |
| `visual_objects` | 否 | `{}` |
| `filter` | 否 | 无滤镜 |

每次进入一个 Dialogue 都会按它的 Presentation 创建新场景，不继承前一个 Dialogue 的画面状态。

## Background

```json
{
  "variants": {
    "default": "example:dialogue/day.png",
    "night": "example:dialogue/night.png"
  },
  "initial_variant": "default",
  "fit": "cover",
  "opacity": 1.0
}
```

| 字段 | 必填 | 默认值/约束 |
|---|---:|---|
| `variants` | 是 | 非空图片映射；key 使用 `[a-z0-9_-]+` |
| `initial_variant` | 否 | `default`，必须存在于 `variants` |
| `fit` | 否 | `cover`；可用 `contain`、`stretch` |
| `opacity` | 否 | `1.0`，范围 `[0,1]` |

## DialogueBox 布局

```json
{
  "x": 0.5,
  "y": 0.98,
  "width": 0.96,
  "max_height": 0.5,
  "anchor": "bottom_center"
}
```

`x`、`y` 范围为 `[0,1]`；`width`、`max_height` 范围为 `(0,1]`。anchor 可选：

```text
top_left      top_center      top_right
center_left   center          center_right
bottom_left   bottom_center   bottom_right
```

## VisualObject

```json
{
  "variants": {
    "default": "example:dialogue/guide.png",
    "happy": "example:dialogue/guide_happy.png"
  },
  "initial_variant": "default",
  "x": 0.5,
  "y": 0.5,
  "anchor": "center",
  "scale": 1.0,
  "sampling": "linear",
  "opacity": 1.0,
  "visible": true,
  "z_index": 0
}
```

| 字段 | 必填 | 默认值/约束 |
|---|---:|---|
| `variants` | 是 | 非空图片映射 |
| `initial_variant` | 是 | 必须存在于 `variants` |
| `x`、`y` | 否 | `0.5`；允许超出 `[0,1]` |
| `anchor` | 否 | `center` |
| `scale` | 否 | `1.0`，必须大于 0 |
| `sampling` | 否 | `linear`；像素图可用 `nearest` |
| `opacity` | 否 | `1.0`，范围 `[0,1]` |
| `visible` | 否 | `true` |
| `z_index` | 否 | `0`，数值越大越靠前 |

对象必须在 `visual_objects` 中预先声明。`background` 和 `dialogue` 是保留名称。

## Color Adjust Filter

```json
{
  "type": "color_adjust",
  "brightness": 0.0,
  "contrast": 1.0,
  "saturation": 1.0,
  "tint": "#30A0C8FF"
}
```

| 字段 | 默认值 | 范围 |
|---|---:|---:|
| `brightness` | `0` | `[-1,1]` |
| `contrast` | `1` | `[0,2]` |
| `saturation` | `1` | `[0,2]` |
| `tint` | 无 | `#RRGGBB` 或 `#AARRGGBB` |

## CRT Filter

| 字段 | 默认值 | 范围 |
|---|---:|---:|
| `curvature` | `0.08` | `[0,1]` |
| `scanline_strength` | `0.22` | `[0,1]` |
| `mask_strength` | `0.12` | `[0,1]` |
| `chromatic_aberration` | `1.0` | `[0,4]` |
| `vignette` | `0.18` | `[0,1]` |
| `noise` | `0.025` | `[0,1]` |
| `flicker` | `0.01` | `[0,1]` |
| `bloom` | `0.1` | `[0,1]` |

Filter 只处理背景与 VisualObject，不处理 Dialogue UI 或后方的 Minecraft 世界。
