---
title: 第一段对话
description: 创建并打开 example:guide/welcome。
---

# 第一段对话

## 本章要实现什么

你将创建对话（Dialogue）`example:guide/welcome`。玩家打开后会看到一句文字，再次推进会关闭界面。

## 开始前

你已经完成[创建内容包](./content-project.md)，并拥有以下两个目录：

```text
<资源包>/assets/example/dialogues/guide/
<数据包>/data/example/dialogues/guide/
```

## 需要修改的文件

把相同内容分别保存到：

```text
<资源包>/assets/example/dialogues/guide/welcome.json
<数据包>/data/example/dialogues/guide/welcome.json
```

## 跟着做

1. 在资源包和数据包对应位置都创建 `welcome.json`。
2. 将下面的完整 JSON 原样复制到两个文件中。高亮部分是本章新建的 Dialogue 内容：

```json:line-numbers {2-11} [welcome.json]
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

`presentation.theme` 使用 MOD 内置的默认主题。主题（Theme）是对话界面的外观设置（颜色、字号、按钮样式），这一章先直接用默认值，[制作主题](../scene/themes.md)一章会教你创建自己的主题。`end` 是这个 Dialogue 必须拥有的结束点，`exit` 决定到达这里后如何离开。

这次由命令直接打开 `welcome`，所以它承担入口对话的角色；入口处的 `return` 会关闭界面。文件名 `welcome` 只是按内容含义起的名字——“入口”取决于对话是被哪条命令打开的，与文件名无关。

::: tip 如何阅读后续代码
后续章节会把“本章改动”和“完整文件”放进同一个代码组。先看高亮的小片段抓住变化，需要复制时再切换到完整文件。
:::

## 进入游戏验证

进入世界后先重载数据包：

```text
/reload
```

再按 `F3 + T` 重载资源包，然后执行：

```text
/maimai_dialogue open @s example:guide/welcome
```

你应看到“你好！这是我的第一段对话。”。文字播放结束后按空格或点击对话空白区域，界面会关闭。

## 如果没有生效

- 提示 Dialogue 不存在：检查数据包中的 `data/example/dialogues/guide/welcome.json`。
- 命令成功但界面不显示：检查资源包的 `assets` 下是否有相同 ID 的文件，并确认该资源包已启用。
- JSON 加载失败：检查逗号、引号和花括号，并查看日志中的资源 ID。
- 修改后仍是旧内容：同时执行 `/reload` 和 `F3 + T`。

## 下一步

继续[添加多个步骤](../dialogue/steps.md)，把一页文字扩展成连续对话。
