package local.usbcontrol;

import java.io.IOException;

/** Vendor adapters change only the existing USB-install setting, never verification policy. */
final class UsbInstall {
    static final String MIUI="miui";
    private static final String URI="content://com.miui.securitycenter.remoteprovider";
    private static final String KEY="security_adb_install_enable";
    private static final String CALL="content call --uri "+URI+" --method callPreference --arg ";
    private static final String EXTRAS=" --extra type:i:1 --extra key:s:"+KEY;
    static final String ENABLE="am start --user current -n com.miui.securitycenter/com.miui.permcenter.install.AdbInstallVerifyActivity -a com.miui.securitycenter.action.ADB_INSTALL_VERIFY";
    static final String PROBE="if [ -n \"$(getprop ro.miui.ui.version.name)\" ]; then "+
        CALL+"CONTAIN_KEY"+EXTRAS+" 2>/dev/null || true; "+
        CALL+"GET"+EXTRAS+" --extra default:b:false 2>/dev/null || true; "+
        "cmd package resolve-activity --brief -a com.miui.securitycenter.action.ADB_INSTALL_VERIFY -p com.miui.securitycenter 2>/dev/null || true; fi";

    static boolean validBackend(String backend){return backend!=null&&backend.matches("global|secure|system|miui");}
    static String backend(String snapshot){
        if(Logic.miuiInstallState(Root.section(snapshot,"MIUI"))!=null)return MIUI;
        return Logic.installNamespace(Root.section(snapshot,"GLOBAL"),Root.section(snapshot,"SECURE"),Root.section(snapshot,"SYSTEM"));
    }
    static String state(String snapshot,String backend){
        if(MIUI.equals(backend))return Logic.miuiInstallState(Root.section(snapshot,"MIUI"));
        if(!validBackend(backend))return null;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?m)^adb_install_enabled=([01])\\r?$").matcher(Root.section(snapshot,backend.toUpperCase(java.util.Locale.ROOT)));
        return m.find()?m.group(1):null;
    }
    static String read(String backend)throws Exception {
        if(!validBackend(backend))throw new IOException("USB 安装接口未识别");
        String value=MIUI.equals(backend)?Logic.miuiInstallState(Root.run(PROBE)):Root.run("settings get "+backend+" adb_install_enabled");
        if(value==null||!value.matches("[01]"))throw new IOException("USB 安装状态读取失败");
        return value;
    }
    static String writeCommand(String backend,boolean on){
        if(!validBackend(backend))throw new IllegalArgumentException("USB 安装接口异常");
        if(MIUI.equals(backend))return on?ENABLE:CALL+"SET"+EXTRAS+" --extra value:b:false";
        return "settings put "+backend+" adb_install_enabled "+(on?"1":"0");
    }
    static String verifyCommand(String backend,String value){
        if(!validBackend(backend)||!value.matches("[01]"))throw new IllegalArgumentException("USB 安装恢复记录异常");
        String get=MIUI.equals(backend)?CALL+"GET"+EXTRAS+" --extra default:b:false":"settings get "+backend+" adb_install_enabled";
        String expected=MIUI.equals(backend)?"Result: Bundle[{"+KEY+"="+("1".equals(value)?"true":"false")+"}]":value;
        return "[ \"$("+get+")\" = "+Root.quote(expected)+" ]";
    }
    static String restoreCommand(String backend,String value){
        String verify=verifyCommand(backend,value);
        if(MIUI.equals(backend)&&"1".equals(value))return "("+verify+" || { "+ENABLE+"; printf '请完成系统 USB 安装确认后，再重试恢复\\n'; "+verify+"; })";
        return "("+writeCommand(backend,"1".equals(value))+" && "+verify+")";
    }
}
