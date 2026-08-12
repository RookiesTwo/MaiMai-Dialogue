---
title: 内置演示内容
description: MOD 自带 debug 与 showcase 两套演示对话的清单与打开方式。
---

# 内置演示内容

MOD 自带两套演示内容，不用装任何额外文件就能打开。它们也是学习 JSON 写法的现成范例：解压 MOD jar 后，可以在 `data/maimai_dialogue/` 与 `assets/maimai_dialogue/` 目录下查看每个演示对应的真实文件。

## 调试演示（debug）

入口命令（默认需要 permission level 2）：

```text
/maimai_dialogue open @s maimai_dialogue:debug/root
```

`debug/root` 本身演示了：入场动画、背景差分切换、带图标的选项、Option 执行两条 `tellraw` 指令、`skip_summary` 确认窗口。它的结尾选项可以进入以下演示：

| 对话 ID | 演示什么 |
|---|---|
| `maimai_dialogue:debug/public` | 无条件公开的对话 |
| `maimai_dialogue:debug/locked` | 带 `requires` 的受限对话；需要节点 `debug.unlocked`（`/maimai_dialogue progress add @s debug.unlocked`） |
| `maimai_dialogue:debug/theme` | 羊皮纸风格 Theme |
| `maimai_dialogue:debug/crt` | CRT 滤镜与 bloom |
| `maimai_dialogue:debug/fast_forward` | 长对话快进（按住 Ctrl） |
| `maimai_dialogue:debug/actions` | 逐条播放全部预设动画，并标注当前调用名称 |

## 成品演示（showcase）

一段完整的中文演示对话"小麦娘"，从基本操作讲到正文排版、选项与条件：

```text
/maimai_dialogue open @s maimai_dialogue:showcase/root
```

| 对话 ID | 演示什么 |
|---|---|
| `maimai_dialogue:showcase/root` | 打字机速度对比、立绘差分切换、基本推进操作 |
| `maimai_dialogue:showcase/main` | 正文排版与 Markdown |
| `maimai_dialogue:showcase/random` | 随机文本 |
| `maimai_dialogue:showcase/sub` | 子对话与返回 |
| `maimai_dialogue:showcase/end_options` | 结尾选项 |
| `maimai_dialogue:showcase/secret` | 用 `showcase.secret_unlocked` 隐藏的对话，解锁后自动链入下一段 |
| `maimai_dialogue:showcase/skip_demo` | 跳过摘要确认 |
| `maimai_dialogue:showcase/theme_paper` | 纸张风格 Theme |
| `maimai_dialogue:showcase/filter_crt` | CRT 滤镜 |

## 内置预设动画

`maimai_dialogue:presets/<名称>` 系列预设动画的完整清单见[内置预设 SceneAction](./preset-actions.md)。

## 下一步

- 遇到问题时先按[故障排查](./troubleshooting.md)的步骤检查。
- 想看"该改哪个文件"，查[常见任务速查](./quick-recipes.md)。
