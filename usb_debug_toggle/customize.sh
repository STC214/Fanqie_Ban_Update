#!/system/bin/sh
SKIPUNZIP=0

ui_print "- Root USB Debug Toggle"
ui_print "- Use the module Action button to toggle USB debugging"
ui_print "- Mountless mode: no metamodule required"

mkdir -p "$MODPATH/state"
settings get global adb_enabled > "$MODPATH/state/adb_enabled.before" 2>/dev/null
settings get global development_settings_enabled > "$MODPATH/state/development_settings_enabled.before" 2>/dev/null
getprop sys.usb.config > "$MODPATH/state/usb_config.before" 2>/dev/null
touch "$MODPATH/skip_mount"

set_perm_recursive "$MODPATH" 0 0 0755 0644
for f in action.sh service.sh status.sh toggle-worker.sh uninstall.sh; do
  set_perm "$MODPATH/$f" 0 0 0755
done

MODDIR="$MODPATH" sh "$MODPATH/status.sh" update

ui_print "- Installation does not change the current USB state"
ui_print "- Done"
