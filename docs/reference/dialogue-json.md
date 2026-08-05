---
title: Dialogue JSON
description: Dialogue、步骤、Speaker、结尾、选项和导航的完整字段参考。
---

# Dialogue JSON

## 顶层字段

```json
{
  "requires": "guide.started && !guide.finished",
  "skip_summary": "跳过后将直接进入最终选择。",
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
| `skip_summary` | 否 | 无确认摘要 | 长按跳过成功后显示的 Markdown 摘要 |
| `presentation` | 是 | — | Theme、场景和对话框布局 |
| `steps` | 否 | `[]` | 按顺序播放的普通步骤 |
| `end` | 是 | — | 最后一步，必须提供 `exit` |

不要使用 JSON `null`。可选字段应直接省略。

`skip_summary` 属于整个 Dialogue，而不是单个 Step。字段存在时必须是非空、非纯空白字符串；长按跳过按钮后，玩家需要阅读摘要并确认。字段省略时，长按完成后直接跳至 `end`。摘要支持与正文相同的 Markdown 子集，但不会使用打字机效果，也不会进入历史记录。

## 普通步骤与结尾

`steps` 中的每项和 `end` 都支持：

| 字段 | 必填 | 默认值 | 说明 |
|---|---:|---|---|
| `text` | 否 | 无正文 | 字符串为固定正文；非空字符串数组为随机正文；仅此字段解析 Markdown |
| `typewriter_interval_ms` | 否 | 玩家客户端默认值（初始 `30`） | 每个 Unicode code point 的显示间隔，范围 `0..1000`；`0` 表示立即显示。显式值始终优先于玩家默认值 |
| `speaker` | 否 | 继承当前状态 | 设置、隐藏或继续沿用 Speaker |
| `actions` | 否 | `[]` | 同时调度的 SceneActionCall |

`end` 额外要求 `exit`。省略 `text` 可制作纯动画步骤。

打字机速度按节点配置，`steps[]` 和 `end` 各自独立。例如：

```json
{
  "text": "这句话会更快显示。",
  "typewriter_interval_ms": 12
}
```

间隔越小，文字显示越快。单句打字机动画最长为 10 秒；`0` 会直接显示完整正文，但仍会等待该节点的 blocking Action。

随机正文直接写在 `text` 数组中：

```json
{
  "text": [
    "今天的天气不错。",
    "你看起来很有精神。",
    "欢迎再次来到这里。"
  ]
}
```

- 数组必须至少包含一个字符串；空数组、`null` 和非字符串元素会导致 Dialogue 加载失败。
- 每个数组位置的抽取概率相同；重复字符串会按出现次数参与抽取。
- 每位玩家进入该节点时独立抽取。同一次 Dialogue session 中结果保持不变，包括返回根 Dialogue 或再次进入同一子 Dialogue；关闭界面后重新打开会重新抽取。
- 抽取后的正文按普通 `text` 处理，继续支持 Markdown。空字符串仍表示无正文。

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
| `command` | 否 | target 前由 logical server 执行的一条指令，或按顺序执行的指令数组 |
| `target` | 是 | `dialogue` 或 `return` |

`options` 至少一项。Dialogue target 在显示前由服务端查询，点击时再次校验。条件不满足的目标会隐藏；过滤后没有可见选项时，下一次推进按 Return 处理。

### Option command

`command` 是 Option 的可选前置副作用，不替代 `target`：

```json
{
  "text": "接受任务",
  "command": [
    "tag @s add accepted_quest",
    "function example:accept_quest"
  ],
  "target": {
    "type": "dialogue",
    "dialogue": "example:quest/accepted"
  }
}
```

- `command` 可以是一个 string，也可以是至少包含一项的 string array；每条指令都必须是非空单行字符串，开头的 `/` 可以省略。
- 服务端先检查当前 Dialogue 和 Dialogue target 的访问条件，再以点击玩家作为 `@s` 和执行位置、permission level 2 按数组顺序执行。
- 任意一条失败或抛出异常都会立即停止后续指令，玩家留在当前选项页；全部成功后才执行原 target。指令不能用于解锁本次 target。
- 服务端只执行 Data Pack 中对应 Option 的 command 序列，不信任客户端 Resource Pack 的指令内容。
- command 可以重复触发，已经完成的副作用不会因后续失败而回滚。奖励类指令应配合 `requires`、score、tag 或幂等 function 防止重复领取；复杂、可复用或要求集中维护的流程仍建议使用 Data Pack function。

## 播放规则

- 正文和全部 blocking SceneAction 完成后，当前步骤才可继续。
- 播放期间第一次推进只提交当前文字和动画的最终状态。
- 再次推进才进入下一步或执行 `end.exit`。
- 默认按住 Ctrl 会以 4 倍速度播放正文和有限 SceneAction；Continue Step 就绪后会自动进入下一步，直到进入 `end`。`end` 仍会加速播放，但不会自动执行 Exit。玩家可在客户端设置中调整倍率和键位。
- 右上角跳过按钮默认需长按 600ms；玩家可以配置统一的鼠标/键盘长按时长。触发后会结算剩余 Speaker 与 SceneAction，并直接完成 `end`；尚未进入的正文不会写入历史。
- `exit` 和普通 Option 只导航；只有显式配置的 Option `command` 会产生服务端副作用。
