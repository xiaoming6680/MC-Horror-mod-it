# It Phase 说明文档

本文档记录 `It v2.3.0` 当前代码中的 Phase 规则。测试命令可强制触发部分事件，不完全受 Phase、概率、环境和冷却限制；默认需要先执行 `/xm debug on`。

## 通用规则

- Phase 由 `HorrorProgressionManager` 推进，默认每 `600 ticks` 检查一次。
- 自然事件由 `HorrorDirectorManager` 统一调度；它会读取玩家上下文、逃避值、团队压力和最近事件间隔。
- Phase 进入文本通过 Receiver 延迟发送；Phase 1 是初始状态，没有进入文本。
- 每个 Phase 继承前面 Phase 的自然事件；下文“新增事件”只列出从该阶段开始自然出现的内容。
- 强事件互斥：Chase、Cave Stalker、Jumpscare、突脸等进行中时，普通事件会减少或跳过自然叠加。
- 被注视值只是门槛之一：Phase 2/3/4/5 分别需要至少 `20 / 40 / 60 / 80`。
- Receiver 记录数、Receiver 打开次数、阶段停留时间和异常经历也会影响推进。
- Passive Progression 会让长时间游玩逐步推进；Avoidance Progression 会让高空、抱团、长期待基地、快速下界、忽略 Receiver 等逃避行为参与推进。
- 默认 `receiverOpenHardRequirementEnabled=false`，Receiver 打开次数是强信号但不是唯一硬锁；需要旧式硬门槛时可在配置中开启。
- 下界事件只在 Nether 自然触发；Portal Anomaly 只破坏 `NETHER_PORTAL` 紫色方块，不破坏黑曜石框架。

## v2.3.0 跨阶段上下文响应

- `GROUND_EXPLORING`、`UNDERGROUND`、`HOME_OR_BASE`、`NETHER`、`END`、`SKYBORNE`、`GROUPED`、`ISOLATED`、`AFK_OR_IDLE`、`FAST_TRAVELING`、`IN_DARKNESS` 和 `IN_RECEIVER_INTERACTION` 会影响 Director 选事件。
- 高空/滑翔/骑乘默认只触发声音、Receiver 高度信号、坐骑心跳异常或远程身份泄漏，不强制杀坐骑、不随机传送。
- 抱团会增加团队压力，触发分离、假队友脚步、团队凝视或假 Tab；抱团不再等同于安全。
- AFK 期间事件会降低频率；玩家恢复活动时可能收到“记录恢复”类反馈。
- 忽略 Receiver 会增加未读压力；打开 Receiver 会记录当前消息数并释放未读压力。

## Phase 1: DORMANT / 接触

### 进入文本

- 无。新玩家或重置后默认处于 Phase 1。

### 进入条件

- 默认初始阶段。

### 新增事件

- Receiver 天气/低威胁记录。
- Receiver 保底：第一次获得 Receiver 后约 `5-8 分钟`，之后约 `8-15 分钟`，只发送低威胁信息。

### 说明

- 不自然触发 Watcher、世界异常、模仿、追逐、突脸或下界强事件。

## Phase 2: WATCHING / 观察

### 进入文本

- `接收器开始记录周围环境。`
- `有东西注意到了你的活动。`
- `地下信号开始变得不稳定。`
- `请留意身后的声音。`

### 进入条件

- 被注视值至少 `20`。
- 总游玩时间至少 `420 秒`。
- Receiver 信息至少 `1` 条。
- Receiver 打开次数至少 `1` 次。
- 地下、黑暗独处或夜晚独处任一累计至少 `2 分钟`。
- 或满足 Phase 2 的被动/逃避推进条件。

### 新增事件

- Receiver 消息扩展为 `WEATHER`、`LOCAL_ALERT`、`OBSERVATION`。
- Phase 2 Receiver 保底：进入 Phase 2 后约 `360-600 秒` 发送第一次普通保底，之后约 `600-1080 秒` 重复。
- 洞穴脚步、挖掘回声、手持物品掉落、背包自动打开、诡异声音。
- 熟悉行为假声：假箱子、假放方块、假吃东西。
- 动物凝视、视距压低、黑白画面。
- 多人分离警告、假队友脚步。
- 轻微异常聚合记录。
- Nether Receiver Signal。
- Phantom Ghast Cry。
- Soul Sand Whisper。
- Director 可根据高空、抱团、基地、下界、AFK 或未读 Receiver 压力选择对应低强度反馈。

## Phase 3: IMITATING / 模仿

### 进入文本

- `信号中出现了不属于你的声音。`
- `它开始学习你们的行为。`
- `不要只通过名字判断一个人。`
- `模仿记录已开始。`

### 进入条件

- 被注视值至少 `40`。
- 当前阶段停留至少 `480 秒`。
- Receiver 信息至少 `3` 条。
- Receiver 打开次数至少 `2` 次。
- 小事件分、洞穴脚步、挖掘回声或黑暗独处经历满足任一门槛。
- 或满足 Phase 3 的被动/逃避推进条件。

### 新增事件

- Receiver 消息扩展为 `LOCAL_ALERT`、`OBSERVATION`、`IDENTITY_WARNING`。
- 假聊天。
- 强制聊天栏异常。
- 普通 Watcher：Phase 3 默认约 `30-50` 格，更偏远处目击。
- Tunnel Watcher：Phase 3 默认约 `36-56` 格，更克制、更远。
- 随机异常告示牌、低频地狱岩十字架、旧版材质视觉异常。
- 队伍同步凝视、假成就、假救援。
- Animal Disguise：伪装动物、攻击附近怪物/动物、击杀反噬。
- Portal Anomaly：下界传送门返回路径异常，音效结束后破坏附近 `NETHER_PORTAL` 方块。
- 睡眠干扰：配置名仍兼容 `enablePhaseFiveSleepInterference`，自然门槛为 Phase 3+。
- Skyborne 身份泄漏：高空场景可低频触发 Receiver 身份警告；假 Tab 仍从 Phase 4 起自然出现。

## Phase 4: INTRUSION / 入侵

### 进入文本

- `异常信号已经进入你的生活区。`
- `它不只在洞里。`
- `接收器检测到身份混淆。`
- `请确认你身边的人。`

### 进入条件

- 被注视值至少 `60`。
- 当前阶段停留至少 `840 秒`。
- Receiver 信息至少 `8` 条。
- Receiver 打开次数至少 `6` 次。
- 假聊天、Watcher、假 Tab、总升级分或黑暗独处经历满足入侵门槛。
- 或满足 Phase 4 的被动/逃避推进条件。

### 新增事件

- Receiver 消息扩展为 `OBSERVATION`、`IDENTITY_WARNING`、`SYSTEM_ERROR`。
- Fake Tab 玩家列表异常。
- Skyborne Fake Tab：高空/滑翔场景可触发短暂额外玩家列表签名。
- 普通假加入/假离开访问。
- Mimic Player：复制在线玩家名称和皮肤，只对目标玩家可见，会走动、凝视、靠近后突脸或消失。
- 基地异常：开门、移除小物件、放置花盆、异常告示牌。
- 睡觉尝试可触发基地异常预兆。
- Director 识别基地停留后可触发基地入侵事件；默认只允许非破坏性变化。
- Watcher 和 Tunnel Watcher 比 Phase 3 更近、更直接。

## Phase 5: MANIFESTATION / 显现

### 进入文本

- `它已经离你很近了。`
- `观察阶段结束。`
- `它不再只是看着你。`
- `接触风险：极高。`

### 进入条件

- 被注视值至少 `80`。
- 当前阶段停留至少 `1080 秒`。
- Receiver 信息至少 `12` 条。
- Receiver 打开次数至少 `9` 次。
- Watcher 目击至少 `1` 次，或总升级分至少 `10`。
- 需要显现环境：独处低光、深层地下、夜晚森林/洞穴，或独处且处在低光/地下/夜晚等不安全环境。
- 或满足 Phase 5 的被动/逃避推进条件；显现环境仍会限制强显现事件的自然出现。

### 新增事件

- Receiver 消息扩展为 `SYSTEM_ERROR`、`PERSONAL_SIGNAL`、`MANIFESTATION`。
- Phase 5 干扰：画面闪烁、噪音爆发、Receiver 失真。
- FaceScare 自然突脸。
- Jumpscare。
- Chase：地表或普通环境追逐。
- Cave Stalker：地下、矿洞和低光环境追逐。
- 追逐中的假救援联动。
- Watcher 和 Tunnel Watcher 出现距离进一步变近。

## 调试入口

```mcfunction
/xm debug on
/it debug
/it debug player <player>
/it phase debug
/it phase set <1-5>
/it setphase <player> <1-5>
/it context [player]
/it avoidance <player>
/it avoidance <player> <0-100>
/it forceevent <player> <minor|noticeable|major|skyborne|group|base|nether|afk>
/it testmode on
/it receiver give
/it event nether signal
/it event nether ghastcry
/it event nether soulsand
/it event nether portal
/it event mimicplayer
/it event forcedchat
/it event tunnelwatcher
/it event chase
/it event cavestalker
/xm debug off
```
