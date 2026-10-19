package local.usbcontrol;

import android.content.*;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import java.io.*;
import java.util.UUID;

final class BackgroundStore {
    static Bitmap decode(ContentResolver resolver,Uri uri)throws IOException {
        Bitmap b;
        if(Build.VERSION.SDK_INT>=28){
            b=ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver,uri),(decoder,info,source)->{
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                float factor=Math.min(1,2048f/Math.max(w,h));
                decoder.setTargetSize(Math.max(1,Math.round(w*factor)),Math.max(1,Math.round(h*factor)));
            });
        }else{
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(InputStream in=resolver.openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
            if(options.outWidth<1||options.outHeight<1)throw new IOException("图片尺寸无效");
            options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;
            try(InputStream in=resolver.openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,options);}
            if(b==null)throw new IOException("图片解码失败");
            int orientation=1;
            try(InputStream in=resolver.openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
            b=orient(b,orientation);
        }
        if(b==null)throw new IOException("系统未支持此图片格式");return b;
    }
    static Bitmap orient(Bitmap b,int orientation){
            Matrix matrix=new Matrix();
            switch(orientation){
                case 2:matrix.setScale(-1,1);break;
                case 3:matrix.setRotate(180);break;
                case 4:matrix.setRotate(180);matrix.postScale(-1,1);break;
                case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;
                case 6:matrix.setRotate(90);break;
                case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;
                case 8:matrix.setRotate(-90);break;
            }
            if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),matrix,true);if(rotated!=b)b.recycle();b=rotated;}
        return b;
    }
    static File selected(File home,SharedPreferences prefs){
        String name=prefs.getString("wallpaper_file","background.png");
        if("none".equals(name))return new File(home,"background.none");
        if(!name.matches("background(?:-[a-f0-9-]{36})?\\.png"))return new File(home,"background.png");
        return new File(home,name);
    }
    static void save(File home,SharedPreferences prefs,Bitmap image,float zoom,float fx,float fy,int opacity)throws IOException {
        File old=selected(home,prefs),file=new File(home,"background-"+UUID.randomUUID()+".png");
        String oldName=prefs.getString("wallpaper_file","background.png");float oz=prefs.getFloat("zoom",1),ox=prefs.getFloat("fx",.5f),oy=prefs.getFloat("fy",.5f);int oa=prefs.getInt("opacity",25);
        boolean preferencesAttempted=false;
        try {
            try(FileOutputStream out=new FileOutputStream(file)){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("图片保存失败");out.getFD().sync();}
            preferencesAttempted=true;
            boolean committed=prefs.edit().putString("wallpaper_file",file.getName()).putFloat("zoom",zoom).putFloat("fx",fx).putFloat("fy",fy).putInt("opacity",opacity).commit();
            if(!committed){boolean restored=prefs.edit().putString("wallpaper_file",oldName).putFloat("zoom",oz).putFloat("fx",ox).putFloat("fy",oy).putInt("opacity",oa).commit();throw new IOException(restored?"背景设置写入失败，已恢复原设置":"背景存储异常；新旧文件均保留，请重启后检查");}
        }catch(IOException e){if(!preferencesAttempted)file.delete();throw e;}
        if(!file.equals(old))old.delete();
    }
    static void remove(File home,SharedPreferences prefs)throws IOException {
        File old=selected(home,prefs);
        String oldName=prefs.getString("wallpaper_file","background.png");
        if(!prefs.edit().putString("wallpaper_file","none").commit()){boolean restored=prefs.edit().putString("wallpaper_file",oldName).commit();throw new IOException(restored?"背景设置写入失败，已恢复原设置":"背景存储异常；原文件仍保留，请重启后检查");}
        old.delete();new File(home,"background.png").delete();
    }
}
