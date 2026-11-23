# My Chat（Android）

把 DeepSeek 官方开源的 [chat-demo](https://github.com/deepseek-ai/chat-demo) 打包成 Android App 的 WebView 壳。
前端资源内置在 APK 里，UI 离线可用；聊天、搜索、语音等请求直连 `api.deepseek.com`。

> **不含任何 API Key。** App 第一次打开会让你在设置里填自己的 Key，只保存在本机 WebView 的 localStorage / IndexedDB 中，不会打包进安装包。

## 功能

- 官方 demo 的完整前端：多会话、流式输出、深度思考、智能搜索与引用、链接阅读、附件上传、Markdown / KaTeX / Mermaid
- 移动端适配：窄屏布局、录音时输入框实时频谱、中英双语、明暗主题
- 壳层补齐：
  - 「+」面板（拍照 / 相册 / 文件），内联展开不遮罩；最近照片条带勾选态、可切换移除
  - 系统文件选择器（附件、历史导入），accept 声明归一化成合法 MIME
  - 选中文件先拷到应用缓存再交给网页：179MB 历史包导入从 ~10 分钟降到 ~20 秒
  - 麦克风权限（语音输入）
  - blob 导出 → 系统「另存为」（历史记录导出 ZIP）
  - 外部链接交给系统浏览器打开
  - 返回键先回退网页历史，再按返回退出
  - 跟随系统深色模式的状态栏 / 导航栏配色
  - 调试包支持 chrome://inspect，前端 console 转 logcat（tag `DSWeb`）
- 设置里提供「API 控制台（去充值）」与「官方网页版」两个外链
- **局域网同步（0.2.0 起暂不上线）**：代码保留，但默认构建不包含该入口；需要时用 `SYNC=1` 构建（见下文）

## 发布版本

| 版本 | 定位 | 关键变化 |
|---|---|---|
| 0.2.4 | **当前发布（首个 GitHub Release）** | 会话分组、导入合并去重、设置页显示版本号 |
| 0.2.0 | 兼容版 | 双构建（modern chrome105 / legacy chrome60）、原生 WebView 版本提示、文件管理「打开方式」导入、导入进度百分比；**不含云同步** |
| 0.1.9 及更早 | 内测 | 单构建、含局域网同步 |

云同步开关是编译期常量 `__ENABLE_SYNC__`（`rspack.config.ts` 里由 `SYNC=1` 注入）。默认关闭时，同步设置区块与启动自动同步都会被 DCE 移除，包内不含「局域网同步 / 立即同步 / 配对码」等字符串。要重新开启：

```bash
SYNC=1 pnpm build && LEGACY=1 SYNC=1 pnpm build
```

## 目录

```
app/src/main/
├── assets/www/                 # chat-demo 构建产物（离线资源，已入库）
├── java/com/qiuzh/deepseekchat/
│   ├── MainActivity.java       # WebView 壳：资源代理、文件选择、相机、相册、下载
│   └── FileProviderLite.java   # 相机临时文件的极简 ContentProvider
├── res/
└── AndroidManifest.xml
frontend/                       # 官方前端源码 + 本项目的改动（见 frontend/README-MODS.md）
scripts/build.sh                # 一键构建
```

## DSH 插件（可选）

仓库里还有两个 DSH 插件，配合使用体验更完整：

| 插件 | 作用 |
|---|---|
| `plugins/dsh-chat` | 在 DSH 里加「对话」面板：药丸切换、纯聊天会话（无工具）、复用原生输入框/模型/流式/历史、可配置存放目录 |
| `plugins/dsh-sync` | 局域网同步服务端：配对码、会话与图片接口、「同步」状态面板 |

```bash
dsh plugin --profile <profile> add file:D:/path/to/ds-chat-android/plugins/dsh-chat
dsh plugin --profile <profile> add file:D:/path/to/ds-chat-android/plugins/dsh-sync
dsh --profile <profile>
```

## 构建

需要 JDK 17、Android SDK（platform 34 + build-tools 34.0.0）、Gradle 8.7。

```bash
# 改成本机的 SDK 路径
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew assembleRelease        # 或 scripts/build.sh
# 产物：app/build/outputs/apk/release/app-release.apk
```

release 默认复用 debug 签名（方便自测安装），正式分发请换成自己的 keystore。

## 更新前端

```bash
cd frontend
pnpm install && pnpm build
cp -r dist/* ../app/src/main/assets/www/
```

## 局域网同步（配合 DSH 插件）

手机与电脑在同一 WiFi 时，可以把会话同步到电脑上的 `dsh-sync` 服务：

1. 电脑装 [dsh-sync](plugins/dsh-sync/README.md) 插件，打开 DSH 的「🔄 同步」面板，记下**配对码**和**局域网地址**
2. 手机 App → 设置 → 数据管理 → 局域网同步：填地址（如 `http://192.168.1.4:62999`）与配对码
3. 点「立即同步」：本地较新的会话推到电脑，电脑上较新的会话拉回手机

实现要点：WebView 无法直连局域网 HTTP（CORS + 混合内容），所以请求经 `DSNative.syncRequest` 交给原生发；拉取的会话写入 IndexedDB 后直接刷新列表（**不能用 replaceHistory**，它会用空 staging 覆盖整库）。

## 首次使用

1. 打开 App → 设置（左下角）→ 填 DeepSeek API Key
2. 可选：设置语言、位置；「数据管理」里导入从 DeepSeek App 导出的 ZIP（会覆盖本地记录）
3. API Key 与聊天记录只在本机

## 白屏排查

安装后白屏（重开、清后台都没用）基本是「系统 WebView 太旧」。0.1.7 起用**双构建**解决：

| WebView 版本 | 使用哪个包 | 说明 |
|---|---|---|
| Chrome ≥ 105 | `www/modern/` | 新语法，体积最小、启动最快 |
| Chrome 60 ~ 104 | `www/legacy/` | 重新转译（含 node_modules），语法降到 ES2018 |
| Chrome < 60 | 原生提示页 | 不发页面，直接告诉用户更新 WebView / 系统 |

选择发生在 `www/index.html` 里一段 **ES5 loader**：解析 UA 的 `Chrome/xx`，动态插入对应的 css/js。这段 loader 不依赖任何构建产物，所以老内核也能跑；页面起不来时它会显示错误面板（错误信息、UA、缺失特性、复制按钮）。

另外：
- `index.html` 以 `no-store` 返回，URL 带 `?v=<versionCode>`，版本号变化时清一次 WebView 缓存；
- `src/app/polyfills.ts` 补了 `Object.hasOwn`、`structuredClone`、`Object.fromEntries`、`Array.prototype.at/flat/flatMap/findLast`、`String.prototype.replaceAll/matchAll`、`Promise.any/allSettled`、`globalThis`、`queueMicrotask`、`AbortController`、`crypto.randomUUID`。

## 双构建与校验

```bash
cd frontend
pnpm build                       # modern -> dist
LEGACY=1 pnpm build              # legacy -> dist-legacy
python ../scripts/assemble-www.py dist dist-legacy ../app/src/main/assets/www

# 校验
node ../scripts/syntax-check.js dist-legacy 2018   # legacy 必须是 ES2018 可解析
python ../scripts/verify-apk.py                    # APK 里两份包都存在且非空
```

`app/build.gradle.kts` 的 `verifyWebAssets` 会在打包前检查 `modern/` 与 `legacy/` 的 `main.*.js` 是否为空、以及 `index.html` 是否引用了它们——之前出现过一次 0 字节脚本，装出来正好是白屏。

## 已知限制

- 导出超大历史（100MB+）时 ZIP 在 WebView 里整块生成再 base64 过桥，内存压力大，可能失败；导入侧已优化。
- 一条请求里图片 ≥15 张时，服务端拒绝单边 >4096px 的图片；本项目已在上传前自动缩到 4096。
- 前端协议跟随 chat-demo v0.1.1，官方升级后需要同步 `frontend/`。

## 许可

- `frontend/` 与 `app/src/main/assets/www/` 来自 [deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo)，MIT，许可证见 `app/src/main/assets/www/LICENSE`。
- 本项目是个人封装的非官方客户端，与 DeepSeek 无隶属关系；请求直接调用官方 API，按 token 计费。

## 下载安装

**最新安卓版：[android-v0.2.4](https://github.com/xiaoyiqiuya/my-chat/releases/tag/android-v0.2.4)**

- 安装包：`my-chat-0.2.4.apk`（3.7 MB，versionCode 15）
- 要求：Android 8.0（API 26）及以上
- 首次启动需在设置里填入自己的 DeepSeek API Key（[获取](https://platform.deepseek.com)）

## 多端形态

| 端 | 说明 |
|---|---|
| **Android App** | 本仓库主体，官方 chat-demo 前端 + 原生 WebView 壳 |
| **DSH 插件** | `plugins/dsh-chat`，在 DeepSeek Harness 里以「HARNESS ｜ Chat」双模式使用，Chat 走 DSH 已配置的模型、无需 API Key |
| **Obsidian 插件** | `obsidian-plugin/`，在 Obsidian 内嵌同一份前端 |
| **桌面应用** | `desktop/`，Electron 封装 |

## 许可

[MIT](LICENSE)

