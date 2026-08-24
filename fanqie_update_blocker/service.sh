#!/system/bin/sh
MODDIR=${0%/*}
PKG=com.dragon.read
LOG="$MODDIR/verification.log"
CHAIN=FQ_UPD_BLOCK
HOSTS="ichannel.snssdk.com toblog.ctobsnssdk.com"
COMPONENTS="
com.ss.android.update.UpdateProgressActivity
com.huawei.updatesdk.service.otaupdate.AppUpdateActivity
com.huawei.updatesdk.support.pm.PackageInstallerActivity
"

apply_rules() {
  users=$(pm list users 2>/dev/null | sed -n 's/.*UserInfo{\([0-9][0-9]*\):.*/\1/p')
  [ -n "$users" ] || users=0
  for user in $users; do
    pm list packages --user "$user" "$PKG" 2>/dev/null | grep -q "package:$PKG" || continue
    appops set --user "$user" "$PKG" REQUEST_INSTALL_PACKAGES ignore >/dev/null 2>&1
    for cls in $COMPONENTS; do pm disable --user "$user" "$PKG/$cls" >/dev/null 2>&1; done
  done
}

apply_firewall() {
  uid=$(dumpsys package "$PKG" 2>/dev/null | sed -n 's/.*userId=\([0-9][0-9]*\).*/\1/p' | head -n 1)
  [ -n "$uid" ] || return
  for bin in iptables ip6tables; do
    command -v "$bin" >/dev/null 2>&1 || continue
    "$bin" -w 2 -N "$CHAIN" >/dev/null 2>&1 || "$bin" -w 2 -F "$CHAIN" >/dev/null 2>&1
    "$bin" -w 2 -C OUTPUT -m owner --uid-owner "$uid" -j "$CHAIN" >/dev/null 2>&1 || \
      "$bin" -w 2 -I OUTPUT 1 -m owner --uid-owner "$uid" -j "$CHAIN" >/dev/null 2>&1
    for host in $HOSTS; do
      "$bin" -w 2 -A "$CHAIN" -p tcp -m string --algo bm --string "$host" -j REJECT >/dev/null 2>&1
      "$bin" -w 2 -A "$CHAIN" -p udp -m string --algo bm --string "$host" -j REJECT >/dev/null 2>&1
    done
  done
}

while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
apply_rules
apply_firewall
{
  echo "timestamp=$(date -Iseconds 2>/dev/null || date)"
  echo "package=$PKG"
  echo "request_install_packages=$(appops get --user 0 "$PKG" REQUEST_INSTALL_PACKAGES 2>&1 | tr '\n' ';')"
  echo "update_activity=$(dumpsys package "$PKG" 2>/dev/null | grep -A 20 'disabledComponents:' | grep 'com.ss.android.update.UpdateProgressActivity' | head -n 1 | tr -d ' ')"
  echo "hosts=$(grep -E 'ichannel\.snssdk\.com|toblog\.ctobsnssdk\.com' /system/etc/hosts 2>/dev/null | tr '\n' ';')"
  echo "iptables=$(iptables -w 2 -S "$CHAIN" 2>&1 | tr '\n' ';')"
} > "$LOG"
while true; do sleep 60; apply_rules; apply_firewall; done
