# DS x YSM Compat

Dragon Survival 1.21.1 + Yes Steve Model 的玩家渲染兼容补丁（纯客户端）。

## 做了什么

两个 mod 都会监听 NeoForge 的 `RenderPlayerEvent.Pre`，取消原版玩家渲染后自己画模型：

- YSM 的监听器是 `NORMAL` + `receiveCanceled = false`；
- DS 的 `ClientDragonRenderer#renderDragon` 是 `NORMAL` + `receiveCanceled = true`，
  并且一旦发现事件已被别人取消就主动让位。

所以原版行为是「同优先级谁先注册谁赢」，结果取决于 mod 加载顺序。本补丁把 DS 的监听器
摘下，改由 `DsYsmHooks` 按客户端配置在指定优先级调用，从而得到固定结果。

## 配置

`config/ds_ysm_compat-client.toml`，也可以在游戏内 **模组列表 → 本模组 → 配置** 里改，
改完立即生效：

| `render_mode` | 行为 |
|---|---|
| `AUTO`（默认） | YSM 真的接管了模型时用 YSM，否则用 DS 画龙 |
| `YSM_PRIORITY` | 永远由 YSM 接管玩家渲染（旧版补丁行为） |
| `DS_PRIORITY` | 龙形态永远由 DS 渲染，YSM 的人形模型被隐藏 |
| `dragon_flight_animation` | 龙飞行时让 YSM 播放 `fly`（创造飞行）动画，默认开 |

### AUTO 如何判断「YSM 没有替换模型」

不需要读 YSM 的配置或混淆类：把 DS 的监听器放到 `LOWEST` 调用，此时 YSM 的 `NORMAL`
监听器已经执行完。

- YSM 真的接管了 → 它在事件里 `setCanceled(true)`，DS 原有的让位分支直接生效；
- YSM 没接管（`DisableSelfModel` / `DisableOtherModel` 打开、玩家没有可用模型、spectator、
  第一人称等）→ 事件仍是未取消状态，DS 正常画龙。

所以「YSM 配置成不替换模型就启用 DS」是自动生效的，不需要单独同步 YSM 的配置项。

### 各模式的实现

- `YSM_PRIORITY`：Mixin 让 DS 的 `renderDragon` 永远不执行；`ClientProxyMixin` 让
  `dragonRenderingWasCancelled` 返回 `true`，阴影/相机/第一人称等补丁一起退回。
- `DS_PRIORITY`：`DsYsmHooks` 在 `HIGHEST` 调用 DS 的 `renderDragon`，DS 先取消事件并画龙，
  YSM 的监听器随后被事件总线跳过；DS 自己不取消事件的分支（BetterCombat 攻击、
  `noDragonModelRendering`）再用 `RenderLivingEvent.Pre` 按 YSM 包名前缀拦掉 YSM 模型。
- `AUTO`：`DsYsmHooks` 在 `LOWEST` 调用 DS 的 `renderDragon`。YSM 接管玩家模型时，
  地面阴影使用原版位置。DS 画龙时使用龙模型的阴影偏移。

## 生效条件

- 客户端、NeoForge 1.21.1；
- 同时装有 Dragon Survival **2.0.7x**（按 `renderDragon` / `dragonRenderingWasCancelled`
  的方法签名写死）与 Yes Steve Model（只按 modid 判断，不碰它内部实现）；
- 缺任一 mod、或 DS 版本不在 2.0.7x 时，mixin 自动禁用（日志里有
  `[ds_ysm_compat] Mixins disabled ...`，此时不会改动任何渲染行为）。

## 构建

正常方式（需要能访问 `maven.neoforged.net`）：

```bat
gradlew.bat build
```

产物：`build/libs/ds_ysm_compat-1.0.0.jar`，丢进 `mods/` 即可。

**离线备用**：本机访问 `maven.neoforged.net` 可能被 TLS 重置，Gradle 拉不到 NeoForge
userdev 包时会失败。仓库里带了不依赖 Gradle 的构建脚本，直接用本机已安装的 NeoForge
运行时 jar + Dragon Survival jar 来 javac：

```powershell
powershell -ExecutionPolicy Bypass -File .\build-no-gradle.ps1 -DragonSurvivalJar "C:\path\to\DragonSurvival-all.jar"
```

启动器的游戏目录或 NeoForge 版本不同，可以传入 `-LibrariesDir` 和 `-NeoForgeVersion`。
Dragon Survival jar 只作为编译期 classpath，不会打进产物。

## 许可证

本项目的兼容补丁代码采用 [MIT 许可证](LICENSE)。NeoForged 模板的版权声明见 [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt)。
Dragon Survival、Yes Steve Model 及其素材各自适用原有许可。

## 龙飞行 → YSM 创造飞行动画

YSM 2.6.5 的内置动画条件表在 `OoOOo0OOoOOooo00OO00oo0O`，其中 `fly`（创造飞行）的判定是
`player.getAbilities().flying`。DS 的飞行不走原版创造飞行，所以龙飞的时候 YSM 会落到
`jump` / 下落动画。

`dragon_flight_animation`（默认开）开启时，`DsYsmAnimationCompat` 会在 YSM 的条件表里追加一个
同名、同优先级（1）的 `fly` 条件：

```java
(player, context) -> player 是龙 && ServerFlightHandler.isFlying(player)
```

- 只追加条件，不改 YSM 的任何方法，也不影响死亡/游泳/爬梯等更高优先级条件；
- 保留模型/玩家原本的 `fly` 判定结果，只在 false 时补成 true；
- 目标是 YSM 2.6.5 的混淆类名，按版本门控，反射失败只打日志、不影响渲染。

注意：若 YSM 的 `DisableSelfModel` / `DisableOtherModel` 打开，YSM 本身不渲染玩家，
这个动画映射自然也看不到效果。

## 第一人称与纸娃娃

- DS 的第一人称龙身是在 `LevelRenderer#renderLevel` 里手动 `renderEntity(本地玩家)` 画出来的。
  这条通道现在**跟随 `render_mode`**，不会无条件画龙：
  - `DS_PRIORITY`：DS 画第一人称龙身；
  - `YSM_PRIORITY`：不画龙身；
  - `AUTO`：沿用该玩家最近一次正常渲染（第三人称/纸娃娃）的归属——YSM 接管就不画龙身，
    DS 接管才画。没有记录时默认按 YSM 接管处理，避免「用着 YSM 模型却在第一人称冒出龙身」。
- 第一人称龙身通道会跳过 YSM 的第三人称模型，避免模型的头挡住视野。
- `AUTO` 和 `YSM_PRIORITY` 下，空手和双手持地图时的手臂会走 NeoForge 的 `RenderArmEvent`，
  由 YSM 按自身配置和模型设置决定是否绘制。YSM 未绘制时，龙形态仍会隐藏原版手臂。
  `DS_PRIORITY` 沿用 DS 原有的手臂渲染规则。手持物品继续走原有的 `RenderHandEvent` 路径。
- DS 不取消事件的分支（BetterCombat 攻击、`noDragonModelRendering`）由 `RenderLivingEvent.Pre`
  兜底拦掉 YSM 渲染器。
- DS 的 `DragonRenderer` 会按 `RenderingUtils#isFirstPerson` 隐藏龙的 `Neck` 骨骼。
  这个判断原本只看相机类型，所以纸娃娃/HUD/GUI 预览（发生在 `renderLevel` 之外）也会被摘头。
  现在只有真正处于 `renderLevel` 调用栈内时才返回第一人称，纸娃娃里的龙保持完整。
- 识别方式：`LevelRendererStateMixin` 给 `renderLevel` 打标志；原版实体循环本身会跳过相机实体，
  所以「世界渲染中 + 本地玩家 + 第一人称」只会命中 DS 的第一人称龙身通道。

## 表情转盘

YSM 2.6.5 的表情转盘会在本地玩家由 DS 渲染时显示当前龙体的表情，保留转盘分页。
选择表情会通过 DS 播放和同步。玩家由 YSM 渲染时，转盘继续使用 YSM 模型的动作。
如果 YSM 的转盘因模型或动作数据不可用而没有打开，同一入口会打开 DS 的表情菜单。

## 已知取舍

- `DS_PRIORITY` 下龙形态不再出现 YSM 人形模型；`YSM_PRIORITY` 下龙模型/动画/龙甲/爪牙、
  龙名板等全部由 YSM 或原版替代（玩家实体仍带 DS 的体型缩放）。

## 开发说明

- 目标方法签名是从 `DragonSurvival-1.21.1-v2.0.71` 的发布 jar 里 `javap` 核对过的：
  - `ClientDragonRenderer.renderDragon(Lnet/neoforged/neoforge/client/event/RenderPlayerEvent$Pre;)V`
  - `ClientProxy.dragonRenderingWasCancelled(Lnet/minecraft/world/entity/player/Player;)Z`
  - `DragonStateProvider.isDragon(Lnet/minecraft/world/entity/Entity;)Z`（反射调用，不编译依赖 DS）
  - `RenderingUtils.isFirstPerson(Lnet/minecraft/world/entity/player/Player;)Z`
  - `ServerFlightHandler.isFlying(Lnet/minecraft/world/entity/player/Player;)Z`（反射调用）
  - YSM 2.6.5：`OoOOo0OOoOOooo00OO00oo0O#oOo0OO0O0o000OO0O000oo0o(O0O00oOooOo000oo00OoO0Oo)` 条件注册、
    `O0O00oOooOo000oo00OoO0Oo(String, ooOoOOooOoooOoooo0oO0Oo0, int, BiPredicate)` 构造器、
    枚举项 `ooOoOOooOoooOoooo0oO0Oo0.oOo0OO0O0o000OO0O000oo0o`
  - `LevelRenderer.renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V`
- Mixin 都在 `"client"` 段。第一人称手臂补丁通过 DS 内置的 MixinSquared 调整 DS 的两个手臂渲染注入，
  再在 NeoForge 发出 `RenderArmEvent` 后处理原版手臂。
- 没有 refmap，NeoForge 1.21.1 的生产环境与开发环境都使用 official 名称。
