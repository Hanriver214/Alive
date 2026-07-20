# Alive / 活着

> 一款提醒你「还活着」的安卓开源应用。

Alive 在后台默默守护，每天用最普通的行为计分来推断你是否安好。当一天结束仍未签到，它会替你向预设的联系人发一封邮件。

## 工作流（手机系统时区）

时间循环不再锁定北京时间，而是使用手机系统本身的时区，每日 00:00 重置计分。

```
00:00  ── 当日监测启动，所有计分归零
        │
        │  用户行为持续累加分数
        │  分数 ≥ 4  ──►  被动签到（当日停止监测）
        │
12:00  ── 若未签到  ──►  推送通知「你还好吗？」
        │
18:00  ── 仍未签到  ──►  再次提醒（更强提醒）
        │
20:00  ── 仍未签到  ──►  首封邮件通知联系人
        │
22:00  ── 仍未签到  ──►  第二封邮件 / 更强提醒
        │
次日    ── 仍未签到  ──►  按设定间隔继续提醒
                          └─ 点击「我挺好」──► 主动签到
```

## 计分规则

每天 00:00 重置分数，所有计分项独立累计：

| 行为 | 分值 | 触发条件 |
| --- | --- | --- |
| 解锁 | +2 | `ACTION_USER_PRESENT`，每次解锁 |
| 未解锁亮屏 ≥2 分钟 | +1 | 当日累计未解锁状态屏幕亮起总时长 ≥2 分钟时记一次 |
| 解锁后亮屏 ≥30 分钟 | +1 | 当日累计解锁后亮屏总时长 ≥30 分钟时记一次 |
| 其他应用启动 | +1 | `ActivityManager.getRunningAppProcesses()` 检测到非本应用进入前台（当日仅记一次） |
| 充电/拔电 | +2 | `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` |
| 数据流量开关变化 | +1 | `Settings.Global.mobile_data` URI 变化（仅检测开关动作，非流量变化） |
| 手机翻转 | +1 | 重力传感器 z 轴符号变化（明显移动或振动不计分） |

**分数 ≥ 4：符合被动签到条件**，当日停止监测。

## SMTP 加密存储

应用密码使用本机侧加密存储，安全模型如下：

- **加密密码**：用户自订，仅用于派生 AES 密钥
- **密钥派生**：PBKDF2WithHmacSHA256，10000 iterations，256-bit
- **加密算法**：AES/GCM/NoPadding，12 字节 IV，128-bit GCM tag
- **持久化**：仅保存 PBKDF2 哈希 + salt，加密密码本身不存储
- **解锁状态**：仅在内存中保存解密后的应用密码，进程退出后自动锁定
- **忘记密码**：无法恢复，只能「重置加密配置」清除所有 SMTP 设置后重新配置
- **导入导出**：JSON 格式（含加密的 pass + salt + hash），方便设备间转移

## 主要特性

- 后台常驻（ForegroundService + 电池优化白名单）
- 开机自启
- Room 数据库日志，应用内可查看/导出
- 计分制签到（7 类行为独立计分，分数 ≥4 自动签到）
- SMTP 加密存储 + 配置导入导出
- 5 个精准闹钟时间点：00:00 重置 / 12:00 / 18:00 / 20:00 / 22:00
- 次日按设定间隔继续邮件提醒
- 仅请求运行所必需的权限，**不使用无障碍服务**

## 构建

```bash
./gradlew :app:assembleRelease
```

输出 APK：`app/build/outputs/apk/release/app-release.apk`

## 发布与签名

每个 release 由 GitHub Actions 使用 [Sigstore / Cosign](https://www.sigstore.dev/) Keyless (OIDC) 签名。验证：

```bash
cosign verify-blob app-release.apk \
  --bundle app-release.apk.sigstore.json \
  --certificate-identity-regexp 'https://github.com/Hanriver214/Alive/' \
  --certificate-oidc-issuer 'https://token.actions.githubusercontent.com'
```

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `FOREGROUND_SERVICE` | 守护进程常驻 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 前台服务类型 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |
| `INTERNET` | SMTP 发件 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 进入 Doze 白名单，避免被杀 |
| `POST_NOTIFICATIONS` | Android 13+ 推送通知 |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Android 12+ 精准闹钟 |
| `PACKAGE_USAGE_STATS` | 解锁日志中附带"解锁后 5 分钟内应用前台"信息（系统设置手动授权，不影响计分） |

> 不使用无障碍服务，不申请 `QUERY_ALL_PACKAGES` / `WAKE_LOCK` / `ACCESS_NETWORK_STATE`，不读取短信/通讯录/相册/位置。

## Changelog

### v1.3.1

- **重构** 前台应用切换计分改为「其他应用启动」：用 `ActivityManager.getRunningAppProcesses()` 替代 `UsageStatsManager`，当日首次检测到非本应用进入前台即 +1（当日仅记一次）
- **清理** 移除未使用的权限：`QUERY_ALL_PACKAGES` / `ACCESS_NETWORK_STATE` / `WAKE_LOCK`
- **清理** 移除未使用的字符串资源（41 条）、Android Studio 模板颜色（7 个）
- **清理** 移除 DAO / Util / Service 中未调用的方法（`observeDay` / `listDay` / `deleteBefore` / `countByDay` / `startOfToday` / `todayAt` / `isToday` / `UsageStatsHelper.openSettings` / `AliveForegroundService.stop`）
- **清理** 移除未使用的 `androidx.navigation.compose` 依赖
- **优化** 启用 R8 代码压缩 + 资源压缩（`isMinifyEnabled` + `isShrinkResources`），APK 体积显著降低
- **优化** 完善 proguard-rules.pro：保留 Room DAO / WorkManager Worker / DataStore 反射入口
- **修正** README 权限说明、计分规则描述与代码实现一致

### v1.3.0

- **重大变更** 重构为用户计分制（取代原事件标记制）
  - 解锁 +2、未解锁亮屏 ≥2 分钟 +1、解锁后亮屏 ≥30 分钟 +1
  - 前台应用切换 +1、充电/拔电 +2、移动数据开关变化 +1、手机翻转 +1
  - 分数 ≥4 即触发被动签到
- **新增** 屏幕亮屏时长分段统计（`ScreenStateObserver`），用 `KeyguardManager` 区分 locked / unlocked 时段
- **新增** 前台应用切换检测（`ForegroundAppObserver`），30 秒轮询 `UsageEvents`
- **新增** SMTP 应用密码加密存储
  - PBKDF2WithHmacSHA256 派生密钥 + AES/GCM/NoPadding 加密
  - 用户自订加密密码，仅存哈希，忘记后只能重置
  - 内存解锁状态，进程退出后自动锁定
- **新增** SMTP 配置导入导出（JSON 格式，含加密 pass + salt + hash）
- **变更** 时间循环改为系统时区（`ZoneId.systemDefault()`），不再锁定北京时间，每日 00:00 重置
- **变更** 通知时间表扩展为 5 个时间点：00:00 重置 / 12:00 / 18:00 / 20:00 / 22:00
- **变更** 次日按设定间隔继续邮件提醒（`EmailRetryWorker` 周期任务）
- **删除** WiFi 连接变化事件观察（`WifiObserver`）
- **优化** Dashboard 改为计分详情卡片，每项显示累计时长与得分，进度条显示当前分数
- **优化** 日志查看页事件类型颜色映射支持全部 7 种计分类型
- **优化** 所有 Observer 启动加 try-catch 保护

### v1.2.1

- **修复** 应用启动闪退：移除键盘唤起（事件 E）的悬浮窗检测方案，该方案需 `SYSTEM_ALERT_WINDOW` 权限且会在 Service 启动时直接崩溃
- **说明** Android 全局键盘检测无可靠的非权限方案（无障碍服务不可用、悬浮窗需授权），因此移除事件 E
- **优化** 所有 Observer 启动添加 try-catch 保护，单个模块异常不影响整体启动
- **保留事件** A 解锁+应用前台 / B 充电/断电 / C 数据流量开关 / D 手机翻转 / F WiFi 连接变化（共 5 个）

### v1.2.0

- **新增** 事件 D：手机姿态变化（翻转检测），使用重力传感器检测 z 轴符号变化
- **新增** 事件 E：输入法键盘唤起，通过窗口布局变化检测键盘弹出
- **新增** 事件 F：WiFi 连接状态变化，使用 ConnectivityManager（不使用定位权限）
- **修改** 事件 B：充电行为扩展为充电 + 断电（拔充电线），亮屏/锁屏均触发
- **更新** 监测事件总数从 3 个扩展为 6 个，签到条件仍为任意 2 个事件触发

### v1.1.2

- **修复** Gmail 587 端口 `[EOF]` 进一步加固：信任所有主机、关闭主机名校验、开启 JavaMail debug 日志
- **新增** SMTP 网络诊断按钮：先探测原始 TCP 是否连通，区分网络问题与配置问题
- **新增** 智能错误提示：检测到 EOF/Auth 失败时自动给出解决建议
- **新增** Gmail 587 端口使用警告，引导用户切换 465 端口

### v1.1.1

- **修复** Gmail 等 587 端口 STARTTLS 握手失败（`[EOF]`），增强 TLS 配置（显式信任主机 + TLSv1.2）
- **新增** Dashboard 邮件通知状态卡片，随时查看邮件配置与发送状态
- **新增** 首页「立即发送提醒邮件」按钮，无需等待自动触发
- **新增** 设置页 SMTP 服务商快速选择（Gmail / QQ / 163），一键填充服务器和端口

### v1.1.0

- **新增** 日志导出功能：支持将 Room 日志导出为 CSV 并通过系统分享发送
- **新增** SMTP 测试邮件按钮：在设置页可直接发送测试邮件验证配置
- **新增** 设置页显示版本号
- **优化** 完善文件分享支持（FileProvider）

## License

MIT © Hanriver214
