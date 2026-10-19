#!/system/bin/sh
# Test-only adapter: AOSP userdebug su uses a UID argument, not Magisk's -c.
# Run only from adb shell on the dedicated emulator, never ship in the APK.
if [ "$1" = -c ]; then
  exec /system/xbin/su 0 sh -c "$2"
fi
exit 2
