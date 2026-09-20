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

CRT 在编辑器预览与正式播放中使用同一条 GPU 管线：曲率改变场景采样坐标，色差分别偏移红蓝通道，扫描线、RGB 栅格、暗角、噪点和闪烁在合成时处理。CRT 视口默认铺黑色底，曲率收缩后的边角和素材透明区域合成到黑底上，不露出后方游戏画面或编辑器方格。黑底不参与滤镜计算，随场景一起变换和淡入淡出，Dialogue UI 保持在其上方。

`bloom` 从场景亮部提取辉光，使用宽高各为场景四分之一的两张纹理进行横向、纵向模糊，再合成回场景。设为 `0` 时跳过提取与两次模糊，只执行 CRT 主 pass。`noise` 与 `flicker` 同时为 `0` 时不主动请求动画刷新；八个参数全为 `0` 时绕过滤镜处理，但仍保留 CRT 黑底。关闭 CRT 或切换为其他滤镜后移除黑底。参数修改不会重新解码或上传素材。

代码中可在 ModernUI UI 线程调用 `DialogueSceneView.setSceneFilter(new CrtFilter(...))` 实时更新八个参数，传入 `null` 关闭滤镜。客户端命令 `/maimai_dialogue_gpu_probe crt` 可打开真实场景诊断页，调整参数、旁路对比并查看 GPU pass 耗时。这个耗时只涵盖滤镜处理，不代表整帧开销。

## 进入游戏验证

按 `F3 + T` 后重新打开 Dialogue。背景和 VisualObject 应出现冷色、低饱和效果，DialogueBox、文字、Options 与后方 Minecraft 世界不受 Filter 影响。

## 如果没有生效

- 完全没有变化：确认 Filter 写在演出配置文件中，而不是 Theme 中。
- DialogueBox 也被染色：这不是预期行为，请检查是否使用了额外 shader MOD。
- 画面过暗：把 `brightness` 调回接近 `0`，并降低 `contrast`。
- CRT 开销或效果过强：先将 `bloom` 设为 `0`，再按需要减少其他效果；将 `noise` 与 `flicker` 设为 `0` 可停止滤镜自身的动画刷新。

## 下一步

继续[制作 Theme](./themes.md)，修改 Dialogue UI 的样式。
