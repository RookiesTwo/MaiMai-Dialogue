---
title: 双端发布
description: 将 Dialogue Data Pack 与 Resource Pack 正确交付给单人实例和 Dedicated Server。
---

# 双端发布

## 本章要实现什么

把教程中的 `example` 内容整理成同一版本的数据包和资源包，并交付给服务端与玩家，避免“服务端允许但客户端没有画面”或“客户端有选项但服务端找不到目标”。

## 开始前

你已经完成内容制作，并在本地测试世界走完所有对话路径。发布前先为这一版内容确定版本号；下面使用 `1.0.0`。

## 需要修改的文件

最终准备两个 ZIP，打开 ZIP 后应直接看到 `pack.mcmeta`：

```text
example_dialogue_resources-1.0.0.zip
├─ pack.mcmeta
└─ assets/example/
   ├─ dialogues/
   ├─ speakers/
   ├─ dialogue_themes/
   ├─ presentation_actions/
   └─ textures/

example_dialogue_data-1.0.0.zip
├─ pack.mcmeta
└─ data/example/
   └─ dialogues/
```

## 跟着做

1. 确认每个 `assets/example/dialogues/<path>.json` 都有同 ID 的 `data` 副本。
2. 确认两份 Dialogue 内容一致，尤其是 `requires`、目标 ID 和文件名。
3. 确认所有 Speaker、Theme、SceneAction 和图片都已包含在资源包的 `assets` 中。
4. 分别压缩资源包和数据包根目录。不要把外层文件夹一起套进 ZIP。
5. 为两个 ZIP 使用相同版本号，例如 `example_dialogue_resources-1.0.0.zip` 与 `example_dialogue_data-1.0.0.zip`。

### 单人游戏

1. 将资源包 ZIP 放入 `<游戏实例>/resourcepacks/` 并在“选项 → 资源包”中启用。
2. 将数据包 ZIP 放入 `<游戏实例>/saves/<世界>/datapacks/`。
3. 进入世界后执行 `/reload`，再按 `F3 + T`。

### Dedicated Server

1. 服务端安装 MaiMai Dialogue，并把数据包 ZIP 放入 `<服务端>/<世界>/datapacks/`。
2. 每个客户端安装 MaiMai Dialogue 与 Modern UI，并加载相同版本的资源包 ZIP。
3. 如果希望服务器自动提供资源包，把资源包 ZIP 上传到可以直接下载的 HTTP(S) 地址，并在 `server.properties` 中设置：

```properties
resource-pack=<资源包 ZIP 的直接下载地址>
resource-pack-sha1=<资源包 ZIP 的 SHA-1>
require-resource-pack=true
```

服务器资源包负责把客户端资源交付给玩家；Data Pack 仍然放在世界目录中。更新内容时应同时更换两个 ZIP，并更新下载地址或 SHA-1。

## 进入游戏验证

在服务端先执行：

```text
/reload
/datapack list enabled
/maimai_dialogue open @s example:guide/root
```

确认列表中存在 `example_dialogue_data`，并在客户端测试：公开选项、受 Progress 限制的选项、子 Dialogue Return、背景、动画、Filter 和 Theme。

## 如果没有生效

- 服务端提示 Dialogue 不存在：数据包未启用，或 ZIP 内的 `data` 外面多套了一层目录。
- 命令显示已发送但客户端无界面：资源包未启用，或缺少同 ID 的 `assets` Dialogue。
- 只有部分玩家报资源缺失：这些玩家拒绝了服务器资源包，或仍在使用旧版本。
- 自动下载失败：确认 `resource-pack` 是 ZIP 的直接下载地址，并重新计算 `resource-pack-sha1`。

## 下一步

继续[命令与管理](./commands.md)，了解如何为在线玩家打开 Dialogue 和维护 ProgressNode。
