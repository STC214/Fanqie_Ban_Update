#!/system/bin/sh
PKG=com.dragon.read
COMPONENTS="com.ss.android.update.UpdateProgressActivity com.huawei.updatesdk.service.otaupdate.AppUpdateActivity com.huawei.updatesdk.support.pm.PackageInstallerActivity"
users=$(pm list users 2>/dev/null | sed -n 's/.*UserInfo{\([0-9][0-9]*\):.*/\1/p')
[ -n "$users" ] || users=0
for user in $users; do
  pm list packages --user "$user" "$PKG" 2>/dev/null | grep -q "package:$PKG" || continue
  appops set --user "$user" "$PKG" REQUEST_INSTALL_PACKAGES default >/dev/null 2>&1
  for cls in $COMPONENTS; do pm default-state --user "$user" "$PKG/$cls" >/dev/null 2>&1; done
done
CHAIN=FQ_UPD_BLOCK
for bin in iptables ip6tables; do
  command -v "$bin" >/dev/null 2>&1 || continue
  while "$bin" -w 2 -D OUTPUT -j "$CHAIN" >/dev/null 2>&1; do :; done
  "$bin" -w 2 -F "$CHAIN" >/dev/null 2>&1
  "$bin" -w 2 -X "$CHAIN" >/dev/null 2>&1
done
