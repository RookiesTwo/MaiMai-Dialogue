---
title: Scene JSON
description: 完整初始场景，包括 Theme、对话框布局、背景、视觉对象和滤镜。
---

# Scene JSON

Scene 保存一套可复用的初始画面，只放在资源包的 `assets/<namespace>/scenes/<path>.json`。例如 `assets/example/scenes/guide/welcome.json` 的 ID 是 `example:guide/welcome`。

Dialogue 使用必填字符串引用它，两份 Dialogue JSON 保持一致：

```json
{
  "scene": "example:guide/welcome",
  "end": {"exit": {"type": "return"}}
}
```

每次进入 Dialogue 都创建独立播放状态。Scene 不继承其他 Scene；Dialogue 不支持内联场景或局部覆盖。需要另一套配置时创建或复制 Scene。

## 顶层字段

| 字段 | 必填 | 默认值 |
|---|---:|---|
| `theme` | 否 | `maimai_dialogue:default`，引用 Theme |
| `dialogue_box` | 否 | `x: 0.5`、`y: 0.76`、`width: 0.4`、`max_height: 0.4`、`anchor: center` |
| `background` | 否 | 无背景 |
| `visual_objects` | 否 | `{}` |
| `filter` | 否 | 无滤镜 |

`{}` 是合法空 Scene。纯文本 Dialogue 可以直接引用内置 `maimai_dialogue:default`，不必创建自有 Scene。VisualObject 可引用独立 [VisualAsset](./visual-asset-json.md)，也可直接声明差分图片。

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
| `scale_x`、`scale_y` | 否 | 均为 `1.0`；水平／垂直缩放系数，必须为有限正数 |
| `sampling` | 否 | 引用时继承 VisualAsset；显式设置会覆盖它。inline 时默认 `linear` |
| `opacity` | 否 | `1.0`，范围 `[0,1]` |
| `visible` | 否 | `true` |
| `z_index` | 否 | `0`，数值越大越靠前 |

VisualAsset 只提供差分和 sampling；位置、缩放、透明度、可见性、层级与初始差分仍由每个 VisualObject 实例决定。

水平方向使用 `scale × scale_x`，垂直方向使用 `scale × scale_y`。例如 `scale: 2.0`、`scale_x: 1.5`、`scale_y: 0.5` 会得到水平 3 倍、垂直 1 倍的尺寸；`anchor` 继续指定哪个点对准 `x/y`。省略两个轴向字段时，旧内容的等比缩放行为不变。SceneAction 的 `scale` 动画仍作用于统一缩放，保留这两个轴向系数。

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
  "contrast": 0.0,
  "saturation": 0.0,
  "tint": "#30A0C8FF"
}
```

| 字段 | 默认值 | 范围 |
|---|---:|---:|
| `brightness` | `0` | `[-100,100]` |
| `contrast` | `0` | `[-100,100]` |
| `saturation` | `0` | `[-100,100]` |
| `tint` | 无 | `#RRGGBB` 或 `#AARRGGBB` |

三个数值的 `0` 均表示不调整，不使用旧的 0～2 倍率语义。饱和度 `-100` 为黑白，正值增强颜色；对比度围绕中灰调整，`-100` 将图片颜色压到中灰。亮度为通道偏移，`-100` 变黑、`100` 变白；染色最后应用，其 alpha 控制混入颜色的强度。原图片透明度始终保留。

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
| `edge_feather` | `0` | `[0,1]` |

各参数的 `0` 均关闭对应效果。`chromatic_aberration` 使用场景实际渲染像素作为偏移单位。曲率扭曲场景采样坐标；噪点和闪烁随时间变化。CRT 视口默认黑底，曲率造成的空隙与素材透明区域显示为黑底，不透出游戏画面。

`edge_feather` 让扭曲后的画面边缘向内逐渐融入黑底。`0` 保留硬边；`1` 的羽化宽度约为视口短边的 10%，随视口缩放保持比例。它在 CRT 主 pass 中处理最终颜色与透明度，中央画面保持清晰，Bloom 也随边缘一起渐隐。不增加纹理或额外 pass。

`bloom > 0` 时增加亮部提取和两次模糊，在宽高各为原场景四分之一的纹理上执行；`bloom = 0` 时仅执行 CRT 主 pass。所有参数全为 `0` 时绕过滤镜处理，但保留 CRT 黑底；移除 CRT 后黑底随之移除。

Filter 只处理背景与 VisualObject，不处理 Dialogue UI 或后方的 Minecraft 世界。

## 下一步

- 给图片起代号见 [VisualAsset JSON](./visual-asset-json.md)，对话引用见 [Dialogue JSON](./dialogue-json.md)。
- 动画写法见 [SceneAction JSON](./scene-action-json.md)。
