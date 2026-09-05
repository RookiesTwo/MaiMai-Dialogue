# 可维护性重构验证记录

日期：2026-09-05。状态：源码重构和自动化检查完成；游戏交互验收未完成，不能据此宣布无可观察行为回归。

## 基线与变更边界

- 起始 HEAD：`c1b81ae72b8e269f13e6da6a3e87937b9b548b24`；开始时 Git 工作区干净。
- 改动前源码和资源 hash 保存在本地忽略目录 `build/refactor/baseline.json`。该文件用于本次对照，不是构建依赖。
- 64 个原有主源码类型迁移包，153 个原有类型均可在新目录中找到；新增职责组件后主源码文件共 173 个。内部旧包不保留桥接。
- 审阅顺序：模型与解析迁移 → 服务端迁移 → controller/session 和屏幕接口 → UI/config 拆分 → 回归与文档。过程中多次通过 IDEA MCP 编译；提交由用户后续明确授权，没有发布或操作原 `run` 的玩家存档。
- `api`、`api.progress`、`ProgressNode`、公开异常、payload 和协议注册实现对照未发现意外变化。116 个原有资源/模板/公开示例文件的 SHA-256 均不变。
- `NbtProgressStore` 唯一行为边界调整是增加包内 writer 注入点：默认仍用原 `IOUtilities.writeNbtCompressed`，load/save 编码、路径和异常包装保持原样。Pending store 编码实现未变。
- 配置默认值和边界归并到 `ClientPreferences`；ModConfigSpec 和编辑组件共享定义，原配置键和数值、滑块步长不变。
- 大类拆分示例（含空行）：ClientConfigFragment 876 → 310，DialogueSceneView 596 → 393，DialogueFragment 604 → 450，DialogueSession 990 → 909。行数只是审阅定位信息，不替代行为验证。

## 自动化证据

| 检查 | 执行方式 | 结果 |
| --- | --- | --- |
| 分阶段编译 | IDEA MCP `build_project` | 多次成功，返回无编译问题 |
| 最终 JAR 构建 | IDEA MCP `execute_run_configuration`，`Build` | exitCode 0，`BUILD SUCCESSFUL` |
| 最终完整本地测试 | IDEA MCP `execute_run_configuration`，`Tests` | exitCode 0；28 个 suite，共 137 测试，0 失败/错误/跳过 |
| 资源/兼容边界对照 | 本地源码和 hash 结构检查 | `contract-audit.json` unexpected 为空 |
| diff 空白检查 | `git diff --check` | 通过 |

IDEA 已启动后，通过其配置的本机 MCP endpoint 恢复连接。所有构建、测试和运行都调用 IDEA MCP；没有用 shell Gradle 代替。`Build` 的原配置不自动执行 test，因此单独运行 `Tests`，并读取 `build/test-results/test/TEST-*.xml` 汇总，未把空控制台输出当作测试证据。

最终产物为 `build/libs/maimai_dialogue-0.3.3.jar`。本地原始结果在 `build/refactor/final-build-result.json`、`final-tests-result.json`、`contract-audit.json`；这些是本次忽略的工作文件。本地测试入口为 `.run/Tests.run.xml`，不纳入版本控制。

137 是当前工作区完整本地套件数量。按用户提交前的要求，整个 `src/test` 和本地 Tests 运行配置均被忽略，原有已追踪测试也移出索引并保留本地文件。因此该套件不随仓库分发，干净 checkout 无法直接复现这一测试数量。

重点覆盖：

- session 内旧 generation/旧 Step 回调、已消费目标请求的重复回包；文本和场景分别完成、推进、跳过、End；随机正文与 History；Option command 成功/失败后的导航；必须完成的单次上报。
- Pending 激活、错误/重复 token、清除失败后的记录保留及恢复 token。
- 解析顺序、缺失资源、默认 Theme、内置资源链、场景运行和文本播放。
- 配置默认值、范围与按键冲突。
- 两类 NBT 的往返、异常、注入 writer 失败；损坏/不支持版本文件读取前后字节不变。
- 纯模型/session/runtime 与 UI、网络发送、静态服务入口的 import 依赖检查；controller 不依赖具体 View。

## 过时测试的处理

生产代码和原有资源作为依据，不反向迁就过时测试：

| 原假设 | 本次处理 |
| --- | --- |
| DialogueBoxLayout 默认 y/width 仍是旧值 | 对照改动前生产定义，默认 y 为 0.76、width 为 0.4；修订测试，动作位移用 DEFAULT 加相对偏移表达 |
| showcase visual 数量必须恰好为 8 | 当前资源原本为 10；测试保留对原必要资源的检查，同时验证实际资源引用 |
| 所有引用 Action 都是 marker_enter | 测试从实际资源 ID 加载对应 Action，取消假的通用 fixture |
| validator 手工列出的资源就是全部资源 | 测试加载实际资源目录，包含已有 debug/actions 和 preset；生产加载/发布策略不变 |
| 测试进程当前目录就是项目根目录 | 通过 test systemProperty 显式传入项目根目录，使源码依赖检查定位正确 |

没有为了以上断言修改布局、动作、资源内容或产品默认值。

## 运行证据与未完成项

Client 和 Server 均通过 IDEA MCP 启动，分别使用 `build/refactor/client-run`、`build/refactor/server-run`，与原运行目录隔离。

- Client：到达欢迎界面；日志加载 17 dialogues、2 speakers、5 themes、9 presentations、5 scenes、4 visual assets、23 actions，load errors 为 0，资源引用校验成功。随后日志记录正常停止。
- GUI：用户按 Esc 停止 Computer Use；之后未继续操作界面。没有完成 debug/showcase、History、输入、配置或渲染的交互验收。
- Dedicated server：日志显示 17 个服务端 Dialogue 加载成功、0 errors，并出现 `Done (5.236s)!`。首次隔离启动有缺失 `server.properties` 的日志，随后自动生成配置并成功启动；没有把这条日志隐藏成“全日志无错误”。
- Server 启动证据取得于最后的 Progress writer 注入和 UI 投递封装之前；这两项随后通过最终 Build/Tests，但未再做运行验收。没有玩家接入测试服务器，因此权限、command、reload、进度和重登恢复均未在本次 dedicated 游戏会话内验证。

待完成的人工/GUI 清单：

- [ ] debug/showcase、权限选项、Return/Close、Option command。
- [ ] 打字、快进、长按跳过、确认框、History、选项展开收起。
- [ ] 背景切换、对象动画、滤镜、采样及反复开关，关注原有残影问题。
- [ ] 配置调整、按键录入、失焦、返回与重进后的保存。
- [ ] reload、进度增删、退出重登、必须完成 Dialogue 恢复。

## 单独记录的已有边界

当前 requestId 在新 session 中重新计数，而 payload 不带 session generation。同 ID、同目标的跨 session 旧回包存在身份碰撞可能；改动前源码已有相同计数和匹配方式。本次不夹带协议/功能修复。现有旧回包回归测试覆盖 session 内失效及已消费请求，不能外推为跨 session 的绝对保证。

历史视觉残留问题本次未重新复现或证明修复。构建和测试成功不替代这些 GUI 检查。
