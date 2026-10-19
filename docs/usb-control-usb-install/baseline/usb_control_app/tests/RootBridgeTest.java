package local.usbcontrol;

/** Runs in dalvikvm from adb shell, on a disposable AOSP userdebug emulator. */
public class RootBridgeTest {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    private static String get(String space,String name)throws Exception{return Root.run("settings get "+space+" "+name);}
    private static String restore(String space,String name,String value){return "settings "+("null".equals(value)?"delete "+space+" "+name:"put "+space+" "+name+" "+Root.quote(value));}
    public static void main(String[] args)throws Exception{
        check("0".equals(Root.run("id -u")),"root transport executes as UID 0");
        String adb=get("global","adb_enabled"),dev=get("global","development_settings_enabled"),install=get("global","adb_install_enabled");
        String beforeDensity=Root.run("wm density");int old=Logic.lastNumber(beforeDensity,"Override density",0);String prior=old>0?String.valueOf(old):"reset";
        System.out.println("BASELINE adb="+adb+" development="+dev+" install="+install+" density="+beforeDensity.replace('\n',';'));
        try {
            Root.run("settings put global adb_enabled 0");check("0".equals(get("global","adb_enabled")),"USB debugging OFF readback");
            Root.run("settings put global development_settings_enabled 1 && settings put global adb_enabled 1");check("1".equals(get("global","adb_enabled")),"USB debugging ON readback");
            String snap=Root.run(Root.SNAPSHOT);
            check("0".equals(Root.section(snap,"UID")),"snapshot marker parser");
            Root.run("settings delete global adb_install_enabled");snap=Root.run(Root.SNAPSHOT);
            check(Logic.installNamespace(Root.section(snap,"GLOBAL"),Root.section(snap,"SECURE"),Root.section(snap,"SYSTEM"))==null,"AOSP USB-install capability absent: disabled");
            Root.run("settings put global adb_install_enabled 0");snap=Root.run(Root.SNAPSHOT);
            check("global".equals(Logic.installNamespace(Root.section(snap,"GLOBAL"),Root.section(snap,"SECURE"),Root.section(snap,"SYSTEM"))),"synthetic existing USB-install key detected");
            Root.run("settings put global adb_install_enabled 1");check("1".equals(get("global","adb_install_enabled")),"synthetic USB-install ON readback (not a vendor installer test)");
            Root.run("settings put global adb_install_enabled 0");check("0".equals(get("global","adb_install_enabled")),"synthetic USB-install OFF readback");
            String dir="/data/local/tmp/usb-control-test";
            String marker=dir+"/density.pending",script=dir+"/revert-density.sh";
            String body="#!/system/bin/sh\nsleep 20\nif [ \"$(cat "+marker+" 2>/dev/null)\" = 123 ]; then\n wm density "+prior+"\n rm -f "+marker+"\nfi\n";
            Root.run("printf %s "+Root.quote(body)+" > "+script+"; printf 123 > "+marker);
            Root.run("nohup sh "+script+" </dev/null >/dev/null 2>&1 & p=$!; sleep 1; kill -0 $p || exit 1; wm density 480");
            check(Logic.lastNumber(Root.run("wm density"),"Override density",0)==480,"density preview applied");
            Thread.sleep(21500);
            check(beforeDensity.equals(Root.run("wm density")),"20-second independent watchdog restores original density");
            System.out.println("WATCHDOG literal readback: "+Root.run("wm density").replace('\n',';'));
        } finally {
            Root.run("wm density "+prior+" && "+restore("global","development_settings_enabled",dev)+" && "+restore("global","adb_enabled",adb)+" && "+restore("global","adb_install_enabled",install));
            check(adb.equals(get("global","adb_enabled"))&&dev.equals(get("global","development_settings_enabled"))&&install.equals(get("global","adb_install_enabled"))&&beforeDensity.equals(Root.run("wm density")),"rollback restores every original emulator setting");
        }
    }
}
