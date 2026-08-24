#!/system/bin/sh
MODDIR=${0%/*}
TARGET=$1
LOG="$MODDIR/usb_toggle.log"
. "$MODDIR/status.sh"

sleep 4
base_functions() {
  value=$(getprop sys.usb.config 2>/dev/null)
  echo "$value" | sed 's/adb//g; s/^,*//; s/,*$//; s/,,*/,/g'
}
apply_functions() {
  base=$(base_functions)
  if [ -n "$base" ]; then svc usb setFunctions "$base" >/dev/null 2>&1; else svc usb setFunctions >/dev/null 2>&1; fi
}
case "$TARGET" in
  on)
    settings put global development_settings_enabled 1
    settings put global adb_enabled 1
    start adbd >/dev/null 2>&1
    apply_functions
    ;;
  off)
    settings put global adb_enabled 0
    apply_functions
    ;;
  *) exit 2 ;;
esac
sleep 1
rm -f "$MODDIR/.pending_status"
update_module_status
{
  echo "timestamp=$(date -Iseconds 2>/dev/null || date)"
  echo "requested=$TARGET"
  echo "adb_enabled=$(adb_value)"
  echo "development_settings_enabled=$(settings get global development_settings_enabled 2>/dev/null)"
  echo "sys.usb.config=$(getprop sys.usb.config 2>/dev/null)"
  echo "sys.usb.state=$(getprop sys.usb.state 2>/dev/null)"
} > "$LOG"
