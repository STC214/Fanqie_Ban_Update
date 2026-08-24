#!/system/bin/sh
SKIPUNZIP=0
ui_print "- Target package: com.dragon.read"
ui_print "- Blocking dedicated update endpoints"
mkdir -p "$MODPATH/system/etc"
if [ -f /system/etc/hosts ]; then
  cat /system/etc/hosts > "$MODPATH/system/etc/hosts"
else
  printf '127.0.0.1 localhost\n::1 localhost\n' > "$MODPATH/system/etc/hosts"
fi
for host in ichannel.snssdk.com toblog.ctobsnssdk.com; do
  grep -q "[[:space:]]$host\$" "$MODPATH/system/etc/hosts" || echo "0.0.0.0 $host" >> "$MODPATH/system/etc/hosts"
done
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
