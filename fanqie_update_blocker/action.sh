#!/system/bin/sh
MODDIR=${0%/*}
echo "=== ???????? ==="
echo "????: $MODDIR"
echo "--- ???? ---"
appops get --user 0 com.dragon.read REQUEST_INSTALL_PACKAGES 2>&1
for c in com.ss.android.update.UpdateProgressActivity com.huawei.updatesdk.service.otaupdate.AppUpdateActivity com.huawei.updatesdk.support.pm.PackageInstallerActivity; do
  dumpsys package com.dragon.read 2>/dev/null | grep -A 20 'disabledComponents:' | grep -q "$c" && echo "disabled: $c" || echo "default: $c"
done
grep -E 'ichannel\.snssdk\.com|toblog\.ctobsnssdk\.com' /system/etc/hosts 2>/dev/null
iptables -w 2 -S FQ_UPD_BLOCK 2>&1
echo "??: $MODDIR/verification.log"
