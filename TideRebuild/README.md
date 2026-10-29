# 潮汐Mini · 0.4.2

当前重点是 **睡眠闹钟 + 声音后台播放**。包名仍为 `io.moreless.tide.rebuild`，与原版 `io.moreless.tide` 并存。专注、呼吸、记录入口已禁用，旧代码与旧数据保留。应用不录音，也不监测呼吸。

## 使用

1. 在睡眠卡片上滚动选择起床时间；时间已过去时设为次日。当前是单次闹钟，不是每天重复。
2. 点击声音卡片选择声音。仅保留 8 项完整音源：海洋、雨天、冥想、森林、篝火、光、挪威的森林、绵雨。所有声音均可离线播放；绵雨使用 5 分 7 秒原版完整音频，支持循环播放。试听音源已移出声音列表及安装包。可独立播放/暂停，也可开始睡眠时自动播放。
3. 点击“开始睡眠”；允许通知和“闹钟和提醒”后再次点击。页面会显示实际已调度的日期和时间。
4. 可关屏。到点后停止助眠声音，播放闹钟，支持“再睡30分钟”与“起床”。音量键在响铃页控制系统闹钟音量。
5. 睡眠中页面显示上下排列的时钟、声音和起床时间；按住“长按结束”1.5 秒，圆环填满后取消闹钟并停止声音；提前松手、移出按钮或离开页面会取消本次长按。播放/暂停只控制声音，不取消闹钟。

闹钟音量独立于媒体音量，可在主界面右上角“设置”中调节。系统闹钟音量为零时首页有提醒。默认使用原 APK 的“光”。在“闹钟铃声”中可选择原版声音、系统闹钟铃声，或通过系统文件选择器导入音频。导入文件上限 50 MB，以 8 KiB 缓冲流式导入，保存到应用设备保护存储，不依赖源文件后续的位置或 URI 授权；保留上一个选择直到导入成功。所选声音加载失败时回退到系统默认闹钟铃声，再失败则使用 APK 内置“光”；同步和异步失败均有处理。每轮响铃 1 分钟，无操作时暂停 4 分钟，最多响 3 轮；第三轮结束后取消本次闹钟。点击“再睡30分钟”后，从点击时刻起延迟 30 分钟，重新开始最多三轮的提醒；点击“起床”立即停止并取消全部后续轮次。轮次、截止时间和下次触发时间持久化，响铃截止与重试均由系统 AlarmManager 调度，服务和页面另有到期检查。

用户界面不提供静音测试。内部验证由独立 instrumentation APK 执行（`tests/android/AlarmChecks.java`），不将测试组件或入口打入用户应用；测试安排为 30 秒，主播放器、fallback 和焦点恢复均保持零音量，通知使用无声频道。不改系统音量。

## 后台与恢复

- 助眠声音：mediaPlayback 前台服务、媒体通知、音频焦点、播放期间 MediaPlayer 唤醒锁；`START_STICKY` 与持久化用户播放意图用于系统回收后的恢复。手动暂停不会因恢复流程重新播放。
- 闹钟：`AlarmManager.setAlarmClock` 独立于页面和进程；不依赖 Handler 倒计时，也不要求屏幕常亮。
- 闹钟状态使用设备保护存储，接收 Direct Boot/正常开机、应用更新、系统时间与时区变更。重启仅恢复闹钟，不自动开启整夜助眠音频。
- 广播与操作带有会话 token，旧闹钟不能误触发已替换的新会话。过期超过十五分钟的闹钟标记为错过，不突然补响；这个窗口是新工程策略。
- 未授权精确闹钟时不假装设置成功；通知及锁屏全屏提醒有系统设置入口。另有厂商锁屏显示/后台弹出权限指引。小米/HyperOS 的“锁屏显示”“后台弹出界面”需要单独允许；Android 全屏提醒权限开启并不代表这两项已开启。响铃期间启动或返回 APP 自动进入响铃页，过期响铃不会继续弹出。
- 使用系统全屏通知拉起锁屏页；前台服务通知在 API 31+ 立即显示，不通过后台直接启动 Activity 绕过系统权限。

**能力边界：** 前台服务不等于永不被杀。用户强行停止应用、设备关机、撤销权限或厂商禁止后台/自启动仍可能影响闹钟。建议在系统中允许后台无限制、自启动并锁定最近任务。重开应用后会重新核对待设闹钟。不能承诺 OEM 全机型或整夜连续运行已经验证。

## UI 与美术

已复用 APK 中的睡眠地平线/夜间背景、闹钟圆环、Inter 时钟字体、通知图标及声音场景图片，素材见 `SLEEP_ART_PROVENANCE.json`，SHA-256 校验在测试脚本内。

原版 `tide://sleep` 页面、`fragment_sleep_alarm.xml`、`layout_text_color_clock_view.xml`、`layout_main_clocktext_with_scene.xml` 及长按结束的 Compose 实现作为参考。睡眠中界面与时间设置页分离，响铃页保留上下时钟，起床按钮使用 84 dp 高的绿色圆角背景及 28 sp 大字。通知使用原版通知图标、起床/再睡30分钟文案，播放通知提供背景封面和媒体控制。通知外框由 Android/厂商系统渲染。主界面卡片按内容高度收缩，在设置工具栏下的可用窗口内居中，宽度最多 480dp，小窗口内容超高时滚动；图标嵌入按钮文字旁，左右占位对称以保持文字中心线。首页已移除 TIDE 标识和“不录音、不监测呼吸。可锁屏播放”提示，实际权限/音量异常提示保留。声音弹窗以及设置、铃声选择、后台说明等应用内弹窗使用背景快照模糊、半透明叠层和圆角裁切，左上角为返回箭头；播放/暂停和长按结束使用矢量图标加文字。光的封面来自 APK 配置中的官方地址，挪威的森林封面来自 APK 内置资源。启动图标原样使用用户提供的图片，来源见 `APP_ICON_PROVENANCE.json`。当前是原生 Android View 重建；没有声称像素级还原，也未恢复原 Compose 动画、所有弹层、在线背景和全量导航。

## 构建与文件

JDK 17、SDK platform 34，Gradle 8.5 / AGP 8.2.2：

```sh
./gradlew :app:assembleDebug
python3 scripts/test.py
```

输出 `app/build/outputs/apk/debug/app-debug.apk`。设置自己的 `local.properties` 或 `ANDROID_HOME`。另提供离线 SDK 工具链（build-tools 默认 35.0.0）：

```sh
python3 scripts/build_offline.py
```

历史试听音频及来源清单已移至工程外 `../analysis/full-only-v032/retired-previews/`，不进入 APK。完整版绵雨通过官方公开分享页正常返回的临时播放地址取得；12,339,888 字节，307.06068 秒，计算出的资源哈希与 APK 配置完全一致，来源见 `FULL_SOUND_PROVENANCE.json`。声音恢复脚本只依据完整版来源清单重建目录，不再下载或加入试听。升级时旧绵雨试听选择转为完整版；其他移除的背景试听回退海洋、闹钟试听回退光。系统铃声、自定义铃声及原有完整音源选择保持不变，迁移不改闹钟时间、会话 token 或播放意图。

两条构建链共享 `build/local-debug.keystore`；保留此本地调试证书才能继续覆盖当前测试版。源码包不带私钥，换电脑构建需自行管理调试身份。它不是原版发布证书，不用于生产发布。

主要新类：`SleepActivity`、`SleepAlarm`、`SleepStore`、`AlarmReceiver`、`AlarmService`、`WakeActivity`、`SleepUi`；播放仍由 `AudioService` 负责。纯 Java 时间规则在 `core/SleepTime.java`。

当前只申请播放前台服务、通知、唤醒锁、精确闹钟、全屏提醒和开机接收相关权限，无网络、麦克风、相机、位置、悬浮窗权限。第一阶段文档保留在 `docs/phase1/`；当前验证结论见 `VALIDATION.md`。

## 官方约束参考

- [系统闹钟](https://developer.android.com/develop/background-work/services/alarms)
- [后台启动前台服务的限制与精确闹钟例外](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Doze 与 App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Direct Boot](https://developer.android.com/privacy-and-security/direct-boot)
- [锁屏紧急通知](https://developer.android.com/develop/ui/views/notifications/time-sensitive)
