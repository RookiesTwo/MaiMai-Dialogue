---
title: 播放 SceneAction
description: 创建可复用动画，并在 Dialogue 步骤中调用它。
---

# 播放 SceneAction

## 本章要实现什么

打开 root 时，`guide_marker` 会从左侧淡入；到达选项页时，它会向右移动并切换为 diamond，背景也会切换差分。

## 开始前

你已经完成[添加 VisualObject](./visual-objects.md)，场景中存在 `guide_marker`、背景 `alternate` 和对象 `alternate` 差分。

## 需要修改的文件

新增可复用 SceneAction：

```text
src/main/resources/assets/example/presentation_actions/guide/enter.json
```

并同步修改两个 `dialogues/guide/root.json`。

## 跟着做

1. 创建 `presentation_actions/guide/enter.json`：

```json
{
  "duration_ms": 500,
  "easing": "ease_out",
  "blocking": true,
  "x": [
    { "at": 0.0, "value": -0.2 },
    { "at": 1.0, "value": 0.0 }
  ],
  "opacity": [
    { "at": 0.0, "value": -1.0 },
    { "at": 1.0, "value": 0.0 }
  ]
}
```

这个资源的 ID 是 `example:guide/enter`。

2. 将客户端和服务端的 `root.json` 都替换为下面的完整内容：

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
      "text": "# 欢迎来到村庄\n\n你想了解什么？",
      "actions": [
        {
          "target": "guide_marker",
          "action": {
            "type": "reference",
            "id": "example:guide/enter"
          }
        }
      ]
    }
  ],
  "end": {
    "text": "请选择一个话题。",
    "actions": [
      {
        "target": "guide_marker",
        "action": {
          "type": "inline",
          "action": {
            "duration_ms": 500,
            "easing": "ease_in_out",
            "x": [
              { "at": 1.0, "value": 0.18 }
            ],
            "variant": {
              "at": 0.55,
              "value": "alternate"
            }
          }
        }
      },
      {
        "target": "background",
        "action": {
          "type": "inline",
          "action": {
            "duration_ms": 500,
            "easing": "ease_in_out",
            "variant": {
              "at": 0.55,
              "value": "alternate"
            }
          }
        }
      }
    ],
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

SceneAction 的数值轨道是相对于进入当前步骤时状态的偏移，不是绝对坐标。

## 进入游戏验证

重载两端后打开 root：

1. emerald 应从左侧淡入；
2. 播放中推进会直接完成入场，不会立刻换页；
3. 进入选项页后，emerald 向右移动并变成 diamond；
4. 背景同时切换为另一张全景图。

## 如果没有生效

- 外部 Action 缺失：检查文件目录是 `presentation_actions`，ID 是 `example:guide/enter`。
- 日志提示 target 缺失：`target` 必须与 `visual_objects` 中的 `guide_marker` 完全一致。
- variant 切换失败：确认 `alternate` 已在对应 `variants` 中声明。
- 同一属性冲突：同一步中不能让多个 Action 写同一 target 的同一属性。

## 下一步

继续[调整对话框布局](./dialogue-box.md)。完整字段和关键帧规则可在 [SceneAction JSON 参考](../reference/scene-action-json.md)中查阅。
