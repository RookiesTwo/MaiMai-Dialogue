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

同步修改资源包与数据包中的：

```text
<资源包>/assets/example/dialogues/guide/welcome.json
<数据包>/data/example/dialogues/guide/welcome.json
```

## 跟着做

在 `presentation` 中加入 `dialogue_box`。先看新增字段，需要复制时切换到完整文件：

::: code-group

```json{3-9} [本章改动]
{
  "presentation": {
    "dialogue_box": {
      "x": 0.5,
      "y": 0.95,
      "width": 0.82,
      "max_height": 0.42,
      "anchor": "bottom_center"
    }
  }
}
```

```json:line-numbers {14-20} [完整 welcome.json]
{
  "presentation": {
    "theme": "maimai_dialogue:default",
    "scene": "example:guide/welcome",
    "dialogue_box": {
      "x": 0.5,
      "y": 0.95,
      "width": 0.82,
      "max_height": 0.42,
      "anchor": "bottom_center"
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

把完整文件同步保存到两个 Pack。`x`、`y` 是锚点在屏幕中的比例位置，`width` 和 `max_height` 是相对于可用画面的比例。`max_height` 限制收缩状态；展开选项后，高度上限会临时放宽到整个可用画面，实际高度仍由当前内容与 Option 数量决定。

## 使用 Action 移动或缩放对话框

把 Action target 写为 `dialogue`，即可在某个 Step 中动画化 DialogueBox 的 `x`、`y`、`scale` 和 `opacity`：

```json
{
  "target": "dialogue",
  "action": {
    "type": "inline",
    "action": {
      "duration_ms": 350,
      "easing": "ease_out",
      "x": [
        { "at": 0.0, "value": -0.08 },
        { "at": 1.0, "value": 0.0 }
      ],
      "scale": [
        { "at": 0.0, "value": -0.08 },
        { "at": 1.0, "value": 0.0 }
      ],
      "opacity": [
        { "at": 0.0, "value": -1.0 },
        { "at": 1.0, "value": 0.0 }
      ]
    }
  }
}
```

数值仍是相对于进入当前 Step 时状态的偏移，最终状态会保留到后续 Step。Action 不修改 `width`、`max_height` 或 `anchor`。

## 进入游戏验证

重载后打开 `example:guide/welcome`。对话框应仍然以底部中心为锚点，但比默认布局更窄，并与屏幕底边留出更明显的距离。

## 如果没有生效

- 对话框跑出屏幕：先恢复 `anchor: bottom_center`，再调整 `x`、`y`。
- JSON 被拒绝：`x`、`y` 必须在 `[0,1]`，尺寸必须大于 0 且不超过 1。
- 收缩时内容被裁切：增大 `max_height`；展开后高度上限会自动放宽到 `1.0`，超出可用空间的选项仍可滚动。

## 下一步

继续[添加场景滤镜](./filters.md)。
