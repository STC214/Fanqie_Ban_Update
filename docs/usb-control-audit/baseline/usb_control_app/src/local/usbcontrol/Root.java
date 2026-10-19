package local.usbcontrol;

import java.io.*;
import java.util.concurrent.*;

final class Root {
    static String quote(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
    static String run(String command) throws Exception {
        // No shell input comes from editable UI: density is numeric; names are constants.
        Process p;
        try { p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start(); }
        catch(IOException e) { throw new IOException("未检测到 Root，请安装并授权超级用户管理器"); }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> { try(InputStream in=p.getInputStream()) {
            byte[] b = new byte[4096]; int n;
            while((n=in.read(b))!=-1) synchronized(out) { if(out.size()<262144) out.write(b,0,n); }
        } catch(IOException ignored) {} });
        reader.setDaemon(true); reader.start();
        if(!p.waitFor(35,TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("Root 请求超时，请检查超级用户授权"); }
        reader.join(1000);
        String result = out.toString("UTF-8");
        if(p.exitValue()!=0) throw new IOException("Root 命令失败 ("+p.exitValue()+")\n"+result);
        return result.trim();
    }
    static String section(String s,String tag) {
        String token="__"+tag+"__\n";
        int i=s.indexOf(token);
        if(i<0) return "";
        i+=token.length(); int end=s.indexOf("\n__",i);
        return (end<0?s.substring(i):s.substring(i,end)).trim();
    }
    static final String SNAPSHOT = "printf '__UID__\\n'; id -u; " +
        "printf '__ADB__\\n'; settings get global adb_enabled; " +
        "printf '__DEV__\\n'; settings get global development_settings_enabled; " +
        "printf '__SIZE__\\n'; wm size; printf '__DENSITY__\\n'; wm density; " +
        "printf '__GLOBAL__\\n'; settings list global; printf '__SECURE__\\n'; settings list secure; " +
        "printf '__SYSTEM__\\n'; settings list system";
}
