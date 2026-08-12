---
title: Theme JSON
description: DialogueBox、文字、选项、间距和控制件的 Theme 字段参考。
---

# Theme JSON

教程对应章节：[制作 Theme](../scene/themes.md)

Theme 文件位于：

```text
assets/<namespace>/themes/<path>.json
```

空对象 `{}` 会使用全部内置默认值。顶层区块和区块内字段都可以省略。

## 完整默认结构

```json
{
  "box": {
    "background": "#CC08080C",
    "border": "#E6FFFFFF",
    "divider": "#80FFFFFF",
    "corner_radius": 4,
    "border_width": 0
  },
  "text": {
    "primary": "#FFFFFFFF",
    "error": "#FFFF8080",
    "speaker_size": 15,
    "dialogue_size": 16,
    "option_size": 15,
    "auxiliary_size": 13
  },
  "option": {
    "background": "#38000000",
    "hover_background": "#78000000",
    "pressed_background": "#A0000000",
    "border": "#70FFFFFF",
    "hover_border": "#FFFFFFFF",
    "border_width": 0,
    "corner_radius": 1,
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
    "icon": "#FFFFFFFF",
    "scrollbar_thumb": "#B8FFFFFF",
    "scrollbar_track": "#38FFFFFF",
    "scrollbar_width": 4
  }
}
```

## 数值范围

- 颜色：`#RRGGBB` 或 `#AARRGGBB`。
- 普通尺寸字段（dp，界面尺寸单位）：`0..64`。
- `scrollbar_width`：`1..64`。
- 文字大小：`8..64`。
- `options_collapsed_limit`、`options_expanded_limit`：`1..32`。
- expanded limit 小于 collapsed limit 时，会自动提升到 collapsed limit。

## 作用范围

- `box`：对话框背景、边框、分割线和圆角。
- `text`：Speaker、正文、Option、错误和辅助文字。
- `option`：选项与控制按钮的普通、hover、pressed 状态、描边和 padding；`border_width: 0` 表示不绘制描边。
- `spacing`：Header、正文、选项区域和折叠/展开数量。
- `controls`：Option icon、History/Options 滚动条。

Theme 不控制背景、VisualObject 或 Scene Filter。缺失 Theme 时客户端会报告错误并临时回退内置默认值；发布内容不应依赖该回退。

## 下一步

- 用 Theme 的地方是 [Presentation JSON](./presentation-json.md) 的 `theme` 字段。
