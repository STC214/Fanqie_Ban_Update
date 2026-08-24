#!/system/bin/sh
SKIPUNZIP=0
ui_print "- Target: com.dragon.read"
ui_print "- Update popup/request/install blocking"
ui_print "- Short-video UI/API/playback blocking"
ui_print "- Mine-page mini-game UI/API/runtime blocking"
# This module is intentionally mountless. Network filtering is per-app through
# iptables/ip6tables and the LSPosed hook, so no metamodule is required.
touch "$MODPATH/skip_mount"
ui_print "- Mountless mode: no metamodule required"
set_perm_recursive "$MODPATH" 0 0 0755 0644
for f in service.sh action.sh uninstall.sh bin/sqlite3; do set_perm "$MODPATH/$f" 0 0 0755; done

# Register the bundled LSPosed hook while Android is already running. The next
# reboot therefore loads the scope before the target process starts.
pm install -r "$MODPATH/fanqie-blocker-hook.apk" >/dev/null 2>&1
db=$(find /data/adb/lspd /data/adb/modules/zygisk_lsposed -name modules_config.db 2>/dev/null | head -n 1)
apk=$(pm path local.fanqie.blocker 2>/dev/null | head -n 1 | cut -d: -f2)
if [ -f "$db" ] && [ -n "$apk" ]; then
  [ -f "$MODPATH/lsposed_db.before" ] || cp -p "$db" "$MODPATH/lsposed_db.before"
  schema=$($MODPATH/bin/sqlite3 "$db" '.schema' 2>/dev/null)
  if echo "$schema" | grep -q 'CREATE TABLE modules_state'; then
    $MODPATH/bin/sqlite3 "$db" "BEGIN; INSERT OR REPLACE INTO modules(module_pkg_name,apk_path) VALUES('local.fanqie.blocker','$apk'); INSERT OR REPLACE INTO modules_state(module_pkg_name,user_id,enabled,scope_request_blocked) VALUES('local.fanqie.blocker',0,1,0); INSERT OR IGNORE INTO scope(module_pkg_name,app_pkg_name,user_id) VALUES('local.fanqie.blocker','com.dragon.read',0); COMMIT;" >/dev/null 2>&1
    ui_print "- LSPosed hook enabled for com.dragon.read"
  fi
fi
