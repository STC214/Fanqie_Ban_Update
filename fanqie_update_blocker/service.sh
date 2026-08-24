#!/system/bin/sh
MODDIR=${0%/*}
PKG=com.dragon.read
HOOKPKG=local.fanqie.blocker
CHAIN=FQ_MEDIA_BLOCK
LOG="$MODDIR/verification.log"
HOSTS="ichannel.snssdk.com toblog.ctobsnssdk.com aweme.snssdk.com test-aweme.snssdk.com is.snssdk.com video.snssdk.com avg-editor.bytedance.net ohayoo.cn microapp.bytedance.com tmaservice.developer.toutiao.com tmaportal.snssdk.com"
TOKENS="ichannel.snssdk.com toblog.ctobsnssdk.com aweme.snssdk.com -video. video. /check_version/ /app_alert_check/ /appupdate/ /video/ .m3u8 .mp4 minigame mini_game microgame game_center fanqie-game /api/apps/game/"
COMPONENTS="
com.ss.android.update.UpdateProgressActivity
com.huawei.updatesdk.service.otaupdate.AppUpdateActivity
com.huawei.updatesdk.support.pm.PackageInstallerActivity
com.dragon.read.pages.video.detail.VideoDetailActivity
com.ss.android.excitingvideo.ExcitingVideoActivity
com.dragon.read.ad.exciting.video.AdBrowserActivity
com.bytedance.sdk.openadsdk.activity.base.TTPlayableWebPageActivity
com.bytedance.sdk.openadsdk.activity.base.TTVideoWebPageActivity
com.bytedance.sdk.openadsdk.activity.base.TTVideoScrollWebPageActivity
com.bytedance.sdk.openadsdk.activity.base.TTRewardVideoActivity
com.bytedance.sdk.openadsdk.activity.base.TTRewardExpressVideoActivity
com.bytedance.sdk.openadsdk.activity.base.TTFullScreenVideoActivity
com.bytedance.sdk.openadsdk.activity.base.TTFullScreenExpressVideoActivity
com.dragon.read.pages.minigame.MiniGameDetailActivity
com.tt.miniapphost.process.base.HostCrossProcessCallService
com.tt.miniapphost.feedback.FeedbackRecordService
com.tt.miniapp.activity.OpenSchemaMiddleActivity
com.tt.miniapp.business.extra.launchapp.LaunchAppMiddleActivity
com.bytedance.bdp.unitycontainer.launch.UCLaunchActivity0
com.bytedance.bdp.unitycontainer.launch.UCLaunchActivity1
com.bytedance.bdp.unitycontainer.launch.UCLaunchActivity2
com.bytedance.bdp.unitycontainer.launch.UCLaunchActivity3
unityplayer.UnityActivity
com.unity3d.player.UCPlayerActivity0
com.unity3d.player.UCPlayerActivity1
com.unity3d.player.UCPlayerActivity2
com.unity3d.player.UCPlayerActivity3
"

apply_package_rules() {
  users=$(pm list users 2>/dev/null | sed -n 's/.*UserInfo{\([0-9][0-9]*\):.*/\1/p'); [ -n "$users" ] || users=0
  for user in $users; do
    pm list packages --user "$user" "$PKG" 2>/dev/null | grep -q "package:$PKG" || continue
    appops set --user "$user" "$PKG" REQUEST_INSTALL_PACKAGES ignore >/dev/null 2>&1
    for cls in $COMPONENTS; do pm disable --user "$user" "$PKG/$cls" >/dev/null 2>&1; done
    for i in 0 1 2 3 4; do
      for cls in MiniappTabActivity MiniappTabFloatStyleActivity MiniappTabHostStackActivity MiniappTabFloatStyleHostStackActivity MiniappService; do
        pm disable --user "$user" "$PKG/com.tt.miniapphost.placeholder.${cls}${i}" >/dev/null 2>&1
      done
    done
    for i in 200 201 202 203; do pm disable --user "$user" "$PKG/com.tt.miniapphost.placeholder.UcAppService$i" >/dev/null 2>&1; done
  done
}

apply_firewall() {
  uid=$(pm list packages -U "$PKG" 2>/dev/null | sed -n 's/.* uid:\([0-9][0-9]*\).*/\1/p' | head -n 1); [ -n "$uid" ] || return
  for bin in iptables ip6tables; do
    command -v "$bin" >/dev/null 2>&1 || continue
    "$bin" -w 2 -N "$CHAIN" >/dev/null 2>&1 || "$bin" -w 2 -F "$CHAIN" >/dev/null 2>&1
    "$bin" -w 2 -C OUTPUT -m owner --uid-owner "$uid" -j "$CHAIN" >/dev/null 2>&1 || "$bin" -w 2 -I OUTPUT 1 -m owner --uid-owner "$uid" -j "$CHAIN" >/dev/null 2>&1
    for token in $TOKENS; do
      "$bin" -w 2 -A "$CHAIN" -p tcp -m string --algo bm --string "$token" -j REJECT >/dev/null 2>&1
      "$bin" -w 2 -A "$CHAIN" -p udp -m string --algo bm --string "$token" -j REJECT >/dev/null 2>&1
    done
  done
}

sanitize_preferences() {
  mkdir -p "$MODDIR/state_backup" "$MODDIR/quarantine"
  for base in /data/user/*/$PKG /data/user_de/*/$PKG; do
    [ -d "$base" ] || continue
    user=$(echo "$base" | sed -n 's#^/data/user[_a-z]*/\([0-9][0-9]*\)/.*#\1#p'); [ -n "$user" ] || user=0
    for f in "$base"/shared_prefs/*.xml; do
      [ -f "$f" ] || continue
      grep -Eq 'key_has_new_video_tab|has_video_tab|key_book_mall_video_tab_data_cache|check_version|app_alert_check|appupdate|UPDATE_VERSION_CODE|mini_game|microgame|ss_timor_entry|play_game' "$f" || continue
      name=$(basename "$f")
      backup="$MODDIR/state_backup$f"
      if [ ! -f "$backup" ]; then mkdir -p "$(dirname "$backup")"; cp -p "$f" "$backup"; fi
      case "$name" in preference_mine_mini_game_entrance.xml|preference_ss_timor_entry.xml) printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map />\n' > "$f"; continue;; esac
      sed -i -E 's#(<boolean name="(key_has_new_video_tab|has_video_tab)" value=")true(" */>)#\1false\3#g; /key_book_mall_video_tab_data_cache/d; /check_version/d; /app_alert_check/d; /appupdate/d' "$f"
    done
    for f in "$base"/cache/update.apk* "$base"/cache/predownload.apk.part "$base"/files/update.apk*; do
      if [ -e "$f" ]; then quarantine="$MODDIR/quarantine$f"; mkdir -p "$(dirname "$quarantine")"; mv -f "$f" "$quarantine" >/dev/null 2>&1; fi
    done
  done
}

enable_hook_scope() {
  pm path "$HOOKPKG" >/dev/null 2>&1 || pm install -r "$MODDIR/fanqie-blocker-hook.apk" >/dev/null 2>&1
  for cli in /data/adb/lspd/bin/cli /data/adb/modules/zygisk_lsposed/bin/cli; do
    [ -x "$cli" ] || continue
    "$cli" scope set -s "$HOOKPKG" "$PKG/0" >/dev/null 2>&1
    "$cli" modules set -e "$HOOKPKG" >/dev/null 2>&1 && return 0
  done
  db=$(find /data/adb/lspd /data/adb/modules/zygisk_lsposed -name modules_config.db 2>/dev/null | head -n 1)
  [ -f "$db" ] || return 1
  apk=$(pm path "$HOOKPKG" 2>/dev/null | head -n 1 | cut -d: -f2); [ -n "$apk" ] || return 1
  [ -f "$MODDIR/lsposed_db.before" ] || cp -p "$db" "$MODDIR/lsposed_db.before"
  schema=$($MODDIR/bin/sqlite3 "$db" '.schema' 2>/dev/null)
  if echo "$schema" | grep -q 'CREATE TABLE modules_state'; then
    "$MODDIR/bin/sqlite3" "$db" "BEGIN; INSERT OR REPLACE INTO modules(module_pkg_name,apk_path) VALUES('$HOOKPKG','$apk'); INSERT OR REPLACE INTO modules_state(module_pkg_name,user_id,enabled,scope_request_blocked) VALUES('$HOOKPKG',0,1,0); INSERT OR IGNORE INTO scope(module_pkg_name,app_pkg_name,user_id) VALUES('$HOOKPKG','$PKG',0); COMMIT;" >/dev/null 2>&1
    return $?
  fi
  cols=$($MODDIR/bin/sqlite3 "$db" 'pragma table_info(modules);' 2>/dev/null)
  case "$cols" in
    *auto_include*) extra=',auto_include'; value=',0' ;;
    *) extra=''; value='' ;;
  esac
  "$MODDIR/bin/sqlite3" "$db" "BEGIN; INSERT OR IGNORE INTO modules(module_pkg_name,apk_path,enabled$extra) VALUES('$HOOKPKG','$apk',1$value); UPDATE modules SET apk_path='$apk',enabled=1 WHERE module_pkg_name='$HOOKPKG'; INSERT OR IGNORE INTO scope(mid,app_pkg_name,user_id) SELECT mid,'$PKG',0 FROM modules WHERE module_pkg_name='$HOOKPKG'; COMMIT;" >/dev/null 2>&1
}

while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
apply_package_rules
apply_firewall
sanitize_preferences
enable_hook_scope; hook_rc=$?
am force-stop "$PKG" >/dev/null 2>&1
{
 echo "timestamp=$(date -Iseconds 2>/dev/null || date)"
 echo "package=$PKG"
 echo "hook_package=$(pm path "$HOOKPKG" 2>&1 | tr '\n' ';')"
 echo "hook_scope_rc=$hook_rc"
 echo "request_install=$(appops get --user 0 "$PKG" REQUEST_INSTALL_PACKAGES 2>&1 | tr '\n' ';')"
 echo "disabled=$(dumpsys package "$PKG" 2>/dev/null | grep -A 120 disabledComponents | grep -E 'UpdateProgressActivity|VideoDetailActivity|ExcitingVideoActivity|TT.*VideoActivity|MiniGame|Miniapp|Unity|UCLaunch' | tr '\n' ';')"
 echo "firewall=$(iptables -w 2 -S "$CHAIN" 2>&1 | tr '\n' ';')"
} > "$LOG"
while true; do sleep 60; apply_package_rules; apply_firewall; sanitize_preferences; done
