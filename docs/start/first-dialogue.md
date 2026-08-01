---
title: 第一段对话
description: 创建并打开 example:guide/root。
---

# 第一段对话

## 本章要实现什么

你将创建对话（Dialogue）`example:guide/root`。玩家打开后会看到一句文字，再次推进会关闭界面。

## 开始前

你已经完成[创建内容工程](./content-project.md)，并拥有以下两个目录：

```text
assets/example/dialogues/guide/
data/example/dialogues/guide/
```

## 需要修改的文件

把相同内容分别保存到：

```text
src/main/resources/assets/example/dialogues/guide/root.json
src/main/resources/data/example/dialogues/guide/root.json
```

## 跟着做

1. 在两个位置都创建 `root.json`。
2. 将下面的完整 JSON 复制到两个文件中：

```json
{
  "presentation": {
    "theme": "maimai_dialogue:default"
  },
  "end": {
    "text": "你好！这是我的第一段对话。",
    "exit": {
      "type": "return"
    }
  }
}
```

`presentation.theme` 使用 MOD 内置的默认 Theme。`end` 是对话的最后一页；root Dialogue 的 `return` 会关闭界面。

## 进入游戏验证

进入世界后先重载服务端资源：

```text
/reload
```

再按 `F3 + T` 重载客户端资源，然后执行：

```text
/maimai_dialogue open @s example:guide/root
```

你应看到“你好！这是我的第一段对话。”。文字播放结束后按空格或点击对话空白区域，界面会关闭。

## 如果没有生效

- 提示 Dialogue 不存在：检查 `data/example/dialogues/guide/root.json`。
- 命令成功但界面不显示：检查 `assets` 下是否有相同 ID 的文件。
- JSON 加载失败：检查逗号、引号和花括号，并查看日志中的资源 ID。
- 修改后仍是旧内容：同时执行 `/reload` 和 `F3 + T`。

## 下一步

继续[添加多个步骤](../dialogue/steps.md)，把一页文字扩展成连续对话。
