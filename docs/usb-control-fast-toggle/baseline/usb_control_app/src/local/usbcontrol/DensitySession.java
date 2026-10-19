package local.usbcontrol;

import java.io.*;
import java.util.*;

/** Durable per-preview journal, atomic decision claim, independent root watchdog. */
final class DensitySession {
    final File home,dir;
    final String token,previous,target;
    final long deadline;
    private DensitySession(File home,String token,String previous,String target,long deadline)throws IOException {
        if(!token.matches("[a-f0-9-]{36}")||!validDensity(previous)||!validDensity(target))throw new IOException("显示恢复记录校验失败");
        this.home=home;this.token=token;this.previous=previous;this.target=target;this.deadline=deadline;dir=new File(home,"density-"+token);
    }
    static boolean validDensity(String value){return "reset".equals(value)||value!=null&&value.matches("[1-9][0-9]{1,3}")&&Integer.parseInt(value)>=72&&Integer.parseInt(value)<=2000;}
    static String previous(String text)throws IOException {
        int physical=Logic.lastNumber(text,"Physical density",0);
        if(physical<72||physical>2000)throw new IOException("系统未返回有效屏幕密度");
        int override=Logic.lastNumber(text,"Override density",0);
        if(override!=0&&!validDensity(String.valueOf(override)))throw new IOException("系统密度覆盖值异常");
        return override>0?String.valueOf(override):"reset";
    }
    static DensitySession begin(File home,String target,long now)throws Exception {
        if(new File(home,"density-active").exists())throw new IOException("上次显示预览尚未结束，请先恢复");
        DensitySession s=new DensitySession(home,UUID.randomUUID().toString(),previous(Root.run("wm density")),target,now+20000);
        if(!s.dir.mkdir())throw new IOException("显示预览目录创建失败");
        SafeFiles.write(s.file("pending"),s.token.getBytes("UTF-8"));
        SafeFiles.write(s.file("watchdog.sh"),s.watchdog(20).getBytes("UTF-8"));
        Properties p=new Properties();p.setProperty("token",s.token);p.setProperty("previous",s.previous);p.setProperty("target",s.target);
        SafeFiles.properties(new File(home,"density-active"),p);
        try {
            String q=Root.quote(s.dir.getAbsolutePath());
            Root.run("command -v nohup >/dev/null && command -v setsid >/dev/null || exit 1; "+
                "nohup setsid sh "+Root.quote(s.file("watchdog.sh").getAbsolutePath())+" </dev/null >/dev/null 2>&1 & p=$!; "+
                "i=0; while [ ! -f "+q+"/armed ] && [ $i -lt 20 ]; do sleep 0.1; i=$((i+1)); done; "+
                "[ -f "+q+"/armed ] && kill -0 $p || exit 1; wm density "+target+" || exit 1");
            s.verify(target);return s;
        }catch(Exception e){try{s.rollback();}catch(Exception restore){e.addSuppressed(restore);}throw e;}
    }
    String watchdog(int seconds){
        String d=Root.quote(dir.getAbsolutePath());
        return "#!/system/bin/sh\ncd "+d+" || exit 1\n"+
            "uptime_ms() { read up rest < /proc/uptime || return 1; sec=${up%.*}; frac=${up#*.}000; frac=$(printf '%.3s' \"$frac\"); echo $((sec*1000+1$frac-1000)); }\n"+
            "start=$(uptime_ms) || exit 1; limit=$((start+"+(seconds*1000)+")); printf armed > armed\n"+
            "while :; do now=$(uptime_ms) || break; [ \"$now\" -ge \"$limit\" ] && break; sleep 1; done\n"+
            "if mv pending expired 2>/dev/null; then\n wm density "+previous+" && printf restored > rollback.ok\nfi\n";
    }
    File file(String name){return new File(dir,name);}
    boolean confirm()throws Exception {
        if(!file("pending").exists())return false;
        verify(target);
        // rename is the single winner against the watchdog's atomic mv.
        if(!file("pending").renameTo(file("confirmed")))return false;
        finish();return true;
    }
    void rollback()throws Exception {
        // Leave the independent watchdog armed if the foreground restore fails.
        Root.run("wm density "+previous);verify(previous);
        file("pending").renameTo(file("expired"));finish();
    }
    private void verify(String expected)throws Exception {
        String actual=previous(Root.run("wm density"));
        if(!expected.equals(actual))throw new IOException("系统密度读回与目标不一致；恢复记录已保留");
    }
    private void finish()throws IOException {
        File active=new File(home,"density-active");
        if(active.exists()&&!active.delete())throw new IOException("显示事务日志清理失败");
        // Keep per-session scripts until the sleeping worker exits; never overwrite them.
    }
    static boolean recover(File home)throws Exception {
        File active=new File(home,"density-active");if(!active.exists())return false;
        Properties p=SafeFiles.read(active);
        DensitySession s=new DensitySession(home,p.getProperty("token",""),p.getProperty("previous",""),p.getProperty("target",""),0);
        if(s.file("confirmed").exists()){s.verify(s.target);s.finish();}else{s.rollback();}
        return true;
    }
    static void prune(File home){
        if(new File(home,"density-active").exists())return;
        File[] dirs=home.listFiles();if(dirs==null)return;
        for(File candidate:dirs){
            if(!candidate.isDirectory()||!candidate.getName().matches("density-[a-f0-9-]{36}")||System.currentTimeMillis()-candidate.lastModified()<120000||new File(candidate,"pending").exists())continue;
            File[] files=candidate.listFiles();if(files==null)continue;boolean known=true;
            for(File f:files)if(!f.isFile()||!f.getName().matches("confirmed|expired|armed|rollback\\.ok|watchdog\\.sh"))known=false;
            if(!known)continue;for(File f:files)f.delete();candidate.delete();
        }
    }
}
