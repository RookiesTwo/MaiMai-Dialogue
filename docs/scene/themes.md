---
title: 制作 Theme
description: 创建可复用 Theme，定制对话框、文字、选项和滚动控件。
---

# 制作 Theme

## 本章要实现什么

把 `welcome` 的默认深色界面改成浅色羊皮纸风格，同时保持背景、VisualObject 和滤镜不变。

## 开始前

你已经完成[添加场景滤镜](./filters.md)。Theme 只控制 Dialogue UI，不控制场景图片。

## 需要修改的文件

新增：

```text
<资源包>/assets/example/dialogue_themes/parchment.json
```

并同步修改资源包与数据包中的两个 `dialogues/guide/welcome.json`。

## 跟着做

1. 创建 `dialogue_themes/parchment.json`：

```json:line-numbers {2-44} [parchment.json]
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

2. 在资源包和数据包的 `welcome.json` 中，把 `presentation.theme` 改为：

```json{2} [本章改动]
{
  "theme": "example:parchment"
}
```

高亮行就是本章对 Dialogue 的唯一修改。

Theme 的每个区块和字段都可以省略；省略部分会使用内置默认值。上面给出完整结构，方便你从一份可运行配置开始调整。

3. 如果要直接复制整个 Dialogue，当前完整 `welcome.json` 如下：

::: details 展开完整 welcome.json

```json:line-numbers {3}
{
  "presentation": {
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
  },
  "steps": [
    {
      "speaker": {
        "type": "set",
        "id": "example:guide"
      },
      "text": "# 欢迎来到村庄\n\n我是这里的 **向导**。",
      "actions": [
        {
          "target": "guide_marker",
          "action": {
            "type": "reference",
            "id": "example:guide/enter"
          }
        }
      ]
    },
    {
      "text": "沿着 *石路* 向前，就能找到 `market`。"
    }
  ],
  "end": {
    "speaker": {
      "type": "hide"
    },
    "text": "请选择一个话题。",
    "actions": [
      {
        "target": "guide_marker",
        "action": {
          "type": "inline",
          "action": {
            "duration_ms": 500,
            "easing": "ease_in_out",
            "x": [
              { "at": 1.0, "value": 0.18 }
            ],
            "variant": {
              "at": 0.55,
              "value": "alternate"
            }
          }
        }
      },
      {
        "target": "background",
        "action": {
          "type": "inline",
          "action": {
            "duration_ms": 500,
            "easing": "ease_in_out",
            "variant": {
              "at": 0.55,
              "value": "alternate"
            }
          }
        }
      }
    ],
    "exit": {
      "type": "options",
      "options": [
        {
          "text": "了解村庄",
          "icon": "question",
          "target": {
            "type": "dialogue",
            "dialogue": "example:guide/about"
          }
        },
        {
          "text": "询问秘密地点",
          "icon": "exclamation",
          "target": {
            "type": "dialogue",
            "dialogue": "example:guide/secret"
          }
        },
        {
          "text": "离开",
          "target": {
            "type": "return"
          }
        }
      ]
    }
  }
}
```

把这份 JSON 同步保存到资源包 `assets` 与数据包 `data` 中的 `welcome.json`。

:::

## 进入游戏验证

按 `F3 + T` 重载客户端资源，并执行 `/reload` 后重新打开 `example:guide/welcome`。对话框、选项、图标与滚动条应变为浅色羊皮纸风格；背景和场景滤镜不应变化。

## 如果没有生效

- 回退到默认 Theme：检查文件路径和 `example:parchment` ID。
- JSON 被拒绝：颜色只能写成 `#RRGGBB` 或 `#AARRGGBB`。
- 选项展开高度异常：确认 expanded limit 不小于 collapsed limit。
- 只重载了服务端：Theme 是客户端资源，还必须按 `F3 + T`。

## 下一步

内容已经具备完整流程和表现。继续[双端发布](../publish/client-server.md)，把它安全地交付给其他玩家。
