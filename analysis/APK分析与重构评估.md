# 潮汐 APK 静态分析与分阶段重构评估

分析日期：2026-10-28。对象仅为本目录提供的 APK；“原作者停止维护”的说法未独立核验。没有原始源码、R8 mapping、服务端源码、发布密钥或产品需求文档。

## 1. 样本与证据范围

- 文件：`潮汐-5.7.0-2026-10-28.apk`，198,245,263 字节。
- 包名 `io.moreless.tide`，versionName `5.7.0`，versionCode `1592`。
- Manifest 声明 minSdk 24、targetSdk 37、compileSdk 37。这与“很久没更新”的直观描述不完全一致，但这些字段不能证明实际发布日期或维护状态。
- 5 个 DEX；28,728 个 Smali 类文件；JADX 输出 24,197 个 Java 文件。这些计数包含第三方 SDK、生成类和混淆辅助类，不等于业务类数量。
- 183 个 Activity、39 个 Service、23 个 Receiver、10 个 Provider；43 个组件显式声明 exported=true。声明导出本身不是漏洞结论，需要结合权限、输入校验和调用者检查逐个审计。
- 2 种 ABI：arm64-v8a、armeabi-v7a；共 36 个 SO 文件，对应 18 个库名。
- SHA-256 见 `sha256.txt`；签名证书摘要见 `signature.txt`。原 APK 已通过 apksigner 校验，不能由此推断提供者身份或拥有原签名私钥。
- JADX 汇总 94 处错误；扫描 Java 输出另发现 3,109 个 `Method not decompiled` 占位标记，详见 `undecompiled-methods.json`。两者是不同统计口径，不能将反编译 Java 直接作为已恢复可编译源码。

## 2. 主要启动入口与方法

### 主启动链

```text
Android 进程启动
  ├─ Manifest ContentProvider 初始化（可能早于 Application.onCreate）
  └─ TideApplication.onCreate()
       └─ RouterActivity.onCreate(Bundle)  [MAIN / LAUNCHER, singleTask, noHistory]
            ├─ 冷启动：协程初始化 → 启动统计 → 预置资源处理
            │    ├─ 有 URL / tide-open：路由分发 → finish
            │    └─ 普通启动：SplashActivity → MainActivity
            └─ 非根任务/后台入口：按需路由分发 → finish
```

这是一条已核对的主要链路，不能表示所有初始化钩子或所有路由分支都已逐条验证。`SplashActivity.llIllI()` 构造到 MainActivity 的 Intent；实际进入主界面的时机由其承载的启动 Fragment 控制。

| 类 / 方法 | 已确认的职责 | 原始证据 |
|---|---|---|
| `TideApplication.onCreate()` | 启动计时、调用父初始化、通过入口调度器执行初始化动作 | `decompiled/sources/io/moreless/tide/TideApplication.java:613` |
| `RouterActivity.onCreate(Bundle)` | 识别根任务、URI、tide-open；区分冷启动和唤醒 | `decompiled/sources/io/moreless/tide/activity/RouterActivity.java` |
| `RouterActivity.onNewIntent(Intent)` | 更新 Intent、记录唤醒并分发路由，随后关闭路由页 | 同上 |
| `RouterActivity.llIlll(Intent)` | 来源优先级：extra_launch_from → tide-open 通知 → URI → 普通启动 | 同上 |
| `RouterActivity.lllIII()` | 更新 DailyRecord 和 `tide.state.launch_count` | 同上 |
| `RouterActivity.llIllI(ar0)` | 调用预置资源处理；Java 未正确还原，应以 Smali 为准 | `recovered/smali_classes4/io/moreless/tide/activity/RouterActivity.smali` |
| `sg0` case 7 / `llll` case 6 | 冷启动协程及最终跳往 SplashActivity 或分发 URL | `decompiled/sources/defpackage/sg0.java`、`llll.java`；sg0 相关段落同时核对 Smali |
| `e.lIlI(Activity, Intent)` | 优先解析 tide-open，其次 Intent.data，再调用 aq6.I | `decompiled/sources/defpackage/e.java:908` |
| `SplashActivity.llIllI()` | 创建 MainActivity Intent，传递 extras/data/from | `decompiled/sources/io/moreless/tide/activity/scheme/SplashActivity.java:37` |
| `MainActivity.onCreate()` | 主界面生命周期入口 | `decompiled/sources/io/moreless/tide/activity/MainActivity.java:833` |

### 其他可触发应用的入口

- `tide://` 自定义 scheme；`tide.fm` 的声音/冥想/睡前故事分享路径；`app.tide.fm`、`dapp.tide.fm` 的 `/app_redirect`。它们由 Manifest 的 Intent filter 声明，并不证明服务器已部署有效的 App Links 验证文件。
- Launcher 快捷方式见 `recovered/res/xml/shortcuts.xml`。
- 小组件：每日一言、声音、睡眠、小憩、专注等 Provider/Receiver；通知动作和快捷设置 Tile 也是入口。
- 开机/Direct Boot 闹钟恢复、闹钟触发、推送接收等后台入口，见 `components.json`。
- 登录/支付回调 Activity 及第三方 SDK 组件不应被误认为主入口。

### 重点业务维护入口

| 模块 | 类 / 方法 | 维护关注点 |
|---|---|---|
| 音频 | `TidePlayerService.onCreate/onStartCommand/onBind`、`TideHomePlayerService` | 媒体会话、前台服务、焦点、耳机切换、混音、解码与后台播放 |
| 专注 | `FocusService.onCreate/onStartCommand` | 已确认 start/pause/resume/end/finish action；休息、严格模式、统计待完整追踪 |
| 呼吸 | `BreathService.onStartCommand`、`defpackage.cx.l`、`BreathingAction.isScalable` | 当前 v5 配置来源、按频率缩放、完整周期展开 |
| 睡眠 | `SleepService.onStartCommand` | 麦克风、后台运行、睡眠分析、音频和闹钟协作 |
| 小憩 | `SnapService.onStartCommand` | 会话状态、结束原因、闹钟恢复 |
| 闹钟 | `AlarmTriggerReceiver`、DirectBootAlarmReceiver/Service | 精确闹钟、锁屏、重启恢复、跨版本行为 |
| 数据 | `TideDatabase`、FocusRecord、BreathRecord、DailyRecord | 数据结构可分析，旧库迁移需另做版本与兼容测试 |

## 3. 权限

Manifest 有 **47 项显式 uses-permission**。aapt 额外显示 `READ_EXTERNAL_STORAGE` 为 WRITE_EXTERNAL_STORAGE 引入的隐含权限，不能将其误计为第 48 项显式声明。

权限声明不代表已经获准，也不证明每条功能路径都会使用。以下用途是依据组件与权限语义的对应分析，不是逐项动态跟踪结果。

| 分组 | 代表权限 | 可能对应的业务与维护要点 |
|---|---|---|
| 网络 | INTERNET、ACCESS_NETWORK_STATE、ACCESS_WIFI_STATE | 内容下载、账户、服务端和 SDK 网络活动 |
| 录音/相机/位置 | RECORD_AUDIO、CAMERA、ACCESS_COARSE_LOCATION | 睡眠声音分析、拍摄/扫码、定位相关路径；本轮未逐一触发验证 |
| 旧设备读取 | READ_PHONE_STATE、WRITE_EXTERNAL_STORAGE（均 maxSdk 28） | 历史兼容代码；不应不加判断地复制到新版本 |
| 应用可见性/使用情况 | QUERY_ALL_PACKAGES、GET_INSTALLED_APPS、PACKAGE_USAGE_STATS、GET_TASKS | 专注限制/使用情况与厂商适配候选；要审查实际调用和必要性 |
| 常驻/电源 | WAKE_LOCK、前台服务及各类型权限、忽略电池优化、开机接收 | 播放、计时、睡眠、闹钟。不同系统版本存在不同资格和限制 |
| 闹钟/通知 | SET_ALARM、SCHEDULE_EXACT_ALARM（maxSdk 32）、USE_EXACT_ALARM、POST_NOTIFICATIONS、USE_FULL_SCREEN_INTENT | 唤醒、通知与锁屏；声明不等于获得特殊访问许可 |
| 特殊访问 | SYSTEM_ALERT_WINDOW、PACKAGE_USAGE_STATS、ACCESS_NOTIFICATION_POLICY | 涉及系统设置授权，不是普通运行时弹窗权限 |
| 账户/支付/SDK | GET_ACCOUNTS、BILLING、小米账户权限、AD_ID、推送/角标权限 | 需要商户、账户、推送配置及签名授权配合 |
| 壁纸/厂商兼容 | SET_WALLPAPER、DISABLE_KEYGUARD、OPPO/Huawei/Freeme 等 | 部分为历史或厂商特有能力，不能假定任意设备都生效 |

完整显式权限如下（系统 grant 状态未检查）：

| 权限 | maxSdkVersion |
|---|---|
| `android.permission.GET_TASKS` | — |
| `android.permission.WRITE_EXTERNAL_STORAGE` | 28 |
| `android.permission.ACCESS_WIFI_STATE` | — |
| `android.permission.READ_PHONE_STATE` | 28 |
| `android.permission.INTERNET` | — |
| `android.permission.ACCESS_NETWORK_STATE` | — |
| `android.permission.GET_ACCOUNTS` | — |
| `com.xiaomi.sdk.permission.APP` | — |
| `com.xiaomi.permission.AUTH_SERVICE` | — |
| `android.permission.GET_INSTALLED_APPS` | — |
| `android.permission.QUERY_ALL_PACKAGES` | — |
| `android.permission.WAKE_LOCK` | — |
| `android.permission.VIBRATE` | — |
| `android.permission.RECEIVE_BOOT_COMPLETED` | — |
| `android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND` | — |
| `android.permission.BLUETOOTH` | — |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | — |
| `android.permission.SET_WALLPAPER` | — |
| `android.permission.DISABLE_KEYGUARD` | — |
| `android.permission.SYSTEM_ALERT_WINDOW` | — |
| `android.permission.SYSTEM_OVERLAY_WINDOW` | — |
| `android.permission.RECORD_AUDIO` | — |
| `android.permission.FOREGROUND_SERVICE` | — |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | — |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | — |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | — |
| `android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | — |
| `android.permission.ACCESS_NOTIFICATION_POLICY` | — |
| `android.permission.BROADCAST_PACKAGE_CHANGED` | — |
| `android.permission.PACKAGE_USAGE_STATS` | — |
| `oppo.permission.OPPO_COMPONENT_SAFE` | — |
| `com.huawei.permission.external_app_settings.USE_COMPONENT` | — |
| `android.permission.ACCESS_COARSE_LOCATION` | — |
| `com.android.alarm.permission.SET_ALARM` | — |
| `android.permission.SCHEDULE_EXACT_ALARM` | 32 |
| `android.permission.USE_EXACT_ALARM` | — |
| `android.permission.POST_NOTIFICATIONS` | — |
| `android.permission.USE_FULL_SCREEN_INTENT` | — |
| `com.android.vending.BILLING` | — |
| `io.moreless.tide.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | — |
| `com.google.android.gms.permission.AD_ID` | — |
| `freemme.permission.msa` | — |
| `com.huawei.android.launcher.permission.CHANGE_BADGE` | — |
| `com.vivo.notification.permission.BADGE_ICON` | — |
| `io.moreless.tide.permission.MIPUSH_RECEIVE` | — |
| `com.xiaomi.permission.WAKE_LOCK` | — |
| `android.permission.CAMERA` | — |

`permissions.csv/json` 可用于检索。组件导出、前台服务类型和组件保护权限另见 `components.csv/json`。Manifest 的 `usesCleartextTraffic=false` 还有 network_security_config 的域名级例外，不能据此说原版完全禁止明文连接。

## 4. 重构难度与真正的阻碍

结论：**局部离线重写可行；完整等价重写难度高。** APK 足以提供功能和数据结构线索，但不能恢复服务端、原发布密钥、原生库源码或原始变量名。

1. **混淆及反编译缺失**：保留了不少业务类名，但方法名、协程和 Compose 代码大量混淆。必须同时使用 Smali、资源和原应用行为作证，不能“导出 Java → 全部编译”解决。
2. **跨模块状态复杂**：专注、睡眠、小憩、呼吸、音乐、精确闹钟、组件恢复会互相影响。只复制界面会遗漏后台与恢复路径。
3. **原生依赖**：`libphase.so`、MediaPipe 音频、Rive、网络/加密等 18 个 SO 名称构成独立工作面；本轮只盘点，未逆向原生实现。
4. **云端与业务资产**：内容、账号、订阅、支付、推送等需要服务器与运营控制权。本轮未访问这些服务，没有证据判断其是否仍可用。
5. **旧用户升级**：普通覆盖升级需要兼容的应用身份和签名。仅有 APK 中的公开证书不能恢复私钥；若使用商店托管签名，需要控制对应商店账号和发布流程。新包名也不会自动继承旧应用私有数据。
6. **原产品体验**：复杂动画、音频策略、计费权益、统计口径、睡眠分析和适配矩阵都需要需求及回归样本。

以下仅是人员投入的粗估，不是已承诺排期，前提为有经验的 Android 工程师、可提供原版体验样本：

| 范围 | 相对难度 | 参考投入（非承诺） |
|---|---|---|
| 内置声音、基本计时、标准呼吸、离线记录的可用版本 | 中等 | 约 2–6 人周；当前交付仅第一阶段基础版 |
| 睡眠/小憩、闹钟、后台恢复、旧数据迁移及多机型验证 | 高 | 约 8–16 人周，受边界案例影响很大 |
| 云内容、账号、订阅、支付、推送、完整 UI 与算法等价 | 很高 | 服务端及控制权齐全时仍可能需要 6–12 人月；缺失时无法可靠封顶 |

建议优先找回 Git 仓库/备份、mapping.txt、发布流程、后台接口文档与运营账户。任何一项找到，都可能比继续纯反编译节省大量工作。没有这些资料时按模块重写比修补整份反编译 Java 更可控。

## 5. 本次 Android 工程范围

`../TideRebuild/` 是新写的 Java 工程，不依赖原包 DEX/Smali，也不把反编译失败的方法作为生产代码。UI 为重构版原生界面，未声称复原原版视觉。

已实现：
- 7 个本地声音资源与目录映射；循环播放、媒体会话、通知控制、音频焦点处理。
- 专注倒计时、暂停/继续、提前结束、本地历史。
- 当前 config.json 的平衡、4-7-8、箱式三种标准呼吸；按 cx.l 的可缩放动作和完整周期规则生成动作。
- 纯 Java 核心与 Android UI/Service 分离；测试与离线构建脚本；Gradle Wrapper。
- 从原 APK 精确复制的 9 个素材文件均有 SHA-256 溯源。

明确未恢复/与原版不同：
- 原 Router/Splash/全量 deep link 链、引导及全部 UI；重构版直接进入首页。
- 专注严格模式、自动休息与原记录格式；5/15/25/45 分钟为当前工程选择，不宣称原版默认值。
- 计时切后台自动暂停，未实现原版后台计时/闹钟；声音使用前台服务独立播放。
- 呼吸只提供约 1 分钟文本引导；未恢复语音提示、动画、震动和课程。
- 云端声音、海边篝火混音、完整播放器策略、睡眠录音分析、小憩、闹钟、壁纸、组件、账户、支付、会员、推送及原用户数据迁移。
- 毫秒级离散化代替原浮点内部表示；默认三种模式有测试，不代表任意自定义频率都已经一致。

重构包名 `io.moreless.tide.rebuild`，原包名 `io.moreless.tide`。二者并存，测试版不能作为原版的覆盖升级包。

## 6. 官方参考与验证边界

- Android 签名及更新身份：[Sign your app](https://developer.android.com/studio/publish/app-signing)。仅用于解释签名边界，不是本 APK 维护状态证据。
- 构建、真机步骤与通过/未通过项目见工程 `README.md` 与 `VALIDATION.md`。
- 本轮没有对原版进行逐屏行为对照、后端接口调用、收费功能验证或原生库逆向。不能声称完整重构或原逻辑全面等价。
