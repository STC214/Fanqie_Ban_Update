#!/system/bin/sh
MODDIR=${0%/*}

read_saved() {
  file="$1"
  [ -f "$file" ] && head -n 1 "$file"
}

adb_before=$(read_saved "$MODDIR/state/adb_enabled.before")
dev_before=$(read_saved "$MODDIR/state/development_settings_enabled.before")
usb_before=$(read_saved "$MODDIR/state/usb_config.before")

case "$dev_before" in 0|1) settings put global development_settings_enabled "$dev_before";; esac
case "$adb_before" in 0|1) settings put global adb_enabled "$adb_before";; esac

base=$(echo "$usb_before" | sed 's/adb//g; s/^,*//; s/,*$//; s/,,*/,/g')
if [ -n "$base" ]; then
  svc usb setFunctions "$base" >/dev/null 2>&1
else
  svc usb setFunctions >/dev/null 2>&1
fi

[ "$adb_before" = "1" ] && start adbd >/dev/null 2>&1
