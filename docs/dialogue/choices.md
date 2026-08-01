---
title: 选项与子对话
description: 在 root Dialogue 中显示选项，并进入可返回的子 Dialogue。
---

# 选项与子对话

## 本章要实现什么

root Dialogue 结束后会出现“了解村庄”和“离开”两个选项。进入子 Dialogue 后，继续推进会返回 root 的开头。

## 开始前

你已经完成[Markdown 正文](./markdown.md)，并能正常显示 `example:guide/root`。

## 需要修改的文件

同步修改 root，并在客户端与服务端各新增一份子 Dialogue：

```text
assets/example/dialogues/guide/root.json
data/example/dialogues/guide/root.json
assets/example/dialogues/guide/about.json
data/example/dialogues/guide/about.json
```

## 跟着做

1. 将两个 `root.json` 都替换为：

```json
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
      "text": "# 欢迎来到村庄\n\n你想了解什么？"
    }
  ],
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

2. 将下面内容同时保存为两个 `about.json`：

```json
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

命令最先打开的 Dialogue 是 root。子 Dialogue 的 `return` 会重新从 root 开头播放；root 中的 `return` 会关闭界面。

## 进入游戏验证

重载后打开 `example:guide/root`：

1. 推进到选项页；
2. 点击“了解村庄”；
3. 读完子 Dialogue 并推进；
4. 确认重新回到 root；
5. 点击“离开”关闭界面。

## 如果没有生效

- 选项消失：确认服务端存在目标 `data/example/dialogues/guide/about.json`。
- 点击后提示缺失：确认客户端也存在同 ID 的 `about.json`。
- Return 直接关闭：检查最初是否确实打开了 `example:guide/root`。
- 选项文字没有 Markdown：这是正常行为，Option 使用普通文本。

## 下一步

继续[使用 Progress 条件](./progress.md)，让一个选项只在解锁后出现。
