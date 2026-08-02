---
title: SceneAction JSON
description: SceneActionCall、关键帧、差分切换、target 和播放规则参考。
---

# SceneAction JSON

## SceneActionCall

每个步骤的 `actions` 是一组同时调度的调用：

```json
{
  "target": "guide_marker",
  "delay_ms": 100,
  "action": {
    "type": "reference",
    "id": "example:guide/enter"
  }
}
```

| 字段 | 必填 | 默认值/约束 |
|---|---:|---|
| `target` | 是 | VisualObject ID，或 `background`、`dialogue` |
| `delay_ms` | 否 | `0`，范围 `0..60000` |
| `action` | 是 | `reference` 或 `inline` |

外部 Action 位于 `assets/<namespace>/presentation_actions/<path>.json`。

## Inline Action

```json
{
  "type": "inline",
  "action": {
    "duration_ms": 500,
    "easing": "ease_out",
    "blocking": true,
    "x": [
      { "at": 0.0, "value": -0.2 },
      { "at": 1.0, "value": 0.0 }
    ]
  }
}
```

| SceneAction 字段 | 默认值/约束 |
|---|---|
| `duration_ms` | `300`，范围 `0..60000` |
| `easing` | `linear`；可用 `ease_in`、`ease_out`、`ease_in_out` |
| `blocking` | `true` |
| `x`、`y`、`scale`、`opacity` | 数值关键帧轨道 |
| `variant` | 单次差分切换 |
| `visible` | 单次可见性切换 |

## 数值关键帧

```json
{
  "x": [
    { "at": 0.0, "value": 0.0 },
    { "at": 1.0, "value": 0.2 }
  ]
}
```

- `at` 范围为 `[0,1]`，并且必须严格递增。
- `value` 是相对于进入步骤时状态的偏移，不是绝对坐标。
- 最终 scale 必须大于 0；最终 opacity 必须在 `[0,1]`。

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

`at` 默认 `0`。variant 必须已经在目标的 `variants` 中声明。

## Target 能力

| Target | 可写属性 |
|---|---|
| VisualObject | `x`、`y`、`scale`、`opacity`、`variant`、`visible` |
| `background` | 仅 `variant` |
| `dialogue` | `x`、`y`、`scale`、`opacity` |

同一步中，多个 Action 不能写同一 target 的同一属性。缺失引用、未声明 target、非法 variant 和最终值冲突都会在客户端 reload 时报告。

`dialogue` 的 `x`、`y` 修改 DialogueBox 锚点的归一化屏幕坐标，`scale` 以当前锚点为缩放中心。它们与 VisualObject 一样使用相对关键帧；最终 scale 必须大于 0，opacity 必须保持在 `[0,1]`。`width`、`max_height` 和 `anchor` 仍是静态布局配置，不能由 Action 修改。

## Blocking 与跳过

blocking Action 会与打字机共同阻止步骤继续。播放中推进会立即提交预先计算的最终状态，不会从中间状态重复叠加相对值。

首个步骤如果没有显式控制 `dialogue`，系统会自动加入 250ms `ease_out` 的阻塞淡入。只要首个步骤显式调用了 `dialogue` Action，就由该 Action 完整负责入场效果；需要淡入时应同时提供 opacity 轨道。
