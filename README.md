# My Chat（Android）

把 DeepSeek 官方开源的 [chat-demo](https://github.com/deepseek-ai/chat-demo) 前端打包成安卓 App。

界面与官方 demo 完全一致，前端资源内置在安装包里，**离线也能打开界面**；聊天、搜索、语音等请求由 App 直连 `api.deepseek.com`。

> **不含任何 API Key。** 首次使用需要填自己的 Key，只保存在手机本机的 WebView 存储里，不会打包进安装包、也不会传给任何第三方。

## 下载安装

**最新版本：[android-v0.2.4](https://github.com/xiaoyiqiuya/my-chat/releases/tag/android-v0.2.4)**

| 项目 | 说明 |
|---|---|
| 安装包 | `my-chat-0.2.4.apk`（3.7 MB） |
| 版本 | versionCode 15 / versionName 0.2.4 |
| 包名 | `com.qiuzh.deepseekchat` |
| 系统要求 | Android 8.0（API 26）及以上 |

安装步骤：

1. 下载上面的 `my-chat-0.2.4.apk`
2. 点击安装，系统提示时允许「安装未知来源应用」
3. 装好后打开 App，按下面的「首次使用」配置 API Key

SHA256 校验（可选）：

```
4CE236485532446B0AAC7D7E8C273917F9142F85B01F9E4013731506BE37BAEF
```

## 功能

### 对话

- 官方 chat-demo 完整能力：多会话、流式输出、深度思考、智能搜索与引用、链接阅读、附件上传
- Markdown / 代码高亮 / 公式（KaTeX）/ 流程图（Mermaid）正常渲染
- 语音输入（麦克风权限）、朗读回复（可选音色）
- 中英双语、浅色 / 深色 / 跟随系统主题
- 会话搜索：按标题与消息正文搜索，结果带关键词高亮

### 会话管理

- 侧边栏会话列表，按最近更新排序
- **分组**：新建 / 重命名 / 删除分组，拖拽会话进组，也可切回平铺视图
- 重命名、删除单条会话

### 数据

- 所有会话、消息、图片都保存在手机本地
- **导入历史对话**：支持 DeepSeek 官方导出的 ZIP
  - 覆盖导入：用压缩包替换当前全部对话
  - 合并导入（去重）：保留本地对话，只并入新内容
- **导出所有历史对话**：生成官方 ZIP，可导入 chat.deepseek.com 或其他客户端继续使用

### 移动端适配

- 窄屏布局，侧栏可收起
- 「+」面板：拍照 / 相册 / 文件，内联展开不遮挡对话
- 系统文件选择器选择附件与历史包
- 麦克风权限申请、录音时输入框显示实时频谱
- 外部链接交给系统浏览器打开
- 返回键先返回上一页，再按返回退出 App
- 状态栏 / 导航栏跟随深浅色主题

## 首次使用

### 第 1 步：获取 API Key

1. 打开 [platform.deepseek.com](https://platform.deepseek.com)，注册 / 登录
2. 进入「API Keys」→ 创建新的 Key（形如 `sk-xxxxxxxx`）
3. 复制保存好（Key 只显示一次；充值入口同页面的「充值」）

> 调用按 token 计费。App 本身免费，不收取任何费用。

### 第 2 步：填写 API Key

1. 打开 My Chat，点左下角 **设置**
2. 进入 **数据管理** 页，在「输入 API Key 开始使用」处粘贴 Key
3. 保存后即可正常对话

Key 只保存在本机 WebView 存储中；卸载 App 或清除应用数据会一起删除。

### 第 3 步：按喜好调整（可选）

设置 → **通用设置**：

| 项目 | 说明 |
|---|---|
| 主题 | 浅色 / 深色 / 跟随系统 |
| 语言 | 简体中文 / English |
| 朗读音色 | 海星（女声）/ 潮汐（男声） |
| 位置信息 | 选择省份 / 城市，联网类回答可参考 |
| 版本 | 显示当前 App 版本号 |

### 第 4 步：导入旧历史（可选）

如果之前在官方网页版 / 其他客户端用过：

1. 在那边导出 ZIP（官方格式）
2. App → 设置 → 数据管理 → **导入历史对话**
3. 选择「覆盖导入」或「合并导入（去重）」，选中 ZIP 等待完成

## 常见问题

**打开是白屏怎么办？**

极少数情况是系统 WebView 版本过旧。App 内置两套前端构建：Chrome ≥105 用新版包，Chrome 60~104 自动切旧版包；更低版本会直接显示提示页，引导更新 WebView。先重开 App 试试，仍白屏可更新「Android System WebView」。

**提示 API Key 无效 / 额度不足？**

到 [platform.deepseek.com](https://platform.deepseek.com) 检查 Key 是否被删除、账户是否还有余额。App 内「设置 → 数据管理 → API 控制台」可直接跳转充值页。

**智能搜索没有联网结果？**

「智能搜索」需要在输入框下方手动点亮后再提问；该能力依赖官方 API Key 的可用范围。

**导出的 ZIP 打不开 / 导入失败？**

只支持 DeepSeek 官方导出的 ZIP。超大历史包（100MB 以上）导出时可能因内存不足失败，建议分批处理或改用合并导入。

**数据会上传吗？**

不会。会话、图片、API Key 全部存在手机本地；只有你主动发消息时，请求才会直连 `api.deepseek.com`。

## 版本记录

见 [CHANGELOG.md](CHANGELOG.md)。

## 开发与构建

需要 JDK 17、Android SDK（platform 34 + build-tools 34.0.0）、Gradle 8.7。

```bash
# 改成本机的 SDK 路径
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

- 前端源码在 `frontend/`，构建产物放在 `app/src/main/assets/www/`
- release 默认复用 debug 签名（方便自测安装），正式分发请换成自己的 keystore

## 许可与声明

- 前端来自 [deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo)（MIT），许可证见 `app/src/main/assets/www/LICENSE`
- 本项目代码以 [MIT](LICENSE) 许可
- 本项目是个人封装的**非官方客户端**，与 DeepSeek 无隶属关系；请求直接调用官方 API，按 token 计费
