---
title: Theme
description: 定制对话框、文字、选项、间距和控制件样式。
---

# Theme

Theme 只控制 Dialogue UI，不包含背景、VisualObject 或 Scene Filter。

路径：

```text
assets/<namespace>/dialogue_themes/<path>.json
```

空对象 `{}` 会完整使用内置默认值。Dialogue 也可以直接引用内置 Theme：

```json
{
  "presentation": {
    "theme": "maimai_dialogue:default"
  }
}
```

## 完整结构

```json
{
  "box": {
    "background": "#CC08080C",
    "border": "#E6FFFFFF",
    "divider": "#80FFFFFF",
    "corner_radius": 1,
    "border_width": 1
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

## 字段范围

- 颜色：`#RRGGBB` 或 `#AARRGGBB`。
- 普通 dp 值：`0..64`。
- `scrollbar_width`：`1..64`。
- 文字大小：`8..64`。
- 折叠/展开选项数：`1..32`；展开数若小于折叠数，会自动提升到折叠数。

顶层区块和区块内字段都可省略，省略部分继承默认值。

## 缺失 Theme

客户端会报告资源错误并临时回退到内置默认 Theme。该回退只用于诊断，发布前仍应修复引用。

当前不支持自定义字体、普通玩家主题编辑、动画倍率或 reduced motion 设置。

