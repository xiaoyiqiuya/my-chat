"""把 modern / legacy 两份构建合成一个 assets/www。

目录结构：
  www/index.html          ES5 loader + 启动诊断（按 UA 里的 Chrome 版本选包）
  www/modern/...          新语法构建（chrome >= 105）
  www/legacy/...          转译构建（chrome >= 60）

用法：python scripts/assemble-www.py <modern dist> <legacy dist> <输出目录>
"""

import os
import re
import shutil
import sys

modern_dir, legacy_dir, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
here = os.path.dirname(os.path.abspath(__file__))
shell = open(os.path.join(here, "www-shell.html"), encoding="utf-8").read()


def refs(dist):
    """从一份构建的 index.html 里取主脚本和主样式文件名。"""
    html = open(os.path.join(dist, "index.html"), encoding="utf-8").read()
    js = re.search(r'src="(static/js/main\.[^"]+\.js)"', html)
    css = re.search(r'href="(static/css/main\.[^"]+\.css)"', html)
    if js is None or css is None:
        raise SystemExit(f"无法在 {dist}/index.html 里找到 main.js / main.css")
    return js.group(1), css.group(1)


def check(dist, relative):
    path = os.path.join(dist, relative)
    if not os.path.isfile(path) or os.path.getsize(path) == 0:
        raise SystemExit(f"构建产物缺失或为空：{path}")
    return os.path.getsize(path)


modern_js, modern_css = refs(modern_dir)
legacy_js, legacy_css = refs(legacy_dir)
check(modern_dir, modern_js)
check(modern_dir, modern_css)
check(legacy_dir, legacy_js)
check(legacy_dir, legacy_css)

if os.path.isdir(out_dir):
    shutil.rmtree(out_dir)
os.makedirs(out_dir)

# 两份构建各自放进子目录，index.html 用相对路径引用
for name, source in (("modern", modern_dir), ("legacy", legacy_dir)):
    shutil.copytree(source, os.path.join(out_dir, name))

html = (
    shell.replace("__MODERN_JS__", "modern/" + modern_js)
    .replace("__MODERN_CSS__", "modern/" + modern_css)
    .replace("__LEGACY_JS__", "legacy/" + legacy_js)
    .replace("__LEGACY_CSS__", "legacy/" + legacy_css)
)
open(os.path.join(out_dir, "index.html"), "w", encoding="utf-8", newline="").write(html)

print(f"modern: {modern_js} ({os.path.getsize(os.path.join(modern_dir, modern_js))} bytes)")
print(f"legacy: {legacy_js} ({os.path.getsize(os.path.join(legacy_dir, legacy_js))} bytes)")
print(f"www -> {os.path.abspath(out_dir)}")
