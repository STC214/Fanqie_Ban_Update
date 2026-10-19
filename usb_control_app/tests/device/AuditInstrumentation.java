package local.usbcontrol.audit;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;

/** Separate signed test APK; exercises production classes in the real app process. */
public class AuditInstrumentation extends Instrumentation {
    final JSONArray results=new JSONArray();
    private String mode="full";
    private Object call(String type,String method,Class<?>[] types,Object... args)throws Exception {
        Class<?> c=Class.forName("local.usbcontrol."+type,true,getTargetContext().getClassLoader());
        Method m=c.getDeclaredMethod(method,types);m.setAccessible(true);
        try{return m.invoke(null,args);}catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new RuntimeException(cause);}
    }
    private Object invoke(Object obj,String name)throws Exception {Method m=obj.getClass().getDeclaredMethod(name);m.setAccessible(true);try{return m.invoke(obj);}catch(InvocationTargetException e){throw new RuntimeException(e.getCause());}}
    private String root(String cmd)throws Exception{return (String)call("Root","run",new Class[]{String.class},cmd);}
    private void check(boolean pass,String label,String actual)throws Exception {
        JSONObject result=new JSONObject().put("pass",pass).put("check",label).put("actual",actual);results.put(result);if(!pass)throw new AssertionError(label+": "+actual);
    }
    private Object begin(File home,int dpi)throws Exception{return call("DensitySession","begin",new Class[]{File.class,String.class,long.class},home,String.valueOf(dpi),SystemClock.elapsedRealtime());}
    @Override public void onCreate(Bundle args){super.onCreate(args);mode=args.getString("mode","full");start();}
    @Override public void callActivityOnCreate(Activity activity,Bundle state){
        super.callActivityOnCreate(activity,state);
        activity.getWindow().getDecorView().setKeepScreenOn(true);
        if(Build.VERSION.SDK_INT>=27){activity.setShowWhenLocked(true);activity.setTurnScreenOn(true);}
    }
    @Override public void onStart(){
        JSONObject report=new JSONObject();Bundle status=new Bundle();String densityBefore=null,adbBefore=null,devBefore=null,wifiBefore=null;boolean passed=false;
        PowerManager.WakeLock wake=null;
        try {
            Intent launch=getTargetContext().getPackageManager().getLaunchIntentForPackage("local.usbcontrol");launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(launch);
            runOnMainSync(()->{activity.getWindow().getDecorView().setKeepScreenOn(true);if(Build.VERSION.SDK_INT>=27){activity.setShowWhenLocked(true);activity.setTurnScreenOn(true);}});
            if(!"ui".equals(mode)&&Build.VERSION.SDK_INT>=29){getUiAutomation().adoptShellPermissionIdentity(android.Manifest.permission.WAKE_LOCK);wake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"usbcontrol:audit");wake.acquire(240000);}
            if("ui".equals(mode)){
                File ready=new File(getTargetContext().getFilesDir(),"ui-audit-ready"),done=new File(getTargetContext().getFilesDir(),"ui-audit-done");
                try(FileOutputStream out=new FileOutputStream(ready)){out.write(1);}
                long until=SystemClock.elapsedRealtime()+180000;while(!done.exists()&&SystemClock.elapsedRealtime()<until)Thread.sleep(500);
                ready.delete();done.delete();passed=true;return;
            }
            check(root("id -u").equals("0"),"Production Root transport: app UID granted root","0");
            densityBefore=root("wm density");adbBefore=root("settings get global adb_enabled");devBefore=root("settings get global development_settings_enabled");wifiBefore=root("settings get global adb_wifi_enabled");
            report.put("baseline_density",densityBefore).put("baseline_adb",adbBefore).put("baseline_development",devBefore).put("baseline_wifi",wifiBefore);
            Field snapshotField=Class.forName("local.usbcontrol.Root",true,getTargetContext().getClassLoader()).getDeclaredField("SNAPSHOT");snapshotField.setAccessible(true);
            String snapshot=root((String)snapshotField.get(null));
            call("Root","validate",new Class[]{String.class},snapshot);
            check(true,"Production snapshot validation",snapshot);
            java.util.regex.Matcher match=java.util.regex.Pattern.compile("(?:Physical|Override) density: (\\d+)").matcher(densityBefore);int current=0;while(match.find())current=Integer.parseInt(match.group(1));
            File home=new File(getTargetContext().getFilesDir(),"audit-density");home.mkdirs();
            if(new File(home,"density-active").exists())call("DensitySession","recover",new Class[]{File.class},home);
            int target=current+12;
            Object expiring=begin(home,target);check(root("wm density").contains("Override density: "+target),"Density preview applied",root("wm density"));
            Thread.sleep(21500);check(root("wm density").equals(densityBefore),"Independent watchdog restores density",root("wm density"));call("DensitySession","recover",new Class[]{File.class},home);
            Object confirmed=begin(home,target);check((Boolean)invoke(confirmed,"confirm"),"Atomic confirm accepted","true");Thread.sleep(21500);
            check(root("wm density").contains("Override density: "+target),"Confirmed density survives old watchdog",root("wm density"));
            restoreDensity(densityBefore);
            Object manual=begin(home,current+16);invoke(manual,"rollback");check(root("wm density").equals(densityBefore),"Manual rollback preserves pre-existing override",root("wm density"));
            begin(home,current+20);call("DensitySession","recover",new Class[]{File.class},home);check(root("wm density").equals(densityBefore),"Pending journal process-restart recovery",root("wm density"));
            check(!new File(home,"density-active").exists(),"No unfinished transaction remains","absent");
            // This process belongs to the app, not adb: it survives USB transport loss.
            root("settings put global adb_enabled 0");Thread.sleep(1500);check(root("settings get global adb_enabled").equals("0"),"USB debugging OFF readback","0");
            root("settings put global development_settings_enabled 1 && settings put global adb_enabled 1");Thread.sleep(1500);check(root("settings get global adb_enabled").equals("1"),"USB debugging ON readback","1");
            check(root("settings get global adb_wifi_enabled").equals(wifiBefore),"Wireless debugging unchanged",wifiBefore);
            android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("audit-background",0);
            File images=new File(getTargetContext().getFilesDir(),"audit-images");images.mkdirs();Bitmap image=Bitmap.createBitmap(96,64,Bitmap.Config.ARGB_8888);image.eraseColor(0xff3b82f6);
            call("BackgroundStore","save",new Class[]{File.class,android.content.SharedPreferences.class,Bitmap.class,float.class,float.class,float.class,int.class},images,prefs,image,1.5f,.2f,.7f,35);
            File selected=(File)call("BackgroundStore","selected",new Class[]{File.class,android.content.SharedPreferences.class},images,prefs);
            Bitmap stored=BitmapFactory.decodeFile(selected.getAbsolutePath());check(stored!=null&&stored.getWidth()==96&&stored.getHeight()==64,"Atomic background file and metadata commit",selected.getName());
            call("BackgroundStore","remove",new Class[]{File.class,android.content.SharedPreferences.class},images,prefs);
            check(!((File)call("BackgroundStore","selected",new Class[]{File.class,android.content.SharedPreferences.class},images,prefs)).exists(),"Background removal stays removed","none");image.recycle();if(stored!=null)stored.recycle();
            int[][] corners={{0xffff0000,0xff00ff00,0xff0000ff,0xffffff00},{0xff00ff00,0xffff0000,0xffffff00,0xff0000ff},{0xffffff00,0xff0000ff,0xff00ff00,0xffff0000},{0xff0000ff,0xffffff00,0xffff0000,0xff00ff00},{0xffff0000,0xff0000ff,0xff00ff00,0xffffff00},{0xff0000ff,0xffff0000,0xffffff00,0xff00ff00},{0xffffff00,0xff00ff00,0xff0000ff,0xffff0000},{0xff00ff00,0xffffff00,0xffff0000,0xff0000ff}};
            for(int orientation=1;orientation<=8;orientation++){
                Bitmap sample=Bitmap.createBitmap(4,2,Bitmap.Config.ARGB_8888);for(int y=0;y<2;y++)for(int x=0;x<4;x++)sample.setPixel(x,y,corners[0][(y==0?0:2)+(x<2?0:1)]);
                Bitmap oriented=(Bitmap)call("BackgroundStore","orient",new Class[]{Bitmap.class,int.class},sample,orientation);
                int w=oriented.getWidth(),h=oriented.getHeight();int[] expected=corners[orientation-1];
                check(w==(orientation>=5?2:4)&&h==(orientation>=5?4:2)&&oriented.getPixel(0,0)==expected[0]&&oriented.getPixel(w-1,0)==expected[1]&&oriented.getPixel(0,h-1)==expected[2]&&oriented.getPixel(w-1,h-1)==expected[3],"EXIF orientation "+orientation,"dimensions and all four corners match");oriented.recycle();
            }
            String recoveryOutput=root("sh '"+getTargetContext().getFilesDir().getAbsolutePath()+"/restore-system.sh'");
            check(recoveryOutput.contains("readback verified"),"Generated emergency recovery script executed with readback",recoveryOutput);
            passed=true;
        }catch(Throwable e){try{report.put("error",android.util.Log.getStackTraceString(e));}catch(Exception ignored){}}
        finally{
            try{
                if(densityBefore!=null)restoreDensity(densityBefore);
                if(devBefore!=null)restoreSetting("development_settings_enabled",devBefore);
                if(adbBefore!=null)restoreSetting("adb_enabled",adbBefore);
                if(densityBefore!=null)check(root("wm density").equals(densityBefore),"Final density restored",root("wm density"));
                if(adbBefore!=null)check(root("settings get global adb_enabled").equals(adbBefore),"Final USB debugging restored",root("settings get global adb_enabled"));
                if(devBefore!=null)check(root("settings get global development_settings_enabled").equals(devBefore),"Final developer options restored",root("settings get global development_settings_enabled"));
                if(wifiBefore!=null)check(root("settings get global adb_wifi_enabled").equals(wifiBefore),"Final wireless debugging unchanged",root("settings get global adb_wifi_enabled"));
            }catch(Throwable e){passed=false;try{report.put("restore_error",android.util.Log.getStackTraceString(e));}catch(Exception ignored){}}
            try{report.put("passed",passed).put("checks",results);try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"audit-report.json"))){out.write(report.toString(2).getBytes("UTF-8"));out.getFD().sync();}}catch(Exception e){passed=false;}
            if(wake!=null&&wake.isHeld())wake.release();if(!"ui".equals(mode)&&Build.VERSION.SDK_INT>=29)getUiAutomation().dropShellPermissionIdentity();status.putString("stream",report.toString());finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,status);
        }
    }
    private void restoreDensity(String text)throws Exception {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("Override density: (\\d+)").matcher(text);root("wm density "+(m.find()?m.group(1):"reset"));
    }
    private void restoreSetting(String key,String value)throws Exception{root("settings "+("null".equals(value)?"delete global "+key:"put global "+key+" "+value));}
}
