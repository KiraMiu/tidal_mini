# 潮汐 Android 重构工程 · 第一阶段

这是从提供的潮汐 5.7.0 APK 分析后**重新编写的 Java 工程**。支持部分离线功能，未完成全量功能、原 UI 或服务端重构。反编译源码在工程外的 `../decompiled/`，不参与编译。

测试版应用名：**潮汐 · 离线重构**。包名 **`io.moreless.tide.rebuild`**，与原版 `io.moreless.tide` 不同，可并存，不读取或迁移原应用私有数据。

## 当前实现

| 功能 | 来源与范围 |
|---|---|
| 本地声音 | 原 APK 的 7 个音频及场景映射；循环播放、通知暂停/继续/停止、音频焦点 |
| 专注 | 开始、暂停、继续、结束、历史记录；默认时长和简化界面为新工程选择 |
| 呼吸 | 原 APK 当前 `config.json` 的平衡、4-7-8、箱式；复现默认频率缩放与完整周期展开 |
| 本地记录 | 保存本工程的完成/提前结束记录；非原数据库格式 |

**计时切到后台自动暂停**，回到前台手动继续；本地音频独立使用前台服务。呼吸提供约一分钟文本引导，未恢复语音、动画或震动。箱式呼吸按完整周期执行，因此一分钟练习实际为 64 秒。

未实现：原启动引导及深链路、严格专注/休息、睡眠监测、小憩、精确闹钟、云端内容、混音、会员/支付、登录、推送、旧数据迁移和原生算法。当前版本不适合作为原版的生产替换版本。

## 构建

需要 JDK 17、Android SDK platform 34。标准构建使用 Gradle 8.5 / Android Gradle Plugin 8.2.2，首次需要下载构建依赖。

用 Android Studio 打开此目录；或在此目录执行：

```sh
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。`local.properties` 中设置自己的 SDK 路径；该文件属于本机配置，不放入源码包。

本机已通过另一条无需 Maven 下载的 SDK 构建路径：

```sh
python3 scripts/test.py
python3 scripts/build_offline.py
```

离线脚本默认使用 `$ANDROID_HOME`，未设置则使用 macOS 的 `~/Library/Android/sdk`；需要 platform 34 和 build-tools 35.0.0。可用 `ANDROID_BUILD_TOOLS` 指定已安装版本。输出路径打印在终端，并写入 `build/latest-apk.txt`。每次构建生成独立目录，不删除旧产物。

两条构建路径共享 `build/local-debug.keystore`，不存在时生成本地调试证书。该证书不是原版发布证书；保留它才能继续覆盖本机已经安装的重构测试版。源码压缩包不包含私钥，另一台机器会生成新的开发身份。不要将此调试证书用于生产发布。

## 实现选择

本阶段采用 Java 与 Android 系统 API，避免把原包混淆代码及庞大 SDK 依赖整体带入新工程；这样能先得到可编译、可测试的离线基线。仅复用可以明确溯源的配置和声音。后台闹钟、服务端和算法在证据不足时保留为未实现项，不以空接口伪装完成。

## 代码结构

- `MainActivity.java`：原生页面、前台练习控制、状态恢复和历史。
- `AudioService.java`：本地音频、媒体会话、通知和焦点处理。
- `Catalog.java`：解析场景及当前呼吸配置，不访问网络。
- `core/SessionTimer.java`：由单调时钟驱动的倒计时，不依赖每秒 tick 累减。
- `core/BreathScript.java`：根据原 `defpackage.cx.l(Standard)` 实现缩放和周期展开。
- `core/BreathTimeline.java`：动作时间轴。
- `core/PlaybackIntent.java`：明确区分用户暂停和系统临时打断。
- `tests/CoreTest.java`：暂停、恢复、完成边界和原呼吸规则测试。
- `ASSET_PROVENANCE.json`：9 个复用资源的路径、大小与 SHA-256。

原始证据、组件和权限全表在 `../analysis/`；主要结论见 `../analysis/APK分析与重构评估.md`。

## 继续维护

1. 补全原版对照测试：启动、深链接、专注状态图、记录口径、声音切换与中断。
2. 恢复后台计时、闹钟与睡眠/小憩，覆盖锁屏、权限拒绝、进程回收、重启和厂商省电限制。
3. 拿到服务器与签名/商店控制权后再接入账号、内容、权益及数据迁移。

本工程只申请前台播放、媒体播放类型、通知和唤醒锁四项权限；不申请网络、麦克风、相机、定位、悬浮窗或应用列表权限。详细验证记录与尚未验证项目见 `VALIDATION.md`。
