#!/usr/bin/env python3
"""把 base-unsigned.apk（资源+清单）和 classes.dex 拼成可签名的 APK。
不重新编译资源，所以不需要 aapt；保持 resources.arsc 不压缩（Android 要求）。"""
import sys, zipfile

base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
zin = zipfile.ZipFile(base)
names = [i.filename for i in zin.infolist()]
with zipfile.ZipFile(out, "w") as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        ni.compress_type = info.compress_type
        ni.external_attr = info.external_attr
        zout.writestr(ni, data)
    with open(dex, "rb") as f:
        data = f.read()
    ni = zipfile.ZipInfo("classes.dex", date_time=(2026, 1, 1, 0, 0, 0))
    ni.compress_type = zipfile.ZIP_DEFLATED
    ni.external_attr = 0o644 << 16
    zout.writestr(ni, data)
print("packed", out, "entries=", len(names) + 1)
