---
title: SceneAction
description: 使用内联或可复用 Action 制作有限关键帧动画。
---

# SceneAction

每个 Step 的 `actions` 可同时调度多个有限 Action。

## SceneActionCall

```json
{
  "target": "guide",
  "delay_ms": 100,
  "action": {
    "type": "reference",
    "id": "example:guide/enter"
  }
}
```

| 字段 | 必填 | 默认/约束 |
|---|---:|---|
| `target` | 是 | VisualObject ID，或特殊目标 `background`、`dialogue` |
| `delay_ms` | 否 | `0`，范围 `0..60000` |
| `action` | 是 | `reference` 或 `inline` |

外部 Action 位于：

```text
assets/<namespace>/presentation_actions/<path>.json
```

## 内联 Action

```json
{
  "target": "guide",
  "action": {
    "type": "inline",
    "action": {
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
  }
}
```

SceneAction 字段：

| 字段 | 默认/约束 |
|---|---|
| `duration_ms` | `300`，范围 `0..60000` |
| `easing` | `linear`；还可用 `ease_in`、`ease_out`、`ease_in_out` |
| `blocking` | `true` |
| `x`、`y`、`scale`、`opacity` | 数值关键帧轨道 |
| `variant` | 单次离散差分切换 |
| `visible` | 单次可见性切换 |

## 关键帧是相对值

```json
{
  "x": [
    { "at": 0.0, "value": 0.0 },
    { "at": 1.0, "value": 0.2 }
  ]
}
```

`at` 是 Action 内 `[0,1]` 的归一化时间，必须严格递增。`value` 是相对进入 Step 时状态 S0 的偏移量，不是绝对坐标。上例会让对象最终向右移动 `0.2`。

`scale` 与 `opacity` 同样使用相对值；最终 scale 必须大于 0，最终 opacity 必须位于 `[0,1]`。

## 离散变更

```json
{
  "variant": {
    "at": 0.5,
    "value": "happy"
  },
  "visible": {
    "at": 0.0,
    "value": true
  }
}
```

`at` 默认 `0`。目标 variant 必须已经在背景或 VisualObject 的 `variants` 中声明。

## Target 能力

| Target | 可写属性 |
|---|---|
| VisualObject | `x`、`y`、`scale`、`opacity`、`variant`、`visible` |
| `background` | 仅 `variant` |
| `dialogue` | 仅 `opacity` |

同一 Step 中，多个 Action 不能写同一 target 的同一属性。缺失的引用、未声明 target、非法最终值和属性冲突都会由客户端资源校验报告。

## Blocking 与跳过

`blocking: true` 的 Action 会和打字机共同阻止 Step 进入 READY。播放中推进会立即提交预先计算的最终状态 S1，不会从中途状态重复叠加相对值。

首个 Step（或没有 DialogueStep 时的 DialogueEnd）如果没有显式控制 `dialogue`，系统会自动加入 250ms `ease_out` 的阻塞淡入。

::: warning 当前限制
当前只支持有限 Action，不支持循环、跨 Step 持久动画、任意 shader、复杂 easing 或动态 VisualObject 生命周期。
:::
