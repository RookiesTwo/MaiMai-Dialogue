# MaiMai Dialogue 产品目标与设计检查点

> 状态：实现中
> 更新日期：2026-07-30
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
- 默认 Theme 使用半透明黑色背景和白色描边；整体为近直角外观，默认圆角半径保持很小，并允许 Theme 将其设为 0。
- 对话框纵向分为三个区域，各区域之间使用清晰可见的横向分割线：
  1. Header：角色名称位于左侧，“展开所有选项”按钮位于同栏并默认隐藏；
  2. Content：显示当前 Dialogue 文字；
  3. Options：显示当前选项分支。
- 没有可见 Options 时第三栏不占据空白空间，也不绘制对应分割线。
- 每个选项占一行，可带问号、感叹号、对话等类型图标。
- Option 内容按“图标、文字”的顺序从左向右排列，文字默认左对齐；文本自身仍使用自动 BiDi 方向，不强制破坏 RTL 内容。
- 每个 Option 元素必须有独立的水平与垂直内边距，文字和图标不能紧贴边缘。
- 鼠标悬浮 Option 时，默认同时加深其半透明背景并提高描边亮度；Theme 可以分别覆盖 hover 背景和描边。
- 选项过多时支持滚动。
- 只有当选项列表实际溢出时才显示“展开所有选项”按钮。
- 展开后，对话框保持底部锚定并向上扩展；超出安全区域后仍使用滚动。

### 3.4 历史记录

- 历史记录入口位于整个界面的右侧，不放在对话框头部。
- 历史记录以右侧抽屉形式打开。
- 历史只属于当前一次 Dialogue UI session；首次打开任意 Dialogue 时创建，切换 root/子 Dialogue 和 Return 时继承，关闭 MOD Dialogue UI 后立即清空。
- 每次进入一个有正文的 Step 时记录当时解析出的 Speaker 显示名称和完整正文；选择 Option 且本地或服务端确认分支后记录 Option 文本。
- 历史仅保存在客户端内存，不写入 NBT、不发送 payload，也不保留旧 View、Fragment、DialogueDefinition 或 Scene 引用。
- 抽屉内容可滚动，每条记录之间绘制分割线；Dialogue 正文按 Markdown 渲染，Option 记录保持普通文本。

### 3.5 主题与客户端设置

- MOD 提供内置默认主题。
- Data Pack 可以定义或选择主题。
- 不向普通玩家提供颜色、边框、间距、字体等主题编辑功能。
- 不提供 reduced motion 或动画倍率覆盖。
- 对话界面始终不暂停游戏世界。

## 4. 交互目标

对话播放与选项选择是两个不同的交互阶段：

- 播放句子时，鼠标左键点击非按钮区域或按空格键，完成或推进内容；空格只在 Dialogue UI 打开时消费，不注册全局 KeyMapping。
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
- 首阶段仍使用普通 String；客户端仅把 Dialogue 正文 String 解释为 Markdown，Speaker、Option 和其他 controls 保持普通文本。本地化模型暂不实现。
- JSON 语法、Codec 或 ProgressExpression 解析失败时，只跳过当前 Dialogue 并记录带 ResourceLocation 的错误。
- 客户端资源 reload 完成后统一校验 Dialogue 对 Theme、Speaker、PresentationAction、目标 Dialogue 和 Scene 图片的引用；错误用于内容包开发，运行时存在性检查仍作为两端文件不一致时的最后防线。

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
- 首版 Speaker 资源位于客户端资源包的 `assets/<namespace>/speakers/<path>.json`，资源路径对应 `<namespace>:<path>`。
- 首版 SpeakerDefinition 只包含非空 `name` String；头像、名称本地化和其他元数据后续扩展。
- Dialogue 引用的 Speaker 在客户端缺失时视为内容包开发错误，日志与客户端消息报告错误，名字栏临时回退显示 Speaker ID。
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

- Theme 资源位于客户端资源包的 `assets/<namespace>/dialogue_themes/<path>.json`，资源 ID 为 `<namespace>:<path>`。
- `Presentation.theme` 引用外部 Theme；引用缺失时报告内容包开发错误并临时回退内置 `ThemeDefinition.DEFAULT`。
- ThemeDefinition 作为单一 Theme 根统一驱动 DialogueBox、正文、Option、滚动区域、展开按钮和历史抽屉；`box`、`text`、`option`、`spacing`、`controls` 共同覆盖这些控件，其中 controls 统一提供 Option icon 与 Options/历史 scrollbar 样式。

```text
Presentation
├─ theme：外部引用或内联，必填
├─ background：可选
├─ filter：可选 Scene Filter
├─ dialogue_box：初始位置和尺寸
└─ visual_objects：预声明对象表
```

- Scene Filter 只作用于 Presentation 的 background 和全部 VisualObject，不影响 DialogueBox、Options、历史记录或其他 UI controls，也不影响透明区域后方的 Minecraft 世界。
- 静态单 pass 色彩滤镜直接应用于 Scene layer 内的 background 与 VisualObject ImageView；DialogueBox 位于该 layer 之外，因此不受影响。需要合成结果的多 pass 滤镜再使用 ModernUI 实际支持的离屏渲染路径。
- `filter` 省略表示不启用滤镜；首版提供内置静态 `color_adjust`，支持 brightness、contrast、saturation 和 tint。
- `background` 支持预声明 variants、initial_variant、cover/contain/stretch fit 与 opacity；Action 只负责切换已声明贴图，不动态发明差分。
- `dialogue_box` 首版支持归一化 x/y、width、max_height 与九宫格 anchor。
- `visual_objects` 首版静态状态支持 variants、initial_variant、归一化 x/y、九宫格 anchor、scale、sampling、opacity、visible 与 z_index。`sampling` 默认为 `linear`，像素风贴图可设为 `nearest`，且差分切换前后的图层使用相同采样方式。
- Filter 随当前 Dialogue 的 Presentation 初始化和销毁；切换 Dialogue 或 Return 到 root 时重新创建，不继承前一个场景状态。
- 首版 Filter 不参与 PresentationAction 动画，也不允许 Data Pack 提供任意 shader；后续可以增加 blur、vignette、grayscale 等内置类型或扩展为多 pass filter list。
- 内置 `crt` Filter 使用 Arc3D GPU RenderTarget 合成 Scene：执行分条曲率采样、扫描线、RGB shadow mask、轻微色差、暗角、噪点和闪烁，并以额外 pass 叠加 Bloom。效果保留 alpha，只处理 Scene，不影响 Dialogue UI 或透明区域后方的 Minecraft 世界。
- 所有 VisualObject 必须在 Presentation 中预先声明；Step 不能动态创建未声明对象。
- 暂不显示的对象使用 `visible: false`。
- 每个对象包含稳定 ID、图片差分表、初始差分、位置、缩放、透明度、可见性和场景内层级。
- 每次激活一个 DialogueDefinition 时都按其 Presentation 创建对应场景，不把前一个 Dialogue 的场景状态带入目标 Dialogue。

Step 使用 PresentationAction 描述表现变化：

- Action 可以作为外部 ResourceLocation 预制，也可以在 Step 中内联。
- 外部 Action 位于客户端资源包的 `assets/<namespace>/presentation_actions/<path>.json`。
- ActionCall 在调用时指定目标；同一 Step 的调用同时调度并通过 delay 控制开始时间。
- 数值轨道使用相对值；图片差分、visible 等离散属性使用显式设置值。
- 首个运行版本支持有限 Action 的 `duration_ms`、`delay_ms`、`easing`、`blocking`，以及 x、y、scale、opacity 数值 keyframes和 variant、visible 定时设置。
- keyframe 的 `at` 为 Action 内 0 到 1 的归一化时间；数值 value 是相对进入 Step 时 S0 的偏移量。
- 外部与内联 Action 统一使用显式 `type: reference/inline`，不根据 JSON 值类型猜测。
- 有限 Action 默认阻塞 Step；循环 Action 永不阻塞。
- 多个 Action 在同一 Step 写入同一对象的同一属性属于加载错误。
- 进入 Step 时以 S0 预计算 S1；正常播放按 keyframes 从 S0 到 S1，跳过时直接提交 S1，不能从中间状态再次叠加相对值。
- Action 数据不直接保存 ModernUI View、Animator 或其他运行时对象。
- Scene/Dialogue 转场作为 PresentationAction 接入同一 PlaybackPhase、跳过和 S1 结算管线，不建立独立 Transition 状态机；默认转场为 fade，首版保持最小可维护配置。

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

### 5.7 延后项

- 循环 Action 的持久运行、取消和跨 Step 组合规则；首个运行版本只执行有限 Action；
- 文字本地化模型；Markdown 已仅在 Dialogue 正文启用。

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

## 9. 当前实现状态（2026-07-30）

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
- 已加入 `maimai_dialogue:demo/root`、`public`、`locked`、`theme`、`crt` 双端示例资源和基础 JUnit 测试。
- 已实现客户端 SpeakerDefinition、资源快照与 reload；默认 demo 使用 `maimai_dialogue:demo/guide`。
- 默认 Dialogue UI 已改为稳定的 Header、Content、Options 三栏 View tree，并实现近直角半透明黑底、白色描边、分割线、Option padding、左对齐和 hover 状态。
- 已实现 Presentation、Background、DialogueBoxLayout、VisualObject 与 SceneFilter Codec。
- 已实现按 Dialogue generation 创建和销毁的静态 Scene layer，支持背景、VisualObject 初始差分、位置、缩放、透明度、可见性和 z_index。
- 已实现 `color_adjust`：通过 ModernUI ImageView 的 ColorFilter 对 background 与 VisualObject 执行同一 ColorMatrix，不处理 DialogueBox 和其他 UI。当前 ModernUI UI Canvas 不支持通过 `Canvas.makeSurface` 创建子 Surface，因此不再使用该无效 RenderTarget 路径。
- 已实现 `crt` 配置 Codec 与 Arc3D Scene composite renderer：曲率、扫描线、RGB mask、色差、暗角、噪点、闪烁及 Bloom multi-pass 均只作用于 Scene；另有 `maimai_dialogue:demo/crt` 示例。
- `maimai_dialogue:demo/root` 已加入 Minecraft 内置 panorama 背景、emerald VisualObject 和可见的 `color_adjust` 示例。进入世界后可执行 `/maimai_dialogue open @s maimai_dialogue:demo/root` 查看。
- 已实现 ThemeDefinition 外部资源、snapshot/reload 和内置 `maimai_dialogue:default`；默认 Dialogue UI 的 box、text、option 与 spacing 已由 Theme 驱动。
- 已实现有限 PresentationAction、外部 Action 资源、内联 ActionCall、相对数值 keyframes、variant/visible 离散变更、属性冲突检查与 S1 预计算。
- 已接入 PlaybackPhase：阻塞 Action 播放中第一次推进只跳过并提交 S1，自然完成后进入 READY；EndStep Options 等到 READY 才显示。
- demo 的 emerald 使用外部入场 Action；进入 EndStep 时使用内联 Action 横移并切换为 diamond。
- 已接入 ModernUI-Markflow，仅 Dialogue 正文解析 Markdown；正文使用打字机动画，并与阻塞 Scene Action 共同决定 PlaybackPhase，第一次推进同时结算文字和 Scene。
- 已实现 Dialogue UI 内固定空格推进，不注册全局 KeyMapping。
- Options 已使用滚动视口；仅在内容实际溢出时显示 Expand，展开后仍保留滚动，折叠/展开高度由统一 Theme spacing 控制。
- 已实现纯客户端 session 历史：记录正文与已确认 Option，Dialogue 切换继承，界面关闭清空；右侧入口打开可滚动、带分割线的历史抽屉。
- 已加入明显不同的 `maimai_dialogue:demo/parchment` 浅色 Theme，并提供 `maimai_dialogue:demo/theme` 双端示例 Dialogue。
- Background 已资源化为预声明 variants，并可由同一 PresentationAction 管线切换；root demo 在 EndStep 切换 panorama。
- Dialogue 默认 fade-in 已作为普通 `dialogue.opacity` Action 注入，与其他 Action 共用 PlaybackPhase、skip 和 S1 结算，不存在独立 Transition 状态机。
- 客户端 reload 末尾统一执行全局引用校验，集中报告缺失 Theme、Speaker、PresentationAction、目标 Dialogue、Scene 图片，以及非法 Action target/variant。

当前确认的 11 项实现顺序已完成。仍明确延后的内容包括循环/跨 Step Action、Background/DialogueBox/Filter 通用动画、复杂 easing、自定义字体、本地化、动态 VisualObject 生命周期和更复杂的 Scene 转场组合。

### 10.1 11 项客户端手测验收

使用 `/maimai_dialogue open @s maimai_dialogue:demo/root` 打开示例，并按顺序检查：

1. 首次打开使用默认 fade，Scene 与 DialogueBox 同步渐入；渐入仍处于 PlaybackPhase，第一次推进可以直接结算。
2. 正文逐字显示；播放中第一次按 Space 只显示完整当前句并结算当前 Scene Action，第二次才进入下一 Step。
3. `demo/theme` 的标题、粗体、斜体和 inline code 只在 Dialogue 正文按 Markdown 渲染；Speaker 与 Option 不解析 Markdown。
4. root 的 Options 超出折叠高度时出现 Expand；折叠与展开状态均可滚动，Collapse 能恢复折叠高度。
5. 默认 Theme 为近直角半透明黑底、白色描边和明显分割线；Option 左对齐、有 padding，hover 时背景或描边高亮。
6. Option icon、Expand/History/Close controls，以及 Options 与历史 scrollbar 均随当前 Theme 改变；`demo/theme` 的 parchment 视觉应明显不同。
7. 打开 History 后可以滚动，记录间有分割线；正文和已确认 Option 均出现。进入子 Dialogue、Return 到 root 时保留记录；关闭 MOD UI 后重新打开任意 Dialogue，旧记录清空。
8. root 从 ContinueStep 推进到 EndStep 时，Background 从 panorama default variant 切换为 alternate；只切换预声明贴图。
9. 进入 root、子 Dialogue、Return root 时均通过同一 `dialogue.opacity` Action 执行默认 fade；Space 跳过与普通 Scene Action 使用同一结算行为。
10. `demo/crt` 中背景和 redstone VisualObject 出现曲率、扫描线、RGB mask、色差、暗角、轻微噪点/闪烁和 Bloom；DialogueBox、Options 与历史保持清晰，透明 Scene 区域不覆盖 Minecraft 世界。
11. 执行资源 reload 后日志出现 `Validated all client Dialogue resource references successfully.`，且不再反复出现 `Could not create the Dialogue Scene RenderTarget`；缺失引用仅作为内容包开发错误集中报告。
