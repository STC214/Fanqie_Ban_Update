#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/status.sh"
while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
last=
pending_ticks=0
while true; do
  current=$(adb_value)
  if [ -f "$MODDIR/.pending_status" ]; then
    pending_ticks=$((pending_ticks + 1))
    if [ "$pending_ticks" -ge 6 ]; then
      rm -f "$MODDIR/.pending_status"
      pending_ticks=0
      update_module_status
      last=$current
    fi
  else
    pending_ticks=0
    if [ "$current" != "$last" ]; then update_module_status; last=$current; fi
  fi
  sleep 5
done
