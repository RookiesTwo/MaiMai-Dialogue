---
title: 安装
description: 安装 MaiMai Dialogue，并确认内置示例可以正常打开。
---

# 安装

## 本章要实现什么

完成后，你会拥有一个可以测试 MaiMai Dialogue 的 Minecraft 1.21.1 实例，并能在世界中打开 MOD 自带的示例对话。

## 开始前

准备一个 Minecraft 1.21.1 测试实例。建议不要直接在重要存档中制作内容。

当前版本要求：

| 组件 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.x |
| MaiMai Dialogue | 0.1.0-alpha |
| Modern UI | 3.13.0.1 或更高版本 |

## 需要修改的文件

把 MOD 文件放进测试实例的：

```text
<游戏实例>/mods/
```

单人游戏和普通客户端需要 MaiMai Dialogue 与 Modern UI。Dedicated Server 需要 MaiMai Dialogue，但不需要安装客户端界面依赖 Modern UI。

## 跟着做

1. 为 Minecraft 1.21.1 安装 NeoForge 21.x。
2. 将 MaiMai Dialogue 的 jar 放入实例的 `mods` 目录。
3. 在客户端的 `mods` 目录中同时放入兼容版本的 Modern UI。
4. 启动游戏，确认 MOD 列表中可以看到 MaiMai Dialogue。
5. 创建或进入一个允许使用命令的测试世界。

## 进入游戏验证

执行内置 Demo 命令：

```text
/maimai_dialogue open @s maimai_dialogue:demo/root
```

命令默认需要 permission level 2。正确安装后会出现一段带背景、动画和选项的示例对话。

## 如果没有生效

- 命令不存在：检查 NeoForge 是否实际加载了 MaiMai Dialogue。
- 客户端启动失败：检查是否缺少 Modern UI，或 Modern UI 版本是否过低。
- 命令提示权限不足：在单人世界开启作弊，或让服务器管理员执行。
- Demo 无法显示：查看客户端和服务端日志中最早出现的 MaiMai Dialogue 错误。

## 下一步

继续[创建内容工程](./content-project.md)，为自己的 Dialogue 准备资源目录。
