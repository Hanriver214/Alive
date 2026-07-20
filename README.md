# Alive / 活着

> 一款提醒你「还活着」的安卓开源应用。

Alive 在后台默默守护，每天观察三种最普通的「生命迹象」：

| 事件 | 触发条件 |
| --- | --- |
| **a** 解锁 + 任一应用进入前台 | `ACTION_USER_PRESENT` + `UsageStatsManager` |
| **b** 充电 | `ACTION_POWER_CONNECTED`（亮屏/锁屏均算） |
| **c** 数据流量开关变化 | `ConnectivityManager.NetworkCallback` |

## 工作流（北京时间）

```
00:00  ── 当日监测启动，a/b/c 全部归零
        │
        │  任意 2 个事件被标记  ──►  被动签到（当日停止监测）
        │
12:00  ── 若 a/b/c 全无  ──►  推送常驻通知「你还好吗？」
        │                       └─ 点击「我挺好」──► 主动签到
        │
23:59  ── 仍未签到  ──►  SMTP 自动发邮件到配置邮箱
                          └─ 之后每隔 N 小时重发一次
```

## 主要特性

- 后台常驻（ForegroundService + 电池优化白名单）
- 开机自启
- Room 数据库日志，应用内可查看/导出
- 可配置 SMTP 邮箱、应用密码、标题、正文、重发间隔
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
| `SCHEDULE_EXACT_ALARM` | Android 12+ 精准闹钟 |
| `PACKAGE_USAGE_STATS` | 推断用户是否在使用手机（系统设置手动授权） |

## Changelog

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
