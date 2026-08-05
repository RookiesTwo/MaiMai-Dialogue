---
title: 内置预设 SceneAction
description: MOD 自带的可复用动画清单、ID、参数与使用约定。
---

# 内置预设 SceneAction

MOD 自带一组可直接引用的预设动画,文件位于 `assets/maimai_dialogue/actions/presets/`,ID 统一为 `maimai_dialogue:presets/<名称>`。演示对话 `maimai_dialogue:demo/actions` 会逐条播放全部预设,并标注当前调用的名称与说明。

## 使用方式

和自定义外部 Action 一样,在步骤的 `actions` 里用 `reference` 调用:

```json
{
  "target": "demo_marker",
  "action": {
    "type": "reference",
    "id": "maimai_dialogue:presets/pop_in"
  }
}
```

## 预设约定

- 数值关键帧是**相对偏移**:所有预设都以偏移 `0` 结尾,播放结束后对象回到进入该步时的状态,可放心组合。
- 淡入与退场预设假定对象默认 `opacity` 为 `1.0`(VisualObject 默认值)。若对象透明度不是 1,请先用 inline Action 恢复,否则退场预设的最终透明度可能低于 0。
- `variant` 是内容相关的单次切换,没有通用预设;切换差分请使用 inline Action。
- 入场、退场与对话框预设为 `blocking: true`,会阻止推进;强调类预设为 `blocking: false`,打字机可继续播放。

## 入场

| ID | 效果 | 时长 / 缓动 |
|---|---|---|
| `fade_in` | 从透明淡入到原有透明度 | 350ms ease_out |
| `slide_in_left` | 从左侧 30% 平移入场 | 500ms ease_out |
| `slide_in_right` | 从右侧 30% 平移入场 | 500ms ease_out |
| `slide_in_up` | 从下方 25% 上移入场 | 500ms ease_out |
| `slide_in_down` | 从上方 25% 下移入场 | 500ms ease_out |
| `pop_in` | 缩小后放大并轻微过冲,弹性入场 | 450ms ease_out |
| `zoom_in` | 从接近消失的微小尺寸放大回原位 | 500ms ease_out |
| `drop_in` | 先在上方现形,再加速落到原位(重力感) | 500ms ease_in |

## 强调(非阻塞)

| ID | 效果 | 时长 / 缓动 |
|---|---|---|
| `bounce` | 向上跳起 15% 再落回 | 600ms ease_in_out |
| `pulse` | 先放大 8% 再缩回 | 400ms ease_in_out |
| `wiggle` | 向两侧各摆 5% | 400ms linear |
| `shake` | 小幅高频左右震动 | 300ms linear |
| `dialogue_shake` | 整个对话框小幅快速震动(作用于 `dialogue`),表达强烈感情 | 450ms linear |
| `flash` | 透明度骤降再恢复 | 250ms linear |

## 退场

| ID | 效果 | 时长 / 缓动 |
|---|---|---|
| `fade_out` | 逐渐变透明直至消失 | 350ms ease_in |
| `slide_out_left` | 边向左移动边淡出 | 400ms ease_in |
| `slide_out_right` | 边向右移动边淡出 | 400ms ease_in |
| `rise_out` | 缓缓上升并淡出,飘走离场 | 450ms ease_in |
| `shrink_out` | 一边缩小一边淡出 | 400ms ease_in |

## 对话框与工具

| ID | 效果 | 时长 / 缓动 |
|---|---|---|
| `dialogue_enter` | 对话框上移、微缩并淡入,适合对话开场 | 700ms ease_out |
| `show` | 立即恢复可见 | 0ms |
| `hide` | 立即隐藏 | 0ms |

`dialogue_enter` 只用于 `target: dialogue`,其透明度轨道以进入该步时的对话框状态为基准(对话开场时为 0);`show` / `hide` 只写 `visible`,不改变透明度。
