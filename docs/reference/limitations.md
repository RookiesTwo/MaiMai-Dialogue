---
title: 当前限制
description: 0.1.0-alpha 已知边界和尚未支持的能力。
---

# 当前限制

本页描述 `0.1.0-alpha` 的能力边界，避免把规划项误认为现有功能。

## 内容与导航

- OptionTarget 只有 `dialogue` 和 `return`。
- 没有导航栈、恢复点、Option ID、脚本动作或自动修改进度。
- Return 总是返回 root 的开头；root Return 关闭界面。
- Dialogue 内部 Step 线性播放，分支只出现在 DialogueEnd。
- 客户端和服务端必须预装本地内容，不支持运行时动态下发 Dialogue。

## 文字与交互

- 只有 Dialogue 正文解析 Markdown。
- Speaker、Option 和 controls 使用普通字符串。
- 没有文本本地化模型和自定义字体。
- 没有自动播放、世界暂停或隐藏世界功能。
- 没有玩家主题编辑、动画倍率或 reduced motion 设置。

## 场景与动画

- VisualObject 必须预声明，不能动态创建或删除。
- 当前只支持有限 Action，不支持循环和跨 Step 动画生命周期。
- Background Action 只支持切换预声明 variant。
- Dialogue 特殊 target 只支持 opacity；Filter 不能作为 Action target。
- 只有 `linear`、`ease_in`、`ease_out`、`ease_in_out` 四种 easing。
- 不支持用户提供任意 shader 或复杂 Scene 转场组合。

## Progress

- ProgressNode 只有布尔存在/不存在状态。
- 没有数值、字符串、父级继承或通配符。
- 命令和 API 仅面向在线玩家，不支持离线 UUID 编辑。
- Exit 与 Option 不会修改节点。

## 历史与服务端状态

- 历史只在当前客户端 UI session 内存中保存。
- 关闭、死亡、断线或界面替换后历史清空。
- 服务端不保存当前 root、Step、选项或播放进度。
- 客户端已经打开 Dialogue 时，新的服务端打开请求会被忽略。

这些限制可能在后续版本变化。升级 alpha 版本前，请阅读变更记录并重新验证全部内容资源。
