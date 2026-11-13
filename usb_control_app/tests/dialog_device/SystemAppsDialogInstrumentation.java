package local.usbcontrol;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Fake entries and a counting callback only: never invokes SystemApps.apply or any shell mutation. */
public final class SystemAppsDialogInstrumentation extends Instrumentation {
    private final JSONArray checks=new JSONArray();
    private final JSONArray skipped=new JSONArray();
    private Activity activity;
    private SystemAppsDialog dialog;
    private int submissions;
    private java.util.List<SystemApps.Entry> submitted;
    private Object field(String name)throws Exception{Field f=SystemAppsDialog.class.getDeclaredField(name);f.setAccessible(true);return f.get(dialog);}
    private void check(boolean value,String label)throws Exception{checks.put(new JSONObject().put("check",label).put("pass",value));Bundle progress=new Bundle();progress.putString("stream",label+": "+value);sendStatus(0,progress);if(!value)throw new AssertionError(label);}
    private void main(Runnable job){runOnMainSync(job);}
    private Button button(String name)throws Exception{return (Button)field(name);}
    private void click(String name)throws Exception{Button button=button(name);main(()->button.performClick());}
    private void row(int index)throws Exception{View view=(View)((java.util.List<?>)field("choiceRows")).get(index);main(()->view.performClick());}
    private void show(boolean dark,boolean restore,float fontScale,int count)throws Exception{
        main(()->{
            Configuration configuration=new Configuration(activity.getResources().getConfiguration());configuration.fontScale=fontScale;
            android.view.ContextThemeWrapper dialogContext=new android.view.ContextThemeWrapper(activity,dark?R.style.DarkTheme:R.style.LightTheme);
            dialogContext.applyOverrideConfiguration(configuration);
            java.util.List<SystemApps.Entry> entries=new ArrayList<>();
            String[] names={"快应用","内容中心","浏览器","系统广告 adsolution","内容中心（备用包名）"};
            try{
                java.lang.reflect.Field targets=SystemApps.class.getDeclaredField("TARGETS");targets.setAccessible(true);
                String[][] catalogue=(String[][])targets.get(null);
                for(int i=0;i<count;i++)entries.add(count==18?new SystemApps.Entry(catalogue[i][0],catalogue[i][1],!restore):new SystemApps.Entry(names[i],"local.test.system.application"+i,!restore));
            }catch(Exception e){throw new RuntimeException(e);}
            SystemAppsDialog.Palette p=new SystemAppsDialog.Palette(dark?0xff8ab4ff:0xff2563eb,dark?0xffe6edf8:0xff192b48,dark?0xffa9b7cb:0xff64748b,dark?0xff1c2736:0xffffffff,dark?0xff293951:0xffeef3fd,dark?0xff344257:0xffe9eef6,dark?0xff102039:0xffffffff,dark);
            dialog=new SystemAppsDialog(dialogContext,p,entries,restore,0,selection->{submissions++;submitted=selection;});
            entries.clear(); // The production dialog must retain its own immutable list snapshot.
            dialog.show();
            // A stopped/locked host can defer WindowManager traversal. Render this app's own
            // decor with the same viewport constraints; never read or dismiss the keyguard.
            View decor=dialog.getWindow().getDecorView();android.graphics.Rect frame=new android.graphics.Rect();
            activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
            int width=dialog.getWindow().getAttributes().width;
            if(width<=0)width=activity.getResources().getDisplayMetrics().widthPixels;
            int height=frame.height()>0?frame.height():activity.getResources().getDisplayMetrics().heightPixels;
            decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.AT_MOST));
            decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());
        });
    }
    private void screenshot(String name)throws Exception{
        Thread.sleep(350);final Bitmap[] captured=new Bitmap[1];
        main(()->{View decor=dialog.getWindow().getDecorView();captured[0]=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new android.graphics.Canvas(captured[0]));});
        Bitmap image=captured[0];
        File dir=new File(getTargetContext().getFilesDir(),"dialog-review");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    private void withinWindow(String label)throws Exception{
        main(()->{
            View decor=dialog.getWindow().getDecorView();int width=dialog.getWindow().getAttributes().width;
            if(width<=0)width=activity.getResources().getDisplayMetrics().widthPixels;
            decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(activity.getResources().getDisplayMetrics().heightPixels,View.MeasureSpec.AT_MOST));
            decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());
        });
        View panel=(View)field("panel");Button primary=button("primaryButton"),secondary=button("secondaryButton");
        android.graphics.Rect frame=new android.graphics.Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
        int[] location=new int[2];primary.getLocationOnScreen(location);
        check(panel.getWidth()<=frame.width()&&panel.getHeight()<=frame.height(),label+" fits visible window");
        check(location[1]+primary.getHeight()<=frame.bottom&&primary.getHeight()>0&&secondary.getHeight()>0,label+" footer actions remain visible");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        JSONObject report=new JSONObject();Bundle status=new Bundle();boolean pass=false;
        try{
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
            // Shell foreground launch avoids ROM background-start interception and an unbounded startActivitySync wait.
            try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("am start -n local.usbcontrol/.MainActivity");
                InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){
                byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}
            }
            activity=monitor.waitForActivityWithTimeout(15000);removeMonitor(monitor);
            if(activity==null)throw new IOException("Target activity did not start within 15 seconds");
            main(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            check(SystemAppsDialog.badge(new SystemApps.Entry("任意标签","com.miui.systemAdSolution",true)).equals("AD"),"only exact ad package gets AD badge");
            check(SystemAppsDialog.badge(new SystemApps.Entry("任意标签","com.miui.hybrid",true)).equals("快"),"badge follows package identity not display name");
            java.lang.reflect.Field catalogueField=SystemApps.class.getDeclaredField("TARGETS");catalogueField.setAccessible(true);
            String[][] catalogue=(String[][])catalogueField.get(null);
            check(catalogue.length==18,"production catalogue contains eighteen candidates");
            for(int i=5;i<catalogue.length;i++)check(SystemAppsDialog.badge(new SystemApps.Entry(catalogue[i][0],catalogue[i][1],true)).equals("注"),"carrier package shows registration badge: "+catalogue[i][1]);
            for(boolean dark:new boolean[]{false,true})for(boolean restore:new boolean[]{false,true}){
                show(dark,restore,activity.getResources().getConfiguration().fontScale,4);withinWindow((dark?"dark":"light")+(restore?" restore":" uninstall"));
                check(!button("primaryButton").isEnabled(),"none selected disables next");
                screenshot((dark?"dark":"light")+(restore?"-restore":"-uninstall"));
                row(1);check(button("primaryButton").isEnabled(),"row selection enables next");
                View selectedRow=(View)((java.util.List<?>)field("choiceRows")).get(1);android.view.accessibility.AccessibilityNodeInfo node=selectedRow.createAccessibilityNodeInfo();check(node.isCheckable()&&node.isChecked(),"selected row exposes checkable/checked accessibility state");node.recycle();
                click("selectAllButton");check(((TextView)field("selectionCount")).getText().toString().contains("4 / 4"),"select all updates count");
                click("selectAllButton");check(!button("primaryButton").isEnabled(),"clear all disables next");
                row(2);Button oldNext=button("primaryButton"),oldAll=button("selectAllButton");View oldRow=(View)((java.util.List<?>)field("choiceRows")).get(0);
                click("primaryButton");check((Boolean)field("confirming"),"next opens confirmation within styled dialog");
                main(()->oldNext.performClick());check(submissions==0,"queued old next click cannot become final confirmation");
                main(()->{oldAll.performClick();oldRow.performClick();});check(((TextView)field("selectionCount")).getText().toString().contains("1 / 4"),"obsolete selection controls cannot change reviewed confirmation entries");
                check(submissions==0,"confirmation step has no operation callback");
                screenshot((dark?"dark":"light")+(restore?"-restore-confirm":"-uninstall-confirm"));
                click("secondaryButton");check(!(Boolean)field("confirming"),"back returns to selection");
                check(((TextView)field("selectionCount")).getText().toString().contains("1 / 4"),"back preserves selected entries");
                click("primaryButton");Button submitButton=button("primaryButton");click("primaryButton");main(()->submitButton.performClick());
                check(submissions==1&&submitted.size()==1&&submitted.get(0).pkg.endsWith("2"),"only final confirm submits exact selected entry once");
                check(!dialog.isShowing(),"submit dismisses dialog");submissions=0;
            }
            show(true,false,activity.getResources().getConfiguration().fontScale,5);row(0);click("primaryButton");main(()->dialog.onBackPressed());check(!(Boolean)field("confirming"),"system back returns to selection without submission");main(()->dialog.dismiss());check(submissions==0,"cancel never submits operation");
            show(false,false,activity.getResources().getConfiguration().fontScale,4);row(0);click("primaryButton");Button stale=button("primaryButton");main(()->dialog.dismiss());main(()->stale.performClick());check(submissions==0,"dismissed confirmation button cannot submit a stale action");
            show(false,true,1.6f,5);withinWindow("large-font five-item list");screenshot("large-font-restore");
            ScrollView scroll=(ScrollView)((android.view.ViewGroup)field("panel")).getChildAt(0);main(()->{scroll.setSmoothScrollingEnabled(false);scroll.fullScroll(View.FOCUS_DOWN);});check(scroll.getScrollY()>0,"large-font warning remains reachable by scrolling");main(()->dialog.dismiss());
            for(boolean dark:new boolean[]{false,true})for(boolean restore:new boolean[]{false,true}){
                show(dark,restore,1.6f,18);withinWindow("large-font eighteen-item list");
                click("selectAllButton");check(((TextView)field("selectionCount")).getText().toString().contains("18 / 18"),"expanded catalogue selects all eighteen");
                click("primaryButton");withinWindow("eighteen-item confirmation");
                ScrollView longScroll=(ScrollView)((android.view.ViewGroup)field("panel")).getChildAt(0);
                main(()->{longScroll.setSmoothScrollingEnabled(false);longScroll.fullScroll(View.FOCUS_DOWN);});
                check(longScroll.getScrollY()>0,"eighteen-item confirmation is scrollable");
                click("secondaryButton");check(((TextView)field("selectionCount")).getText().toString().contains("18 / 18"),"expanded selection survives confirmation back");
                main(()->dialog.dismiss());check(submissions==0,"expanded catalogue cancellation never mutates packages");
            }
            int orientation=activity.getRequestedOrientation();
            try{
                main(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(1400);
                android.graphics.Rect actualFrame=new android.graphics.Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(actualFrame);
                if(activity.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE&&actualFrame.width()>actualFrame.height()){
                    for(boolean restore:new boolean[]{false,true}){
                        show(true,restore,1.3f,18);withinWindow("landscape eighteen-item list");
                        click("selectAllButton");click("primaryButton");withinWindow("landscape eighteen-item confirmation");
                        main(()->dialog.dismiss());check(submissions==0,"landscape cancellation never mutates packages");
                    }
                }else skipped.put("Landscape request not applied by device (for example, while locked); no landscape coverage claimed.");
            }finally{main(()->activity.setRequestedOrientation(orientation));Thread.sleep(800);}
            pass=true;
        }catch(Throwable e){try{report.put("error",android.util.Log.getStackTraceString(e));}catch(Exception ignored){}}
        finally{try{main(()->{if(dialog!=null)dialog.dismiss();});}catch(Exception ignored){}}
        try{report.put("passed",pass).put("checks",checks).put("skipped",skipped).put("shell_mutations",0);
            File file=new File(getTargetContext().getFilesDir(),"dialog-review.json");try(FileOutputStream out=new FileOutputStream(file)){out.write(report.toString(2).getBytes("UTF-8"));}
        }catch(Exception e){pass=false;}
        status.putString("stream",report.toString());finish(pass?-1:0,status);
    }
}
