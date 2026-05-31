# It

`It` 是一个面向 Fabric 的 Minecraft 生存恐怖 MOD。它通过 Receiver 信号、被注视值、Phase 推进、多人异常、下界信号、追逐和矿洞潜伏者等低频事件，让玩家在正常生存过程中逐步意识到世界正在被观察。

当前版本：`2.3.0`

## 特性

- 渐进式恐怖体验：事件会随玩家经历、Receiver 记录和 Phase 逐步升级。
- Horror Director：根据玩家上下文调度洞穴、基地、下界、多人、高空、AFK 和强事件反馈。
- Receiver 系统：记录异常信号，支持服务端同步、GUI 实时刷新和未读压力。
- 多人恐怖：分离警告、假脚步、假 Tab、假成就、假救援和伪装玩家。
- 强事件：Chase、Cave Stalker、Face Scare、Mimic Face Scare 和 Manifestation 相关反馈。
- 安全边界：默认不删除玩家物品、不随机传送玩家、不生成破坏性 boss。

## 支持版本

| 项目 | 版本 |
| --- | --- |
| Minecraft | `1.21.11` |
| Fabric Loader | `0.18.4` |
| Fabric API | `0.141.4+1.21.11` |
| Java | `21` |

## 安装

1. 安装 Minecraft `1.21.11` 对应的 Fabric Loader。
2. 安装匹配版本的 Fabric API。
3. 从 GitHub Releases 下载 `it-2.3.0.jar`。
4. 将 jar 放入客户端或服务端的 `mods/` 目录。

服务端和客户端都应安装本 MOD。配置文件会在首次运行后生成，建议先使用默认配置体验完整流程。

## 构建

Windows PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build
```

macOS / Linux:

```bash
./gradlew build
```

构建产物：

```text
build/libs/it-2.3.0.jar
build/libs/it-2.3.0-sources.jar
```


## 测试命令

测试命令默认隐藏，需要 OP 执行：

```mcfunction
/xm debug on
```

常用入口：

```mcfunction
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
/it event chase
/it event cavestalker
/it event facescare
```

测试完成后关闭命令树：

```mcfunction
/xm debug off
```

完整回归流程见 [MOD测试文档.md](MOD测试文档.md)。

## 项目结构

```text
src/main/java/com/xm6680/it
├─ api/              扩展接口
├─ runtime/          manager 装配与运行时上下文
├─ director/         Horror Director、玩家上下文、逃避值、团队压力、高空事件
├─ analog/           Receiver、信号记录、轻微异常聚合
├─ progression/      玩家恐怖进度与 Phase 推进
├─ event/            普通事件、多人异常、下界信号、世界异常
├─ chase/            地表追逐事件
├─ cavestalker/      矿洞潜伏者事件
├─ manifestation/    Phase 5 显现环境与睡眠干扰
├─ jumpscare/        跳脸与强反馈
├─ entity/           Watcher、追猎实体、目标可见性
├─ network/          S2C payload 和服务端发送入口
├─ client/           客户端渲染、GUI、overlay 与 payload 接收
├─ command/          /it 测试命令与 /xm debug 开关
└─ config/           配置默认值、校验与迁移
```

## 开发文档

- [MOD说明文档.md](MOD说明文档.md)：系统结构、扩展边界和维护规则。
- [MOD测试文档.md](MOD测试文档.md)：构建与回归测试清单。
- [PHASE说明文档.md](PHASE说明文档.md)：Phase 进入条件、事件分布和调试入口。

## 维护规则

- 服务端逻辑不要直接引用 `client` 包。
- 客户端表现通过 `ItNetwork` 增加 S2C payload。
- 配置默认值和旧配置迁移需要同时更新 `ItConfig` 与 `ItConfigManager`。
- 测试命令只放在 `/it` 下，并继续由 `/xm debug on/off` 控制。
- 发行前运行 `.\gradlew.bat build`，确认 jar 文件名和 jar 内 `fabric.mod.json` 版本一致。

## 许可证

All rights reserved. 该项目未提供开源授权；未经作者许可，不得复制、再分发或修改发布。
