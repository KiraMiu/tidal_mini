# 潮汐 Mini

潮汐 Mini 是一个 Android 重构项目。原版应用长期未更新、原开发者停止维护后，本项目基于已获取的代码、资源与实际行为，逐步建立可独立构建和维护的应用。

当前版本为 **0.4.2-mini**，重点是睡眠闹钟与离线助眠声音。使用 Java 和 Android 系统 API，包名为 `io.moreless.tide.rebuild`，可与原版应用并存。

## 当前功能

- **睡眠闹钟**：选择起床时间并设置单次闹钟，时间已过去时安排到次日；支持取消和再睡 30 分钟。
- **响铃规则**：每轮响铃 1 分钟，无操作时暂停 4 分钟，最多响 3 轮；稍后提醒会重新开始轮次。
- **离线声音**：海洋、雨天、冥想、森林、篝火、光、挪威的森林、绵雨，共 8 项完整音源，支持循环播放和独立暂停。
- **铃声选择**：支持内置声音、系统闹钟铃声，以及通过系统文件选择器导入不超过 50 MB 的音频。
- **后台播放与提醒**：通过前台服务播放助眠声音，通过系统 AlarmManager 调度闹钟，并提供通知、全屏提醒及厂商权限设置指引。
- **界面**：睡眠时间选择、睡眠中与响铃页面，以及半透明模糊背景的声音和设置弹窗。

专注、呼吸、记录的旧代码与数据保留，当前入口已禁用。录音、睡眠分析、云内容、账号功能以及原版全量 UI 和动画尚未恢复。

## 开始使用

1. 设置起床时间，并选择需要的助眠声音。
2. 点击“开始睡眠”，按提示允许通知和精确闹钟权限；完成授权后再次点击。
3. 检查应用显示的闹钟日期和时间。睡眠期间可以暂停声音；取消睡眠需长按结束按钮 1.5 秒。
4. 闹钟响起时，选择“起床”结束，或选择“再睡30分钟”延后提醒。

闹钟音量与媒体音量独立。锁屏全屏提醒还取决于系统和厂商权限；小米 / HyperOS 设备可能需要额外允许“锁屏显示”和“后台弹出界面”。

## 构建

最低支持 Android 7.0（API 24），当前 compileSdk 和 targetSdk 均为 34。

准备 JDK 17、Android SDK Platform 34，以及可用的 Android SDK Build Tools。工程使用 Gradle 8.5 和 Android Gradle Plugin 8.2.2。在本机设置 `ANDROID_HOME`，或创建不纳入 Git 的 `TideRebuild/local.properties` 并填写 SDK 路径。

在仓库根目录执行：

```sh
cd TideRebuild
./gradlew :app:assembleDebug
```

APK 输出到 `TideRebuild/app/build/outputs/apk/debug/app-debug.apk`。首次使用 Gradle 构建时，需要下载 Gradle 和相关依赖。

如果本机已安装 JDK 17、SDK Platform 34 和 Build Tools 35.0.0，也可以从 `TideRebuild/` 目录使用离线构建脚本：

```sh
python3 scripts/build_offline.py
```

离线脚本不下载依赖，产物位置会在命令结束时输出，并记录在 `build/latest-apk.txt`。

两条构建链共用本地调试密钥 `TideRebuild/build/local-debug.keystore`，缺失时会生成。该密钥不纳入 Git；如需覆盖安装现有测试版，必须保留并使用相同签名。它不是原版发布证书。

## 验证与限制

在 `TideRebuild/` 目录运行：

```sh
python3 scripts/test.py
```

脚本检查核心时间与状态规则、资源哈希及部分静态约束。通过脚本不代表 Android 后台调度、锁屏弹窗或整夜播放已经通过真机验证。

现有验证记录包含 0.4.2 在 Android 16 / HyperOS 设备上的短时静音锁屏测试：允许相应厂商权限后，观察到自动亮屏并显示响铃页。该记录不能证明有声提醒、Doze、重启、长时省电或整夜可靠性。强行停止应用、关闭设备或撤销权限也可能影响提醒。

详细证据与各版本未覆盖项见 [验证记录](TideRebuild/VALIDATION.md)。本 README 汇总已有实现和记录，不代表重新执行过这些验证。

## 目录

| 路径 | 内容 |
| --- | --- |
| `TideRebuild/` | 当前维护的 Android 工程、测试脚本与资源来源清单 |
| `analysis/` | 分析结论、历史验证记录和证据 |
| `docs/`、`plans/` | 已有方案与阶段记录 |
| `tools/` | 已纳入版本管理的构建辅助文件 |
| `交付/` | 已纳入版本管理的校验清单和部分素材 |

原版 APK、`decompiled/`、`recovered/`、下载的分析工具、构建缓存及历史 APK / ZIP 交付包通过 `.gitignore` 保留在本地，克隆仓库不会包含这些文件。应用构建所需资源已放在 `TideRebuild/app/src/main/`。

## 进一步阅读

- [工程说明与详细行为](TideRebuild/README.md)
- [验证记录与能力边界](TideRebuild/VALIDATION.md)
- [声音资源来源](TideRebuild/FULL_SOUND_PROVENANCE.json)
- [睡眠界面素材来源](TideRebuild/SLEEP_ART_PROVENANCE.json)
- [应用图标来源](TideRebuild/APP_ICON_PROVENANCE.json)

复用素材的来源按工程中的 `*_PROVENANCE.json` 记录追溯。
