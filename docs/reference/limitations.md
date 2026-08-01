---
title: 当前限制
description: MaiMai Dialogue 0.1.0-alpha 当前支持范围与尚未提供的功能。
---

# 当前限制

本页描述 `0.1.0-alpha` 的实际能力，避免把计划中的功能误认为已经可用。

## 对话与导航

- Option target 只有 `dialogue` 和 `return`。
- Dialogue 内部步骤线性播放，分支只出现在唯一结尾。
- 子 Dialogue 的 Return 返回本次入口 Dialogue 的开头；入口 Dialogue 的 Return 关闭界面。
- 没有导航栈、恢复点、Option ID、脚本动作或自动修改进度。
- 服务端 Dialogue 必须来自已启用的 Data Pack；客户端 Dialogue 与画面资源必须来自已启用的 Resource Pack。
- MaiMai Dialogue 的 network payload 不会传输正文、Theme、SceneAction 或图片；服务器资源包仍可使用 Minecraft 自带的资源包下载流程交付客户端内容。

## 文字与操作

- 只有 Dialogue 正文解析 Markdown。
- Speaker、Option 和控制按钮使用普通字符串。
- 没有文本本地化模型和自定义字体。
- 没有自动播放、世界暂停或隐藏世界功能。
- 没有玩家主题编辑、动画倍率或 reduced motion 设置。

## 场景与动画

- VisualObject 必须预先声明，不能由 Action 动态创建或删除。
- 不支持循环或跨步骤持续的 Action。
- Background Action 只支持切换已声明 variant。
- `dialogue` target 只支持 opacity；Filter 不能作为 Action target。
- easing 只有 `linear`、`ease_in`、`ease_out`、`ease_in_out`。
- 不支持用户提供任意 shader 或复杂场景转场组合。

## Progress

- ProgressNode 只有存在与不存在两种状态。
- 没有数值、字符串、父级继承或通配符。
- 命令和 API 仅面向在线玩家，不支持离线 UUID 编辑。
- Exit 与 Option 不会自动修改节点。

## 历史与运行状态

- History 只保存在当前客户端 UI 打开期间。
- 关闭、死亡、断线或界面替换后历史清空。
- 服务端不保存玩家当前所在 Dialogue、步骤、选项或播放进度。
- 客户端已经打开 Dialogue 时，新的服务端 open 请求会被忽略。

升级 alpha 版本前，请阅读变更说明并重新验证全部内容资源。
