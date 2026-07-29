# MaiMai Dialogue 产品目标与设计检查点

> 状态：规划中
> 更新日期：2026-07-29
> 本文只记录已经确认的方向，并将尚未确定的设计明确列为待讨论事项。

## 1. 产品目标

MaiMai Dialogue 是一个基于 NeoForge 1.21.1 和 ModernUI 的通用对话引擎 MOD。

它不与特定 NPC、实体类型或任务系统绑定，而是为整合包作者和其他 MOD 提供可复用的现代化对话能力：

- Data Pack 与命令是主要内容接入方式。
- Java API 用于第三方 MOD 集成。
- 客户端负责完整对话内容的展示与 Dialogue 内部线性流程。
- logical server 负责维护玩家 ProgressNode、校验 Dialogue 是否存在，并权威判断 Dialogue 是否允许启动。
- ProgressNode 由独立命令和 Java API 管理，Dialogue 与 Exit 只读取节点，不修改节点。
- 产品保持为专注的对话引擎，不扩展为完整视觉小说制作框架。

首个里程碑以“服务端授权根 Dialogue，客户端完成线性 Step 播放，并在 EndStep 根据 ProgressNode 显示和进入目标 Dialogue”为最小闭环。

## 2. 内容分发与运行职责

客户端与服务端预装相同的内容包或 MOD。单人游戏由 integrated server 承担服务端职责，多人游戏由 dedicated server 承担。

同一个内容包包含完整 Dialogue 定义和 UI/图片资源，并同时安装在客户端与服务端。两端各自读取本地的同一份 Dialogue 文件，共用同一个 `DialogueDefinition` 与 Codec。资源在 `assets` 与 `data` 中的具体落位属于实现细节，不再作为产品层面的待定问题。

已确认的职责边界：

- 客户端和服务端都持有同一份完整 Dialogue 文件；客户端使用正文和表现数据，服务端只使用 Dialogue ID 与 `requires`。
- logical server 不负责下发或逐步推进 Dialogue 内容。
- 服务端授权和校验的单位是整场 `dialogue_id`。
- 服务端保存在线玩家的 ProgressNode 集合，并权威计算目标 Dialogue 是否允许启动。
- 服务端不存在目标 Dialogue 时返回明确错误；服务端允许但客户端本地缺少文件时，由客户端报告本地资源错误。后者被视为 Data Pack 开发或双端内容不一致的诊断错误，不属于正常运行分支。
- 客户端与服务端内容属于同一份分发包；单人 integrated server 和多人 dedicated server 执行相同节点规则。

对话运行状态完全保存在客户端。Esc、死亡、断线、Screen 被替换及 root Dialogue 正常结束时，客户端直接关闭界面，不通知服务端。

## 3. UI 与视觉目标

### 3.1 画面分层

固定的基础绘制顺序为：

```text
Minecraft 游戏世界
→ 可选的自定义背景
→ 多个画面对象
→ 对话框
→ 历史记录、展开按钮等 UI controls
```

- 不提供“隐藏或停止渲染游戏世界”的开关。
- 自定义背景可以覆盖游戏画面，并可配置位置、尺寸、缩放方式和入场动画。
- 布局采用“锚点 + 归一化坐标 + dp 偏移”，以适配不同分辨率和 GUI Scale。
- 画面对象属于场景层，不覆盖交互 UI。

### 3.2 通用画面对象

不强制定义“角色立绘”，而是提供可复用的通用画面对象，概念类似 sprite。

一场对话中可以同时存在多个画面对象。对象需要支持：

- 位置和缩放；
- 显示、隐藏及透明度；
- 素材或差分切换；
- 位移、缩放、淡入淡出及组合动画；
- 由对话内容触发表现变化。

动画的具体 JSON 结构、复用方式和时间线模型留待后续数据模型设计。

### 3.3 对话框

- 默认位于屏幕底部。
- Data Pack 可以配置位置、尺寸及对话过程中触发的动画。
- 不包含参考界面中的左侧头像。
- 角色名固定显示在对话框左上角。
- 默认文字区域位于角色名下方。
- 选项区域位于文字区域下方。
- 每个选项占一行，可带问号、感叹号、对话等类型图标。
- 选项过多时支持滚动。
- 只有当选项列表实际溢出时才显示“展开所有选项”按钮。
- 展开后，对话框保持底部锚定并向上扩展；超出安全区域后仍使用滚动。

### 3.4 历史记录

- 历史记录入口位于整个界面的右侧，不放在对话框头部。
- 客户端可以选择是否显示该入口。
- 历史记录以右侧抽屉形式打开。
- 历史内容的保存范围和具体数据结构尚未确定。

### 3.5 主题与客户端设置

- MOD 提供内置默认主题。
- Data Pack 可以定义或选择主题。
- 不向普通玩家提供颜色、边框、间距、字体等主题编辑功能。
- 不提供 reduced motion 或动画倍率覆盖。
- 对话界面始终不暂停游戏世界。

## 4. 交互目标

对话播放与选项选择是两个不同的交互阶段：

- 播放句子时，鼠标左键点击非按钮区域或按下可重绑的“推进对话”KeyMapping，完成或推进内容。
- 如果当前句子的打字机动画尚未结束，第一次推进只立即显示完整当前句，不进入下一句。
- 当前句已经完整显示后，再次推进才进入下一句。
- 出现选项时，空白区域点击不推进；只有选项行及历史、展开等独立 UI controls 可点击。
- 选择某个选项后进入对应的客户端对话分支。
- 不提供自动播放功能。

默认启用打字机动画：

- 客户端保存默认打字机速度。
- 对话内容未指定速度时使用客户端默认值。
- 对话内容显式指定速度时，该值覆盖客户端默认配置。

## 5. Dialogue 核心模型

### 5.1 DialogueDefinition 与 root Dialogue

`DialogueDefinition` 是客户端和服务端共用的完整内容类型。两端持有并解析同一份本地文件，但只消费各自需要的数据。

- Dialogue 只有一个抽象入口，不额外定义入口节点。
- ID 由资源文件的 namespace、相对路径和文件名确定，底层使用 Minecraft `ResourceLocation`。
- 例如 `assets/example/dialogues/skier/job_intro.json` 对应 `example:skier/job_intro`。
- Dialogue 内部始终线性播放，所有分支只能出现在唯一结尾。
- Dialogue 可以声明可选的 `requires` 表达式；缺省表示公开。

```text
DialogueDefinition
├─ requires: ProgressExpression?
├─ presentation: Presentation
├─ steps: List<ContinueStep>
└─ end: EndStep
```

初始界面直接复用普通 DialogueDefinition，不新增 Menu、Conversation 或服务端 session 类型：

- Data Pack 不在 DialogueDefinition 中声明 `root: true` 或固定 root ID。
- NPC 集成、命令、Java API 或客户端入口指定本次首先请求打开的 dialogue_id；该 Dialogue 成功打开后，客户端将它同时记为 root 和 current。
- 同一个 Dialogue 在不同打开流程中既可以作为 root，也可以作为普通目标，不需要复制定义。
- root 可以没有 ContinueStep，直接以 EndStep 显示默认文字和 OptionsExit。
- 进入其他 Dialogue 时，客户端只保留 root dialogue_id。
- 子 Dialogue 的 ReturnExit 只表示导航到 root Dialogue 的唯一入口；引擎不定义“从哪里继续”、恢复点或跳转位置。
- 设计者通过 ProgressNode、`requires` 和 Dialogue 组织决定玩家返回 root 后看到的内容。
- root Dialogue 自己执行 ReturnExit 时直接关闭界面。

### 5.2 JSON 与 Codec 基础规则

- Exit、Option target 和 speaker 操作等多态结构统一使用显式 `type`。
- `steps` 省略等价于空列表。
- `presentation`、`end` 和 EndStep 的 `exit` 必填。
- Step 的 `text` 省略表示没有文字，可用于纯画面 Step；不使用 JSON `null`。
- TextContent、Markdown 和本地化模型暂不实现，首阶段使用普通 String。
- JSON 语法、Codec 或 ProgressExpression 解析失败时，只跳过当前 Dialogue 并记录带 ResourceLocation 的错误。
- 不在加载时强制目标 Dialogue 存在；两端文件不一致由运行时存在性校验和客户端错误处理覆盖。

示例：

```json
{
  "requires": "quest.skier.started && !quest.skier.finished",
  "presentation": {
    "theme": "example:default",
    "visual_objects": {}
  },
  "steps": [
    {
      "speaker": {
        "type": "set",
        "id": "example:skier"
      },
      "text": "你好。"
    },
    {
      "text": "这一句继承上一位 Speaker。"
    },
    {
      "speaker": {
        "type": "hide"
      }
    }
  ],
  "end": {
    "text": "接下来要做什么？",
    "exit": {
      "type": "options",
      "options": [
        {
          "text": "继续交谈",
          "icon": "dialogue",
          "target": {
            "type": "dialogue",
            "dialogue": "example:skier/more"
          }
        },
        {
          "text": "返回",
          "target": {
            "type": "return"
          }
        }
      ]
    }
  }
}
```

### 5.3 Step、文字与 Speaker

```text
Step
├─ ContinueStep
└─ EndStep
```

- `ContinueStep` 播放完成后只进入下一个 Step。
- `EndStep` 拥有普通 Step 的全部表现能力，并在完成后执行 Exit。
- `steps` 只保存 ContinueStep；唯一且必填的 EndStep 单独保存在 `end` 字段。
- Speaker 使用外部可复用资源，并与 VisualObject 完全解耦。
- `speaker` 省略表示继承；第一个 Step 省略时名字栏保持隐藏。
- `{"type":"set","id":"<speaker_id>"}` 切换 Speaker。
- `{"type":"hide"}` 隐藏名字栏。

播放阶段为 `PLAYING` 和 `READY`：

- 进入 Step 时同时启动打字机和有限阻塞动画。
- 文字与全部阻塞动画自然完成后进入 READY，但不自动进入下一 Step。
- PLAYING 时推进会立即显示完整文字、结算有限动画并提交预计算的最终状态 S1，本次输入不进入下一 Step。
- READY 时再次推进才进入下一个 Step 或执行 EndStep 的 Exit。
- 纯动画 Step 使用相同规则；循环动画不参与完成判断。

### 5.4 Presentation、Theme 与 VisualObject

Theme 只定义 UI 样式，包括对话框、选项行、字体、颜色、边框、间距、历史抽屉和 controls，不包含背景或 VisualObject。

```text
Presentation
├─ theme：外部引用或内联，必填
├─ background：可选
├─ filter：可选 Scene Filter
├─ dialogue_box：初始位置和尺寸
└─ visual_objects：预声明对象表
```

- Scene Filter 只作用于 Presentation 的 background 和全部 VisualObject，不影响 DialogueBox、Options、历史记录或其他 UI controls，也不影响透明区域后方的 Minecraft 世界。
- 渲染时先将 background 与 VisualObject 合成到独立 Scene RenderTarget，再执行 Filter pass，最后在其上方绘制 UI layer。
- `filter` 省略表示不启用滤镜；首版提供内置静态 `color_adjust`，支持 brightness、contrast、saturation 和 tint。
- Filter 随当前 Dialogue 的 Presentation 初始化和销毁；切换 Dialogue 或 Return 到 root 时重新创建，不继承前一个场景状态。
- 首版 Filter 不参与 PresentationAction 动画，也不允许 Data Pack 提供任意 shader；后续可以增加 blur、vignette、grayscale 等内置类型或扩展为多 pass filter list。
- 所有 VisualObject 必须在 Presentation 中预先声明；Step 不能动态创建未声明对象。
- 暂不显示的对象使用 `visible: false`。
- 每个对象包含稳定 ID、图片差分表、初始差分、位置、缩放、透明度、可见性和场景内层级。
- 每次激活一个 DialogueDefinition 时都按其 Presentation 创建对应场景，不把前一个 Dialogue 的场景状态带入目标 Dialogue。

Step 使用 PresentationAction 描述表现变化：

- Action 可以作为外部 ResourceLocation 预制，也可以在 Step 中内联。
- ActionCall 在调用时指定目标；同一 Step 的调用同时调度并通过 delay 控制开始时间。
- 数值轨道使用相对值；图片差分、visible 等离散属性使用显式设置值。
- 有限 Action 默认阻塞 Step；循环 Action 永不阻塞。
- 多个 Action 在同一 Step 写入同一对象的同一属性属于加载错误。
- 进入 Step 时以 S0 预计算 S1；正常播放按 keyframes 从 S0 到 S1，跳过时直接提交 S1，不能从中间状态再次叠加相对值。
- Action 数据不直接保存 ModernUI View、Animator 或其他运行时对象。

### 5.5 Exit 与 Option

```text
Exit
├─ ReturnExit
└─ OptionsExit

OptionTarget
├─ DialogueTarget
└─ ReturnTarget
```

```json
{"type": "return"}
```

```json
{
  "type": "options",
  "options": [
    {
      "text": "继续",
      "icon": "dialogue",
      "target": {
        "type": "dialogue",
        "dialogue": "example:skier/more"
      }
    }
  ]
}
```

- Option text 必须为非空字符串。
- icon 可省略，默认 `none`；首版支持 `none`、`question`、`exclamation`、`dialogue`。
- OptionsExit 至少包含一个 Option。
- 首版 OptionTarget 只有 `dialogue` 和 `return`，不加入 Option ID、导航栈、脚本动作或其他跳转类型。
- 允许多个 Option 指向同一 Dialogue，也允许 Dialogue 指向自身。
- Exit 和 Option 不修改 ProgressNode。
- Dialogue target 不保存重复条件，而是读取目标 Dialogue 的 `requires`。
- 客户端加载一个 `end.exit` 为 OptionsExit 的 Dialogue 时立即批量查询并缓存结果，不等待播放到 EndStep；点击 Dialogue target 时服务端再次校验。
- 过滤后没有可见 Option 时进入 Return fallback，下一次推进按 ReturnExit 处理。

### 5.6 ProgressNode 与进度仓库

ProgressNode 是玩家进度的布尔节点集合，名称借鉴 Minecraft 插件权限节点，但不是权限系统，也不提供数值或字符串变量。

```text
quest.trader.level_1
dialogue.skier.introduced
```

- 节点由 `[a-z0-9_-]+` 分段并以 `.` 连接。
- 点分段只属于命名，不提供父级继承、通配符或树形语义。
- 在线缓存使用扁平 HashSet，精确查询平均为 O(1)。
- ProgressExpression 支持节点、括号、`!`、`&&`、`||`。
- 运算优先级为 `!`、`&&`、`||`，并使用短路求值。
- 表达式在资源加载时解析并缓存 AST，不在每次查询时重新解析。
- `requires` 省略表示公开；空字符串或非法表达式使当前 Dialogue 加载失败。
- 服务端是判断结果的唯一权威。

进度只为在线玩家加载和编辑：

```text
<world>/data/maimai_dialogue/progress/
└─ <player-uuid>.dat
```

- 登录时异步加载，在线期间缓存；不提供离线玩家命令或 UUID 编辑 API。
- 所有修改经 `PlayerProgressRepository` 串行化，内存立即更新，CompletionStage 在原子保存成功后完成。
- 写入使用同目录临时文件后原子替换。
- 文件损坏时保留原文件，不得当作空进度或被 add/remove 覆盖。
- 进度不可用时，公开 Dialogue 仍可进入，带 `requires` 的 Dialogue 拒绝进入。
- 写入失败时保留 dirty 内存状态，当前调用异常完成，后续修改或退出时重试。
- NBT 首版包含 `SchemaVersion`、`PlayerUUID` 和 `Nodes`。

命令：

```text
/maimai_dialogue open <player> <dialogue_id>
/maimai_dialogue progress add <player> <node>
/maimai_dialogue progress remove <player> <node>
/maimai_dialogue progress list <player>
/maimai_dialogue progress check <player> <node>
```

- `<player>` 只接受在线玩家，默认要求 command permission level 2。
- `check` 存在时返回 1，不存在时返回 0；`list` 按字典序输出。
- 重复 add 返回 ALREADY_PRESENT，删除不存在节点返回 NOT_PRESENT。

公开 Java API：

```text
MaiMaiDialogueApi.get()
├─ dialogues(): DialogueService
│  └─ open(ServerPlayer, ResourceLocation): CompletionStage<DialogueOpenResult>
└─ progress(): PlayerProgressService
   ├─ snapshot(ServerPlayer): CompletionStage<ProgressSnapshot>
   ├─ contains(ServerPlayer, ProgressNode): CompletionStage<Boolean>
   ├─ add(ServerPlayer, ProgressNode): CompletionStage<ProgressChangeResult>
   └─ remove(ServerPlayer, ProgressNode): CompletionStage<ProgressChangeResult>
```

`PlayerProgressRepository` 属于内部实现，不直接作为公共 API 暴露。

### 5.7 仍待确定

- Presentation、Theme、VisualObject 和 PresentationAction 的最终 JSON/Codec；
- Action 支持的具体属性、keyframe 字段和 easing 集合；
- 历史记录的保存范围和展示数据；
- 文字本地化和 Markdown 支持，首阶段明确延期。

## 6. 无 session 网络协议

### 6.1 网络边界

服务端不保存 root、current、Step、UI 或播放进度。request_id 只用于客户端关联响应，不是 session，服务端收到请求后原样回传，不持久化。

网络只负责：

1. 客户端批量查询目标 Dialogue 的可访问状态；
2. 客户端请求进入单个 Dialogue；
3. 命令或 Java API 在客户端尚未显示 Dialogue UI 时打开一个新 root Dialogue。

网络不发送 Dialogue 内容、ProgressNode 集合、客户端关闭通知或任何播放状态。

### 6.2 访问状态与 Payload

```text
DialogueAccessStatus
├─ ALLOWED
├─ DIALOGUE_NOT_FOUND
├─ REQUIREMENTS_NOT_MET
├─ PROGRESS_UNAVAILABLE
└─ INTERNAL_ERROR
```

枚举使用稳定的显式 network ID，不使用 Java ordinal。

首版协议版本为 `1`，包含五个 required play-phase payload：

```text
RequestDialogueC2S
├─ request_id: long
└─ dialogue_id: ResourceLocation

DialogueRequestResultS2C
├─ request_id: long
├─ dialogue_id: ResourceLocation
└─ status: DialogueAccessStatus

QueryDialogueAccessC2S
├─ request_id: long
└─ dialogue_ids: List<ResourceLocation>

DialogueAccessResultS2C
├─ request_id: long
└─ entries: List<DialogueAccessEntry(dialogue_id, status)>

OpenDialogueS2C
└─ dialogue_id: ResourceLocation
```

- 批量查询由客户端去重，最多包含 256 个 ID；Return target 不参与查询。
- 批量结果返回每个目标的状态，以区分条件不满足、服务端缺少文件和内部错误。
- 客户端 request_id 是当前连接内递增的 long；关闭或切换 Dialogue 后，旧 generation 的响应直接忽略。
- 单人 integrated server 也执行完整序列化和服务端校验。

### 6.3 服务端处理

所有入口复用同一个访问判断：

```text
evaluateAccess(player, dialogue_id)
→ 服务端本地没有 Dialogue：DIALOGUE_NOT_FOUND
→ requires 缺省：ALLOWED
→ ProgressSnapshot 不可用：PROGRESS_UNAVAILABLE
→ 表达式为 true：ALLOWED
→ 表达式为 false：REQUIREMENTS_NOT_MET
→ 非预期异常：INTERNAL_ERROR
```

- 客户端可以请求任意 dialogue_id；服务端安全边界就是“本地存在且 requires 满足”。
- 服务端不验证 Option 来源，也不保存玩家当前所在 Dialogue。
- 批量查询只用于显示过滤；点击后必须通过 RequestDialogueC2S 再次校验。
- `DialogueService.open()` 先执行相同检查，成功后发送 OpenDialogueS2C。
- 客户端发起请求成功后，由 DialogueRequestResultS2C 授权客户端本地打开或切换；服务端不再发送正文或导航状态。
- 客户端已有 Dialogue UI 时直接忽略新的 OpenDialogueS2C，并记录 debug 日志；服务端不为此保存状态或等待 ACK。

## 7. 客户端状态机

### 7.1 Controller 与运行状态

顶层 ControllerState：

```text
IDLE
├─ 客户端请求 root → ROOT_REQUEST_PENDING
├─ 收到 OpenDialogueS2C → OPEN
ROOT_REQUEST_PENDING
├─ ALLOWED 且本地文件存在 → OPEN
├─ 收到 OpenDialogueS2C → 取消 pending 并 OPEN
└─ 拒绝、缺失或错误 → IDLE
OPEN
├─ 进入目标、返回 root → OPEN（新 generation）
├─ 收到 OpenDialogueS2C → 保持 OPEN，忽略并记录日志
└─ root Return、Esc、死亡、断线或 Screen 被替换 → IDLE
```

ConversationRuntime 至少保存：

```text
generation
root_dialogue_id
current_dialogue_id
DialogueDefinition
StepCursor
current_speaker_id?
SceneRuntime
PlaybackPhase
OptionPhase
```

- StepCursor 使用 `Continue(index)` 或 `End`，不使用越界整数表示 EndStep。
- generation 在每次打开、切换、返回或关闭时递增；异步结果必须同时匹配 request_id、dialogue_id 和 generation。
- 客户端一次只保留一个 Dialogue Screen。

### 7.2 PlaybackPhase 与推进

```text
PlaybackPhase
├─ PLAYING
└─ READY
```

输入规则：

| 状态 | 空白左键或推进 KeyMapping |
|---|---|
| PLAYING | 结算当前 Step，进入 READY |
| READY + ContinueStep | 进入下一 Step |
| READY + ReturnExit | 返回 root 或关闭 |
| READY + Options 查询中 | 无操作 |
| READY + 可见 Options | 无操作，只允许点击 Option |
| READY + 请求目标中 | 无操作 |
| READY + Return fallback | 执行 Return |

历史、滚动、展开和 Option 等 controls 必须消费自己的点击，不能冒泡为推进输入。

### 7.3 OptionPhase

```text
OptionPhase
├─ NOT_APPLICABLE
├─ QUERYING(request_id, generation)
├─ READY(visible_options)
├─ REQUESTING_TARGET(request_id, generation, target, previous_options)
└─ FALLBACK_RETURN
```

- 客户端成功加载 DialogueDefinition 后立即检查其唯一 EndStep；如果 `end.exit` 是 OptionsExit，马上发起批量查询，不等待 ContinueStep 播放或进入 EndStep。
- 查询结果按当前 Dialogue generation 缓存在 OptionPhase 中，可以在前面的 ContinueStep 播放期间到达。
- 播放到 EndStep 且 PlaybackPhase.READY 后才显示和启用缓存中的选项；如果此时查询尚未返回，则继续显示加载状态。
- 到达 EndStep 时不重复查询。缓存结果可能因玩家进度在播放期间变化而过期，因此点击 Dialogue target 时仍必须再次校验。
- 查询仍未返回时显示加载状态，空白推进无效。
- ALLOWED 保留；REQUIREMENTS_NOT_MET 隐藏；DIALOGUE_NOT_FOUND 隐藏并报告内容包错误。
- PROGRESS_UNAVAILABLE 或 INTERNAL_ERROR 显示错误，不伪装成条件不满足。
- 点击 Return target 完全本地处理。
- 点击 Dialogue target 后禁用选项并发送 RequestDialogueC2S。
- 点击后二次校验失败时留在当前页；目标缺失则移除坏 Option；内部错误允许重试。
- 服务端允许但客户端本地缺失目标时，客户端报告 Data Pack 开发错误并恢复当前 Option 界面。该路径只用于发现双端内容包不一致，不作为正常交互状态设计。

### 7.4 导航与界面生命周期

- 子 Dialogue Return 时导航到 root Dialogue 的唯一入口；状态机不提供恢复位置、跳到 EndStep 或其他继续策略。
- 返回后呈现何种内容属于 Data Pack 设计，由 ProgressNode、`requires` 和 Dialogue 结构表达。
- root Return 直接关闭 Dialogue Screen。
- 切换目标时按目标 Presentation 重新初始化 scene、speaker 和 Step。
- 客户端没有 Dialogue Screen 时，OpenDialogueS2C 将目标设为新的 root/current 并打开界面。
- 客户端已有 Dialogue Screen 时，直接忽略 OpenDialogueS2C 并记录 debug 日志，不替换、不排队，也不修改 root/current。
- 当前是其他 Minecraft Screen 时，Dialogue Screen 可以按正常打开流程替换它。
- Dialogue Screen 不暂停世界、不绘制 ModernUI 默认背景、不启用背景模糊。
- 关闭界面时只清理客户端状态，不发送网络消息。

### 7.5 线程规则

```text
NeoForge payload handler
→ client main thread
→ DialogueClientController 更新逻辑状态
→ 发布不可变 DialogueViewState
→ ModernUI UI thread 更新 View 与 Animator
```

反向 UI 事件通过 DialogueIntent 投递回 client main thread：

- `Minecraft.setScreen()` 与 Screen 生命周期操作只在 client main thread 执行。
- Fragment、View 和 Animator 只在 ModernUI UI thread 修改。
- Fragment 不直接修改 Controller，只发送 intent。
- Screen removed 只清理与自身 generation 匹配的 runtime，避免旧 Screen 清除新状态。
- NBT I/O 在后台执行，完成后回到服务端主线程更新缓存或回复 payload。

## 8. 下一步

首个最小闭环按以下顺序实现：

1. 实现 DialogueDefinition、ProgressExpression、Step、Exit 和 Option 的基础 Codec 与双端资源快照；
2. 实现 PlayerProgressRepository、在线玩家生命周期、NBT 原子保存、命令和公开 Java API；
3. 注册五个 payload，并实现无 session 的服务端访问判断；
4. 实现 DialogueClientController、状态机和最小 ModernUI Screen；
5. 使用简单 Presentation 完成 root → Step → Options → target/return 的端到端测试；
6. 再细化 PresentationAction、Theme、VisualObject、资源校验与错误展示；
7. 最后处理历史记录、本地化和 Markdown。

## 9. 当前实现状态（2026-07-29）

首个最小闭环的基础实现已经落地：

- 已实现 ProgressNode、ProgressExpression 解析与短路求值；
- 已实现 DialogueDefinition、Step、Exit、Option 的基础 Codec；
- 已实现客户端与服务端 Dialogue 资源快照及独立 reload；
- 已实现在线 PlayerProgressRepository、异步 NBT 加载、串行原子保存、失败保留 dirty 状态与退出重试；
- 已实现进度命令和公开 Java API；
- 已注册五个无 session payload，并统一复用服务端访问判断；
- 已实现最小 ClientDialogueController 与 ModernUI Screen；
- OptionsExit 的目标访问状态在 Dialogue 激活时预取，点击目标时再次向服务端校验；
- 已实现 root/current 识别、Return 导航、客户端文件缺失开发错误，以及已有 Dialogue UI 时忽略 OpenDialogueS2C；
- 已加入 `maimai_dialogue:demo/root`、`public`、`locked` 双端示例资源和基础 JUnit 测试。

当前 ModernUI Screen 是可连通网络与导航逻辑的最小版本。打字机 PlaybackPhase、可重绑推进 KeyMapping、完整 Presentation/Theme/VisualObject/Action、历史记录、本地化和 Markdown 仍按前述顺序后续实现。
