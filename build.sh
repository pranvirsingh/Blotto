#!/bin/bash
set -euo pipefail
cd /home/claude/blotto
TC=/home/claude/tc
B=build/apk
rm -rf $B && mkdir -p $B/classes $B/dex
echo "== aapt2 compile/link"
$TC/aapt2 compile --dir res -o $B/res.zip
$TC/aapt2 link -o $B/base.apk -I $TC/android.jar --manifest AndroidManifest.xml -A assets \
  --min-sdk-version 24 --target-sdk-version 34 --version-code ${VC:-1} --version-name ${VN:-1.0} \
  --proguard $B/aapt_rules.pro $B/res.zip
echo "== kotlinc"
./kc.sh $B/classes $TC/android.jar src/com/pranvir/blotto/*.kt
(cd $B/classes && jar cf ../classes.jar .)
echo "== r8"
java -Xmx2g -cp $TC/d8.jar com.android.tools.r8.R8 --release --min-api 24 --lib $TC/android.jar \
  --pg-conf rules.pro --pg-conf $B/aapt_rules.pro --output $B/dex \
  $B/classes.jar $TC/kotlin-stdlib-2.3.10-RC.jar 2>&1 | grep -v JAVA_TOOL | head -40 || true
ls -la $B/dex
echo "== package"
python3 zipalign.py $B/base.apk $B/unsigned.apk $B/dex/classes.dex
if [ ! -f blotto.jks ]; then
  keytool -genkeypair -keystore blotto.jks -storepass blottoink -keypass blottoink -alias blotto \
    -keyalg RSA -keysize 2048 -validity 12000 -dname "CN=Pranvir Singh, O=Blotto Pictures, C=IN" 2>&1 | grep -v JAVA_TOOL || true
fi
java -jar $TC/apksigner.jar sign --ks blotto.jks --ks-pass pass:blottoink --key-pass pass:blottoink --ks-key-alias blotto \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out build/Blotto.apk $B/unsigned.apk 2>&1 | grep -v JAVA_TOOL || true
java -jar $TC/apksigner.jar verify --verbose build/Blotto.apk 2>&1 | grep -v JAVA_TOOL | head -8
$TC/aapt2 dump badging build/Blotto.apk | head -12
ls -la build/Blotto.apk
