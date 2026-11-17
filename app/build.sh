#!/usr/bin/env bash
# 设备监控 App 构建脚本（aarch64 / proot 环境，不需要 aapt）
#
# 资源与清单不重新编译：沿用 base-unsigned.apk（由 apktool 解出来的资源 + 清单），
# 只把新编译的 classes.dex 装回去再签名，所以 aapt 不可用也能出包。
#
# 用法： bash build.sh
set -e
cd "$(dirname "$0")"

AJ=${ANDROID_JAR:-/root/android-sdk/platforms/android-34/android.jar}
BT=${BUILD_TOOLS:-/root/android-sdk/build-tools/34.0.0}
KS=${KEYSTORE:-sysmon.jks}
KSPASS=${KS_PASS:-android}

rm -rf build/classes build/dex
mkdir -p build/classes build/dex

echo "[1/4] javac"
javac -source 8 -target 8 -nowarn -cp "$AJ" -d build/classes $(find src gen -name '*.java')

echo "[2/4] d8"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$AJ" --min-api 24 \
     --output build/dex $(find build/classes -name '*.class')

echo "[3/4] pack"
python3 pack.py base-unsigned.apk build/dex/classes.dex build/unaligned.apk

echo "[4/4] sign"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KSPASS" --key-pass "pass:$KSPASS" \
     --ks-key-alias sysmon --out app-signed.apk build/unaligned.apk
"$BT/apksigner" verify app-signed.apk >/dev/null

echo "OK -> app-signed.apk"
echo "安装： su -c 'cp app-signed.apk /data/local/tmp/ && pm install -r -d /data/local/tmp/app-signed.apk'"
