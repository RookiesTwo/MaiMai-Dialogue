---
title: 双端发布
description: 将 Dialogue 内容正确安装到单人实例、客户端与 Dedicated Server。
---

# 双端发布

## 本章要实现什么

把教程中的 `example` 内容作为同一个版本交付给客户端和服务端，避免“服务端允许但客户端没有画面”或“客户端有选项但服务端找不到目标”等问题。

## 开始前

你已经完成内容制作，并在本地测试世界走完所有对话路径。

## 需要修改的文件

发布包至少应包含：

```text
assets/example/dialogues/
assets/example/speakers/
assets/example/dialogue_themes/
assets/example/presentation_actions/
assets/example/textures/
data/example/dialogues/
```

## 跟着做

1. 确认每个 `assets/example/dialogues/<path>.json` 都有同 ID 的 `data` 副本。
2. 确认两份 Dialogue 内容一致，尤其是 `requires`、目标 ID 和文件名。
3. 确认所有 Speaker、Theme、SceneAction 和图片都已包含在客户端 `assets` 中。
4. 将 `assets` 与 `data` 打进同一个附属内容 MOD，并为它设置清晰的版本号。
5. 单人游戏：把该内容 MOD、MaiMai Dialogue 和 Modern UI 安装到同一实例。
6. Dedicated Server：服务端安装 MaiMai Dialogue 与内容 MOD；每个客户端安装 MaiMai Dialogue、Modern UI 和完全相同版本的内容 MOD。

也可以分别发布 Data Pack 与 Resource Pack，但两者必须来自同一次内容构建。MaiMai Dialogue 不会在运行时把 Dialogue 正文或图片从服务端发送给客户端。

## 进入游戏验证

在服务器上依次验证：

```text
/reload
/maimai_dialogue open @s example:guide/root
```

客户端同时按一次 `F3 + T`。随后测试：公开选项、受 Progress 限制的选项、子 Dialogue Return、背景、动画、Filter 和 Theme。

## 如果没有生效

- 服务端提示 Dialogue 不存在：发布包缺少 `data` 副本。
- 命令显示已发送但客户端无界面：客户端缺少同 ID 的 `assets` 副本。
- 只有部分玩家报资源缺失：客户端安装的内容包版本不一致。
- `/reload` 后服务端正常但画面仍旧：客户端还需要按 `F3 + T` 或重启实例。

## 下一步

继续[命令与管理](./commands.md)，了解如何为在线玩家打开 Dialogue 和维护 ProgressNode。
