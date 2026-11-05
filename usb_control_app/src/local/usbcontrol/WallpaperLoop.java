package local.usbcontrol;

import android.content.Context;
import java.io.*;

final class WallpaperLoop {
    static final String DIRECTORY="/sdcard/Android/data/com.android.thememanager/files/MIUI/.videowallpaper";
    static synchronized String run(Context context, boolean undo) throws Exception {
        if(android.os.Process.myUid()/100000!=0)throw new IOException("此壁纸目录仅适配手机主用户，请在主用户中使用轻控");
        File dir=new File(context.getFilesDir(),"wallpaper-loop");
        if(!dir.isDirectory()&&!dir.mkdir())throw new IOException("壁纸任务目录创建失败");
        File expected=new File(context.getFilesDir().getCanonicalFile(),"wallpaper-loop");
        if(!dir.getCanonicalFile().equals(expected))throw new IOException("壁纸任务目录异常，请检查 App 私有目录");
        File script=new File(dir,"wallpaper.sh");
        try(InputStream in=context.getResources().openRawResource(R.raw.wallpaper);
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);
            SafeFiles.write(script,out.toByteArray());
        }
        return Root.run("sh "+Root.quote(script.getAbsolutePath())+" "+Root.quote(DIRECTORY)+" "+Root.quote(dir.getAbsolutePath())+" "+(undo?"undo":"apply"),600);
    }
}
