package local.usbcontrol.wallpapertest;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;

/** No activity, UI automation, screenshot or system-setting changes. */
public class WallpaperInstrumentation extends Instrumentation {
    private final JSONArray checks=new JSONArray();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private Object call(String type,String name,Class<?>[] types,Object... args)throws Exception{
        Class<?> c=Class.forName("local.usbcontrol."+type,true,getTargetContext().getClassLoader());
        Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(null,args);}catch(InvocationTargetException e){throw new Exception(e.getCause());}
    }
    private String root(String cmd)throws Exception{return (String)call("Root","run",new Class[]{String.class},cmd);}
    private void check(boolean ok,String label,String result)throws Exception{
        checks.put(new JSONObject().put("check",label).put("pass",ok).put("actual",result));
        if(!ok)throw new AssertionError(label+": "+result);
    }
    @Override public void onStart(){
        Bundle status=new Bundle();JSONObject report=new JSONObject();boolean pass=false;
        try{
            Context ctx=getTargetContext();
            String version=ctx.getPackageManager().getPackageInfo("local.usbcontrol",0).versionName;
            check("1.0.5".equals(version),"Installed target version",version);
            check("0".equals(root("id -u")),"Production Root transport from app UID","root UID 0");
            File audit=new File(ctx.getFilesDir(),"wallpaper-symlink-test-"+System.nanoTime());
            File sibling=new File(audit,"other"),link=new File(audit,"wallpaper-loop");
            if(!sibling.mkdirs())throw new IOException("test directory creation failed");
            try{
                android.system.Os.symlink(sibling.getAbsolutePath(),link.getAbsolutePath());
                Context redirected=new android.content.ContextWrapper(ctx){@Override public File getFilesDir(){return audit;}};
                boolean rejected=false;
                try{call("WallpaperLoop","run",new Class[]{Context.class,boolean.class},redirected,false);}
                catch(Exception e){rejected=e.toString().contains("壁纸任务目录异常");}
                check(rejected&&sibling.list().length==0,"State symlink rejected BEFORE extracting script","rejected; sibling remains empty");
            }finally{link.delete();sibling.delete();audit.delete();}
            String dir="/sdcard/Android/data/com.android.thememanager/files/MIUI/.videowallpaper";
            // Gate the real-directory integration call: existing known canonical files only.
            String names=root("find "+dir+" -maxdepth 1 -type f -name 'LocalImport*' -exec basename {} \\;");
            String[] list=names.split("\\n");check(list.length==5,"Known real-directory fixture count",names);
            for(String name:list)check(name.matches("LocalImport_&_[0-9a-f]{32}"),"Existing name canonical",name);
            String before=root("find "+dir+" -type f -exec sha256sum {} \\;");
            String journal=ctx.getFilesDir()+"/wallpaper-loop/wallpaper-renames.tsv";
            check("empty".equals(root("if [ -s '"+journal+"' ]; then echo pending; else echo empty; fi")),"No user undo records will be consumed","empty");
            String apply=(String)call("WallpaperLoop","run",new Class[]{Context.class,boolean.class},ctx,false);
            check(apply.contains("已处理 0 个")&&apply.contains("已规范 5 个"),"Production wrapper, packaged raw resource and real directory",apply);
            String undo=(String)call("WallpaperLoop","run",new Class[]{Context.class,boolean.class},ctx,true);
            check(undo.contains("没有待撤销"),"Production wrapper undo no-op",undo);
            check(before.equals(root("find "+dir+" -type f -exec sha256sum {} \\;")),"Real filenames and contents unchanged",before);
            pass=true;
        }catch(Throwable e){try{report.put("error",android.util.Log.getStackTraceString(e));}catch(Exception ignored){}}
        try{report.put("passed",pass).put("checks",checks);
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"wallpaper-review-test.json"))){out.write(report.toString(2).getBytes("UTF-8"));out.getFD().sync();}
        }catch(Exception e){pass=false;}
        status.putString("stream",report.toString());finish(pass?-1:0,status);
    }
}
