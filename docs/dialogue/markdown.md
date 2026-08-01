---
title: 编写 Markdown 正文
description: 在 Dialogue 正文中使用标题、强调和行内代码。
---

# 编写 Markdown 正文

## 本章要实现什么

把前两句话改成带标题、粗体、斜体和行内代码的正文，同时保持 Speaker 名称为普通文本。

## 开始前

你已经创建 `example:guide` Speaker，并完成[显示 Speaker](./speaker.md)。

## 需要修改的文件

同步修改资源包与数据包中的：

```text
<资源包>/assets/example/dialogues/guide/welcome.json
<数据包>/data/example/dialogues/guide/welcome.json
```

## 跟着做

本章只修改三个 `text`。高亮行是新的 Markdown 正文：

::: code-group

```json{4,7,11} [本章改动]
{
  "steps": [
    {
      "text": "# 欢迎来到村庄\n\n我是这里的 **向导**。"
    },
    {
      "text": "沿着 *石路* 向前，就能找到 `market`。"
    }
  ],
  "end": {
    "text": "向导向你挥了挥手。"
  }
}
```

```json:line-numbers {11,14,21} [完整 welcome.json]
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
      "text": "# 欢迎来到村庄\n\n我是这里的 **向导**。"
    },
    {
      "text": "沿着 *石路* 向前，就能找到 `market`。"
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

把完整文件同步保存到两个 Pack。JSON 字符串中的换行写成 `\n`。只有 Dialogue 的 `text` 会解析 Markdown；Speaker 名称、选项文字和按钮不会解析。

## 进入游戏验证

重载后打开 `example:guide/welcome`。第一步应出现标题和粗体，第二步的“石路”应为斜体，`market` 应显示为行内代码。

## 如果没有生效

- JSON 报错：确认换行写成 `\n`，没有直接在字符串中按回车。
- `**` 原样显示：确认内容写在 `text` 字段，而不是 Speaker 或 Option。
- 排版过长：把正文按自然段拆成多个步骤。

## 下一步

继续[添加选项与子对话](./choices.md)。
