---
title: Progress 条件
description: 使用 ProgressNode 控制 Dialogue 和选项是否可访问。
---

# Progress 条件

## 本章要实现什么

为 root 增加“秘密地点”选项。玩家没有 `guide.secret_unlocked` 时看不到它，管理员添加节点后它才会出现。

## 开始前

你已经完成[选项与子对话](./choices.md)，并能进入 `example:guide/about` 后返回。

## 需要修改的文件

同步修改两个 `root.json`，并新增：

```text
assets/example/dialogues/guide/secret.json
data/example/dialogues/guide/secret.json
```

## 跟着做

1. 将两个 `root.json` 都替换为下面的完整内容：

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
          "text": "询问秘密地点",
          "icon": "exclamation",
          "target": {
            "type": "dialogue",
            "dialogue": "example:guide/secret"
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

2. 将下面内容同时保存为两个 `secret.json`：

```json
{
  "requires": "guide.secret_unlocked",
  "presentation": {
    "theme": "maimai_dialogue:default"
  },
  "end": {
    "speaker": {
      "type": "set",
      "id": "example:guide"
    },
    "text": "瀑布后面藏着一条小路。",
    "exit": {
      "type": "return"
    }
  }
}
```

条件写在目标 Dialogue 自己的 `requires` 中，不需要在 Option 上重复。

## 进入游戏验证

先移除节点并打开 root：

```text
/maimai_dialogue progress remove @s guide.secret_unlocked
/maimai_dialogue open @s example:guide/root
```

“询问秘密地点”不应出现。关闭对话后添加节点，再重新打开：

```text
/maimai_dialogue progress add @s guide.secret_unlocked
/maimai_dialogue open @s example:guide/root
```

此时选项应出现，并能进入 secret Dialogue。

## 如果没有生效

- 选项始终隐藏：使用 `progress check` 确认节点名称完全一致。
- 选项出现但无法进入：确认服务端 `secret.json` 的 `requires` 和当前节点状态。
- 修改条件后没有变化：执行 `/reload`，并重新打开整场对话。
- 使用了大写或冒号：ProgressNode 只接受小写点分名称。

## 下一步

对话流程已经完整。继续[添加背景](../scene/background.md)，开始制作画面表现。
