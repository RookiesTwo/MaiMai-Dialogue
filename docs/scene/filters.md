---
title: 添加场景滤镜
description: 使用 color_adjust 或 crt 改变背景与 VisualObject 的画面效果。
---

# 添加场景滤镜

## 本章要实现什么

降低背景与 VisualObject 的饱和度，并加入轻微冷色调。对话框和选项仍保持原来的清晰颜色。

## 开始前

你已经完成[调整对话框布局](./dialogue-box.md)，`welcome` 的 `presentation` 中已有背景和 `guide_marker`。

## 需要修改的文件

同步修改资源包与数据包中的：

```text
<资源包>/assets/example/dialogues/guide/welcome.json
<数据包>/data/example/dialogues/guide/welcome.json
```

## 跟着做

在 `presentation` 中加入 `filter`。本章使用 `color_adjust`：

::: code-group

```json{3-9} [本章改动]
{
  "presentation": {
    "filter": {
      "type": "color_adjust",
      "brightness": -0.03,
      "contrast": 1.08,
      "saturation": 0.6,
      "tint": "#30A0C8FF"
    }
  }
}
```

```json:line-numbers {41-47} [完整 welcome.json]
{
  "presentation": {
    "theme": "maimai_dialogue:default",
    "background": {
      "variants": {
        "default": "minecraft:gui/title/background/panorama_0.png",
        "alternate": "minecraft:gui/title/background/panorama_1.png"
      },
      "initial_variant": "default",
      "fit": "cover",
      "opacity": 0.82
    },
    "dialogue_box": {
      "x": 0.5,
      "y": 0.95,
      "width": 0.82,
      "max_height": 0.42,
      "anchor": "bottom_center"
    },
    "visual_objects": {
      "guide_marker": {
        "variants": {
          "default": "minecraft:item/emerald.png",
          "alternate": "minecraft:item/diamond.png"
        },
        "initial_variant": "default",
        "x": 0.5,
        "y": 0.3,
        "anchor": "center",
        "scale": 8.0,
        "sampling": "nearest",
        "opacity": 1.0,
        "visible": true,
        "z_index": 10
      }
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

:::

把完整文件同步保存到两个 Pack。Filter 只处理当前 Dialogue 的背景和 VisualObject，不会改变 DialogueBox、Options、历史记录或后方的 Minecraft 世界。

如果你想尝试 CRT 效果，可将 `filter` 完整替换为：

```json{2-10} [CRT filter]
{
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
```

## 进入游戏验证

重载后打开 `example:guide/welcome`。背景和 emerald/diamond 应明显降低饱和度并带有冷色调，对话框文字颜色不应变化。

## 如果没有生效

- 对话框也被调色：确认你修改的是 `presentation.filter`，而不是 Theme。
- JSON 加载失败：检查 `type` 是 `color_adjust` 或 `crt`。
- CRT 过强：先降低 `curvature`、`scanline_strength`、`mask_strength` 和 `bloom`。
- 切换到子 Dialogue 后滤镜消失：每个 Dialogue 都有自己的 Presentation，这是正常行为。

## 下一步

继续[制作 Theme](./themes.md)，单独调整对话框与选项样式。
