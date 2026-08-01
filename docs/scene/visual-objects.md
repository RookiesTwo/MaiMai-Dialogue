---
title: 添加 VisualObject
description: 在背景上添加可定位、缩放和切换差分的画面对象。
---

# 添加 VisualObject

## 本章要实现什么

在场景中央偏上位置显示一个放大的 emerald，并预先声明 diamond 差分，供下一章动画切换。

## 开始前

你已经完成[添加背景](./background.md)。本章继续使用 Minecraft 自带图片。

## 需要修改的文件

同步替换两个 `assets/data` 位置的 `dialogues/guide/root.json`。

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
    },
    "visual_objects": {
      "guide_marker": {
        "variants": {
          "default": "minecraft:item/emerald.png",
          "alternate": "minecraft:item/diamond.png"
        },
        "initial_variant": "default",
        "x": 0.5,
        "y": 0.3,
        "anchor": "center",
        "scale": 8.0,
        "sampling": "nearest",
        "opacity": 1.0,
        "visible": true,
        "z_index": 10
      }
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

`guide_marker` 是本场 Dialogue 内部使用的对象 ID。`x`、`y` 使用画面比例位置，`anchor` 决定哪个点对齐到该坐标，`z_index` 越大越靠前。像素图使用 `nearest` 可避免放大后变模糊。

## 进入游戏验证

重载后打开 root。背景前方应出现放大的 emerald；它位于画面水平中央、约三成高度处。

## 如果没有生效

- 对象完全不显示：检查 `visible`、`initial_variant` 和图片 ID。
- 图片模糊：将 `sampling` 设为 `nearest`。
- 对象位置异常：先使用 `anchor: center`，再调整 `x`、`y`。
- 对象 ID 使用了 `background` 或 `dialogue`：这两个名称是保留 target，不能作为 VisualObject ID。

## 下一步

继续[播放 SceneAction](./actions.md)，让 emerald 入场并切换为 diamond。
