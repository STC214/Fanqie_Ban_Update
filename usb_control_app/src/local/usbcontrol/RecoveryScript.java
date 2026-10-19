package local.usbcontrol;

final class RecoveryScript {
    static String make(String command,String density,String adb,String dev,String space,String install){
        String script="#!/system/bin/sh\n[ \"$(id -u)\" = 0 ] || exit 1\n"+command+" || exit 1\n";
        script+="[ \"$(settings get global adb_enabled)\" = "+Root.quote(adb)+" ] || exit 2\n";
        script+="[ \"$(settings get global development_settings_enabled)\" = "+Root.quote(dev)+" ] || exit 2\n";
        if(space!=null)script+="[ \"$(settings get "+space+" adb_install_enabled)\" = "+Root.quote(install)+" ] || exit 2\n";
        script+="actual=$(wm density) || exit 3\nprintf '%s\\n' \"$actual\" | grep -q '^Physical density: [0-9]' || exit 3\n";
        if("reset".equals(density))script+="if printf '%s\\n' \"$actual\" | grep -q '^Override density:'; then exit 3; fi\n";
        else script+="printf '%s\\n' \"$actual\" | grep -q '^Override density: "+density+"$' || exit 3\n";
        return script+"printf 'Restored initial settings; readback verified\\n'\nprintf '%s\\n' \"$actual\"\nsettings get global adb_enabled\n";
    }
}
