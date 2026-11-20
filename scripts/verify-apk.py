"""校验 APK 里的离线资源：index.html 引用的文件都存在且非空。"""

import re
import sys
import zipfile

apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/release/app-release.apk"

with zipfile.ZipFile(apk) as archive:
    html = archive.read("assets/www/index.html").decode("utf-8")
    refs = re.findall(r'(?:src|href)="([^"]+)"', html)
    print("index refs:", refs)
    ok = True
    for ref in refs:
        if ref.startswith(("http://", "https://", "//")):
            continue
        name = "assets/www/" + ref
        try:
            size = archive.getinfo(name).file_size
        except KeyError:
            print("MISSING", ref)
            ok = False
            continue
        print(f"{size:>10}  {ref}")
        if size == 0:
            print("EMPTY", ref)
            ok = False
    has_diagnostics = "页面启动失败" in html
    print("boot diagnostics:", has_diagnostics)
    print("RESULT:", "OK" if ok and has_diagnostics else "BROKEN")
    sys.exit(0 if ok else 1)
