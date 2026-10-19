package local.usbcontrol;
import android.content.SharedPreferences;
import java.io.*;
import java.util.Properties;

/** The durable file is authoritative even if SharedPreferences.commit fails. */
final class Baseline {
    static void ensure(File home,SharedPreferences prefs,String snapshot)throws Exception {
        File file=new File(home,"system-baseline.properties");Properties p=new Properties();
        if(file.exists())p=SafeFiles.read(file);
        else if(prefs.contains("baseline_adb")){
            for(String key:new String[]{"baseline_adb","baseline_dev","baseline_density","baseline_install_space","baseline_install"})if(prefs.contains(key))p.setProperty(key,prefs.getString(key,""));
        }else{
            p.setProperty("baseline_adb",Root.section(snapshot,"ADB"));p.setProperty("baseline_dev",Root.section(snapshot,"DEV"));p.setProperty("baseline_density",DensitySession.previous(Root.section(snapshot,"DENSITY")));
        }
        String ns=Logic.installNamespace(Root.section(snapshot,"GLOBAL"),Root.section(snapshot,"SECURE"),Root.section(snapshot,"SYSTEM"));
        if(ns!=null&&!p.containsKey("baseline_install")){
            String value=Root.run("settings get "+ns+" adb_install_enabled");if(!value.matches("[01]"))throw new IOException("USB 安装状态在检测期间变化");
            p.setProperty("baseline_install_space",ns);p.setProperty("baseline_install",value);
        }
        for(String key:new String[]{"baseline_adb","baseline_dev"})if(!p.getProperty(key,"").matches("null|[0-9]{1,9}"))throw new IOException("初始系统值异常："+key);
        if(!DensitySession.validDensity(p.getProperty("baseline_density","")))throw new IOException("初始密度记录异常");
        if(p.containsKey("baseline_install")&&(!p.getProperty("baseline_install").matches("[01]")||!p.getProperty("baseline_install_space","").matches("global|secure|system")))throw new IOException("初始 USB 安装记录异常");
        SafeFiles.properties(file,p);
        SharedPreferences.Editor edit=prefs.edit();for(String key:p.stringPropertyNames())edit.putString(key,p.getProperty(key));
        edit.putLong("baseline_commit_generation",System.nanoTime());
        if(!edit.commit())throw new IOException("初始状态缓存写入失败；原始值已保留在恢复文件中");
    }
}
