---
title: 显示 Speaker
description: 创建可复用的 Speaker，并在步骤中显示或隐藏名称。
---

# 显示 Speaker

## 本章要实现什么

玩家会看到说话者（Speaker）“村庄向导”。后续步骤会继承这位 Speaker，最后一页则作为旁白隐藏名称。

## 开始前

你已经完成[步骤与推进](./steps.md)。

## 需要修改的文件

在资源包中新增 Speaker：

```text
<资源包>/assets/example/speakers/guide.json
```

并同步修改资源包与数据包中的两个 `welcome.json`。

## 跟着做

1. 创建 Speaker 文件。这个小文件的全部内容都是本章新增的：

```json:line-numbers {2} [guide.json]
{
  "name": "村庄向导"
}
```

它的资源 ID 是 `example:guide`。

2. 在第一步设置 Speaker，在结束点隐藏 Speaker。先看改动，再切换到完整文件复制：

::: code-group

```json{4-7,13-15} [本章改动]
{
  "steps": [
    {
      "speaker": {
        "type": "set",
        "id": "example:guide"
      },
      "text": "欢迎来到村庄。"
    },
    {
      "text": "沿着石路向前，就能找到集市。"
    }
  ],
  "end": {
    "speaker": {
      "type": "hide"
    }
  }
}
```

```json:line-numbers {7-10,18-20} [完整 welcome.json]
{
  "presentation": {
    "theme": "maimai_dialogue:default"
  },
  "steps": [
    {
      "speaker": {
        "type": "set",
        "id": "example:guide"
      },
      "text": "欢迎来到村庄。"
    },
    {
      "text": "沿着石路向前，就能找到集市。"
    }
  ],
  "end": {
    "speaker": {
      "type": "hide"
    },
    "text": "向导向你挥了挥手。",
    "exit": {
      "type": "return"
    }
  }
}
```

:::

`set` 切换当前 Speaker；后续省略 `speaker` 的步骤会继承它。`hide` 会隐藏名称栏。把完整 `welcome.json` 同步保存到两个 Pack。

## 进入游戏验证

执行 `/reload`、按 `F3 + T`，再打开 `example:guide/welcome`。前两页应显示“村庄向导”，最后一页不显示名称。

## 如果没有生效

- 名称显示成 `example:guide`：客户端没有成功加载 Speaker 文件。
- 第二页没有名称：检查第一步是否成功执行了 `speaker.type: set`。
- 名称栏没有隐藏：确认 `hide` 写在实际播放的 `end` 中。

## 下一步

继续[编写 Markdown 正文](./markdown.md)。
