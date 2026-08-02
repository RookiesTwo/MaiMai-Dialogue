---
title: 添加 VisualObject
description: 在背景上添加可定位、缩放和切换差分的画面对象。
---

# 添加 VisualObject

## 本章要实现什么

先把 emerald 与 diamond 差分定义为可复用 VisualAsset，再在场景中央偏上位置创建一个 VisualObject，供下一章动画切换。

## 开始前

你已经完成[添加背景](./background.md)。本章继续使用 Minecraft 自带图片。

## 需要修改的文件

新增只属于资源包的 VisualAsset：

```text
<资源包>/assets/example/visual_assets/guide/marker.json
```

再新增一个只属于资源包的 Scene：

```text
<资源包>/assets/example/scenes/guide/welcome.json
```

最后新增一个只属于资源包的 Presentation：

```text
<资源包>/assets/example/presentations/guide/welcome.json
```

并同步替换资源包与数据包中的 Dialogue：

```text
<资源包>/assets/example/dialogues/guide/welcome.json
<数据包>/data/example/dialogues/guide/welcome.json
```

## 跟着做

先创建 `marker.json`。它只定义可复用的差分和图片采样方式：

```json:line-numbers [visual_assets/guide/marker.json]
{
  "variants": {
    "default": "minecraft:item/emerald.png",
    "alternate": "minecraft:item/diamond.png"
  },
  "sampling": "nearest"
}
```

VisualAsset ID 是 `example:guide/marker`。接着创建 `scenes/guide/welcome.json`，把上一章的 Background 和新的 VisualObject 集中放进可复用 Scene。`guide_marker` 是场景内的对象 ID，`asset` 指向刚才创建的 VisualAsset：

::: code-group

```json:line-numbers [scenes/guide/welcome.json]
{
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
      "asset": "example:guide/marker",
      "initial_variant": "default",
      "x": 0.5,
      "y": 0.3,
      "anchor": "center",
      "scale": 8.0,
      "opacity": 1.0,
      "visible": true,
      "z_index": 10
    }
  }
}
```

```json:line-numbers [presentations/guide/welcome.json]
{
  "theme": "maimai_dialogue:default",
  "scene": "example:guide/welcome"
}
```

```json:line-numbers {3-4} [完整 dialogues/guide/welcome.json]
{
  "presentation": {
    "type": "reference",
    "id": "example:guide/welcome"
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

把 VisualAsset、Scene 与 Presentation 只保存到 Resource Pack，并把引用 Presentation 的完整 Dialogue 同步保存到两个 Pack。`x`、`y` 使用画面比例位置，`anchor` 决定哪个点对齐到该坐标，`z_index` 越大越靠前。VisualObject 省略 `sampling` 后会继承 VisualAsset 的 `nearest`，避免像素图放大后变模糊。

## 进入游戏验证

重载后打开 `example:guide/welcome`。背景前方应出现放大的 emerald；它位于画面水平中央、约三成高度处。

## 如果没有生效

- 对象完全不显示：依次检查 Dialogue 的 Presentation ID、Presentation 的 `scene` ID，再检查 `asset` ID、`visible`、`initial_variant` 和图片 ID。
- 图片模糊：在 VisualAsset 中将 `sampling` 设为 `nearest`，或在单个 VisualObject 中覆盖它。
- 对象位置异常：先使用 `anchor: center`，再调整 `x`、`y`。
- 对象 ID 使用了 `background` 或 `dialogue`：这两个名称是保留 target，不能作为 VisualObject ID。

同一个 VisualAsset 可以被任意多个 Dialogue 引用；每次引用都可以选择不同 `initial_variant`、位置、缩放和层级。只使用一次的简单对象也可以继续在 VisualObject 内直接写 `variants`。

同一个 Scene 也可以被多个 PresentationDefinition 引用。某套 Presentation 需要额外角色时，可以在该 definition 的 `visual_objects` 中添加；与 Scene 同名的对象会被 Presentation 局部定义覆盖。

同一个 PresentationDefinition 可以被多个 Dialogue 直接引用，统一它们的 Theme、Scene 和 DialogueBox。reference 是完整替换式调用，不能再在 Dialogue 中添加局部 Presentation 字段；需要不同配置时创建另一个 PresentationDefinition。

## 下一步

继续[播放 SceneAction](./actions.md)，让 emerald 入场并切换为 diamond。
