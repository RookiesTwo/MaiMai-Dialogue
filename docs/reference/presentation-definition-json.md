---
title: 可复用 Presentation
description: 将完整 Presentation 定义一次，并在多个 Dialogue 中引用。
---

# 可复用 Presentation

PresentationDefinition 保存完整的 Presentation，包括 Theme、Scene、DialogueBox 布局，以及可选的局部 Background、VisualObject 和 Filter。

## 文件位置

PresentationDefinition 只属于客户端 Resource Pack：

```text
assets/<namespace>/presentations/<path>.json
```

例如：

```text
assets/example/presentations/guide/default.json
                            ↓
               example:guide/default
```

## 完整示例

```json
{
  "theme": "maimai_dialogue:default",
  "scene": "example:village/square",
  "dialogue_box": {
    "x": 0.5,
    "y": 0.98,
    "width": 0.5,
    "max_height": 0.4,
    "anchor": "bottom_center"
  }
}
```

文件内容与 inline [Presentation JSON](./presentation-json.md) 完全相同。`theme` 必填；`scene`、`background`、`dialogue_box`、`visual_objects` 和 `filter` 均可选。

## 在 Dialogue 中引用

```json
{
  "presentation": {
    "type": "reference",
    "id": "example:guide/default"
  }
}
```

引用对象只能包含：

| 字段 | 必填 | 值 |
|---|---:|---|
| `type` | 是 | 固定为 `reference` |
| `id` | 是 | PresentationDefinition ID |

reference 不能同时声明 `theme`、`scene`、`dialogue_box` 或其他 inline 字段。需要另一套配置时，创建新的 PresentationDefinition。

## 与 Scene 的职责区别

- Scene 复用纯视觉舞台：Background、VisualObject、Filter。
- PresentationDefinition 复用一次完整演出配置：Theme、Scene 引用、DialogueBox 和局部场景覆盖。
- Dialogue 负责正文、Speaker、步骤、SceneAction 调用、条件与选项。

客户端打开 Dialogue 时按以下顺序解析：

```text
PresentationDefinition
→ Theme
→ Scene
→ VisualAsset
→ SceneAction
→ UI 与图片
```

PresentationDefinition 不能引用另一个 PresentationDefinition，因此不会产生引用循环。它只放在 Resource Pack；引用它的 Dialogue JSON 仍须同步到 Resource Pack 与 Data Pack。
