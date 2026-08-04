---
title: 制作 Theme
description: 创建可复用 Theme，定制对话框、文字、选项和滚动控件。
---

# 制作 Theme

## 本章要实现什么

把 `welcome` 的默认深色界面改成浅色羊皮纸风格，同时保持 Scene 和 Filter 不变。

## 开始前

你已经完成[添加场景滤镜](./filters.md)。Theme 只控制 Dialogue UI，不控制场景图片。

## 需要修改的文件

新增 Theme：

```text
<资源包>/assets/example/themes/parchment.json
```

并修改 PresentationDefinition：

```text
<资源包>/assets/example/presentations/guide/welcome.json
```

Dialogue 的双端 reference 不需要修改。

## 跟着做

1. 创建 `themes/parchment.json`：

```json:line-numbers [parchment.json]
{
  "box": {
    "background": "#E8F3E2C2",
    "border": "#FF5B4028",
    "divider": "#805B4028",
    "corner_radius": 2,
    "border_width": 1
  },
  "text": {
    "primary": "#FF2C2118",
    "error": "#FF9E2A2A",
    "speaker_size": 15,
    "dialogue_size": 16,
    "option_size": 15,
    "auxiliary_size": 13
  },
  "option": {
    "background": "#30FFFFFF",
    "hover_background": "#70FFFFFF",
    "pressed_background": "#A0FFFFFF",
    "border": "#705B4028",
    "hover_border": "#FF5B4028",
    "border_width": 1,
    "corner_radius": 2,
    "horizontal_padding": 12,
    "vertical_padding": 8,
    "spacing": 2
  },
  "spacing": {
    "header_horizontal": 12,
    "header_vertical": 7,
    "content_horizontal": 12,
    "content_vertical": 10,
    "options_padding": 6,
    "options_collapsed_limit": 3,
    "options_expanded_limit": 6
  },
  "controls": {
    "icon": "#FF5B4028",
    "scrollbar_thumb": "#B85B4028",
    "scrollbar_track": "#385B4028",
    "scrollbar_width": 4
  }
}
```

它的资源 ID 是 `example:parchment`。

2. 把 `presentations/guide/welcome.json` 的 `theme` 改为 `example:parchment`：

```json:line-numbers {2} [presentations/guide/welcome.json]
{
  "theme": "example:parchment",
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
    "brightness": -0.03,
    "contrast": 1.08,
    "saturation": 0.6,
    "tint": "#30A0C8FF"
  }
}
```

Theme 的每个区块和字段都可以省略；省略部分使用内置默认值。因为多个 Dialogue 可以引用同一个 PresentationDefinition，这次修改会同时影响它们。

## 进入游戏验证

按 `F3 + T` 后重新打开 Dialogue。DialogueBox、文字、Options 和 scrollbar 应变成羊皮纸风格，Scene 图片与 Filter 保持不变。

## 如果没有生效

- 仍显示默认 Theme：检查 PresentationDefinition 的 `theme` ID。
- Theme 文件未加载：确认目录是 `themes`。
- 颜色透明度异常：颜色格式使用 `#RRGGBB` 或 `#AARRGGBB`。
- 只想修改一个 Dialogue：为它创建单独的 PresentationDefinition，再切换 reference ID。

## 下一步

继续[双端发布](../publish/client-server.md)，整理 Resource Pack 与 Data Pack。
