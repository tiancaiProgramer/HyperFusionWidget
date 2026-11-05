# HyperFusionWidget / 融合设备卡片

把 HyperOS 的 MiLink 融合设备放到负一屏和桌面，点击打开系统原生设备面板。当前本地版本 **0.0.11**，包名 `dev.fusion.widget`，仍是 LSPosed 模块。

本项目仅负责 MiLink 设备来源、系统面板、负一屏和桌面组件。此前试验性的海尔账号与云端控制实现已从本项目移出。

## 融合设备卡片

- 保留 MiLink 导出、同源设备目录及原生电视来源；来自 MiLink 的米家条目仍按 MiLink 面板操作。
- 首页两列，常驻、隐藏及排序由用户选择；断连保留占位，不直接移除常驻项。
- 设备名称按 ID 缓存，电视离线时仍显示最后的真实名称；在线发现的新名称会同步更新。
- 刷新位于标题旁。设备不可用时先重新发现，再决定是否打开系统面板。
- 负一屏和桌面使用独立 Provider。重复添加先由本 App 查询自己的绑定，再由经过校验的 Hook 请求返回结果。
- MiLink Hook 增加 bridge v2 握手，避免更新 APK 后实际仍使用旧进程代码。

## 启用

在 LSPosed 启用「融合设备卡片」，作用域仅保留：

- `com.milink.service`（互联互通服务）
- `com.miui.personalassistant`（智能助理）

更新后重新加载目标进程或重启手机，再回 App 确认连接。桌面添加仍受 HyperOS「创建桌面快捷方式」权限影响。

MiLink 标准基线为 `17.2.0.1.2601081906`，兼容模式可尝试其他版本；负一屏自动添加实测基线为智能助理 `25.31.06-07091932`。其他版本不保证接口相同。

## 构建

JDK17，Android platform35 / build-tools36，编译用 libxposed API102。依赖下载有固定校验：

```sh
python3 tools/fetch_xposed_api.py
FUSION_ANDROID_SDK=/path/to/sdk FUSION_JAVA_HOME=/path/to/jdk17 python3 build.py
```

通过 `FUSION_KEYSTORE` 和 `FUSION_KEYSTORE_PASSWORD` 使用自己的签名，`FUSION_OUTPUT` 指定输出。生成 APK 与 SHA-256；运行63项身份、展示、版本、电视及名称检查。

CI 使用临时开发签名；不能覆盖与其签名不同的正式安装包。安装包从 [GitHub Releases](https://github.com/tiancaiProgramer/HyperFusionWidget/releases/tag/v0.0.11) 下载。

## 验证与边界

[验证记录](docs/VALIDATION.md) · [更新记录](CHANGELOG.md)

2026-11-05 已恢复「客厅的小米电视」名称，MiLink Hook 握手及13台系统设备读取通过；六项 MiLink 常驻选择保留。

本轮只做读取和界面验证，没有执行实体设备控制。电视当时不可用，未验证真实改名事件或电视在线控制。
