#!/system/bin/sh
MODDIR=${0%/*}
echo "=== Fanqie update/video blocker ==="
cat "$MODDIR/verification.log" 2>/dev/null
echo "--- hook APK ---"
pm path local.fanqie.blocker 2>&1
echo "--- firewall ---"
iptables -w 2 -S FQ_MEDIA_BLOCK 2>&1
