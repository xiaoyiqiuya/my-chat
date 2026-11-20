"""校验 APK 或 www 目录里的离线资源：双构建各自的 main.js/css 都存在且非空。

用法：python scripts/verify-apk.py [apk 或 www 目录]
默认校验 app/build/outputs/apk/release/app-release.apk。
"""

import os
import re
import sys
import zipfile

target = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/release/app-release.apk"
is_apk = target.lower().endswith(".apk")

if is_apk:
    archive = zipfile.ZipFile(target)
    html = archive.read("assets/www/index.html").decode("utf-8")

    def read(path):
        try:
            data = archive.read("assets/www/" + path)
        except KeyError:
            return None
        return data
else:
    html = open(os.path.join(target, "index.html"), encoding="utf-8").read()

    def read(path):
        full = os.path.join(target, path)
        if not os.path.isfile(full):
            return None
        return open(full, "rb").read()

# 壳里以字符串形式引用两份构建的入口
refs = sorted(set(re.findall(r"(?:modern|legacy)/static/(?:js|css)/main\.[A-Za-z0-9]+\.(?:js|css)", html)))
print("bundle refs:", refs)

ok = True
if len(refs) < 4:
    print("MISSING bundle refs: 期望 modern/legacy 各一对 js+css")
    ok = False

for ref in refs:
    data = read(ref)
    if data is None:
        print("MISSING", ref)
        ok = False
        continue
    print(f"{len(data):>10}  {ref}")
    if len(data) == 0:
        print("EMPTY", ref)
        ok = False

diagnostics = "页面启动失败" in html and "复制错误信息" in html
print("boot diagnostics:", diagnostics)
if not diagnostics:
    ok = False

print("RESULT:", "OK" if ok else "BROKEN")
sys.exit(0 if ok else 1)
