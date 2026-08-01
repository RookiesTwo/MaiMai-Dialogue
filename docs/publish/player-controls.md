---
title: 玩家操作
description: 向玩家说明推进、跳过、选择、滚动、历史和关闭行为。
---

# 玩家操作

## 本章要实现什么

了解玩家实际如何操作 Dialogue，方便你设计文字长度、选项数量，并为整合包编写准确的操作说明。

## 开始前

打开任意可用 Dialogue；推荐使用教程中的 `example:guide/root` 或内置 Demo。

## 需要修改的文件

本章不修改资源文件。

## 跟着做

### 推进与跳过

- 按空格，或点击不属于按钮和滚动区域的画面来推进。
- 正文或阻塞动画尚未完成时，第一次推进只会立即完成当前播放。
- 当前内容已经完成后，再次推进才进入下一步。
- 选项出现后，点击空白区域不会替玩家做选择。

### 选项与滚动

- 只有服务端允许访问的目标选项才会出现。
- 选项较多时可以滚动；实际溢出时会显示 Expand 按钮。
- 点击 Dialogue target 后，服务端会再次检查最新 Progress 状态。

### 历史记录

- 右侧 History 入口会显示本次界面中已经播放的正文和已确认的选项。
- 进入子 Dialogue 或 Return 到 root 时，历史仍然保留。
- 关闭界面、死亡、断线或被其他 Screen 替换后，历史会清空且不会写入存档。

### Return 与关闭

- 子 Dialogue 的 Return 会重新进入 root 的开头。
- root 的 Return 会关闭界面。
- Esc 和界面关闭操作会结束当前对话。
- Dialogue 不会暂停游戏世界，也没有自动播放。

## 进入游戏验证

打开内置 Demo：

```text
/maimai_dialogue open @s maimai_dialogue:demo/root
```

依次验证播放中跳过、正常推进、选项滚动、History、子 Dialogue Return 和关闭。

## 如果没有生效

- 按一次空格没有换页：当前文字或阻塞动画可能仍在播放。
- 空白点击无法越过选项：这是防止误选的正常行为。
- 重新打开后没有旧历史：History 只属于一次 UI 打开过程。

## 下一步

内容作者可以继续查阅[参考资料](../reference/resource-paths.md)。第三方 MOD 开发者可以进入 [Java API](../integration/java-api.md)。
