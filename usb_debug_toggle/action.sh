#!/system/bin/sh
MODDIR=${0%/*}
MODE=${1:-toggle}
. "$MODDIR/status.sh"

status_parts
current=$(adb_value)
dev_value=$(settings get global development_settings_enabled 2>/dev/null)
usb_value=$(getprop sys.usb.config 2>/dev/null)

case "$MODE" in
  status)
    echo "========================================"
    echo " Root USB Debug Toggle v1.3.0"
    echo "========================================"
    echo "Status : $STATUS_DOT $STATUS_TEXT"
    echo "adb_enabled=$current"
    echo "development_settings_enabled=$dev_value"
    echo "sys.usb.config=$usb_value"
    echo "Log: $MODDIR/usb_toggle.log"
    update_module_status
    exit 0
    ;;
  on|enable|enabled) target=on ;;
  off|disable|disabled) target=off ;;
  toggle) if [ "$current" = "1" ]; then target=off; else target=on; fi ;;
  *) echo "Usage: action.sh [toggle|on|off|status]"; exit 2 ;;
esac

if [ "$target" = "on" ]; then
  target_value=1
  target_dot=$(printf '\360\237\237\242')
  target_text=ON
  planned_commands="settings put global development_settings_enabled 1; settings put global adb_enabled 1; start adbd; svc usb setFunctions"
else
  target_value=0
  target_dot=$(printf '\360\237\224\264')
  target_text=OFF
  planned_commands="settings put global adb_enabled 0; svc usb setFunctions"
fi

# All user-facing output is emitted before USB functions can be reset.
echo "========================================"
echo " Root USB Debug Toggle v1.3.0"
echo "========================================"
echo "[1/4] Root context : $(id 2>/dev/null)"
echo "[2/4] Current state: $STATUS_DOT $STATUS_TEXT (adb_enabled=$current)"
echo "      USB functions: ${usb_value:-charging}"
echo "[3/4] Target state : $target_dot $target_text"
echo "$target" > "$MODDIR/.pending_status"
set_module_status "$target_value"
echo "[4/5] Module status refreshed: $target_dot $target_text"
echo "[5/5] Queuing root worker; apply after 4 seconds"
echo "Commands:"
echo "  $planned_commands"
echo "Log file: $MODDIR/usb_toggle.log"
echo "Result: request accepted"

if command -v setsid >/dev/null 2>&1; then
  nohup setsid sh "$MODDIR/toggle-worker.sh" "$target" </dev/null >/dev/null 2>&1 &
else
  nohup sh "$MODDIR/toggle-worker.sh" "$target" </dev/null >/dev/null 2>&1 &
fi
sleep 1
exit 0
