---
title: 快速入门
description: 创建并打开第一个 MaiMai Dialogue。
---

# 快速入门

本章以 namespace `example` 创建一个最小对话 `example:hello`。示例适用于附属内容 MOD 或现有 MOD 的 `src/main/resources`。

## 1. 创建资源目录

```text
src/main/resources/
├─ assets/example/
│  ├─ dialogues/hello.json
│  └─ speakers/guide.json
└─ data/example/
   └─ dialogues/hello.json
```

Dialogue 必须在 `assets` 与 `data` 中各有一份。`speakers` 只需放在客户端 `assets` 中。

## 2. 定义 Speaker

创建 `assets/example/speakers/guide.json`：

```json
{
  "name": "向导"
}
```

文件 ID 为 `example:guide`。

## 3. 编写 Dialogue

将以下内容同时写入：

- `assets/example/dialogues/hello.json`
- `data/example/dialogues/hello.json`

```json
{
  "presentation": {
    "theme": "maimai_dialogue:default"
  },
  "steps": [
    {
      "speaker": {
        "type": "set",
        "id": "example:guide"
      },
      "text": "# 欢迎\n\n这是你的第一个 **MaiMai Dialogue**。"
    }
  ],
  "end": {
    "text": "再次推进将关闭对话。",
    "exit": {
      "type": "return"
    }
  }
}
```

资源路径 `dialogues/hello.json` 会映射为 Dialogue ID `example:hello`。正文支持 Markdown；Speaker 名称和选项文字不解析 Markdown。

## 4. 重载并打开

进入世界后执行：

```text
/reload
```

再按 `F3 + T` 重载客户端资源，然后执行：

```text
/maimai_dialogue open @s example:hello
```

命令要求 permission level 2。按空格或点击非按钮区域推进：若打字机或阻塞动画仍在播放，第一次只会立即完成当前内容，第二次才进入下一 Step。

## 5. 添加选项

将 `end.exit` 替换为：

```json
{
  "type": "options",
  "options": [
    {
      "text": "重新开始",
      "icon": "dialogue",
      "target": {
        "type": "dialogue",
        "dialogue": "example:hello"
      }
    },
    {
      "text": "关闭",
      "target": {
        "type": "return"
      }
    }
  ]
}
```

`dialogue` 目标会再次经过服务端访问校验；`return` 在根 Dialogue 中会关闭界面。

## 下一步

- 理解双端目录与 ID：[资源组织](../content/resources.md)
- 编写步骤、分支和返回：[Dialogue 与流程](../content/dialogues.md)
- 添加背景和画面对象：[场景表现](../content/presentation.md)
- 根据任务进度隐藏选项：[ProgressNode 与访问条件](../content/progress.md)
- 遇到无法打开或资源缺失：[故障排查](../reference/troubleshooting.md)
