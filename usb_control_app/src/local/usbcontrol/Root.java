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
        java.util.concurrent.atomic.AtomicBoolean truncated=new java.util.concurrent.atomic.AtomicBoolean();
        Thread reader = new Thread(() -> { try(InputStream in=p.getInputStream()) {
            byte[] b = new byte[4096]; int n;
            while((n=in.read(b))!=-1) synchronized(out) { if(out.size()+n<=262144) out.write(b,0,n); else truncated.set(true); }
        } catch(IOException ignored) {} });
        reader.setDaemon(true); reader.start();
        try {
        if(!p.waitFor(35,TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("Root 请求超时，请检查超级用户授权"); }
        reader.join(1000);
        if(reader.isAlive())throw new IOException("Root 输出流尚未结束，请重试");
        if(truncated.get())throw new IOException("Root 返回内容超限，已停止解析");
        String result;
        synchronized(out){result=out.toString("UTF-8");}
        if(p.exitValue()!=0) throw new IOException("Root 命令失败 ("+p.exitValue()+")\n"+result);
        return result.trim();
        }finally{p.getInputStream().close();p.getOutputStream().close();p.getErrorStream().close();if(p.isAlive())p.destroyForcibly();}
    }
    static String section(String s,String tag) {
        String token="__"+tag+"__\n";
        int i=s.indexOf(token);
        if(i<0) return "";
        i+=token.length(); int end=s.indexOf("\n__",i);
        return (end<0?s.substring(i):s.substring(i,end)).trim();
    }
    static final String SNAPSHOT = "set -e; printf '__UID__\\n'; id -u; " +
        "printf '__ADB__\\n'; settings get global adb_enabled; " +
        "printf '__DEV__\\n'; settings get global development_settings_enabled; " +
        "printf '__SIZE__\\n'; wm size; printf '__DENSITY__\\n'; wm density; " +
        "printf '__GLOBAL__\\n'; v=$(settings get global adb_install_enabled); printf 'adb_install_enabled=%s\\n' \"$v\"; "+
        "printf '__SECURE__\\n'; v=$(settings get secure adb_install_enabled); printf 'adb_install_enabled=%s\\n' \"$v\"; "+
        "printf '__SYSTEM__\\n'; v=$(settings get system adb_install_enabled); printf 'adb_install_enabled=%s\\n' \"$v\"";
    static void validate(String s)throws IOException {
        for(String tag:new String[]{"UID","ADB","DEV","SIZE","DENSITY","GLOBAL","SECURE","SYSTEM"})
            if(!s.contains("__"+tag+"__\n"))throw new IOException("系统状态返回不完整："+tag);
        if(!"0".equals(section(s,"UID")))throw new IOException("请授予 Root 权限");
        for(String tag:new String[]{"ADB","DEV"})if(!section(s,tag).matches("null|[0-9]{1,9}"))throw new IOException("系统状态值异常："+tag);
        int[] size=Logic.size(section(s,"SIZE"));if(size[0]<=0||size[1]<=0)throw new IOException("屏幕尺寸读取失败");
        DensitySession.previous(section(s,"DENSITY"));
    }
}
