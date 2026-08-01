---
title: 调整对话框布局
description: 使用归一化坐标改变对话框的位置、宽度和最大高度。
---

# 调整对话框布局

## 本章要实现什么

把默认贴近屏幕底边的对话框稍微上移，并缩窄到屏幕宽度的 82%。

## 开始前

你已经完成[播放 SceneAction](./actions.md)。本章只改变对话框的位置，不改变背景、对象或动画。

## 需要修改的文件

同步修改两个 `assets/data` 位置的 `dialogues/guide/root.json`。

## 跟着做

1. 在 `presentation` 中加入完整的 `dialogue_box`：

```json
{
  "x": 0.5,
  "y": 0.95,
  "width": 0.82,
  "max_height": 0.42,
  "anchor": "bottom_center"
}
```

加入后，root 的完整 `presentation` 应为：

```json
{
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
  }
}
```

将这段作为 root 顶层的 `presentation` 值；`steps` 与 `end` 保持上一章不变。`x`、`y` 是锚点在屏幕中的比例位置，`width` 和 `max_height` 是相对于可用画面的比例。

2. 如果不想手工拼接字段，可将两个 `root.json` 直接替换为下面的完整内容：

::: details 展开完整 root.json

```json
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
    }
  },
  "steps": [
    {
      "speaker": {
        "type": "set",
        "id": "example:guide"
      },
      "text": "# 欢迎来到村庄\n\n你想了解什么？",
      "actions": [
        {
          "target": "guide_marker",
          "action": {
            "type": "reference",
            "id": "example:guide/enter"
          }
        }
      ]
    }
  ],
  "end": {
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

## 进入游戏验证

重载后打开 root。对话框应仍然以底部中心为锚点，但比默认布局更窄，并与屏幕底边留出更明显的距离。

## 如果没有生效

- 对话框跑出屏幕：先恢复 `anchor: bottom_center`，再调整 `x`、`y`。
- JSON 被拒绝：`x`、`y` 必须在 `[0,1]`，尺寸必须大于 0 且不超过 1。
- 内容被裁切：增大 `max_height`；选项仍可在限制高度内滚动。

## 下一步

继续[添加场景滤镜](./filters.md)。
