#!/system/bin/sh
MODDIR=${0%/*}; PKG=com.dragon.read; HOOKPKG=local.fanqie.blocker; CHAIN=FQ_MEDIA_BLOCK
COMPONENTS="com.ss.android.update.UpdateProgressActivity com.huawei.updatesdk.service.otaupdate.AppUpdateActivity com.huawei.updatesdk.support.pm.PackageInstallerActivity com.dragon.read.pages.video.detail.VideoDetailActivity com.ss.android.excitingvideo.ExcitingVideoActivity com.dragon.read.ad.exciting.video.AdBrowserActivity com.bytedance.sdk.openadsdk.activity.base.TTPlayableWebPageActivity com.bytedance.sdk.openadsdk.activity.base.TTVideoWebPageActivity com.bytedance.sdk.openadsdk.activity.base.TTVideoScrollWebPageActivity com.bytedance.sdk.openadsdk.activity.base.TTRewardVideoActivity com.bytedance.sdk.openadsdk.activity.base.TTRewardExpressVideoActivity com.bytedance.sdk.openadsdk.activity.base.TTFullScreenVideoActivity com.bytedance.sdk.openadsdk.activity.base.TTFullScreenExpressVideoActivity com.dragon.read.pages.minigame.MiniGameDetailActivity com.tt.miniapphost.process.base.HostCrossProcessCallService com.tt.miniapphost.feedback.FeedbackRecordService com.tt.miniapp.activity.OpenSchemaMiddleActivity com.tt.miniapp.business.extra.launchapp.LaunchAppMiddleActivity com.bytedance.bdp.unitycontainer.launch.UCLaunchActivity0 com.bytedance.bdp.unitycontainer.launch.UCLaunchActivity1 com.bytedance.bdp.unitycontainer.launch.UCLaunchActivity2 com.bytedance.bdp.unitycontainer.launch.UCLaunchActivity3 unityplayer.UnityActivity com.unity3d.player.UCPlayerActivity0 com.unity3d.player.UCPlayerActivity1 com.unity3d.player.UCPlayerActivity2 com.unity3d.player.UCPlayerActivity3"
users=$(pm list users 2>/dev/null | sed -n 's/.*UserInfo{\([0-9][0-9]*\):.*/\1/p'); [ -n "$users" ] || users=0
for user in $users; do
 appops set --user "$user" "$PKG" REQUEST_INSTALL_PACKAGES default >/dev/null 2>&1
 for cls in $COMPONENTS; do pm default-state --user "$user" "$PKG/$cls" >/dev/null 2>&1; done
 for i in 0 1 2 3 4; do for cls in MiniappTabActivity MiniappTabFloatStyleActivity MiniappTabHostStackActivity MiniappTabFloatStyleHostStackActivity MiniappService; do pm default-state --user "$user" "$PKG/com.tt.miniapphost.placeholder.${cls}${i}" >/dev/null 2>&1; done; done
 for i in 200 201 202 203; do pm default-state --user "$user" "$PKG/com.tt.miniapphost.placeholder.UcAppService$i" >/dev/null 2>&1; done
done
if [ -d "$MODDIR/state_backup" ]; then
 find "$MODDIR/state_backup" -type f 2>/dev/null | while IFS= read -r backup; do
  dest=${backup#"$MODDIR/state_backup"}
  mkdir -p "$(dirname "$dest")"
  cp -p "$backup" "$dest"
 done
fi
if [ -d "$MODDIR/quarantine" ]; then
 find "$MODDIR/quarantine" -type f 2>/dev/null | while IFS= read -r saved; do
  dest=${saved#"$MODDIR/quarantine"}
  mkdir -p "$(dirname "$dest")"
  mv -f "$saved" "$dest"
 done
fi
for bin in iptables ip6tables; do
 command -v "$bin" >/dev/null 2>&1 || continue
 while true; do
  rule=$("$bin" -w 2 -S OUTPUT 2>/dev/null | grep " -j $CHAIN" | head -n 1)
  [ -n "$rule" ] || break
  set -- $rule
  shift 2
  "$bin" -w 2 -D OUTPUT "$@" >/dev/null 2>&1 || break
 done
 "$bin" -w 2 -F "$CHAIN" >/dev/null 2>&1; "$bin" -w 2 -X "$CHAIN" >/dev/null 2>&1
done
db=$(find /data/adb/lspd /data/adb/modules/zygisk_lsposed -name modules_config.db 2>/dev/null | head -n 1)
if [ -f "$db" ] && [ -x "$MODDIR/bin/sqlite3" ]; then
 schema=$($MODDIR/bin/sqlite3 "$db" '.schema' 2>/dev/null)
 if echo "$schema" | grep -q 'CREATE TABLE modules_state'; then
   "$MODDIR/bin/sqlite3" "$db" "DELETE FROM scope WHERE module_pkg_name='$HOOKPKG'; DELETE FROM modules_state WHERE module_pkg_name='$HOOKPKG'; DELETE FROM modules WHERE module_pkg_name='$HOOKPKG';" >/dev/null 2>&1
 else
   "$MODDIR/bin/sqlite3" "$db" "DELETE FROM scope WHERE mid IN (SELECT mid FROM modules WHERE module_pkg_name='$HOOKPKG'); DELETE FROM modules WHERE module_pkg_name='$HOOKPKG';" >/dev/null 2>&1
 fi
fi
pm uninstall "$HOOKPKG" >/dev/null 2>&1
