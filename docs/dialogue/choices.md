---
title: 选项与子对话
description: 在入口 Dialogue 中显示选项，并进入可返回的子 Dialogue。
---

# 选项与子对话

## 本章要实现什么

`welcome` 的结束点会出现“了解村庄”和“离开”两个选项。玩家进入 `about` 后，读完内容会返回入口 Dialogue `welcome` 的开头。

## 开始前

你已经完成[Markdown 正文](./markdown.md)，并能正常显示 `example:guide/welcome`。

## 需要修改的文件

同步修改 `welcome`，并在资源包与数据包中各新增一份子 Dialogue：

```text
<资源包>/assets/example/dialogues/guide/welcome.json
<数据包>/data/example/dialogues/guide/welcome.json
<资源包>/assets/example/dialogues/guide/about.json
<数据包>/data/example/dialogues/guide/about.json
```

## 跟着做

1. 把 `welcome` 的结束方式从 `return` 改为两个选项。本章重点是高亮的 `exit`：

::: code-group

```json{4-24} [本章改动]
{
  "end": {
    "text": "请选择一个话题。",
    "exit": {
      "type": "options",
      "options": [
        {
          "text": "了解村庄",
          "icon": "question",
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
  }
}
```

```json:line-numbers {21-43} [完整 welcome.json]
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
    "text": "请选择一个话题。",
    "exit": {
      "type": "options",
      "options": [
        {
          "text": "了解村庄",
          "icon": "question",
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
  }
}
```

:::

把完整 `welcome.json` 同步保存到两个 Pack。

2. 创建 `about.json`。它是一段新的 Dialogue，因此下面高亮的是整个内容结构：

```json:line-numbers {2-14} [about.json]
{
  "presentation": {
    "theme": "maimai_dialogue:default"
  },
  "end": {
    "speaker": {
      "type": "set",
      "id": "example:guide"
    },
    "text": "这里以农田和集市闻名。",
    "exit": {
      "type": "return"
    }
  }
}
```

把 `about.json` 同步保存到资源包和数据包。选项的 Dialogue target 负责从 `welcome` 进入 `about`；`about` 的 Return 会回到本次入口 `welcome`。在入口 `welcome` 选择 Return 则会关闭界面。

## 进入游戏验证

重载后打开 `example:guide/welcome`：

1. 推进到选项页；
2. 点击“了解村庄”；
3. 读完 `about` 并推进；
4. 确认重新回到 `welcome` 的开头；
5. 点击“离开”关闭界面。

## 如果没有生效

- 选项消失：确认数据包中存在目标 `data/example/dialogues/guide/about.json`。
- 点击后提示缺失：确认已启用的资源包中也存在同 ID 的 `about.json`。
- Return 直接关闭：检查最初是否确实打开了 `example:guide/welcome`。
- 选项文字没有 Markdown：这是正常行为，Option 使用普通文本。

## 下一步

继续[使用 Progress 条件](./progress.md)，让一个选项只在解锁后出现。
