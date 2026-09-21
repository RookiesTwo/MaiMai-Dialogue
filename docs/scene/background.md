---
title: 添加背景
description: 在 Scene 中配置背景，并让 Dialogue 引用它。
---

# 添加背景

完成前面的正文、选项与进度教程后，创建一个自有 Scene，为对话配置背景。本章使用 Minecraft 自带图片，无需导入 PNG。

## 创建 Scene

新增资源包文件 `assets/example/scenes/guide/welcome.json`。Scene 只需要资源包副本：

```json
{
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
}
```

`variants` 声明两个背景差分，`initial_variant` 选择开始显示的图片。`cover` 等比铺满视口并裁切多余部分；`contain` 完整显示图片；`stretch` 拉伸铺满。`opacity` 的范围为 0～1。

## 让 Dialogue 引用 Scene

把资源包和数据包中的 `dialogues/guide/welcome.json` 同步改为：

```json
{
  "scene": "example:guide/welcome",
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

## 验证

执行 `/reload`，按 `F3 + T` 重载资源包，再执行 `/maimai_dialogue open @s example:guide/welcome`。如果没有背景，检查 Scene ID、图片路径和客户端资源加载日志。

下一步：[添加 VisualObject](./visual-objects.md)。
