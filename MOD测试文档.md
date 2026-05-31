# It v2.3.0 测试文档

本文档用于当前版本回归。所有 `/it` 测试命令默认关闭，需要 OP 先执行：

```mcfunction
/xm debug on
```

## 1. 构建与产物

```powershell
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build
```

期望：

```text
build/libs/it-2.3.0.jar
build/libs/it-2.3.0-sources.jar
```

同时确认 jar 内 `fabric.mod.json` 版本为 `2.3.0`。

## 2. 结构清理回归

- `/xm debug on` 后 `/it` 命令树可见。
- `/xm debug off` 后 `/it` 命令树不可见。
- `/siw` 不应恢复。
- `src/main/resources/assets/it/textures/gui/` 只应保留实际运行贴图，不应再包含 `*_backup_1_0_*.png`。
- 普通 FaceScare、追猎 FaceScare、Cave Stalker FaceScare 和 Mimic FaceScare 仍分别使用各自贴图。

## 3. Receiver 基础流程

```mcfunction
/it receiver give
/it phase set 2
/it event receiver weather
/it context
```

期望：

- Receiver 物品可获得并打开。
- 首次获得 Receiver 后出现“右键打开查看信号”类 actionbar 提示。
- 新消息能显示在 Receiver GUI。
- 若 GUI 已打开，服务端同步的新消息能实时刷新。
- 小声音类事件不应每次直接刷 Receiver，而应按轻微异常聚合策略记录。
- `/it context` 能显示当前上下文、未读 Receiver、逃避值、个人压力、团队压力和导演状态。

## 4. Phase 与 Watcher

```mcfunction
/it phase set 3
/it spawnwatcher
/it event tunnelwatcher
/it phase debug
```

期望：

- Phase 3 普通 Watcher 默认在约 `30-50` 格出现。
- Phase 3 Tunnel Watcher 默认在约 `36-56` 格出现。
- Phase 4/5 的近距离压迫不被拉远。
- 远距离注视仍能记录目击并触发消失逻辑。
- `/it phase debug` 输出的门槛与实际推进逻辑一致。

## 5. 下界事件

准备：

```mcfunction
/it receiver give
/it phase set 3
```

在 Nether 中执行：

```mcfunction
/it event nether signal
/it event nether ghastcry
/it event nether soulsand
/it event nether portal
```

期望：

- 玩家不在 Nether 时提示需要位于下界。
- Nether Receiver Signal 走 delayed Receiver message，不立即硬写 GUI。
- `/it event nether ghastcry` 测试路径稳定播放明显幽灵恶魂声，即使附近有真实 Ghast。
- Soul Sand Whisper 只在灵魂类方块附近生效，听感为低频低语，不应有玻璃碎裂感。
- Portal Anomaly 先播放异常声，约 `45 ticks` 后破坏附近最多 `6` 个 `NETHER_PORTAL` 方块。
- Portal Anomaly 不破坏黑曜石框架、不破坏其他建筑、不删除物品、不传送玩家。

## 6. 多人异常

至少两名玩家在线：

```mcfunction
/it phase set 4
/it event mimicplayer
/it event mimicplayer clear
```

期望：

- 伪装玩家复制另一名在线玩家的名称和皮肤。
- 伪装玩家只对目标玩家可见。
- 伪装玩家会在安全地面移动，不应浮空滑行。
- 靠近时使用 `mimic_face_scare.png`，不应生成旧 Watcher 冲刺实体。
- `clear` 能清理实体和临时玩家列表资料。

## 7. 强制聊天栏

```mcfunction
/it event forcedchat
```

期望：

- 客户端聊天栏被强制打开。
- 多句文本逐字出现。
- 退格、输入、发送和关闭窗口都不能中断事件。
- 文本不会自动发送到公共聊天。

## 8. Animal Disguise

```mcfunction
/it phase set 3
/it event animaldisguise
```

期望：

- 生成位置播放心跳声。
- 伪装动物会尝试攻击附近怪物和允许的普通动物。
- 默认索敌半径为 `24` 格。
- 最大攻击次数配置低于 `3` 时会被校验迁移到至少 `3`。
- 玩家击杀伪装动物后只对击杀者触发反噬。

## 9. 强事件回归

```mcfunction
/it phase set 5
/it event chase
/it event cavestalker
/it event facescare
/it event huntfacescare
```

期望：

- Chase 和 Cave Stalker 进行中不会自然叠加另一个强事件。
- `/it` 和 `/xm` 在追逐中仍可用于测试和调试。
- 非测试逃脱指令仍被拦截。
- Chase、Cave Stalker、普通 FaceScare、Mimic FaceScare 的 overlay 不串图。

## 10. Horror Director 与逃避值

```mcfunction
/it context
/it avoidance @s
/it avoidance @s 80
/it forceevent @s minor
/it forceevent @s noticeable
/it forceevent @s major
/it debug player @s
```

期望：

- `/it context` 输出玩家上下文，不依赖客户端 GUI。
- `/it avoidance` 可读取和设置当前玩家逃避值，数值限制在 `0-100`。
- `/it forceevent` 可通过 Director 强制触发 `minor`、`noticeable`、`major`，并复用已有事件 manager，不新增破坏性 boss 或随机传送。
- `/it debug player` 输出当前阶段、上下文、导演状态、逃避值、团队压力、未读 Receiver 和新增事件计数。

## 11. 高空、抱团、AFK 与下界压力

准备：

```mcfunction
/it testmode on
/it setphase @s 3
```

观察点：

- 高于 `skyborneMinY`、长时间离地、滑翔或高空骑乘时，`/it context` 应包含 `SKYBORNE`，逃避值会缓慢上升。
- 高空事件只应产生远处声音、Receiver 高度信号、坐骑心跳异常或假 Tab/身份泄漏，不应杀坐骑、不应随机传送。
- 两名以上玩家靠近时应显示 `GROUPED`，团队压力会上升；抱团不应完全屏蔽恐怖事件。
- AFK 后重新移动时，Director 可触发低强度“记录恢复”反馈。
- 早期快速进入 Nether 会记录下界逃避信号，并增加逃避推进压力。

## 12. 被动推进与 Receiver 未读压力

```mcfunction
/it phase debug
/it testmode on
/it receiver give
```

期望：

- `/it phase debug` 除传统 Receiver/事件门槛外，还显示 Passive Progression 和 Avoidance Progression 相关条件。
- 默认 `receiverOpenHardRequirementEnabled=false` 时，Receiver 打开次数不再是唯一硬门槛；未读 Receiver 会提高 `unreadSignalPressure`。
- `passiveProgressionEnabled` 和 `avoidanceProgressionEnabled` 开启时，长时间正常游玩或长期逃避行为都能参与 Phase 推进。
- `/it testmode on` 会开启导演测试和加速推进；完成测试后使用 `/it testmode off` 还原。

关闭测试入口：

```mcfunction
/xm debug off
```
