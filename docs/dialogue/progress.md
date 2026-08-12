---
title: Progress 条件
description: 使用 ProgressNode 控制 Dialogue 和选项是否可访问。
---

# Progress 条件

## 本章要实现什么

为入口 Dialogue `welcome` 增加“秘密地点”选项。玩家没有 `guide.secret_unlocked` 时看不到它，管理员添加节点后它才会出现。

## 开始前

你已经完成[选项与子对话](./choices.md)，并能进入 `example:guide/about` 后返回 `welcome`。

## 需要修改的文件

同步修改资源包与数据包中的两个 `welcome.json`，并新增：

```text
<资源包>/assets/example/dialogues/guide/secret.json
<数据包>/data/example/dialogues/guide/secret.json
```

## 跟着做

1. 在 `welcome` 的“离开”选项之前添加一个新的 Dialogue target。先看新增选项，再切换到完整文件：

::: code-group

```json{2-8} [新增选项]
{
  "text": "询问秘密地点",
  "icon": "exclamation",
  "target": {
    "type": "dialogue",
    "dialogue": "example:guide/secret"
  }
}
```

```json:line-numbers {35-43} [完整 welcome.json]
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

:::

把完整 `welcome.json` 同步保存到两个 Pack。

2. 创建目标 Dialogue `secret.json`。`requires` 是本章真正控制访问权限的部分：

```json:line-numbers {2} [secret.json]
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

把 `secret.json` 同步保存到两个 Pack。条件写在目标 Dialogue 自己的 `requires` 中，Option 只负责指向目标，不重复声明条件。

## 进入游戏验证

先移除节点并打开 `welcome`：

```text
/maimai_dialogue progress remove @s guide.secret_unlocked
/maimai_dialogue open @s example:guide/welcome
```

“询问秘密地点”不应出现。关闭对话后添加节点，再重新打开：

```text
/maimai_dialogue progress add @s guide.secret_unlocked
/maimai_dialogue open @s example:guide/welcome
```

此时选项应出现，并能进入 `secret` Dialogue。

## 如果没有生效

- 选项始终隐藏：使用 `progress check` 确认节点名称完全一致。
- 选项出现但无法进入：确认数据包 `secret.json` 的 `requires` 和当前节点状态。
- 修改条件后没有变化：执行 `/reload`，并重新打开整场对话。
- 使用了大写或冒号：ProgressNode 只接受小写点分名称。

## 下一步

对话流程已经完整。继续[添加背景](../scene/background.md)，开始制作画面表现。
