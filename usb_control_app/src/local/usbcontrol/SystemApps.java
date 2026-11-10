package local.usbcontrol;

import java.io.IOException;
import java.util.*;

/** Fixed allowlist; never accepts a package name from editable UI. */
final class SystemApps {
    interface Runner { String run(String command) throws Exception; }
    static final class Entry {
        final String name, pkg;
        final boolean installed;
        Entry(String name,String pkg,boolean installed){this.name=name;this.pkg=pkg;this.installed=installed;}
        String label(){return name+"\n"+pkg;}
    }
    private static final String[][] TARGETS={
        {"快应用","com.miui.hybrid"},
        {"内容中心","com.miui.newhome"},
        {"内容中心","com.miui.contentcenter"},
        {"浏览器","com.android.browser"},
        {"系统广告 adsolution","com.miui.systemAdSolution"}
    };
    private static String user(int user){if(user<0)throw new IllegalArgumentException("用户编号异常");return Integer.toString(user);}
    private static Set<String> packages(String output)throws IOException {
        Set<String> result=new HashSet<>();
        for(String line:output.split("\\r?\\n")){
            if(line.trim().isEmpty())continue;
            if(!line.matches("package:[a-zA-Z0-9_.]+"))throw new IOException("应用列表读取异常："+line);
            result.add(line.substring(8));
        }
        return result;
    }
    static List<Entry> scan(Runner runner,int user)throws Exception {
        String uid=user(user);
        Set<String> system=packages(runner.run("pm list packages -s -u --user "+uid));
        Set<String> installed=packages(runner.run("pm list packages --user "+uid));
        List<Entry> result=new ArrayList<>();
        for(String[] target:TARGETS)if(system.contains(target[1]))result.add(new Entry(target[0],target[1],installed.contains(target[1])));
        return result;
    }
    static String command(String pkg,int user,boolean restore){
        boolean known=false;for(String[] t:TARGETS)if(t[1].equals(pkg))known=true;
        if(!known)throw new IllegalArgumentException("未知应用");
        return restore?"cmd package install-existing --user "+user(user)+" "+pkg:
            "pm uninstall --user "+user(user)+" "+pkg;
    }
    static String apply(Runner runner,int user,List<Entry> selection,boolean restore)throws Exception {
        if(selection.isEmpty())throw new IllegalArgumentException("请先勾选应用");
        // Re-read before mutation: stale dialogs and caller-created entries confer no eligibility.
        List<Entry> current=scan(runner,user);
        StringBuilder report=new StringBuilder();Set<String> seen=new HashSet<>();
        for(Entry selected:selection){
            if(!seen.add(selected.pkg))continue;
            Entry actual=null;for(Entry e:current)if(e.pkg.equals(selected.pkg))actual=e;
            if(actual==null){report.append(selected.name).append("：跳过（未识别为受支持的系统应用）\n");continue;}
            if(actual.installed==restore){report.append(actual.name).append(restore?"：已安装，跳过\n":"：已卸载，跳过\n");continue;}
            try {
                String output=runner.run(command(actual.pkg,user,restore));
                boolean installed=packages(runner.run("pm list packages --user "+user(user)+" "+actual.pkg)).contains(actual.pkg);
                if(installed!=restore)throw new IOException("状态读回未符合预期；"+output);
                report.append(actual.name).append(restore?"：已恢复应用（不恢复已删除的数据）":"：已卸载（不保留应用数据）").append("\n");
            }catch(Exception e){report.append(actual.name).append("：操作未完成，").append(e.getMessage()).append("\n");}
        }
        return report.toString().trim();
    }
}
