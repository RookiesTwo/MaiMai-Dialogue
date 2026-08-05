---
title: 播放 SceneAction
description: 创建可复用动画，并在 Dialogue 步骤中调用它。
---

# 播放 SceneAction

## 本章要实现什么

打开 `welcome` 时，`guide_marker` 会从左侧淡入；到达结束点时，它会向右移动并切换为 diamond，背景也会切换差分。

## 开始前

你已经完成[添加 VisualObject](./visual-objects.md)，场景中存在 `guide_marker`、背景 `alternate` 和对象 `alternate` 差分。

## 需要修改的文件

新增可复用 SceneAction：

```text
<资源包>/assets/example/actions/guide/enter.json
```

并同步修改资源包与数据包中的两个 `dialogues/guide/welcome.json`。

## 跟着做

1. 创建独立的动画文件 `enter.json`。它定义 500 ms 的横向移动与淡入：

```json:line-numbers {2-14} [enter.json]
{
  "duration_ms": 500,
  "easing": "ease_out",
  "blocking": true,
  "x": [
    { "at": 0.0, "value": -0.2 },
    { "at": 1.0, "value": 0.0 }
  ],
  "opacity": [
    { "at": 0.0, "value": -1.0 },
    { "at": 1.0, "value": 0.0 }
  ]
}
```

这个资源的代号是 `example:guide/enter`。在往下看之前，先花一分钟理解动画（场景动作，SceneAction）是怎么写的，因为后面会出现不少新写法。

### 先理解：一个动画是怎么写出来的

一个场景动作就是在**一段时间内**，对画面对象（或背景、对话框）的某些属性做变化。写法由这几部分组成：

| 写法 | 含义 |
|---|---|
| `duration_ms` | 动画总时长，单位毫秒（500 就是半秒） |
| `easing` | 速度变化方式：`linear` 匀速；`ease_in` 先慢后快；`ease_out` 先快后慢；`ease_in_out` 两头慢中间快 |
| `blocking` | 是否挡住推进：`true` 时动画播完之前不能翻页，`false` 时文字播完就能翻页、动画在后台继续 |
| `x`、`opacity` 等 | 数值轨道：一组"进度点"，告诉动画在百分之多少时值是多少 |
| `variant`、`visible` | 离散切换：在某个时刻把图片换成另一个差分、或切换显示/隐藏 |

数值轨道里每一项是 `{ "at": ..., "value": ... }`：`at` 表示动画播到百分之多少（`0` 是开始，`1` 是结束，必须是递增的），`value` 是那一刻的值。

::: warning 重要：数值是"挪多少"，不是"挪到哪"
`x` 轨道里的值表示**从当前状态挪动多少**，不是移动到的绝对位置。比如对象当前在 `x: 0.5`，轨道写了 `{ "at": 1.0, "value": 0.18 }`，播完后对象在 `0.5 + 0.18 = 0.68`，而不是 `0.18`。写多段动画时，注意别按"绝对坐标"的思路写值。
:::

`variant` 的写法只有一项（比如 `{ "at": 0.55, "value": "alternate" }`），表示播到 55% 时把图片换成 `alternate` 这张差分。换的差分必须已经在这张图的 `variants` 里声明过。

### 在对话里调用动画

2. 在第一步调用独立文件里的动画，在结束点加入两个直接写在对话里的动画。先看新增调用，再切换到完整 Dialogue：

::: code-group

```json{4-12,16-46} [本章改动]
{
  "steps": [
    {
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
    ]
  }
}
```

```json:line-numbers {39-48,58-90} [完整 welcome.json]
{
  "presentation": {
    "type": "reference",
    "id": "example:guide/welcome"
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

把完整 `welcome.json` 同步保存到两个 Pack。这段代码里出现了两种调用动画的方式：

- `type: "reference"`：指向**独立文件里的动画**（本例指向 `example:guide/enter`），适合要复用的动画；
- `type: "inline"`：把动画内容**直接写在对话里**，适合只用一次的动画，不用单独建文件。

`target` 指定动画作用在谁身上：`guide_marker` 是场景里定义的视觉对象，`background` 是背景。注意背景只能切差分（`variant`），不能移动或缩放；`dialogue` 可以移动对话框。

## 进入游戏验证

重载后打开 `example:guide/welcome`：

1. emerald 应从左侧淡入；
2. 播放中推进会直接完成入场，不会立刻换页；
3. 进入结束点后，emerald 向右移动并变成 diamond；
4. 背景同时切换为另一张全景图。

## 如果没有生效

- 动画文件缺失：检查文件目录是 `actions`，代号是 `example:guide/enter`。
- 日志提示 target 缺失：`target` 必须与 `visual_objects` 中的 `guide_marker` 完全一致。
- variant 切换失败：确认 `alternate` 已在对应 `variants` 中声明。
- 同一属性冲突：同一步中不能让多个 Action 写同一 target 的同一属性。

## 下一步

继续[调整对话框布局](./dialogue-box.md)。完整字段和关键帧规则可在 [SceneAction JSON 参考](../reference/scene-action-json.md) 中查阅，MOD 自带的可直接引用的动画见[内置预设 SceneAction](../reference/preset-actions.md)。
