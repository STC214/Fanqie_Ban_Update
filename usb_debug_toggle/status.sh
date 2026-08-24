#!/system/bin/sh
MODDIR=${MODDIR:-${0%/*}}

adb_value() {
  value=$(settings get global adb_enabled 2>/dev/null)
  [ "$value" = "1" ] && echo 1 || echo 0
}

status_parts() {
  value=$(adb_value)
  if [ "$value" = "1" ]; then
    STATUS_DOT=$(printf '\360\237\237\242')
    STATUS_TEXT=ON
  else
    STATUS_DOT=$(printf '\360\237\224\264')
    STATUS_TEXT=OFF
  fi
}

set_module_status() {
  value=$1
  if [ "$value" = "1" ]; then
    STATUS_DOT=$(printf '\360\237\237\242')
    STATUS_TEXT=ON
  else
    STATUS_DOT=$(printf '\360\237\224\264')
    STATUS_TEXT=OFF
  fi
  new_description="USB debugging: $STATUS_DOT $STATUS_TEXT | Tap Action to toggle. Mountless; no metamodule required."
  prop="$MODDIR/module.prop"
  tmp="$MODDIR/.module.prop.tmp"
  awk -v d="$new_description" '
    BEGIN { done=0 }
    /^description=/ { print "description=" d; done=1; next }
    { print }
    END { if (!done) print "description=" d }
  ' "$prop" > "$tmp" && cat "$tmp" > "$prop"
  rm -f "$tmp"
  chmod 0644 "$prop" >/dev/null 2>&1
}

update_module_status() {
  set_module_status "$(adb_value)"
}

[ "$1" = "update" ] && update_module_status
