# frontend（基于 deepseek-ai/chat-demo v0.1.1）

这里的源码来自官方开源前端 [deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo)（MIT，见 LICENSE），
为适配 Android 壳做了以下改动：

| 文件 | 改动 |
|---|---|
| `src/components/base/icons.tsx` | 新增 `IconPlusOutline16` |
| `src/components/chatInput/ChatInput.tsx` | 附件按钮改为「+」面板开关；选图后显示建议词并可直接发送；接收原生回传的照片 |
| `src/components/chatInput/AttachPanel.tsx` | 新增：拍照 / 相册 / 文件 + 最近照片条（内联展开，不遮罩） |
| `src/components/chatInput/ChatInput.module.css` | 面板、照片条、勾选态、删除角标常显（移动端无 hover） |
| `src/components/chatInput/DraftAttachments.tsx` | 缩略图尺寸与角标样式对齐官方 |
| `src/components/messages/ReplyArea.tsx` | 思考结束后自动折叠（手动展开过则不再自动收） |
| `src/services/attachments.ts` | 上传前把长边 >4096px 的图片缩到 4096（服务端多图限制） |
| `src/i18n/zh.ts` | 输入框占位改为「发消息或按住说话」 |

## 构建

```bash
pnpm install
pnpm build            # 产物在 dist/
```

然后把 `dist/` 的内容拷到 `app/src/main/assets/www/`。

注意：构建后的 `index.html` 标题是「API 使用 demo」，仓库里已入库的 `app/src/main/assets/www/`
是替换过标题、并已拷入资源的版本。
