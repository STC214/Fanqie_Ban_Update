package local.usbcontrol;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;

/** Presentation only. No Root, package-manager mutation or shell commands belong here. */
final class SystemAppsDialog extends Dialog {
    interface Submit { void accept(java.util.List<SystemApps.Entry> selected); }
    static final class Palette {
        final int blue,ink,muted,surface,soft,line,primaryText;
        final boolean dark;
        Palette(int blue,int ink,int muted,int surface,int soft,int line,int primaryText,boolean dark){
            this.blue=blue;this.ink=ink;this.muted=muted;this.surface=surface;this.soft=soft;this.line=line;this.primaryText=primaryText;this.dark=dark;
        }
    }
    private final Context context;
    private final Palette colors;
    private final java.util.List<SystemApps.Entry> entries;
    private final boolean restore;
    private final int user;
    private final Submit submit;
    private final boolean[] checked;
    private boolean confirming=false,submitted=false;
    private int renderGeneration;
    private BoundedLayout panel;
    private Button primaryButton,secondaryButton,selectAllButton;
    private TextView selectionCount;
    private final ArrayList<ChoiceRow> choiceRows=new ArrayList<>();

    SystemAppsDialog(Context context,Palette colors,java.util.List<SystemApps.Entry> entries,boolean restore,int user,Submit submit){
        super(context);this.context=context;this.colors=colors;this.entries=new ArrayList<>(entries);this.restore=restore;this.user=user;this.submit=submit;
        if(this.entries.isEmpty())throw new IllegalArgumentException("应用列表为空");
        checked=new boolean[this.entries.size()];
    }
    private int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private void pad(View view,int left,int top,int right,int bottom){view.setPadding(dp(left),dp(top),dp(right),dp(bottom));}
    private LinearLayout column(){LinearLayout view=new LinearLayout(context);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private TextView text(String value,int size,int color){TextView view=new TextView(context);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setLineSpacing(dp(3),1);view.setFontFeatureSettings("kern");return view;}
    private GradientDrawable shape(int fill,int radius,int stroke){GradientDrawable value=new GradientDrawable();value.setColor(fill);value.setCornerRadius(dp(radius));if(stroke!=0)value.setStroke(dp(1),stroke);return value;}
    private Drawable touch(int fill,int radius,int stroke){return new RippleDrawable(ColorStateList.valueOf(colors.dark?0x248ab4ff:0x182563eb),shape(fill,radius,stroke),null);}
    private void gap(LinearLayout parent,int height){parent.addView(new View(context),new LinearLayout.LayoutParams(1,dp(height)));}
    private Button action(String label,Runnable click,boolean primary){
        Button view=new Button(context);view.setText(label);view.setAllCaps(false);view.setTextSize(14);view.setMinHeight(dp(50));view.setMinimumWidth(0);view.setMinWidth(0);pad(view,12,10,12,10);
        view.setTextColor(primary?colors.primaryText:colors.blue);view.setBackground(touch(primary?colors.blue:colors.soft,14,0));view.setOnClickListener(v->click.run());return view;
    }
    @Override protected void onCreate(Bundle state){super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);setCanceledOnTouchOutside(true);render();}
    @Override protected void onStart(){super.onStart();Window window=getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=window.getAttributes();p.dimAmount=.52f;window.setAttributes(p);}updateBounds();}
    void updateBounds(){
        if(panel==null)return;panel.requestLayout();Window window=getWindow();if(window==null)return;
        Rect available=availableFrame();window.setLayout(Math.min(dp(420),Math.max(dp(180),available.width()-dp(32))),WindowManager.LayoutParams.WRAP_CONTENT);
    }
    private Rect availableFrame(){
        Context c=context;while(c instanceof ContextWrapper&&!(c instanceof Activity)){Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}
        Rect frame=new Rect();if(c instanceof Activity)((Activity)c).getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
        if(frame.width()<=0||frame.height()<=0)frame.set(0,0,context.getResources().getDisplayMetrics().widthPixels,context.getResources().getDisplayMetrics().heightPixels);
        return frame;
    }
    private final class BoundedLayout extends LinearLayout {
        BoundedLayout(){super(context);setOrientation(VERTICAL);}
        @Override protected void onMeasure(int width,int height){super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(Math.max(dp(120),availableFrame().height()-dp(32)),View.MeasureSpec.AT_MOST));}
    }
    private int count(){int count=0;for(boolean value:checked)if(value)count++;return count;}
    private java.util.List<SystemApps.Entry> selection(){ArrayList<SystemApps.Entry> result=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])result.add(entries.get(i));return result;}
    private boolean active(int generation){return isShowing()&&!submitted&&generation==renderGeneration;}
    private void render(){
        final int generation=++renderGeneration;
        panel=new BoundedLayout();panel.setBackground(shape(colors.surface,26,0));panel.setClipToOutline(true);
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);
        LinearLayout body=column();pad(body,22,22,22,18);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));

        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView emblem=text(restore?"+":"−",25,colors.blue);emblem.setGravity(Gravity.CENTER);emblem.setBackground(shape(colors.soft,14,0));header.addView(emblem,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout headings=column();pad(headings,12,0,6,0);
        TextView eyebrow=text("SYSTEM APPS  /  "+(restore?"RESTORE":"REMOVE"),9,colors.muted);eyebrow.setLetterSpacing(.10f);headings.addView(eyebrow);
        TextView title=text(confirming?(restore?"确认恢复应用":"确认卸载应用"):(restore?"恢复系统应用":"卸载系统应用"),21,colors.ink);title.setTypeface(null,android.graphics.Typeface.BOLD);headings.addView(title);header.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        Button close=action("×",()->{if(active(generation))dismiss();},false);close.setTextSize(23);close.setMinHeight(0);pad(close,0,0,0,0);close.setContentDescription("关闭系统应用管理");header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(header);gap(body,14);
        body.addView(text(confirming?"请核对以下应用，再确认操作。":"选择需要"+(restore?"恢复":"卸载")+"的预装应用，未勾选的应用不受影响。",12,colors.muted));gap(body,16);

        choiceRows.clear();selectAllButton=null;
        if(!confirming){
            LinearLayout controls=new LinearLayout(context);controls.setGravity(Gravity.CENTER_VERTICAL);
            controls.addView(text("可"+(restore?"恢复":"卸载")+" · "+entries.size()+" 项",12,colors.muted),new LinearLayout.LayoutParams(0,-2,1));
            selectAllButton=action("全选",()->{if(!active(generation)||confirming)return;boolean all=count()!=entries.size();for(int i=0;i<checked.length;i++)checked[i]=all;refreshSelection();},false);selectAllButton.setTextSize(12);selectAllButton.setMinHeight(dp(48));pad(selectAllButton,14,8,14,8);controls.addView(selectAllButton,new LinearLayout.LayoutParams(-2,-2));body.addView(controls);gap(body,8);
            for(int i=0;i<entries.size();i++){ChoiceRow row=new ChoiceRow(i,generation);choiceRows.add(row);body.addView(row,new LinearLayout.LayoutParams(-1,-2));if(i<entries.size()-1)gap(body,9);}
        }else{
            for(SystemApps.Entry entry:selection()){body.addView(summaryRow(entry),new LinearLayout.LayoutParams(-1,-2));gap(body,8);}
        }
        gap(body,14);body.addView(notice(),new LinearLayout.LayoutParams(-1,-2));

        View divider=new View(context);divider.setBackgroundColor(colors.line);panel.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout footer=column();pad(footer,20,12,20,18);
        LinearLayout meta=new LinearLayout(context);meta.setGravity(Gravity.CENTER_VERTICAL);
        selectionCount=text("",12,colors.blue);selectionCount.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);meta.addView(selectionCount,new LinearLayout.LayoutParams(0,-2,1));
        meta.addView(text("当前用户 · "+user,11,colors.muted));footer.addView(meta);gap(footer,8);
        LinearLayout actions=new LinearLayout(context);actions.setGravity(Gravity.CENTER_VERTICAL);
        secondaryButton=action(confirming?"返回选择":"取消",()->{if(!active(generation))return;if(confirming){confirming=false;render();updateBounds();}else dismiss();},false);
        actions.addView(secondaryButton,new LinearLayout.LayoutParams(0,-2,1));View spacer=new View(context);actions.addView(spacer,new LinearLayout.LayoutParams(dp(10),1));
        primaryButton=action("",()->{
            if(!active(generation)||count()==0)return;
            if(!confirming){confirming=true;render();updateBounds();return;}
            submitted=true;java.util.List<SystemApps.Entry> selected=selection();dismiss();submit.accept(selected);
        },true);actions.addView(primaryButton,new LinearLayout.LayoutParams(0,-2,1.55f));footer.addView(actions);panel.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        setContentView(panel);refreshSelection();
    }
    private View notice(){
        int ink=restore?colors.blue:(colors.dark?0xffffc8a3:0xff955016);
        int fill=restore?colors.soft:(colors.dark?0xff332c29:0xfffff4e8);
        LinearLayout box=column();pad(box,13,11,13,11);box.setBackground(shape(fill,13,0));
        TextView heading=text(restore?"恢复应用，不恢复数据":"卸载会删除应用数据",12,ink);heading.setTypeface(null,android.graphics.Typeface.BOLD);box.addView(heading);
        box.addView(text(restore?"重新安装系统保留的预装包；不会找回已删除的数据，也不更改原有启用状态。":"数据删除不可撤销，系统安装包仍保留。浏览器卸载后可能影响网页打开。",11,ink));return box;
    }
    private String badge(SystemApps.Entry entry){if(entry.name.startsWith("快"))return "快";if(entry.name.startsWith("内"))return "内";if(entry.name.startsWith("浏"))return "浏";return "AD";}
    private View summaryRow(SystemApps.Entry entry){
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);pad(row,13,12,13,12);row.setBackground(shape(colors.soft,15,0));addLabels(row,entry);return row;
    }
    private void addLabels(LinearLayout row,SystemApps.Entry entry){
        TextView icon=text(badge(entry),14,colors.blue);icon.setTypeface(null,android.graphics.Typeface.BOLD);icon.setGravity(Gravity.CENTER);icon.setBackground(shape(colors.dark?0xff354963:0xffe1ebff,12,0));row.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout labels=column();pad(labels,12,0,8,0);TextView name=text(entry.name,15,colors.ink);name.setTypeface(null,android.graphics.Typeface.BOLD);labels.addView(name);labels.addView(text(entry.pkg,10,colors.muted));row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
    }
    private final class ChoiceRow extends LinearLayout {
        final int index;final CheckBox check;
        ChoiceRow(int index,int generation){
            super(context);this.index=index;setGravity(Gravity.CENTER_VERTICAL);setMinimumHeight(dp(78));pad(this,13,12,10,12);addLabels(this,entries.get(index));
            check=new CheckBox(context);check.setClickable(false);check.setFocusable(false);check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{colors.blue,colors.muted}));
            addView(check,new LinearLayout.LayoutParams(dp(34),dp(48)));getChildAt(1).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);setFocusable(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);if(android.os.Build.VERSION.SDK_INT>=28)setScreenReaderFocusable(true);setOnClickListener(v->{if(!active(generation)||confirming)return;checked[index]=!checked[index];refreshSelection();});
        }
        void bind(){boolean on=checked[index];check.setChecked(on);setSelected(on);setBackground(touch(on?(colors.dark?0xff263c58:0xffeaf1ff):colors.soft,16,on?colors.blue:colors.line));setContentDescription(entries.get(index).name+"，"+entries.get(index).pkg+"，"+(on?"已选择":"未选择"));}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.CheckBox");info.setCheckable(true);info.setChecked(checked[index]);}
    }
    private void refreshSelection(){
        int count=count();selectionCount.setText("已选 "+count+" / "+entries.size()+" 项");
        primaryButton.setEnabled(count>0&&!submitted);primaryButton.setAlpha(count>0?1:.4f);
        primaryButton.setText(confirming?(restore?"确认恢复":"确认卸载")+" · "+count:"下一步"+(count>0?" · "+count:""));
        if(selectAllButton!=null)selectAllButton.setText(count==entries.size()?"取消全选":"全选");
        for(ChoiceRow row:choiceRows)row.bind();
    }
    @Override public void onBackPressed(){if(!isShowing())return;if(confirming){confirming=false;render();updateBounds();}else super.onBackPressed();}
}
