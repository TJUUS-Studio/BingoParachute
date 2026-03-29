# Bingo Parachute

`Bingo Parachute` 是一个只面向 `Yet Another Minecraft Bingo` 的服务端附属 Fabric mod。

它会在 Bingo 开局时把参赛玩家接管到高空空降流程里，而不是直接从地面出生点开始。当前主要目标是给 Bingo 提供一个可配置的开局空降体验，包括：

- 高空起跳
- `BAT` / `ELYTRA` 两种模式
- 开局前几秒强制 PVP 保护
- 空降期间背包与装备托管
- `BAT` 手动脱离和超时后的短时摔落免疫
- 落地、落水、落岩浆后的自动结束

## 当前状态

当前项目已经可以用于实机测试，主链路包括：

- 通过 Bingo 生命周期事件接入游戏流程
- 在 Bingo `COUNTDOWN` 阶段把玩家固定到未来空降起点
- 在 `PLAYING` 后正式进入空降
- `BAT` 模式空降
- `ELYTRA` 模式空降
- 开局 PVP 保护
- 超时处理
- 超时和 `BAT` 手动脱离后的摔落免疫
- 落地后的结束、恢复和清理

当前实现是服务端 mod，不要求客户端安装。

## 当前支持版本

- `mc1.21.11`
- `mc1.21.8`

项目结构：

- `common`
  放公共流程、状态、配置和尽量不依赖 Minecraft 类型的逻辑
- `mc1.21.11`
  放 `1.21.11` 的版本实现
- `mc1.21.8`
  放 `1.21.8` 的版本实现

## 游戏流程概览

当前实际流程大致是：

1. Bingo 初始化后，本 mod 注册并等待生命周期事件
2. Bingo 进入 `COUNTDOWN` 时，本 mod 计算并缓存高空空降起点
3. `COUNTDOWN` 期间把玩家固定在高空起点，而不是原地面出生点
4. Bingo 进入 `PLAYING` 后创建本局空降 session
5. 到达激活时机后：
   - 备份并清空玩家背包/装备
   - 进入 `BAT` 或 `ELYTRA` 模式
6. 空降过程中持续处理：
   - 飞行控制
   - PVP 保护
   - 超时倒计时
   - 落地/落水/落岩浆/死亡判定
7. 空降结束后：
   - 清理临时载具或能力
   - 在需要时给予短时摔落免疫
   - 恢复原始背包/装备
   - 从活跃空降跟踪中移除

更详细的运行链路说明见：

- `.ai_doc/current_strategy_and_flow.md`

## 构建

编译两个版本：

```powershell
.\gradlew.bat :mc1.21.8:compileKotlin :mc1.21.11:compileKotlin
```

打包 `1.21.11`：

```powershell
.\gradlew.bat :mc1.21.11:build
```

产物位置：

- `mc1.21.11/build/libs`
- `mc1.21.8/build/libs`

## 配置

当前主要配置项：

- `enabled`
  是否启用整个附属 mod。
- `debugLogging`
  是否输出更详细的调试日志。
- `mode`
  当前空降模式，`BAT` 或 `ELYTRA`。
- `startDelayTicks`
  不复用 `COUNTDOWN` 高空锚点时，`PLAYING` 后延迟多少 tick 再正式进入空降。
- `spawnHeight`
  空降起点的目标高度。
- `pvpProtectionSeconds`
  开局 PVP 保护时长，同时也是空降超时基准时长。
- `timeoutFallImmunitySeconds`
  `timeout` 强制结束后额外给予的摔落免疫时长；`BAT` 模式手动 `Shift` 脱离后当前也沿用这段时长。
- `bat.descentSpeed`
  `BAT` 模式的固定基础下降速度。
- `bat.flightSpeed`
  `BAT` 模式的飞行速度标量，视角会改变其水平/垂直分量分配。
- `bat.maxHorizontalRadiusChunks`
  `BAT` 模式允许偏离起点的最大水平半径，单位为区块；小于 `0` 视为无限。
- `elytra.glideSpeedScale`
  `ELYTRA` 模式的滑翔速度缩放。
- `elytra.maxDiveSpeed`
  `ELYTRA` 模式允许的最大俯冲速度。
- `elytra.maxHorizontalRadiusChunks`
  `ELYTRA` 模式允许偏离起点的最大水平半径，单位为区块；小于 `0` 视为无限。

当前默认值示例：

- `mode = BAT`
- `spawnHeight = 196`
- `pvpProtectionSeconds = 30`
- `timeoutFallImmunitySeconds = 10`
- `bat.descentSpeed = 0.33`
- `bat.flightSpeed = 0.6`
- `bat.maxHorizontalRadiusChunks = 10.0`

## 依赖

运行这个 mod 需要服务器同时安装：

- `Yet Another Minecraft Bingo`
- `Fabric API`

## TODO

这些目标已经明确，但目前还没有完成：

- 测试 `ELYTRA` 模式的完整实机表现与边界行为
- 测试 `1.21.8` 版本的完整实机兼容性
- 支持更多 Minecraft 版本
