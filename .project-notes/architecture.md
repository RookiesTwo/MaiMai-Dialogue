# MaiMai Dialogue 开发架构（内部）

模型描述内容，session 和 scene runtime 计算状态，controller 执行 effect，UI 展示状态。logical server 独立校验权限、执行 Option command 并保存玩家数据。客户端资源不能替代服务端权限判断。

## 包职责

以下路径均相对于 `top.rookiestwo.maimai_dialogue`。

| 包 | 维护内容 |
| --- | --- |
| `api`、`api.progress` | 对其他 MOD 的稳定入口、结果和服务接口 |
| `dialogue` | DialogueDefinition、Step、End、Text 与 Codec |
| `dialogue.branch` | Option、OptionIcon、Target、Exit 及具体分支 |
| `speaker` | SpeakerDefinition 与 Set/Hide 操作 |
| `presentation` | Presentation、PresentationDefinition、DialogueBoxLayout |
| `presentation.scene`、`.visual`、`.filter` | Scene/Background、VisualObject/Asset、滤镜模型 |
| `presentation.action`、`theme` | 动作、轨道、关键帧、缓动；Theme 模型与 Codec |
| `content`、`content.resolve` | 加载、registry、repository；资源引用及完整表现解析 |
| `client.bootstrap`、`client.controller` | 服务组装；网络结果适配、effect 执行、屏幕连接与操作接口 |
| `client.session` | 导航、请求关联、正文缓存、History、步骤完成规则 |
| `client.scene` | 纯场景状态、SceneRuntime、ScenePlayback，无 View |
| `client.ui.screen` | Fragment、RootLayout、确认框、快进调度、按键与跳过控件 |
| `client.ui.box`、`.text` | 对话框、选项滚动；Markdown、打字、正文 viewport |
| `client.ui.history`、`.scene` | History；场景 View、图片、滤镜与切换生命周期 |
| `client.ui.style`、`.animation` | Typography、滚动条、按钮样式；Modern UI PlaybackTimeline |
| `client.config`、`.config.ui` | 配置定义与快照；页面、字体、按键、数值编辑组件 |
| `client.resource`、`server.resource` | 双端资源加载、校验和发布 |
| `server.dialogue`、`server.option` | Dialogue 打开、访问校验；Option command |
| `server.pending`、`.pending.storage` | 必须完成对话的玩家生命周期；压缩 NBT |
| `progress` | 公共 ProgressNode、表达式及既有异常 |
| `server.progress`、`.progress.storage` | 进度服务、在线状态、事件；压缩 NBT |
| `network`、`.payload`、`.client`、`.server` | 协议注册及状态类型、payload、双端 handler |
| `command`、`internal.bootstrap` | 命令入口；公共服务组装 |

## 依赖约束

```text
UI -> DialogueUiActions -> controller -> session -> models / resolvers / scene runtime
controller -> DialogueScreenHandle <- UI
bootstrap -> controller + UI（只在组装处连接具体类型）
API / command / network.server -> server services -> repository / storage
reload adapters -> content / resolvers / validators
```

- 模型不依赖 UI、controller 或服务端实现；session、scene runtime 不依赖 Modern UI、Minecraft client、网络发送或静态服务入口。ResourceLocation、Codec 等值类型仍可使用。
- controller 不引用具体 Fragment。`DialogueScreenHost` 创建 handle，`DialogueScreenHandle` 发布状态，UI 用 `DialogueUiActions` 提交输入。
- History 只接收状态 Supplier 和关闭回调，不扫描或强转其他 Fragment。共享按钮样式从 `ui.style` 获取。
- 只公开跨包组件入口；确认框协调、按键状态、数值编辑、场景内部 View 等实现保持包内可见。
- `ArchitectureBoundaryTest` 用源码 import 检查防止依赖倒流。这是轻量检查，不替代代码审查，不覆盖反射和所有全限定名引用。
- 不引入 DI 框架、事件总线或多模块构建；原 NeoForge 订阅侧别、网络注册不变。

## 主要调用链

### 打开 Dialogue

1. 服务端 API/命令进入 `DefaultDialogueService`，通过 `DialogueAccessService` 校验。
2. `must_complete` 先由 `PendingDialogueService.prepareOpen` 持久化待完成记录，保存成功才激活 token 和发送打开 payload。
3. `network.client.ClientDialoguePayloadHandlers` 在 client thread 调用 controller。
4. controller 查询本地 definition，创建 `DialogueSession` 和 screen handle，执行 `start()` 的 effect，再显示屏幕。
5. session 解析完整表现、进入首步、预查询选项权限；Fragment 将 screen state 分发给文本和场景 View。

客户端主动打开则先经过 `requestRoot` → `RequestDialogueC2S` → 服务端校验 → `DialogueRequestResultS2C`，获准后才进入本地打开流程。

### 选择 Option

1. `DialogueOptionsView` → `DialogueUiActions.selectOption` → session。
2. 目标导航和 Option command 共用互斥 `PendingAction`；选项权限预查询独立，仍可并行。
3. command payload 只携带 source Dialogue、Option index 和 requestId。`OptionCommandService` 从服务端 Data Pack 获取 command，重新校验 source/target，以玩家身份按顺序执行；首个失败停止后续 command。
4. session 仅接受当前 pending 身份匹配的结果。失败保留位置，成功才执行导航和原有 History 记录流程。
5. Return 回到固定 root，已经位于 root 时关闭；Close 关闭；DialogueTarget 切换目标，不建立返回栈。

### 播放、推进和跳过

`enterCurrentStep` 解析缓存正文、应用 Speaker、准备动作和播放状态。首次进入的默认淡入在这里消费，`currentStepActions()` 只读取。

`StepPlaybackState` 分别记录文本和场景完成，两者都完成才推导为 READY。Fragment 回传 generation 与 playbackToken，session 先校验再修改状态。普通推进、完成当前播放和跳到 End 各自沿用原规则；`FastForwardPlayback` 保留原来的自动推进条件。

`DialogueTextCache` 按 Dialogue 和 Step/End 缓存随机正文，跨内部 Dialogue 切换保留，整个 session 销毁时丢弃。History 仍由 session 按进入步骤和选择选项的原时机写入。

根 Dialogue 到达 End 且 READY、持有 completionToken 时，session 至多产生一次 `CompleteRequiredDialogue` effect；服务端校验 token 并成功清除存储后才报告成功。

### 关闭和重建 View

`DialogueConfirmations` 协调确认框；确认退出 → controller → handle.close。`onDestroyView` 释放图片、文本播放器、临时输入和 View 引用，但不销毁 session。History 返回后的 View 可用当前状态重建。

Fragment 的 `onDestroy` 将自身 handle 投递回 client thread。controller 只清理与当前 handle 相同的连接，避免旧屏幕销毁通知清掉新连接。

### 断线恢复

`PendingDialogueEvents` 登录时异步读取待完成记录，转回 server thread 后交给 `DefaultDialogueService.restorePending`。重新激活产生新 token。退出时卸载在线实例；旧加载/保存回调保留原有实例身份检查，不能写回新的在线状态。Progress 由独立的 `ProgressPlayerEvents` 和 service 处理。

## 标识和失效条件

| 标识 | 含义与检查位置 | 失效条件 |
| --- | --- | --- |
| View/handle 身份 | UI 队列检查 View；controller 检查 screen | View 重建或屏幕连接更换 |
| generation | 当前 ActiveDialogue 的本地代次；pending 和播放完成校验 | session 内切换或重新进入 Dialogue |
| requestId | 根请求由 controller 计数；session 请求由本地计数器分配 | pending 被消费、清空或替换；目标/Option 身份也要匹配 |
| playbackToken | SceneRuntime 每次准备播放生成；与 generation 一起校验 | 新 Step/End 播放替换旧播放 |
| completionToken | 服务端激活必须完成 Dialogue 时产生的 UUID | 消费完成请求或卸载在线状态；恢复时重新生成 |

这些标识不能互相替代。现有协议没有携带 session generation；requestId 在新 session 中重新计数，跨 session 的同 ID、同目标旧回包存在碰撞边界。本次保留该已有行为，后续修复应独立评估和验证，不能宣称当前协议绝对隔离所有旧回包。

## 线程、资源和持久化所有权

- controller 的修改操作在 Minecraft client thread。`DialogueUiDispatch.toClient` 保留 Minecraft.execute 语义；`toView` 仍用 View.post，在 UI 队列执行原有 View 身份检查，不新增同步等待或改变 effect 顺序。
- session 和 SceneRuntime 只同步计算。`PlaybackPhase` 属于业务状态，`PlaybackTimeline` 属于 Modern UI 计时。
- `DialogueSceneView` 创建图片 View；`SceneTransition` 持有当前/离场场景，在原切换完成点释放离场图片；`SceneContentView` 持有对象绑定；`SceneImageRenderer` 管理图片层。清屏和销毁 View 释放余下图片。渲染顺序、透明度公式、采样、混合和布局参数不变。
- `DialoguePresentationResolver` 按 Presentation 引用、Theme/default、Scene、VisualAsset 顺序解析；validator 另行校验图片、Speaker 和 Action。
- reload 后台加载，通过 barrier 后在 game executor 校验并一次替换 registry。即使有校验错误，仍按原策略发布可加载资源。读取继续查询当前 repository，不把整个 session 改成固定资源快照。
- `SessionMessage` 保存原翻译键和参数，在 `DialogueBoxView` 翻译。开发诊断仍由 controller 边界输出原日志和聊天提示。
- Progress 和 Pending 各自拥有保存队列、在线状态及 outstanding writes。IO 在原 executor 执行，完成后切回 server thread；不合并队列，不提前发送“保存成功”。
- `PendingPlayerState` 管理预留、激活、清除中状态。清除失败保留待完成记录，沿用重登恢复流程，不补发已消费的 token。
- NBT 路径、SchemaVersion、UUID 与字段名不变。缺失文件和损坏文件必须区分。两种 store 的包内 writer 用于失败测试，默认仍调用 `IOUtilities.writeNbtCompressed`。

## 修改入口

| 功能 | 首先阅读 | 关键回归 |
| --- | --- | --- |
| JSON/分支 | 对应模型 Codec、DefinitionTypes | Codec、资源、默认值 |
| 导航/End/正文 | DialogueSession、DialogueTextCache | 导航、缓存、History、完成上报 |
| 播放/跳过 | StepPlaybackState、SceneRuntime、FastForwardPlayback | 旧回调、文本/场景完成、End |
| 资源引用 | DialoguePresentationResolver、ClientResourceValidator | 顺序、默认 Theme、缺失资源 |
| 屏幕/输入 | DialogueFragment、DialogueInputHandler、DialogueConfirmations | 失焦、长按、History、反复开关 |
| 图片/动画 | ui.scene、ui.animation；纯逻辑在 client.scene | 采样、透明度、释放、视觉残留 |
| 配置 | ClientPreferences、ClientConfig、config.ui | 默认/边界、步长、冲突、保存 |
| 权限/command | DialogueAccessService、OptionCommandService | 重校验、首个失败停止、失败不导航 |
| 必须完成/重登 | PendingDialogueService、PendingPlayerState | 错误/重复 token、写入失败、恢复 |
| 进度/NBT | DefaultPlayerProgressService、各自 store | 往返、错误传播、损坏不覆盖 |

## 回归与兼容规则

- 公开 `api`、`api.progress`、`progress.ProgressNode` 和现有公开异常保持路径及签名；内部迁移不加旧包桥接。
- 资源 ID/目录、JSON 默认值、命令、配置键、payload ID/编码顺序/版本、NBT 路径/格式属于兼容边界。
- 测试以当前生产行为和资源为基准；过时断言应修订并记录依据，不能为了测试变绿修改产品行为。
- 构建、测试、Client/Server 启动均通过 IDEA MCP。本地 `Tests` 配置执行完整测试；当前 `Build` 不替代测试。测试源码及该运行配置仅留本地，不纳入版本控制。
- GUI 清单：debug/showcase、权限/Return/Close/command、文本/快进/长按跳过/确认框/History、背景/对象/滤镜/采样、配置保存、reload、进度增删、退出重登和必须完成恢复。使用隔离测试世界。
- 本次证据和未验证项见 [重构验证记录](refactor-verification.md)。关键逻辑用简短中文 `//` 解释约束，不为 getter 或纯委托重复注释。
