---
title: Dialogue JSON
description: Dialogue、步骤、Speaker、结尾、选项和导航的完整字段参考。
---

# Dialogue JSON

## 顶层字段

```json
{
  "requires": "guide.started && !guide.finished",
  "presentation": {
    "theme": "maimai_dialogue:default"
  },
  "steps": [],
  "end": {
    "exit": {
      "type": "return"
    }
  }
}
```

| 字段 | 必填 | 默认值 | 说明 |
|---|---:|---|---|
| `requires` | 否 | 无条件公开 | 服务端访问表达式 |
| `presentation` | 是 | — | Theme、场景和对话框布局 |
| `steps` | 否 | `[]` | 按顺序播放的普通步骤 |
| `end` | 是 | — | 最后一步，必须提供 `exit` |

不要使用 JSON `null`。可选字段应直接省略。

## 普通步骤与结尾

`steps` 中的每项和 `end` 都支持：

| 字段 | 必填 | 默认值 | 说明 |
|---|---:|---|---|
| `text` | 否 | 无正文 | 仅此字段解析 Markdown |
| `speaker` | 否 | 继承当前状态 | 设置、隐藏或继续沿用 Speaker |
| `actions` | 否 | `[]` | 同时调度的 SceneActionCall |

`end` 额外要求 `exit`。省略 `text` 可制作纯动画步骤。

## Speaker 操作

设置 Speaker：

```json
{
  "type": "set",
  "id": "example:guide"
}
```

隐藏 Speaker：

```json
{
  "type": "hide"
}
```

省略 `speaker` 表示继承；第一步省略时名称栏保持隐藏。Speaker 资源本身只有一个必填的非空 `name`：

```json
{
  "name": "村庄向导"
}
```

## Return

```json
{
  "type": "return"
}
```

- 入口 Dialogue 的 Return 关闭界面。
- 子 Dialogue 的 Return 从本次入口 Dialogue 开头重新播放。
- 系统没有导航栈或恢复位置。

## Options

```json
{
  "type": "options",
  "options": [
    {
      "text": "继续交谈",
      "icon": "dialogue",
      "target": {
        "type": "dialogue",
        "dialogue": "example:guide/about"
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
```

| Option 字段 | 必填 | 默认值/说明 |
|---|---:|---|
| `text` | 是 | 非空普通文本，不解析 Markdown |
| `icon` | 否 | `none`；还可用 `question`、`exclamation`、`dialogue` |
| `target` | 是 | `dialogue` 或 `return` |

`options` 至少一项。Dialogue target 在显示前由服务端查询，点击时再次校验。条件不满足的目标会隐藏；过滤后没有可见选项时，下一次推进按 Return 处理。

## 播放规则

- 正文和全部 blocking SceneAction 完成后，当前步骤才可继续。
- 播放期间第一次推进只提交当前文字和动画的最终状态。
- 再次推进才进入下一步或执行 `end.exit`。
- `exit` 和 Option 只导航，不会自动修改 ProgressNode。
