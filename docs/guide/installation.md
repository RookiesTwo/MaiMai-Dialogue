---
title: 安装与双端分发
description: 安装 MaiMai Dialogue，并在单人游戏或服务器中正确分发内容。
---

# 安装与双端分发

## 版本要求

| 组件 | 当前要求 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.x |
| MaiMai Dialogue | 0.1.0-alpha |
| Modern UI | 客户端 3.13.0.1 或更高版本 |
| Java | 开发与构建使用 Java 21 |

普通客户端需要安装 MaiMai Dialogue 与 Modern UI。Dedicated Server 需要安装 MaiMai Dialogue，但 Modern UI 是客户端依赖。

## 为什么内容需要双端存在

一次对话由两端共同完成：

1. 服务端从 `data/<namespace>/dialogues/` 读取 Dialogue，检查 ID 是否存在并计算 `requires`。
2. 客户端从 `assets/<namespace>/dialogues/` 读取同 ID 的 Dialogue，显示正文、场景和 UI。
3. Speaker、Theme、SceneAction 与图片仅由客户端加载。

因此，每个 Dialogue JSON 都应在 `data` 和 `assets` 下以相同 namespace、相同相对路径保存，并保持内容一致。两端内容不一致属于内容包错误，不是正常兼容机制。

## 单人游戏

Integrated Server 与客户端位于同一游戏实例。把同时包含 `assets` 与 `data` 的内容 MOD 安装到实例中，是最简单可靠的分发方式。

## Dedicated Server

- 服务端：安装 MaiMai Dialogue，并提供 `data` 下的 Dialogue。
- 所有客户端：安装 MaiMai Dialogue、Modern UI，以及 `assets` 下的 Dialogue 和全部引用资源。
- 推荐把 `assets` 与 `data` 打包在同一个附属内容 MOD 中，然后同时分发到服务端和客户端。

如果使用传统 Data Pack 与 Resource Pack，则服务端 Data Pack 和客户端 Resource Pack 必须来自同一版本；服务器资源包下发方式由整合包或服务器自行管理。

## 从源码构建

```powershell
.\gradlew.bat build
```

开发环境可运行：

```powershell
.\gradlew.bat runClient
```

构建产物位于 `build/libs/`。当前为 alpha 版本，发布前请先执行完整测试。

## Reload

- `/reload`：重载服务端 Data Pack 中的 Dialogue。
- `F3 + T`：重载客户端 Dialogue、Speaker、Theme、Action 和图片。

制作过程中修改了双方资源时，应同时重载两端；无法确认状态时直接重启测试实例更可靠。
