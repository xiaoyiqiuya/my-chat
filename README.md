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

## 首次使用

1. 打开 App → 设置（左下角）→ 填 DeepSeek API Key
2. 可选：设置语言、位置；「数据管理」里导入从 DeepSeek App 导出的 ZIP（会覆盖本地记录）
3. API Key 与聊天记录只在本机

## 已知限制

- 导出超大历史（100MB+）时 ZIP 在 WebView 里整块生成再 base64 过桥，内存压力大，可能失败；导入侧已优化。
- 一条请求里图片 ≥15 张时，服务端拒绝单边 >4096px 的图片；本项目已在上传前自动缩到 4096。
- 前端协议跟随 chat-demo v0.1.1，官方升级后需要同步 `frontend/`。

## 许可

- `frontend/` 与 `app/src/main/assets/www/` 来自 [deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo)，MIT，许可证见 `app/src/main/assets/www/LICENSE`。
- 本项目是个人封装的非官方客户端，与 DeepSeek 无隶属关系；请求直接调用官方 API，按 token 计费。
