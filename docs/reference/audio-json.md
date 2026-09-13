---
title: 音频 JSON
description: BGM、打字机和 action 音效的字段、默认值及播放规则。
---

# 音频 JSON

教程：[BGM 与音效](../dialogue/audio.md)。

## BGM 操作

Dialogue 顶层和 SceneAction 内均可填写可选字段 `bgm`：

| 字段 | 默认值 | 规则 |
|---|---|---|
| `type` | 必填 | `play` 或 `stop` |
| `sound` | 无 | 声音事件 ID；`play` 必填，`stop` 必须省略 |
| `volume` | `1` | `0..1` |
| `loop` | `true` | 是否在曲目结束后续播 |
| `fade_ms` | `500` | `0..60000`；真实时间，不受快进影响 |

`stop` 使用 `fade_ms` 淡出，其他播放参数没有作用。省略 `bgm` 保留当前音乐。相同 ID 仍在播放时更新音量及循环设置；不同 ID 交叉淡化。循环在曲目边界续播，不提供采样级无缝循环保证。

同一步中的 BGM 按 `delay_ms` 排序，同一时刻按 JSON 调用顺序执行。跳过时只应用最终一条 BGM 指令。

## Action 音效

SceneAction 的可选 `sound` 对象：

| 字段 | 默认值 | 规则 |
|---|---|---|
| `sound` | 必填 | 声音事件 ID |
| `volume` | `1` | `0..1` |
| `pitch` | `1` | `0.5..2` |

不循环，不随距离衰减，只对当前玩家播放。调用的 `delay_ms` 同时决定音效和 BGM 操作的触发时间。纯音频 action 可以省略 `target`，不参与 blocking；`duration_ms` 不代表音频长度。带动画轨道的 action 仍需要原有 target，动画时长和 blocking 规则不变。

## 打字机音效

Speaker、Step、End 都支持可选 `typewriter_sound`。写 `false` 静音；写对象启用：

| 字段 | 默认值 | 规则 |
|---|---|---|
| `sound` | `maimai_dialogue:ui.typewriter` | 声音事件 ID |
| `volume` | `0.15` | `0..1` |
| `pitch` | `1` | `0.5..2` |
| `min_interval_ms` | `50` | `0..60000`；真实时间的最短发声间隔 |

优先选择整个配置对象：Step／End → 当前 Speaker → MOD 默认。对象内省略的属性使用上表默认值，不逐属性合并 Speaker 配置。`false` 同样是有效覆盖，`true` 和 JSON `null` 不作为配置方式。

客户端打字机开关和音量最后应用；内容作者不能绕过玩家的静音设置。快进、瞬间显示全文和历史查看不发出打字机声音，帧间补显的多个字符不会补成一串音效。

## 声音资源与错误

`sound` 指向 Minecraft `sounds.json` 定义的事件 ID，支持原版、其他 MOD 和资源包新增的事件。BGM、音效和打字机使用相同的资源体系。

数值越界或未知操作类型属于 JSON 加载错误。不存在或空的声音事件在尝试播放时记录去重警告，并忽略该次操作；缺失 BGM 不会阻止对话继续。无效换曲保留之前仍在播放的音乐。
