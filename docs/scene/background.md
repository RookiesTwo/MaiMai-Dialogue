---
title: 添加背景
description: 为 Dialogue 场景添加可切换的背景图片。
---

# 添加背景

## 本章要实现什么

打开 `example:guide/root` 时，Minecraft 全景图会作为半透明背景覆盖在世界画面上。我们先声明两个背景差分，后续动画章节会切换它们。

## 开始前

你已经完成[Progress 条件](../dialogue/progress.md)。本章直接使用 Minecraft 自带图片，因此不需要准备额外 PNG。

## 需要修改的文件

同步替换资源包与数据包中的：

```text
<资源包>/assets/example/dialogues/guide/root.json
<数据包>/data/example/dialogues/guide/root.json
```

## 跟着做

把两个 `root.json` 都替换为：

```json
{
  "presentation": {
    "theme": "maimai_dialogue:default",
    "background": {
      "variants": {
        "default": "minecraft:gui/title/background/panorama_0.png",
        "alternate": "minecraft:gui/title/background/panorama_1.png"
      },
      "initial_variant": "default",
      "fit": "cover",
      "opacity": 0.82
    }
  },
  "steps": [
    {
      "speaker": {
        "type": "set",
        "id": "example:guide"
      },
      "text": "# 欢迎来到村庄\n\n你想了解什么？"
    }
  ],
  "end": {
    "text": "请选择一个话题。",
    "exit": {
      "type": "options",
      "options": [
        {
          "text": "了解村庄",
          "icon": "question",
          "target": {
            "type": "dialogue",
            "dialogue": "example:guide/about"
          }
        },
        {
          "text": "询问秘密地点",
          "icon": "exclamation",
          "target": {
            "type": "dialogue",
            "dialogue": "example:guide/secret"
          }
        },
        {
          "text": "离开",
          "target": {
            "type": "return"
          }
        }
      ]
    }
  }
}
```

`variants` 先声明可用图片，`initial_variant` 决定最初显示哪一张。`cover` 会让图片覆盖整个场景区域，`opacity` 控制透明度。

使用自己的图片时，例如：

```text
<资源包>/assets/example/textures/dialogue/village.png
```

对应的 JSON ID 是 `example:dialogue/village.png`，ID 中不重复写 `textures/`。

## 进入游戏验证

执行 `/reload`、按 `F3 + T`，再打开 `example:guide/root`。对话框后方应显示半透明的 Minecraft 全景图，世界仍会在透明区域后方运行。

## 如果没有生效

- 图片 ID 包含 `textures/`：从 ID 中删掉这一段。
- 背景为空：确认 `initial_variant` 与 `variants` 中的 key 完全一致。
- 图片被拉伸得不合预期：尝试 `contain` 或 `stretch`，并查看参考页中的区别。
- 客户端报缺失图片：背景图片属于 `assets`，服务端无法提供它。

## 下一步

继续[添加 VisualObject](./visual-objects.md)，在背景上放置独立画面对象。
