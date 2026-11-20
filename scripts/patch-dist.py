"""构建后处理：改标题，并校验 index.html 引用的资源都存在且非空。

之前用一行式改标题时把 main.*.js 截成了 0 字节（先 truncate 再 read），
装出来的 APK 就是白屏。这里显式先读后写，并在最后做完整性校验。

用法：python scripts/patch-dist.py <dist 目录>
"""

import glob
import io
import os
import re
import sys

dist = sys.argv[1] if len(sys.argv) > 1 else "dist"
os.chdir(dist)

page = "index.html"
text = io.open(page, encoding="utf-8").read()
text = text.replace("<title>API 使用 demo</title>", "<title>My Chat</title>")
io.open(page, "w", encoding="utf-8", newline="").write(text)

for name in glob.glob("static/js/main.*.js"):
    source = io.open(name, encoding="utf-8").read()
    io.open(name, "w", encoding="utf-8", newline="").write(source.replace("API 使用 demo", "My Chat"))

html = io.open(page, encoding="utf-8").read()
missing = []
for ref in re.findall(r'(?:src|href)="([^"]+)"', html):
    if ref.startswith(("http://", "https://", "//")):
        continue
    if not os.path.isfile(ref) or os.path.getsize(ref) == 0:
        missing.append(ref)

if missing:
    raise SystemExit("构建产物缺失或为空：" + ", ".join(missing))

print("dist ok:", os.path.abspath(page))
