---
title: 玩家操作
description: 向玩家说明推进、跳过、选择、滚动、历史和关闭行为。
---

# 玩家操作

## 本章要实现什么

了解玩家实际如何操作 Dialogue，方便你设计文字长度、选项数量，并为整合包编写准确的操作说明。

## 开始前

打开任意可用 Dialogue；推荐使用教程中的 `example:guide/welcome` 或内置 Demo。

## 需要修改的文件

本章不修改资源文件。

## 跟着做

### 推进与跳过

- 按空格，或点击不属于按钮和滚动区域的画面来推进。
- 正文或阻塞动画尚未完成时，第一次推进只会立即完成当前播放。
- 当前内容已经完成后，再次推进才进入下一步。
- 选项出现后，点击空白区域不会替玩家做选择。
- 默认按住左侧或右侧 Ctrl 时，文字和有限场景动画以 4 倍速度播放；Continue Step 就绪后会自动播放下一步，直到进入 EndStep。EndStep 不会自动退出，松开 Ctrl 后恢复正常速度。
- 右上角方形按钮默认需要持续按住 600ms，外围圆环填满后才会跳至当前 Dialogue 的结尾。
- 内容作者配置了跳过摘要时，会先显示 Markdown 确认窗口；按 Esc 或“取消”返回，选择“确认跳过”才会执行。没有摘要时直接跳转。
- 确认窗口打开期间，后方当前播放可以自然完成，但所有 Dialogue 操作都会被拦截。

### 选项与滚动

- 只有服务端允许访问的目标选项才会出现。
- 选项较多时可以滚动；实际溢出时会显示 Expand 按钮。
- 点击 Dialogue target 后，服务端会再次检查最新 Progress 状态。

### 历史记录

- 右侧 History 入口会显示本次界面中已经播放的正文和已确认的选项。
- 默认按 H 可以打开或关闭 History。
- 进入子 Dialogue 或 Return 到入口 Dialogue 时，历史仍然保留。
- 关闭界面、死亡、断线或被其他 Screen 替换后，历史会清空且不会写入存档。

### Return 与关闭

- 子 Dialogue 的 Return 会重新进入入口 Dialogue 的开头。
- 入口 Dialogue 的 Return 会关闭界面。
- 作者配置 `dialogue` Exit 时，EndStep 播放完成会自动进入目标 Dialogue。
- Esc 和界面关闭操作会结束当前对话。
- Dialogue 不会暂停游戏世界，也没有全局自动播放。

## 进入游戏验证

打开内置 Demo：

```text
/maimai_dialogue open @s maimai_dialogue:demo/root
```

依次验证播放中跳过、Ctrl 加速、长按跳至结尾、摘要确认、正常推进、选项滚动、History、子 Dialogue Return 和关闭。

## 如果没有生效

- 按一次空格没有换页：当前文字或阻塞动画可能仍在播放。
- 短按右上角按钮没有反应：这是防误触设计，需要等外围圆环完整填满。
- 空白点击无法越过选项：这是防止误选的正常行为。
- 重新打开后没有旧历史：History 只属于一次 UI 打开过程。

## 下一步

内容作者可以继续查阅[参考资料](../reference/resource-paths.md)。第三方 MOD 开发者可以进入 [Java API](../integration/java-api.md)。

玩家可以在 Mods 列表的 MaiMai Dialogue → Config 中修改上述倍率、长按时长、键位和字体，详见[客户端设置](./client-settings.md)。
