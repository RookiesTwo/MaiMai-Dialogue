---
title: 调整对话框布局
description: 使用归一化坐标改变对话框的位置、宽度和最大高度。
---

# 调整对话框布局

## 本章要实现什么

把默认贴近屏幕底边的对话框稍微上移，并缩窄到屏幕宽度的 82%。

## 开始前

你已经完成[播放 SceneAction](./actions.md)，Dialogue 正在引用 `example:guide/welcome` Presentation。

## 需要修改的文件

只修改 Resource Pack 中的演出配置文件（PresentationDefinition）：

```text
<资源包>/assets/example/presentations/guide/welcome.json
```

Dialogue 继续使用原来的 reference，不需要修改双端副本。

## 跟着做

在演出配置文件中加入 `dialogue_box`：

```json:line-numbers {4-10} [presentations/guide/welcome.json]
{
  "theme": "maimai_dialogue:default",
  "scene": "example:guide/welcome",
  "dialogue_box": {
    "x": 0.5,
    "y": 0.95,
    "width": 0.82,
    "max_height": 0.42,
    "anchor": "bottom_center"
  }
}
```

字段含义：

| 字段 | 含义 |
|---|---|
| `x`、`y` | anchor 在可用画面中的比例位置 |
| `width` | 对话框宽度相对于可用画面的比例 |
| `max_height` | 选项收缩时的最大高度比例 |
| `anchor` | 对齐到 x/y 的九宫格锚点 |

`x`、`y` 范围是 `[0,1]`；`width`、`max_height` 范围是 `(0,1]`。展开选项后，高度上限会临时放宽到 `1.0`，实际高度仍由正文和 Option 数量决定。

`anchor` 决定对话框的哪个点对准 `x`/`y` 指定的位置，可选九个值：

```text
top_left      top_center      top_right
center_left   center          center_right
bottom_left   bottom_center   bottom_right
```

`x`、`y` 是"anchor 点"在画面中的位置。这里的"画面"指整个对话界面铺满的游戏窗口区域。例如 `x: 0.5, y: 0.95, anchor: bottom_center` 表示：对话框底部中心点放在画面水平正中、离底边 5% 的位置。

正文超过剩余高度时会自动变成可滚动区域，Speaker 和 Option 不会跟着正文滚动。Option 数量超过 Theme 的显示上限时，也会使用自己的滚动区域。因此正文和 Option 可以分别滚动，但为了让玩家更容易阅读，建议不要在显示大量 Option 的结尾同时放置长正文。

## 进入游戏验证

按 `F3 + T` reload Resource Pack 后再次打开 `example:guide/welcome`。对话框应稍微离开底边，宽度约占画面的 82%。这里只改了资源包，不需要执行 `/reload`。

## 如果没有生效

- 布局没有变化：确认修改的是 `presentations/guide/welcome.json`，Dialogue reference ID 是 `example:guide/welcome`。
- 对话框出现在意外位置：检查 `anchor`；本例使用 `bottom_center`。
- 正文出现滚动条：说明内容超过了 `max_height`；可以滚动阅读，或适当提高高度上限。
- Option 区域过小：使用选项展开按钮，并避免在选项页同时放置长正文。
- JSON 报错：确认 `width`、`max_height` 大于 0，且没有超过 1。

## 下一步

继续[添加场景滤镜](./filters.md)，为同一套 Presentation 添加画面效果。
