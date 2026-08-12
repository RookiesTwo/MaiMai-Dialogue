---
title: 添加背景
description: 为 Dialogue 场景添加可切换的背景图片。
---

# 添加背景

## 本章要实现什么

打开 `example:guide/welcome` 时，Minecraft 全景图会作为半透明背景覆盖在世界画面上。我们先声明两个背景差分（variant：同一个背景的不同图片，可在动画中切换），后续动画章节会切换它们。

## 开始前

你已经完成[Progress 条件](../dialogue/progress.md)。本章直接使用 Minecraft 自带图片，因此不需要准备额外 PNG。

## 需要修改的文件

同步替换资源包与数据包中的：

```text
<资源包>/assets/example/dialogues/guide/welcome.json
<数据包>/data/example/dialogues/guide/welcome.json
```

## 跟着做

在 `presentation` 中加入 `background`。高亮部分是本章新增的完整字段：

::: code-group

```json{3-14} [本章改动]
{
  "presentation": {
    "background": {
      "variants": {
        "default": "minecraft:gui/title/background/panorama_0.png",
        "alternate": "minecraft:gui/title/background/panorama_1.png"
      },
      "initial_variant": "default",
      "fit": "cover",
      "opacity": 0.82
    }
  }
}
```

```json:line-numbers {5-15} [完整 welcome.json]
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
      "text": "# 欢迎来到村庄\n\n我是这里的 **向导**。"
    },
    {
      "text": "沿着 *石路* 向前，就能找到 `market`。"
    }
  ],
  "end": {
    "speaker": {
      "type": "hide"
    },
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

:::

把完整文件同步保存到两个 Pack。`variants` 声明可用图片，`initial_variant` 决定最初显示哪一张。`cover` 会让图片覆盖整个场景区域，`opacity` 控制透明度。

使用自己的图片时，例如：

```text
<资源包>/assets/example/textures/dialogue/village.png
```

对应的 JSON ID 是 `example:dialogue/village.png`，ID 中不重复写 `textures/`。

## 进入游戏验证

执行 `/reload`、按 `F3 + T`，再打开 `example:guide/welcome`。对话框后方应显示半透明的 Minecraft 全景图，世界仍会在透明区域后方运行。

## 如果没有生效

- 图片 ID 包含 `textures/`：从 ID 中删掉这一段。
- 背景为空：确认 `initial_variant` 与 `variants` 中的 key 完全一致。
- 图片被拉伸得不合预期：`fit` 有三种取值，效果不同：`cover` 把图片裁切后填满整个区域；`contain` 完整显示整张图片、多余部分留空；`stretch` 把图片拉伸到填满区域（可能变形）。
- 游戏报缺失图片：确认图片位于已启用的资源包中。

## 什么时候该把背景拆出去

本章把背景直接写进了对话文件里，这样最简单，适合"只有这一处用"的情况。当出现下面这些情况时，就应该把背景拆到独立文件里：

- 多段对话要共用同一张背景，不想每段都抄一遍；
- 要在背景上放人物立绘等视觉对象（下一章的内容）；
- 要做背景动画（切换差分）。

下一章会把背景、画面对象和滤镜集中放进一个"场景"文件里。

## 下一步

继续[添加 VisualObject](./visual-objects.md)，在背景上放置独立的视觉对象。
