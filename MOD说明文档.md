# It MOD 说明文档

当前版本：`2.3.0`

本文档面向维护和后续开发，记录当前结构、核心机制和扩展边界。玩家侧验证流程见 `MOD测试文档.md`，阶段规则见 `PHASE说明文档.md`。

## 环境与产物

- Minecraft：`1.21.11`
- Fabric Loader：`0.18.4`
- Fabric API：`0.141.4+1.21.11`
- Java：`21`
- 主 jar：`build/libs/it-2.3.0.jar`
- sources jar：`build/libs/it-2.3.0-sources.jar`

## 结构原则

- `ItMod` 只负责 Fabric 主入口、基础注册和把运行时容器交给事件/命令层。
- `ItManagers` 是服务端 manager 图的唯一装配点，负责创建和连接 Progression、Receiver、Horror Director、事件、追逐、下界信号、持久化等对象。
- `api` 包只放稳定扩展接口，避免未来功能直接依赖入口类或互相硬连。
- `client` 包只放客户端渲染、GUI 和 payload 接收；服务端不得直接引用这些类。
- `network/ItNetwork` 是服务端触发客户端表现的统一出口。
- 配置默认值和旧配置迁移必须同时更新 `ItConfig` 和 `ItConfigManager`。

## 扩展接口

### `HorrorExtension`

用于以后接入新的服务端恐怖功能。当前预留生命周期：

- `onRegister(HorrorExtensionContext context)`：扩展注册时初始化。
- `tickActive(MinecraftServer server, long currentTick)`：每 tick 处理短生命周期状态。
- `tick(MinecraftServer server, long currentTick)`：低频事件尝试，跟随现有 `HORROR_EVENT_INTERVAL_TICKS`。
- `remove(ServerPlayerEntity player)`：玩家死亡、换维度或断线时清理状态。

### `HorrorExtensionContext`

提供核心服务访问：

- `watchingLevelManager()`
- `progressionManager()`
- `receiverManager()`
- `analogHorrorManager()`
- `minorAnomalyAccumulator()`

更具体的系统应优先通过已有 manager 的公开方法协作，不要跨包直接改内部状态。

## 核心系统

### Receiver

- 负责展示异常记录和玩家阶段信号。
- 普通记录、强事件记录和 delayed message 都集中到 `ReceiverManager`。
- 小声音默认先进入 `MinorAnomalyAccumulator`，避免 Receiver 变成刷屏事件日志。
- GUI 由服务端 payload 同步，客户端只展示服务端计算结果。
- 首次获得 Receiver 后会给出使用提示；玩家长期不打开 Receiver 时，未读记录会转化为压力和逃避推进信号。

### Phase

- `HorrorProgressionManager` 负责 Phase 推进和调试信息。
- Phase 进入文本由 `AnalogHorrorManager#onPhaseAdvanced` 发送 delayed Receiver message。
- 被注视值只是门槛之一，还需要 Receiver 次数、打开次数、阶段停留时间和对应异常经历。
- `passiveProgressionEnabled` 允许长时间游玩后进入后续阶段；`avoidanceProgressionEnabled` 允许飞天、抱团、长期待基地、快速进出下界和忽略 Receiver 等逃避行为参与推进。
- `receiverOpenHardRequirementEnabled` 默认为 `false`，Receiver 打开次数不再是唯一硬门槛；需要传统硬门槛时可在配置中开启。

### Horror Director

- `HorrorDirectorManager` 替代旧的低频随机事件串联入口，统一根据玩家上下文选择轻微、明显或强事件。
- `PlayerContextDetector` 标记地表、地下、基地、下界、末地、高空、抱团、独处、AFK、快速移动、低光和近期 Receiver 交互。
- `AvoidanceManager` 维护 `avoidanceScore`，高空/骑乘、抱团、快速移动、长期基地停留、忽略 Receiver 和早期下界会提高分数，正常游玩会缓慢衰减。
- `GroupDreadManager` 让抱团不再等于安全；多人靠近会积累团队压力并低频触发分离、假脚步、团队凝视或假 Tab。
- `SkyborneHorrorManager` 处理高空、鞘翅/滑翔、骑乘和超高 Y 值场景，只做声音、Receiver 信号、坐骑心跳异常或远程身份泄漏，不杀坐骑、不随机传送。

### 事件层

- `HorrorEventManager`：普通声音、挖掘、掉落、强制聊天等基础恐怖事件。
- `WorldAnomalyManager`：基地、告示牌、十字架、动物凝视等世界异常。
- `MultiplayerDreadManager`：多人分离、假队友、假 Tab、伪装玩家、假救援。
- `AnimalDisguiseManager`：伪装动物、索敌攻击、击杀反噬。
- `NetherSignalManager`：下界专属 Receiver、幽灵恶魂、灵魂沙、传送门异常。
- `WatcherSpawnManager`：普通 Watcher 和 Tunnel Watcher 生成。

### 强事件

- `ChaseManager`：地表或普通环境追逐。
- `CaveStalkerManager`：矿洞、低光和地下环境追逐。
- `JumpscareManager`：强跳脸和高压反馈。
- `ManifestationManager`：Phase 5 显现环境、睡眠干扰和强事件互斥辅助。

## 安全边界

- 不恢复 `/siw`。
- `/it` 测试命令默认不可见，必须先执行 `/xm debug on`。
- Chase / Cave Stalker 中仍允许 `/it` 和 `/xm`，其他逃脱型命令会被拦截。
- 下界传送门异常只破坏 `NETHER_PORTAL` 紫色方块，不破坏黑曜石框架和玩家建筑。
- 事件不删除玩家物品，不随机传送玩家，不生成新的攻击型 boss。
- 基地异常默认只允许非破坏性变化；`allowItemManipulation` 默认为 `false`，不会自然移除火把、花盆等小物件。
- 多人恐怖事件保持低频、冷却驱动、服务端拥有状态。

## 资源策略

- 只保留运行时实际使用的贴图、模型和语言文件。
- 历史备份素材不放在 `src/main/resources`，避免被打进发布 jar。
- 当前 GUI 跳脸资源包括：
  - `face_scare.png`
  - `hunt_face_scare.png`
  - `cavestalker_facescare.png`
  - `mimic_face_scare.png`

## 版本维护

- 代码行为、配置默认值、配置迁移、说明文档和构建产物版本必须同步。
- 发行式修改后运行 Java 21 构建，并确认 jar 名称和 jar 内 `fabric.mod.json` 版本。
