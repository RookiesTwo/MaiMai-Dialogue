---
title: 场景表现
description: 配置对话框布局、背景、VisualObject 与场景滤镜。
---

# 场景表现

`presentation` 决定一个 Dialogue 激活时创建的场景。切换到另一个 Dialogue 或 Return 到 root 时会重新创建场景，不继承前一个 Dialogue 的画面状态。

## 顶层字段

| 字段 | 必填 | 默认值 |
|---|---:|---|
| `theme` | 是 | — |
| `background` | 否 | 无 |
| `dialogue_box` | 否 | 底部默认布局 |
| `visual_objects` | 否 | `{}` |
| `filter` | 否 | 无 |

```json
{
  "presentation": {
    "theme": "maimai_dialogue:default",
    "background": {
      "variants": {
        "day": "example:dialogue/background/day.png",
        "night": "example:dialogue/background/night.png"
      },
      "initial_variant": "day",
      "fit": "cover",
      "opacity": 1.0
    },
    "visual_objects": {
      "guide": {
        "variants": {
          "normal": "example:dialogue/guide/normal.png",
          "happy": "example:dialogue/guide/happy.png"
        },
        "initial_variant": "normal",
        "x": 0.5,
        "y": 0.5,
        "anchor": "center",
        "scale": 1.0,
        "visible": true,
        "z_index": 10
      }
    }
  }
}
```

## 对话框布局

```json
{
  "dialogue_box": {
    "x": 0.5,
    "y": 0.98,
    "width": 0.96,
    "max_height": 0.5,
    "anchor": "bottom_center"
  }
}
```

`x`、`y` 范围为 `[0,1]`；`width`、`max_height` 为 `(0,1]`。九宫格 anchor 可选：

```text
top_left      top_center      top_right
center_left   center          center_right
bottom_left   bottom_center   bottom_right
```

## 背景

| 字段 | 必填 | 默认/约束 |
|---|---:|---|
| `variants` | 是 | 非空；局部 ID 为 `[a-z0-9_-]+` |
| `initial_variant` | 否 | `default`，必须存在 |
| `fit` | 否 | `cover`；还可用 `contain`、`stretch` |
| `opacity` | 否 | `1.0`，范围 `[0,1]` |

背景只覆盖 Scene layer；透明区域后方仍是 Minecraft 世界。

## VisualObject

VisualObject 是通用画面对象，不强制代表角色立绘。

| 字段 | 必填 | 默认/约束 |
|---|---:|---|
| `variants` | 是 | 非空图片映射 |
| `initial_variant` | 是 | 必须存在于 `variants` |
| `x`、`y` | 否 | `0.5`；可超出 `[0,1]` |
| `anchor` | 否 | `center` |
| `scale` | 否 | `1.0`，必须大于 0 |
| `sampling` | 否 | `linear`；像素图可用 `nearest` |
| `opacity` | 否 | `1.0`，范围 `[0,1]` |
| `visible` | 否 | `true` |
| `z_index` | 否 | `0`，数值越大越靠前 |

所有对象必须预先声明。当前 Action 只能修改已有对象，不能动态创建或删除对象。

## `color_adjust` Filter

```json
{
  "filter": {
    "type": "color_adjust",
    "brightness": -0.05,
    "contrast": 1.1,
    "saturation": 0.7,
    "tint": "#30A0C8FF"
  }
}
```

- `brightness`：`[-1,1]`，默认 `0`
- `contrast`、`saturation`：`[0,2]`，默认 `1`
- `tint`：可选，格式为 `#RRGGBB` 或 `#AARRGGBB`

## `crt` Filter

```json
{
  "filter": {
    "type": "crt",
    "curvature": 0.14,
    "scanline_strength": 0.32,
    "mask_strength": 0.2,
    "chromatic_aberration": 1.3,
    "vignette": 0.3,
    "noise": 0.05,
    "flicker": 0.025,
    "bloom": 0.24
  }
}
```

除 `chromatic_aberration` 范围为 `[0,4]` 外，其余参数范围均为 `[0,1]`。CRT 包含曲率、扫描线、RGB mask、色差、暗角、噪点、闪烁与 Bloom。

Filter 只作用于背景和 VisualObject，不影响 DialogueBox、Options、历史记录，也不处理 Minecraft 世界。

