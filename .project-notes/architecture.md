# MaiMai Dialogue 开发架构（内部）

MaiMai Dialogue 将内容定义、运行时规则和平台适配分开。JSON 与 Codec 只描述内容，Dialogue session 和 Scene runtime 负责纯状态变化，NeoForge、Network、Modern UI 与 NBT 位于边界层。

## 核心流程

```text
JSON resources -> Content snapshot -> Dialogue access/session -> Screen state
Player progress -------------------> Server access/option command check
Dialogue session -> Scene runtime -> Client views
```

logical server 负责 Dialogue 访问权限、Option command 和玩家进度，client 只负责内容展示、输入和播放。客户端的本地资源不能替代服务端权限判断，也不能提供待执行的 command 原文。

## 模块边界

- `api`：对其他 MOD 保持稳定的入口和结果类型。
- `dialogue`、`presentation`、`theme`、`speaker`：数据模型与 Codec。
- `content`：通用资源加载、不可变 registry 和 snapshot 发布。
- `client.session`：Dialogue 导航与播放状态。
- `client`、`client.scene`：Modern UI 视图与场景播放组件。
- `server`：访问检查和服务端用例。
- `progress`、`server.progress.storage`：在线进度状态与持久化。
- `network`、`command`：NeoForge 边界适配。

## 依赖规则

1. UI 只渲染不可变 screen state，并把用户操作交给 controller。
2. session 不依赖 Modern UI、PacketDistributor 或 Minecraft client。
3. content 在完整加载和交叉验证后一次性发布，读取方不会看到半更新状态。
4. service 通过构造参数接收 repository/store；静态入口只保留在事件和 API adapter。
5. JSON 字段、资源目录和 network payload 属于兼容性边界，内部重构不得修改。
6. command Option 仍采用无 session 协议：服务端按 source Dialogue 与 Option index 解析 Data Pack，先校验 source/target 再顺序执行 command，不信任客户端 command 原文。

## 注释规则

重要方法和不直观的关键逻辑块使用一句简短中文 `//` 注释，说明下面的代码在做什么。简单 getter、委托和显而易见的数据转换无需注释。
