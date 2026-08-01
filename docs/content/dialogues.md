---
title: Dialogue 与流程
description: 定义步骤、说话者操作、结尾、选项和导航。
---

# Dialogue 与流程

## 顶层结构

```json
{
  "requires": "quest.intro.started && !quest.intro.finished",
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

| 字段 | 必填 | 默认值 | 用途 |
|---|---:|---|---|
| `requires` | 否 | 无条件公开 | 服务端访问表达式 |
| `presentation` | 是 | — | Theme、背景、布局与画面对象 |
| `steps` | 否 | `[]` | 顺序播放的 DialogueStep |
| `end` | 是 | — | 唯一 DialogueEnd，必须包含 `exit` |

## DialogueStep 与 DialogueEnd

`steps` 中的每项以及 `end` 都可使用：

| 字段 | 必填 | 默认值 |
|---|---:|---|
| `text` | 否 | 无正文 |
| `speaker` | 否 | 继承当前 Speaker 状态 |
| `actions` | 否 | `[]` |

DialogueEnd 额外要求 `exit`。省略 `text` 可制作纯动画 Step；不要使用 JSON `null`。

```json
{
  "speaker": {
    "type": "set",
    "id": "example:guide"
  },
  "text": "这一句设置 Speaker。"
}
```

```json
{
  "speaker": {
    "type": "hide"
  },
  "text": "这一句隐藏名字栏。"
}
```

省略 `speaker` 会继承上一 Step；第一 Step 省略时名字栏保持隐藏。

## Return

```json
{
  "type": "return"
}
```

首次通过命令或 Java API 打开的 Dialogue 会成为本次 UI session 的 root。子 Dialogue 的 Return 会重新进入 root 的开头；root 自己 Return 会关闭界面。系统没有导航栈或恢复点。

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
        "dialogue": "example:guide/more"
      }
    },
    {
      "text": "返回",
      "target": {
        "type": "return"
      }
    }
  ]
}
```

`options` 至少包含一项。每个 Option：

| 字段 | 必填 | 说明 |
|---|---:|---|
| `text` | 是 | 非空白普通文本，不解析 Markdown |
| `icon` | 否 | `none`、`question`、`exclamation`、`dialogue`；默认 `none` |
| `target` | 是 | `dialogue` 或 `return` |

目标 Dialogue 会在选项显示前查询服务端，点击时再校验一次。条件不满足的选项会隐藏；若过滤后没有可见选项，当前 DialogueEnd 按 Return fallback 处理。

## 组织菜单和分支

一个实用模式是：

```text
root 菜单
├─ public Dialogue ── Return ──> root
├─ requires A 的 Dialogue ─────> root
└─ Return ─────────────────────> 关闭
```

同一个 Dialogue 可以在一次流程中作为 root，在另一次流程中作为子 Dialogue。允许多个选项指向同一目标，也允许指向自身，但需要避免无法退出的内容循环。

::: warning
Exit 和 Option 只负责导航，不会添加或删除 ProgressNode。进度变化应由命令、数据包逻辑或 Java API 完成。
:::
