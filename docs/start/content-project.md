---
title: 创建内容包
description: 为 example namespace 建立 Resource Pack 与 Data Pack。
---

# 创建内容包

## 本章要实现什么

完成后，你会得到配套的资源包（Resource Pack）和数据包（Data Pack）。资源包向客户端提供正文与画面，数据包向服务端提供访问检查所需的 Dialogue。

## 开始前

你已经[了解 MOD 如何工作](./dialogue-structure.md)，并创建了一个用于测试的单人世界。退出世界后，在单人游戏列表中选择该世界，通过“编辑 → 打开世界文件夹”可以找到它的目录。

本教程固定使用：

- 资源包目录名：`example_dialogue_resources`
- 数据包目录名：`example_dialogue_data`
- namespace：`example`

## 需要修改的文件

在游戏实例和测试世界中创建以下两个 Pack：

```text
<游戏实例>/
├─ resourcepacks/
│  └─ example_dialogue_resources/
│     ├─ pack.mcmeta
│     └─ assets/example/
│        ├─ dialogues/guide/
│        ├─ speakers/
│        ├─ dialogue_themes/
│        ├─ presentations/
│        ├─ scenes/
│        ├─ visual_assets/
│        ├─ presentation_actions/
│        └─ textures/dialogue/
└─ saves/<测试世界>/
   └─ datapacks/
      └─ example_dialogue_data/
         ├─ pack.mcmeta
         └─ data/example/
            └─ dialogues/guide/
```

后续页面使用 `<资源包>` 和 `<数据包>` 分别代指上面的两个 Pack 根目录。

## 跟着做

1. 在 `<游戏实例>/resourcepacks/` 中创建 `example_dialogue_resources`。
2. 在资源包根目录创建 `pack.mcmeta`：

```json:line-numbers {2-5} [资源包 pack.mcmeta]
{
  "pack": {
    "pack_format": 34,
    "description": "MaiMai Dialogue example resources"
  }
}
```

3. 在资源包内创建 `assets/example` 以及目录树中列出的子目录。
4. 在 `<测试世界>/datapacks/` 中创建 `example_dialogue_data`。
5. 在数据包根目录创建另一份 `pack.mcmeta`：

```json:line-numbers {2-5} [数据包 pack.mcmeta]
{
  "pack": {
    "pack_format": 48,
    "description": "MaiMai Dialogue example data"
  }
}
```

6. 在数据包内创建 `data/example/dialogues/guide`。

这里的 Resource Pack format `34` 和 Data Pack format `48` 对应 Minecraft 1.21 与 1.21.1。namespace 必须使用小写字符；本教程始终使用 `example`。

资源 ID 由 namespace 和定义文件的相对路径组成。例如资源包中的：

```text
assets/example/dialogues/guide/welcome.json
                         ↓
            example:guide/welcome
```

每个 Dialogue 都要有两份同 ID 文件：

- 资源包中的 `assets` 副本供客户端显示正文和画面；
- 数据包中的 `data` 副本供服务端检查 Dialogue、`requires` 和访问权限。

后续每次修改 Dialogue，都要把相同 JSON 同步保存到两个 Pack 中。Speaker、Theme、Presentation、Scene、VisualAsset、SceneAction 和图片只放入资源包。

## 进入游戏验证

1. 启动游戏，在“选项 → 资源包”中启用 `MaiMai Dialogue example resources`。
2. 进入测试世界并执行：

```text
/datapack list enabled
```

结果中应出现 `file/example_dialogue_data`。空目录暂时不会增加可打开的 Dialogue；下一章会创建第一个内容文件。

## 如果没有生效

- Pack 没有出现在列表中：确认 `pack.mcmeta` 位于 Pack 根目录，而不是多套了一层文件夹。
- 数据包显示版本不兼容：确认 Minecraft 是 1.21.1，并使用 `pack_format: 48`。
- 资源包显示版本不兼容：确认资源包使用 `pack_format: 34`。
- namespace 含大写或空格：请只使用小写字母、数字、下划线和连字符。

## 下一步

继续[创建第一段对话](./first-dialogue.md)。
