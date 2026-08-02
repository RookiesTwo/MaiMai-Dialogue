---
title: 步骤与推进
description: 使用 steps 把一段对话拆成多个阅读步骤。
---

# 步骤与推进

## 本章要实现什么

玩家会依次读到三段文字。每段播放完成后再次推进，才会进入下一段。

## 开始前

你已经能打开[第一段对话](../start/first-dialogue.md)。当前 `welcome.json` 只有结束点，没有前置步骤。

## 需要修改的文件

同步替换资源包与数据包中的两个 `welcome.json`：

```text
<资源包>/assets/example/dialogues/guide/welcome.json
<数据包>/data/example/dialogues/guide/welcome.json
```

## 跟着做

这次只关注新增的 `steps` 数组，以及修改后的结束点文字。切换代码组可查看完整文件：

::: code-group

```json{2-9,11} [本章改动]
{
  "steps": [
    {
      "text": "欢迎来到村庄。"
    },
    {
      "text": "沿着石路向前，就能找到集市。"
    }
  ],
  "end": {
    "text": "祝你旅途顺利。"
  }
}
```

```json:line-numbers {5-12,14} [完整 welcome.json]
{
  "presentation": {
    "theme": "maimai_dialogue:default"
  },
  "steps": [
    {
      "text": "欢迎来到村庄。"
    },
    {
      "text": "沿着石路向前，就能找到集市。"
    }
  ],
  "end": {
    "text": "祝你旅途顺利。",
    "exit": {
      "type": "return"
    }
  }
}
```

:::

把完整文件同步保存到两个 Pack。`steps` 按数组顺序播放，`end` 始终是最后一页。正文正在逐字显示时，第一次推进只会立即显示完整文字；再次推进才会进入下一页。

## 为步骤设置随机文本

把某个 `steps[]` 项或 `end` 的 `text` 从字符串改成非空字符串数组：

```json
{
  "steps": [
    {
      "text": [
        "欢迎来到村庄。",
        "很高兴再次见到你。",
        "今天也要去集市吗？"
      ]
    }
  ]
}
```

玩家第一次进入该步骤时会等概率抽取一项。抽取结果在当前 Dialogue session 中固定，因此返回根 Dialogue 或再次进入同一子 Dialogue 时不会变化；关闭对话界面后重新打开才会重新抽取。数组正文同样支持 Markdown，数组不能为空，也不能包含非字符串值。

## 进入游戏验证

执行 `/reload`、按 `F3 + T`，再打开：

```text
/maimai_dialogue open @s example:guide/welcome
```

依次推进后，应看到“欢迎来到村庄”→“沿着石路向前”→“祝你旅途顺利”，最后关闭。

## 如果没有生效

- 只看到最后一句：检查 `steps` 是否位于顶层，而不是写进 `end`。
- 一次按键没有换页：正文还在播放时，第一次推进只负责跳过播放。
- 两端显示不同：确认资源包与数据包中的 JSON 完全一致。

## 下一步

继续[显示 Speaker](./speaker.md)，为文字加入说话者名称。
