---
title: 创建内容工程
description: 建立 assets、data 和 example namespace 的基础目录。
---

# 创建内容工程

## 本章要实现什么

完成后，你会得到一个用于后续全部教程的 `example` 内容工程。它还没有自定义对话，但客户端与服务端目录已经准备好。

## 开始前

你已经完成[安装](./installation.md)，并准备了一个现有 NeoForge MOD 工程或专门用于装内容的附属 MOD 工程。

教程统一使用 `src/main/resources`。如果你使用独立 Resource Pack 和 Data Pack，请保持相同的 `assets/example` 与 `data/example` 内部结构。

## 需要修改的文件

创建以下目录：

```text
src/main/resources/
├─ assets/example/
│  ├─ dialogues/guide/
│  ├─ speakers/
│  ├─ dialogue_themes/
│  ├─ presentation_actions/
│  └─ textures/dialogue/
└─ data/example/
   └─ dialogues/guide/
```

## 跟着做

1. 在工程的 `src/main/resources` 下创建 `assets/example`。
2. 在同一位置创建 `data/example`。
3. 按上面的目录树补齐 `dialogues`、`speakers`、`dialogue_themes`、`presentation_actions` 和 `textures`。
4. 保持 namespace 全部小写；本教程固定使用 `example`。

资源 ID 由 namespace 和文件相对路径组成。例如：

```text
assets/example/dialogues/guide/root.json
                         ↓
              example:guide/root
```

每个 Dialogue 都要有两份同 ID 文件：

- `assets` 副本供客户端显示正文和画面；
- `data` 副本供服务端检查是否存在以及玩家是否满足条件。

后续教程每次修改 Dialogue 时，都要把同一份 JSON 同步写入这两个位置。

## 进入游戏验证

空目录不会增加可打开的 Dialogue。启动测试实例后，日志中不应出现来自 `example` namespace 的资源解析错误；下一章会创建第一个可验证文件。

## 如果没有生效

- 目录写成 `dialogue`：正确目录名是复数 `dialogues`。
- 把客户端资源放进 `data`：Speaker、Theme、SceneAction 和图片都应位于 `assets`。
- namespace 含大写或空格：请使用小写字母、数字、下划线和连字符。

## 下一步

继续[创建第一段对话](./first-dialogue.md)。
