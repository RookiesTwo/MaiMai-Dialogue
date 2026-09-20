---
title: 添加场景滤镜
description: 使用 color_adjust 或 crt 改变背景与 VisualObject 的画面效果。
---

# 添加场景滤镜

## 本章要实现什么

降低背景与 VisualObject 的饱和度，并加入轻微冷色调。DialogueBox 和 Options 保持清晰。

## 开始前

你已经完成[调整对话框布局](./dialogue-box.md)，Dialogue 正在引用 `example:guide/welcome` Presentation。

## 需要修改的文件

继续修改 Resource Pack 中的演出配置文件（PresentationDefinition）：

```text
<资源包>/assets/example/presentations/guide/welcome.json
```

## 跟着做

加入 `color_adjust` Filter：

```json:line-numbers {11-17} [presentations/guide/welcome.json]
{
  "theme": "maimai_dialogue:default",
  "scene": "example:guide/welcome",
  "dialogue_box": {
    "x": 0.5,
    "y": 0.95,
    "width": 0.82,
    "max_height": 0.42,
    "anchor": "bottom_center"
  },
  "filter": {
    "type": "color_adjust",
    "brightness": -3,
    "contrast": 8,
    "saturation": -40,
    "tint": "#30A0C8FF"
  }
}
```

`color_adjust` 字段：

| 字段 | 范围 | 默认值 | 白话含义 |
|---|---|---|---|
| `brightness` | `[-100,100]` | `0` | 亮度偏移，负数变暗，正数变亮 |
| `contrast` | `[-100,100]` | `0` | 以中灰为中心调整明暗差异；正数增加反差，负数减弱，-100 为中灰 |
| `saturation` | `[-100,100]` | `0` | 负数减少颜色，-100 为黑白；正数增强颜色 |
| `tint` | `#RRGGBB` 或 `#AARRGGBB` | 无 | 给场景图片混入颜色，最前面的两位为染色强度，保留图片透明度 |

三个调整值的 `0` 都表示不调整，编辑器与 JSON 使用相同范围。像素依次执行饱和度、对比度、亮度和染色；仅处理背景与 VisualObject，透明区域不覆盖游戏世界或编辑器方格背景。

编辑器预览与正式播放使用相同的 GPU 调色 pass。修改参数不会重新处理或上传整张图片；场景绘制与调色结果在 GPU 中按顺序合成，保留原透明度。全中性参数且无有效染色时跳过该 pass。

代码中可在 ModernUI UI 线程调用 `DialogueSceneView.setColorAdjustment(...)` 更新当前 Scene 的显示参数，传入 `null` 关闭调色。例如：

```java
MuiModApi.postToUiThread(() -> sceneView.setColorAdjustment(
        new ColorAdjustFilter(0, 20, -30, Optional.empty())
));
```

这个调用不修改项目或播放数据；需要保存／导出的参数仍应写入 Scene 或 Presentation。GPU 处理仍有开销，性能需要结合实际场景、分辨率和设备验证。

如果要使用 CRT 效果，可以把 Filter 替换为：

```json
{
  "type": "crt"
}
```

CRT 还支持 curvature、scanline、RGB mask、chromatic aberration、vignette、noise、flicker 和 bloom 参数；完整字段见 [Presentation JSON](../reference/presentation-json.md#crt-filter)。

## 进入游戏验证

按 `F3 + T` 后重新打开 Dialogue。背景和 VisualObject 应出现冷色、低饱和效果，DialogueBox、文字、Options 与后方 Minecraft 世界不受 Filter 影响。

## 如果没有生效

- 完全没有变化：确认 Filter 写在演出配置文件中，而不是 Theme 中。
- DialogueBox 也被染色：这不是预期行为，请检查是否使用了额外 shader MOD。
- 画面过暗：把 `brightness` 调回接近 `0`，并降低 `contrast`。
- CRT 开销或效果过强：改用 `color_adjust`，或降低动态参数。

## 下一步

继续[制作 Theme](./themes.md)，修改 Dialogue UI 的样式。
