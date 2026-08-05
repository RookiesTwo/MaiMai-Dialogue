---
title: Presentation JSON
description: Theme、Scene、背景、对话框布局、VisualObject 和 Filter 的字段参考。
---

# Presentation JSON

Presentation 可以直接写在 Dialogue 中，也可以保存为可复用的 [PresentationDefinition](./presentation-definition-json.md)。

引用形式为：

```json
{
  "type": "reference",
  "id": "example:guide/default"
}
```

旧的 inline object 保持兼容。reference 是纯引用，不能夹带下方的 inline 字段。

## 顶层字段

| 字段 | 必填 | 默认值 |
|---|---:|---|
| `theme` | 是 | — |
| `scene` | 否 | 无；引用客户端 Scene |
| `background` | 否 | 无背景 |
| `dialogue_box` | 否 | 默认底部布局 |
| `visual_objects` | 否 | `{}` |
| `filter` | 否 | 无滤镜 |

每次进入一个 Dialogue 都会按它的 Presentation 创建新场景，不继承前一个 Dialogue 的画面状态。经常复用的 `background`、`visual_objects` 与 `filter` 可以放入 [Scene](./scene-json.md)，再通过 `scene` 引用。

Presentation 中的局部 Background 和 Filter 会整体覆盖 Scene 对应字段；VisualObject 按 ID 合并，同名对象由 Presentation 覆盖。Theme 与 DialogueBox 不属于 Scene。

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

`fit` 三种取值的差别（图片与画面区域形状不同时看得出来）：

| 值 | 效果 |
|---|---|
| `cover` | 图片等比放大，把画面区域**铺满**，放不下的部分裁掉 |
| `contain` | 图片**完整显示**，多余的区域留空 |
| `stretch` | 图片直接**拉满**整个区域，可能变形 |

## DialogueBox 布局

```json
{
  "x": 0.5,
  "y": 0.98,
  "width": 0.5,
  "max_height": 0.4,
  "anchor": "bottom_center"
}
```

`x`、`y` 范围为 `[0,1]`；`width`、`max_height` 范围为 `(0,1]`。anchor 可选：

`max_height` 是选项收缩时整个 DialogueBox 的高度上限。正文超过 Header、错误提示和 Option 之外的剩余高度时，会自动使用独立的滚动区域；Option 超出 Theme 配置的显示数量时继续使用自己的滚动区域。选项可展开时，点击展开按钮会临时把高度上限放宽到 `1.0`，再次收缩或进入其他 Dialogue 后恢复这里配置的上限。

正文和 Option 可以分别滚动，但建议不要在显示大量 Option 的结尾同时放置长正文，以免两个滚动区域挤占可读空间。

```text
top_left      top_center      top_right
center_left   center          center_right
bottom_left   bottom_center   bottom_right
```

## VisualObject（视觉对象）

视觉对象是画面上一个可移动、缩放、换图、显示或隐藏的图片对象。推荐通过 `asset` 引用可复用的 [VisualAsset](./visual-asset-json.md)：

```json
{
  "asset": "example:characters/guide",
  "initial_variant": "neutral",
  "x": 0.5,
  "y": 0.5,
  "anchor": "center",
  "scale": 1.0,
  "opacity": 1.0,
  "visible": true,
  "z_index": 0
}
```

| 字段 | 必填 | 默认值/约束 |
|---|---:|---|
| `asset` | 二选一 | VisualAsset ID；与 `variants` 不能同时出现 |
| `variants` | 二选一 | 兼容旧内容的非空 inline 图片映射；与 `asset` 不能同时出现 |
| `initial_variant` | 是 | 必须存在于 VisualAsset 或 inline `variants` |
| `x`、`y` | 否 | `0.5`；允许超出 `[0,1]` |
| `anchor` | 否 | `center` |
| `scale` | 否 | `1.0`，必须大于 0 |
| `sampling` | 否 | 引用时继承 VisualAsset；显式设置会覆盖它。inline 时默认 `linear` |
| `opacity` | 否 | `1.0`，范围 `[0,1]` |
| `visible` | 否 | `true` |
| `z_index` | 否 | `0`，数值越大越靠前 |

VisualAsset 只提供差分和 sampling；位置、缩放、透明度、可见性、层级与初始差分仍由每个 VisualObject 实例决定。

对象必须在 `visual_objects` 中预先声明。`background` 和 `dialogue` 是保留名称。旧的 inline `variants` 写法保持兼容，例如：

```json
{
  "variants": {
    "default": "example:dialogue/guide.png",
    "happy": "example:dialogue/guide_happy.png"
  },
  "initial_variant": "default",
  "sampling": "linear"
}
```

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
