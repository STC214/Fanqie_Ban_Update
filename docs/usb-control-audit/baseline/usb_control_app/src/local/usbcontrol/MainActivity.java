package local.usbcontrol;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int BLUE=0xff2563eb, INK=0xff192b48, MUTED=0xff64748b, PICK=81;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private FrameLayout stage;
    private LinearLayout page;
    private Backdrop background;
    private Bitmap wallpaper;
    private Switch adbSwitch,installSwitch;
    private TextView rootLabel,adbDetail,installDetail,densityValue;
    private Button densityButton,restoreButton;
    private String installSpace, snapshot="", densityText="", statusMessage="正在检查 Root 权限…";
    private boolean rootReady=false,busy=false,binding=false,densityInFlight=false;
    private int shortPx,currentDpi;
    private interface Job { String run() throws Exception; }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){ TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t; }
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void gap(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private void note(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("preferences",MODE_PRIVATE);
        getWindow().setStatusBarColor(0xfff4f7fc);getWindow().setNavigationBarColor(0xfff4f7fc);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        File file=new File(getFilesDir(),"background.png");
        if(file.exists())wallpaper=BitmapFactory.decodeFile(file.getPath());
        build();
    }
    @Override protected void onResume(){super.onResume();if(!densityInFlight)refresh();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);build();if(!busy)renderState();}
    @Override protected void onDestroy(){super.onDestroy();worker.shutdown();}
    private void build(){
        stage=new FrameLayout(this);background=new Backdrop(this);loadBackground();
        stage.addView(background,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        page=vertical();pad(page,24,14,24,24);scroll.addView(page);stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        if(Build.VERSION.SDK_INT>=30)stage.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        setContentView(stage);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(this);icon.setImageResource(local.usbcontrol.R.drawable.app_icon);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);icon.setBackground(shape(BLUE,16));icon.setClipToOutline(true);
        header.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout branding=vertical();pad(branding,14,0,0,0);branding.addView(text("轻控",25,INK));branding.addView(text("USB & DISPLAY",10,MUTED));header.addView(branding,new LinearLayout.LayoutParams(0,-2,1));
        TextView version=text("1.0",12,MUTED);pad(version,10,6,10,6);version.setBackground(shape(0xcce8eef9,10));header.addView(version);page.addView(header);gap(page,24);
        page.addView(text("刚刚好，掌控日常。",27,INK));gap(page,6);page.addView(text("连接、显示与外观，轻一点就好。",13,MUTED));gap(page,20);
        rootLabel=text("●  正在检查 Root 权限…",12,BLUE);pad(rootLabel,14,10,14,10);rootLabel.setBackground(shape(0xe6e8efff,14));rootLabel.setOnClickListener(v->refresh());page.addView(rootLabel);gap(page,22);
        section("01  /  连接控制");
        LinearLayout connect=card();
        adbSwitch=toggle(connect,"USB 调试","检查系统状态…",true);adbDetail=(TextView)adbSwitch.getTag();
        View line=new View(this);line.setBackgroundColor(0xffe9eef6);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(16),0,dp(16));connect.addView(line,lp);
        installSwitch=toggle(connect,"USB 安装","检测此系统是否提供独立开关",false);installDetail=(TextView)installSwitch.getTag();
        adbSwitch.setOnCheckedChangeListener((b,on)->{if(!binding)confirmAdb(on);});
        installSwitch.setOnCheckedChangeListener((b,on)->{if(!binding)changeInstall(on);});
        page.addView(connect);gap(page,22);
        section("02  /  显示调节");LinearLayout display=card();display.addView(text("最小宽度",17,INK));gap(display,5);display.addView(text("数值越大，内容越紧凑；不是物理屏幕 DPI。",12,MUTED));gap(display,16);
        densityValue=text("— dp",29,BLUE);display.addView(densityValue);gap(display,12);
        densityButton=button("调整最小宽度",()->densityDialog(),true);display.addView(densityButton);
        Button system=button("恢复系统默认显示",()->confirmResetDensity(),false);display.addView(system);page.addView(display);gap(page,22);
        section("03  /  你的风格");LinearLayout appearance=card();appearance.addView(text("让喜欢的画面，留在这里",17,INK));gap(appearance,5);appearance.addView(text("原比例缩放 · 自由取景 · 透明度实时预览",12,MUTED));gap(appearance,12);
        appearance.addView(button(wallpaper==null?"选择背景图片":"更换背景图片",()->pickImage(),true));
        if(wallpaper!=null){appearance.addView(button("取景与透明度",()->editBackground(wallpaper,false),false));appearance.addView(button("移除背景",()->{
            new AlertDialog.Builder(this).setTitle("移除背景？").setMessage("只删除 App 内的副本，不影响手机相册。").setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->{new File(getFilesDir(),"background.png").delete();wallpaper=null;build();renderState();}).show();
        },false));}
        page.addView(appearance);gap(page,18);
        restoreButton=button("恢复首次使用前的系统设置",()->confirmRestore(),false);page.addView(restoreButton);
        TextView footer=text("本地运行 · 无网络权限 · 不收集数据\n系统操作需 Root；图片留在本机。",11,MUTED);footer.setGravity(Gravity.CENTER);pad(footer,0,16,0,4);page.addView(footer);
        renderState();
    }
    private void section(String s){TextView t=text(s,11,MUTED);t.setLetterSpacing(.10f);page.addView(t);gap(page,10);}
    private LinearLayout card(){LinearLayout c=vertical();pad(c,20,20,20,18);c.setBackground(shape(0xf5ffffff,22));return c;}
    private Switch toggle(LinearLayout parent,String title,String detail,boolean adb){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels=vertical();labels.addView(text(title,17,INK));TextView sub=text(detail,12,MUTED);gap(labels,5);labels.addView(sub);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        Switch sw=new Switch(this);sw.setContentDescription(title);sw.setTag(sub);sw.setShowText(false);sw.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{BLUE,0xffa3afc2}));sw.setTrackTintList(ColorStateList.valueOf(0xffcedaf0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(56),dp(48));p.setMargins(dp(12),0,0,0);row.addView(sw,p);parent.addView(row);return sw;
    }
    private Button button(String label,Runnable action,boolean primary){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:BLUE);b.setBackground(shape(primary?BLUE:0xffeef3fd,13));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(7),0,dp(2));b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
    private void loadBackground(){if(background!=null)background.settings(wallpaper,prefs.getFloat("zoom",1),prefs.getFloat("fx",.5f),prefs.getFloat("fy",.5f),prefs.getInt("opacity",25));}
    private void task(String label,Job job,Runnable success){
        if(busy){renderState();return;}busy=true;statusMessage=label;renderState();
        worker.execute(()->{String error=null;try{job.run();}catch(Exception e){error=e.getMessage();}String err=error;
            ui.post(()->{if(isDestroyed())return;busy=false;if(err!=null){densityInFlight=false;note(err);statusMessage=err.split("\n")[0];renderState();}else if(success!=null)success.run();});});
    }
    private void refresh(){
        if(busy)return;
        task("读取系统状态…",()->{
            String s=Root.run(Root.SNAPSHOT);if(!"0".equals(Root.section(s,"UID")))throw new IOException("请在超级用户管理器中授予轻控 Root 权限");
            if(new File(getFilesDir(),"density.pending").exists()&&!densityInFlight){
                Root.run("wm density "+prefs.getString("pending_previous","reset"));new File(getFilesDir(),"density.pending").delete();s=Root.run(Root.SNAPSHOT);
            }
            snapshot=s;rootReady=true;
            if(!prefs.contains("baseline_adb"))prefs.edit().putString("baseline_adb",Root.section(s,"ADB")).putString("baseline_dev",Root.section(s,"DEV")).putString("baseline_density",previousDensity(Root.section(s,"DENSITY"))).commit();
            String ns=Logic.installNamespace(Root.section(s,"GLOBAL"),Root.section(s,"SECURE"),Root.section(s,"SYSTEM"));
            if(ns!=null&&!prefs.contains("baseline_install"))prefs.edit().putString("baseline_install_space",ns).putString("baseline_install",Root.run("settings get "+ns+" adb_install_enabled")).commit();
            try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"restore-system.sh"))){
                String recovery="#!/system/bin/sh\n[ \"$(id -u)\" = 0 ] || exit 1\n"+restoreSystemCommand()+" || exit 1\nprintf 'Restored initial settings\\n'\nsettings get global adb_enabled\nwm density\n";
                out.write(recovery.getBytes("UTF-8"));
            }
            return s;
        },()->{statusMessage="Root 已连接   ·   "+Build.MANUFACTURER+" "+Build.MODEL+"   ↻";renderState();});
    }
    private static String previousDensity(String density){int override=Logic.lastNumber(density,"Override density",0);return override>0?String.valueOf(override):"reset";}
    private void renderState(){
        if(adbSwitch==null)return;binding=true;rootLabel.setText("●  "+statusMessage);
        boolean adb="1".equals(Root.section(snapshot,"ADB"));adbSwitch.setChecked(adb);adbSwitch.setEnabled(rootReady&&!busy);
        adbDetail.setText(rootReady?(adb?"已开启 · 连接电脑仍需 RSA 授权":"已关闭 · 无线调试保持原状"):"等待 Root 授权；点击上方状态重试");
        installSpace=Logic.installNamespace(Root.section(snapshot,"GLOBAL"),Root.section(snapshot,"SECURE"),Root.section(snapshot,"SYSTEM"));
        String table=installSpace==null?"":Root.section(snapshot,installSpace.toUpperCase(java.util.Locale.ROOT));
        installSwitch.setChecked(java.util.regex.Pattern.compile("(?m)^adb_install_enabled=1$").matcher(table).find());
        installSwitch.setEnabled(rootReady&&!busy&&installSpace!=null);installSwitch.setAlpha(installSpace==null?.35f:1);
        installDetail.setText(!rootReady?"等待系统能力检测":installSpace==null?"此系统未暴露可识别的独立开关":"系统独立开关 · "+installSpace+" / adb_install_enabled");
        densityText=Root.section(snapshot,"DENSITY");int[] size=Logic.size(Root.section(snapshot,"SIZE"));shortPx=Math.min(size[0],size[1]);currentDpi=Logic.lastNumber(densityText,"(?:Physical|Override) density",0);
        densityValue.setText(currentDpi>0?Math.round(shortPx*160f/currentDpi)+" dp  ·  "+currentDpi+" dpi":getResources().getConfiguration().smallestScreenWidthDp+" dp");
        densityButton.setEnabled(rootReady&&!busy&&currentDpi>0);densityButton.setAlpha(densityButton.isEnabled()?1:.45f);restoreButton.setEnabled(rootReady&&!busy&&prefs.contains("baseline_adb"));binding=false;
    }
    private void confirmAdb(boolean on){
        renderState();if(!rootReady||busy)return;
        new AlertDialog.Builder(this).setTitle(on?"开启 USB 调试？":"关闭 USB 调试？").setMessage(on?"连接电脑时仍需你确认 RSA 授权；不会开启无线调试。":"当前 USB ADB 连接将中断，App 内仍可重新开启。").setNegativeButton("取消",null).setPositiveButton(on?"开启":"关闭",(d,w)->task("切换 USB 调试…",()->{
            Root.run((on?"settings put global development_settings_enabled 1 && ":"")+"settings put global adb_enabled "+(on?"1":"0"));
            String actual=Root.run("settings get global adb_enabled");if(!actual.equals(on?"1":"0"))throw new IOException("系统未接受 USB 调试变更");
            Root.run("if [ -f /data/adb/modules/usb_debug_toggle/status.sh ]; then sh /data/adb/modules/usb_debug_toggle/status.sh update; fi; true");return actual;
        },()->{note("USB 调试已"+(on?"开启":"关闭"));refresh();})).show();
    }
    private void changeInstall(boolean on){
        String ns=installSpace;renderState();if(ns==null||!rootReady||busy)return;
        new AlertDialog.Builder(this).setTitle(on?"允许通过 USB 安装？":"关闭 USB 安装？").setMessage("仅更改系统已存在的 USB 安装独立开关，不更改安装验证、未知来源或账号限制。").setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->task("设置 USB 安装…",()->{
            Root.run("settings put "+ns+" adb_install_enabled "+(on?"1":"0"));String actual=Root.run("settings get "+ns+" adb_install_enabled");
            if(!actual.equals(on?"1":"0"))throw new IOException("系统未接受 USB 安装变更");return actual;
        },()->{note("USB 安装设置已更新；实际安装仍受系统策略约束");refresh();})).show();
    }
    private void densityDialog(){
        if(!rootReady||busy)return;
        LinearLayout box=vertical();pad(box,24,4,24,4);
        EditText input=new EditText(this);input.setSingleLine();input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(String.valueOf(Math.round(shortPx*160f/currentDpi)));input.setSelectAllOnFocus(true);box.addView(input);
        TextView help=text("280–720 dp · 数值增大，界面变小\n应用后需在 20 秒内确认，否则自动还原。",12,MUTED);box.addView(help);
        LinearLayout presets=new LinearLayout(this);
        for(int n:new int[]{360,393,411,480}){Button b=button(String.valueOf(n),()->input.setText(String.valueOf(n)),false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(2),dp(8),dp(2),0);presets.addView(b,p);}box.addView(presets);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("调整最小宽度 / dp").setView(box).setNegativeButton("取消",null).setPositiveButton("预览应用",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{int value=Integer.parseInt(input.getText().toString());int dpi=Logic.densityForWidth(shortPx,value);dialog.dismiss();applyDensity(String.valueOf(dpi));}catch(Exception e){input.setError("请输入适合此屏幕的 280–720 dp 数值");}}));dialog.show();
    }
    private void confirmResetDensity(){if(!rootReady||busy)return;new AlertDialog.Builder(this).setTitle("恢复系统默认显示？").setMessage("移除自定义密度覆盖，保留系统物理密度。也会提供 20 秒确认。").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->applyDensity("reset")).show();}
    private void applyDensity(String target){
        if(densityInFlight||busy)return;
        densityInFlight=true;
        task("应用显示预览…",()->{
            String previous=previousDensity(Root.run("wm density"));prefs.edit().putString("pending_previous",previous).commit();
            File marker=new File(getFilesDir(),"density.pending"),watchdog=new File(getFilesDir(),"revert-density.sh");String token=String.valueOf(System.currentTimeMillis());
            try(FileOutputStream f=new FileOutputStream(marker)){f.write(token.getBytes("UTF-8"));}
            String script="#!/system/bin/sh\nsleep 20\nif [ \"$(cat "+Root.quote(marker.getAbsolutePath())+" 2>/dev/null)\" = "+Root.quote(token)+" ]; then\n wm density "+previous+"\n rm -f "+Root.quote(marker.getAbsolutePath())+"\nfi\n";
            try(FileOutputStream f=new FileOutputStream(watchdog)){f.write(script.getBytes("UTF-8"));}
            try{Root.run("nohup sh "+Root.quote(watchdog.getAbsolutePath())+" </dev/null >/dev/null 2>&1 & p=$!; sleep 1; kill -0 $p || exit 1; wm density "+target+" || exit 1");}
            catch(Exception e){marker.delete();Root.run("wm density "+previous);ui.post(()->densityInFlight=false);throw e;}
            return "";
        },()->densityConfirmation());
    }
    private void densityConfirmation(){
        long deadline=SystemClock.elapsedRealtime()+17500;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("保留这个显示大小？").setMessage("正在预览").setCancelable(false).setNegativeButton("还原",(d,w)->revertDensity()).setPositiveButton("保留",(d,w)->{
            File marker=new File(getFilesDir(),"density.pending");if(marker.delete()){densityInFlight=false;note("显示设置已保留");refresh();}else{densityInFlight=false;note("预览已到期，已还原");refresh();}
        }).create();dialog.show();
        Runnable tick=new Runnable(){public void run(){if(!dialog.isShowing())return;long left=(deadline-SystemClock.elapsedRealtime()+999)/1000;if(left<=0){dialog.dismiss();revertDensity();return;}dialog.setMessage("显示内容是否清晰、大小合适？\n"+left+" 秒后自动还原；App 退出也会还原。");ui.postDelayed(this,500);}};ui.post(tick);
    }
    private void revertDensity(){
        task("恢复显示设置…",()->{Root.run("wm density "+prefs.getString("pending_previous","reset"));new File(getFilesDir(),"density.pending").delete();densityInFlight=false;return "";},()->refresh());
    }
    private String restoreSetting(String space,String key,String value){return "settings "+("null".equals(value)||value.isEmpty()?"delete "+space+" "+key:"put "+space+" "+key+" "+Root.quote(value));}
    private String restoreSystemCommand(){
        String cmd="wm density "+prefs.getString("baseline_density","reset")+" && "+restoreSetting("global","development_settings_enabled",prefs.getString("baseline_dev","0"))+" && "+restoreSetting("global","adb_enabled",prefs.getString("baseline_adb","0"));
        if(prefs.contains("baseline_install"))cmd+=" && "+restoreSetting(prefs.getString("baseline_install_space","global"),"adb_install_enabled",prefs.getString("baseline_install","0"));
        return cmd;
    }
    private void confirmRestore(){
        if(!rootReady||busy||densityInFlight)return;
        new AlertDialog.Builder(this).setTitle("恢复首次使用前的系统设置？").setMessage("恢复首次成功读取的 USB 调试、开发者选项、USB 安装和显示密度。USB 连接可能中断；不会删除图片。").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->task("恢复初始设置…",()->{
            Root.run(restoreSystemCommand());return "";
        },()->{note("已恢复首次使用前的设置");refresh();})).show();
    }
    private void pickImage(){try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK);}catch(ActivityNotFoundException e){note("请先安装或启用系统文件选择器");}}
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);if(req!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();note("正在读取图片…");worker.execute(()->{
            try{
                Bitmap b;
                if(Build.VERSION.SDK_INT>=28){ImageDecoder.Source src=ImageDecoder.createSource(getContentResolver(),uri);b=ImageDecoder.decodeBitmap(src,(decoder,info,source)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);int w=info.getSize().getWidth(),h=info.getSize().getHeight();float factor=Math.min(1,2048f/Math.max(w,h));decoder.setTargetSize(Math.max(1,Math.round(w*factor)),Math.max(1,Math.round(h*factor)));});}
                else {BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>2048)o.inSampleSize*=2;o.inJustDecodeBounds=false;try(InputStream in=getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,o);}}
                if(b==null)throw new IOException("图片格式未被系统支持");Bitmap image=b;
                ui.post(()->{if(!isDestroyed())editBackground(image,true);});
            }catch(Exception|OutOfMemoryError e){ui.post(()->note("图片读取失败，请选择其他图片"));}
        });
    }
    private void editBackground(Bitmap image,boolean fresh){
        LinearLayout panel=vertical();pad(panel,20,4,20,8);
        TextView hint=text("单指拖动选区 · 双指等比缩放\n背景不变形，卡片保持高对比。",12,MUTED);panel.addView(hint);gap(panel,10);
        FrameLayout preview=new FrameLayout(this);preview.setBackground(shape(0xfff4f7fc,16));preview.setClipToOutline(true);
        Backdrop crop=new Backdrop(this);crop.editable=true;crop.settings(image,fresh?1:prefs.getFloat("zoom",1),fresh?.5f:prefs.getFloat("fx",.5f),fresh?.5f:prefs.getFloat("fy",.5f),prefs.getInt("opacity",25));
        preview.addView(crop,new FrameLayout.LayoutParams(-1,-1));
        // Preview has the same viewport aspect as the actual main background.
        int width=getResources().getDisplayMetrics().widthPixels-dp(88);int height=Math.round(width*(stage.getHeight()/(float)Math.max(1,stage.getWidth())));
        int maxHeight=Math.round(getResources().getDisplayMetrics().heightPixels*.37f);if(height>maxHeight){width=Math.round(width*maxHeight/(float)height);height=maxHeight;}
        LinearLayout.LayoutParams previewLp=new LinearLayout.LayoutParams(width,height);previewLp.gravity=Gravity.CENTER_HORIZONTAL;panel.addView(preview,previewLp);
        View foreground=new View(this){protected void onDraw(Canvas c){
            super.onDraw(c);c.save();float s=getWidth()/(float)Math.max(1,stage.getWidth());c.scale(s,s);c.translate(0,dp(24));page.draw(c);c.restore();
        }};
        preview.addView(foreground,new FrameLayout.LayoutParams(-1,-1));
        TextView label=text("图片透明度 "+(100-crop.opacity)+"%",13,INK);gap(panel,12);panel.addView(label);
        SeekBar alpha=new SeekBar(this);alpha.setMax(100);alpha.setProgress(100-crop.opacity);alpha.setContentDescription("背景图片透明度");panel.addView(alpha,new LinearLayout.LayoutParams(-1,dp(44)));
        alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){crop.opacity=100-v;crop.invalidate();label.setText("图片透明度 "+v+"%");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        Button center=button("居中复位",()->{crop.zoom=1;crop.fx=.5f;crop.fy=.5f;crop.invalidate();},false);panel.addView(center);
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("背景取景与透明度").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
            dialog.getButton(-1).setEnabled(false);worker.execute(()->{try{
                File temp=new File(getFilesDir(),"background.new.png");try(FileOutputStream out=new FileOutputStream(temp)){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("图片保存失败");}
                File target=new File(getFilesDir(),"background.png");if(!temp.renameTo(target))throw new IOException("背景保存失败");
                prefs.edit().putFloat("zoom",crop.zoom).putFloat("fx",crop.fx).putFloat("fy",crop.fy).putInt("opacity",crop.opacity).commit();
                ui.post(()->{wallpaper=image;dialog.dismiss();build();renderState();note("背景已保存");});
            }catch(Exception e){ui.post(()->{dialog.getButton(-1).setEnabled(true);note("保存失败，请重试");});}});
        }));dialog.show();
    }
}
