# HyperFusionWidget

把 HyperOS「融合设备中心」中的设备放到负一屏卡片和桌面小组件，点击设备打开系统原生面板。

当前版本：**0.0.1**，首个公开预览版本。应用名称为「融合设备卡片」，包名为 `dev.fusion.widget`。

[下载 APK](https://github.com/tiancaiProgramer/HyperFusionWidget/releases/latest) · [验证范围](docs/VALIDATION.md) · [实现结构](ARCHITECTURE.txt) · [更新记录](CHANGELOG.md)

## 功能

- 显示系统融合设备中心提供的真实设备、状态和可用电量。
- 根据卡片高度展示 2 / 4 / 6 台设备，支持手动刷新。
- 点击设备打开系统原生设备面板；“更多”直接打开系统完整设备中心。
- 在 App 内一键发起负一屏添加，检查重复卡片，不向系统选择器添加额外按钮。
- 使用系统内容变化任务、曝光刷新和周期任务更新缓存。
- 无网络权限，日常运行不调用 `su`，不修改内核或系统分区。

这张卡片使用 Android RemoteViews。原生设备面板会在点击后弹出，并非嵌入卡片内部。海尔等其他平台接入仍在调研，**本版本没有海尔设备控制功能**。

## 适配范围

目前只在以下组合验证过：

| 项目 | 版本 |
| --- | --- |
| 手机 | Xiaomi MIX Fold 3 |
| HyperOS | OS3.0.304.0.WMVCNXM |
| Android | 16 |
| 设备互联 / MiLink | 17.2.0.1.2601081906 |
| 智能助理 / 负一屏 | 25.31.06-07091932 |
| LSPosed | 2.2.1 (7912)，现代 API 102 |

适配依赖系统私有接口。MiLink 或智能助理版本不匹配时，相关调用会停止；不能把上述结果推广到全部 HyperOS 机型。只装 KSU 不能加载本模块，还需要支持现代 API 102 的 LSPosed 环境。

## 安装

1. 在 Releases 下载并安装 `HyperFusionWidget-0.0.1.apk`。
2. 在 LSPosed 启用「融合设备卡片」，勾选两个作用域：
   - 设备互联：`com.milink.service`
   - 智能助理：`com.miui.personalassistant`
3. 重新启动两个目标应用，或重启手机。更新模块后也需要重新加载作用域。
4. 在桌面打开一次负一屏，让原生小组件宿主初始化。
5. 打开「融合设备卡片」，刷新并确认模块连接；点击「添加到负一屏」。
6. 回到负一屏，卡片会追加到已有卡片之后。已有卡片不会重复添加。

桌面也提供标准 AppWidget 添加入口，但桌面放置与折叠屏内屏布局尚未完成验证。

## 使用与限制

设备状态来自系统缓存，底部显示读取时间；“刷新”会发起有时限的设备发现。后台任务可能受系统调度限制，不能保证设备状态持续实时。

锁屏时不发起设备面板请求。设备必须仍在当前系统列表中；原生面板继续负责认证和实际控制。会直接执行的米家场景不在本版本调用范围内。其他设备类型、后台长期恢复和内外屏切换尚未逐项验证。

若显示模块未连接，检查作用域、组件版本和目标进程是否已重新加载。单设备面板不可用时，可通过“更多”打开系统设备中心。

移除时，在负一屏长按删除卡片，然后在 LSPosed 禁用本模块并重新加载目标应用，最后卸载 App。

## 数据处理

桥接仅传递卡片所需字段，不转发账号凭据、MAC、SSID 或原始私有数据。专用桥接方法校验调用者 UID、唯一包绑定和签名证书；系统原有 Provider 方法保持原调用路径。没有全局禁用 Android 的签名或权限校验。

仓库只包含本项目源码和构建配置，不包含系统 APK、第三方 App 安装包、反编译代码、签名私钥、手机截图或设备缓存。

## 构建

需要 Python 3、JDK 17、Android SDK `platforms;android-35` 和 `build-tools;36.0.0`。

```sh
# 从 Maven Central 下载 libxposed API 102.0.0；脚本会验证固定 SHA-256。
python3 tools/fetch_xposed_api.py

export FUSION_JAVA_HOME="/path/to/jdk17"
export FUSION_ANDROID_SDK="/path/to/android-sdk"
python3 build.py
```

也可使用 `JAVA_HOME` 和 `ANDROID_HOME`。构建输出位于 `dist/`，包含 APK 与 SHA-256 文件；构建过程运行 11 项调用身份和状态解析检查，并验证 APK 签名。

libxposed 仅作为编译依赖，不打入 APK，也不纳入仓库。可用 `FUSION_XPOSED_JAR` 指定已经准备好的 API 102 JAR。

默认在 `.local/` 创建本地开发签名。自行发布时设置 `FUSION_KEYSTORE` 和 `FUSION_KEYSTORE_PASSWORD`，密钥别名为 `fusion`；可用 `FUSION_OUTPUT` 指定输出目录。调用者证书指纹随构建生成，因此所有组件必须使用同一个安装包。更新已安装版本必须保留同一签名私钥。

GitHub Actions 也会构建并运行检查。CI 产物使用临时开发签名，无法覆盖 Releases 中不同签名的安装包；日常安装请下载 Releases APK。

## 反馈

提交问题时请提供机型、HyperOS / Android、MiLink、智能助理和 LSPosed 的版本，以及可复现步骤。请先删除日志或截图中的账号、设备标识等个人信息。
