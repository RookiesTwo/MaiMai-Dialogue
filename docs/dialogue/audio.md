---
title: BGM 与音效
description: 添加背景音乐、角色打字音色和按步骤触发的提示音。
---

# BGM 与音效

对话音频只在正在阅读的玩家客户端播放。BGM 可以贯穿多个 Dialogue；打字机音效跟随文字出现；action 音效适合提示音、敲门声等一次性声音。

## 先试听内置演示

安装 MOD 后，在有权限的游戏中执行：

```text
/maimai_dialogue open @s maimai_dialogue:debug/audio
```

演示依次展示默认打字音色、Speaker 覆盖、步骤静音、延迟提示音和 BGM 换曲。选择“进入子对话”检查音乐延续与淡出，关闭对话会清理本 MOD 播放的声音。

演示复用原版音乐与音效，没有附带第三方音频素材。默认打字音效为低音量的原版音符盒短音，资源 ID 是 `maimai_dialogue:ui.typewriter`。

## 给 Dialogue 配置 BGM

在 Dialogue 顶层加入以下字段，其余 `presentation`、`steps`、`end` 照常填写：

```json
{
  "bgm": {
    "type": "play",
    "sound": "minecraft:music.creative",
    "volume": 0.4,
    "loop": true,
    "fade_ms": 500
  }
}
```

`bgm` 是可选字段。首次打开对话时省略它，就没有对话 BGM；同一 session 中切换 Dialogue 时省略，则继续当前音乐。明确停止音乐使用 `"bgm": { "type": "stop" }`。返回 root 会重新应用 root 的 BGM 配置，同一首仍在播放的音乐不会从头开始。

换成不同声音 ID 时会交叉淡化。`fade_ms` 默认 `500`，设为 `0` 就立即切换。BGM 播放期间暂停 Minecraft 原版背景音乐调度，BGM 停止或对话关闭后恢复；其他游戏音效不受影响。

## 在步骤里播放音效或换曲

把下面内容加入某一步的 `actions`：

```json
{
  "actions": [
    {
      "delay_ms": 500,
      "action": {
        "type": "inline",
        "action": {
          "sound": {
            "sound": "minecraft:block.amethyst_block.chime",
            "volume": 0.6,
            "pitch": 1.0
          }
        }
      }
    },
    {
      "delay_ms": 500,
      "action": {
        "type": "inline",
        "action": {
          "bgm": {
            "type": "play",
            "sound": "minecraft:music.menu",
            "fade_ms": 1000
          }
        }
      }
    }
  ]
}
```

两个调用会在该步开始后的 `500ms` 触发。纯音频调用不需要 `target`，也不等待声音播放完毕。它们仍支持独立 action 文件和 `reference`，例如内置 `maimai_dialogue:debug/chime`。

声音也能与原有动画轨道写在同一个 action 里：保留动画需要的 `target`，音效在该调用的 `delay_ms` 时刻触发。希望在动画中途响起时，添加另一个带延迟的声音调用。

## 为 Speaker 配置打字音色

Speaker 文件示例：

```json
{
  "name": "向导",
  "typewriter_sound": {
    "sound": "minecraft:block.note_block.pling",
    "volume": 0.08,
    "pitch": 1.5,
    "min_interval_ms": 90
  }
}
```

省略时使用默认音色；填写 `false` 表示该 Speaker 静音。Step 和 End 也能填写 `typewriter_sound`，优先级高于 Speaker，但只影响当前节点。下一步省略时会重新使用 Speaker 的配置；隐藏 Speaker 后使用默认配置。

默认音量 `0.15`、音高 `1`、最短间隔 `50ms`。只对新显示的文字发声，空白和标点不响；同一帧出现多个字符也只响一次。直接显示全文、点击跳过文字、查看历史及缩放重排不会补播音效。

## 使用自己的声音

将文件放到资源包：

```text
assets/example/sounds/music/room.ogg
assets/example/sounds/ui/type.ogg
assets/example/sounds.json
```

`sounds.json`：

```json
{
  "music.room": {
    "sounds": [{ "name": "example:music/room", "stream": true }]
  },
  "ui.type": {
    "sounds": ["example:ui/type"]
  }
}
```

在 Dialogue 中引用 `example:music.room`，在打字机配置中引用 `example:ui.type`。声音定义的文件名不要写 `.ogg` 后缀。这里只需资源包中的声音定义，不需要为每个自定义声音编写 Java 注册代码。

要统一替换 MOD 默认打字音效，在资源包的 `assets/maimai_dialogue/sounds.json` 中写：

```json
{
  "ui.typewriter": {
    "replace": true,
    "sounds": ["example:ui/type"]
  }
}
```

长音乐建议设置 `stream: true`，避免整段读入内存。声音、Speaker 和独立 action 放在资源包中；Dialogue JSON 与其他对话内容一样同步放在客户端资源包与服务端数据包。

## 快进、跳过和玩家设置

- 按住快进键时，打字机静音，action 按加速后的时间触发；声音与 BGM 淡化本身不变速。
- 点击跳过当前播放或提前进入下一步时，未触发的短音效取消，当前步骤的最终 BGM 指令仍会生效。
- “跳到结尾”只应用剩余步骤和 End 的最终 BGM 指令，中间音乐和短音效不会集中补播。
- 已经响起的短音效可以自然结束。关闭 session 或断线会立即停止本 MOD 的声音。
- 资源重载后，正在使用的 BGM 会从头重新建立，不补播短音效。
- 客户端设置中的“音频”提供三项独立音量和打字机开关。BGM 同时受 Minecraft 音乐音量影响；全部音频都受主音量影响。

## 试听验收清单

1. 执行演示命令，确认 BGM 淡入、字符声音间隔和 Speaker 音色切换。
2. 在第三步等待提示音与换曲，再重开演示并提前跳过该步：应取消尚未响起的提示音，并切到最终曲目。
3. 按住快进，确认打字机静音、提示音仍触发，BGM 不加速。
4. 进入子对话，确认音乐延续；推进到停止步骤，确认淡出后原版音乐恢复调度。
5. 查看历史、改变窗口大小和 GUI scale，确认 BGM 没有重启、已显示的文本没有重新发声。
6. 调整三类音量及打字机开关，确认各项独立生效。
7. 在 BGM 播放时重载资源，确认只恢复一份 BGM；关闭对话和断线后确认没有残留声音。

完整字段范围见 [音频 JSON](../reference/audio-json.md)。
