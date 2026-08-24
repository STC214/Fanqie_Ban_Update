#!/system/bin/sh
MODDIR=${0%/*}
LOG="$MODDIR/usb_toggle.log"
MODE=${1:-toggle}

current_functions() {
  getprop sys.usb.config 2>/dev/null
}

base_functions() {
  value=$(current_functions)
  echo "$value" | sed 's/adb//g; s/^,*//; s/,*$//; s/,,*/,/g'
}

apply_functions() {
  base=$(base_functions)
  if [ -n "$base" ]; then
    svc usb setFunctions "$base" >/dev/null 2>&1
  else
    svc usb setFunctions >/dev/null 2>&1
  fi
}

enable_adb() {
  settings put global development_settings_enabled 1
  settings put global adb_enabled 1
  start adbd >/dev/null 2>&1
  apply_functions
  sleep 2
  state=enabled
}

disable_adb() {
  settings put global adb_enabled 0
  apply_functions
  state=disabled
}

case "$MODE" in
  on|enable|enabled) enable_adb ;;
  off|disable|disabled) disable_adb ;;
  status) state=status ;;
  toggle)
    if [ "$(settings get global adb_enabled 2>/dev/null)" = "1" ]; then
      disable_adb
    else
      enable_adb
    fi
    ;;
  *)
    echo "Usage: action.sh [toggle|on|off|status]"
    exit 2
    ;;
esac

adb_value=$(settings get global adb_enabled 2>/dev/null)
dev_value=$(settings get global development_settings_enabled 2>/dev/null)
usb_value=$(current_functions)
{
  echo "timestamp=$(date -Iseconds 2>/dev/null || date)"
  echo "requested=$MODE"
  echo "result=$state"
  echo "adb_enabled=$adb_value"
  echo "development_settings_enabled=$dev_value"
  echo "sys.usb.config=$usb_value"
} > "$LOG"

echo "USB debugging: $adb_value"
echo "Developer settings: $dev_value"
echo "USB functions: $usb_value"
if [ "$adb_value" = "1" ]; then
  echo "Result: USB debugging enabled"
else
  echo "Result: USB debugging disabled"
fi
