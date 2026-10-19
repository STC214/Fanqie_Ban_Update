package local.usbcontrol;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** One uniform scale on both axes. Drag selects the visible region; pinch only zooms. */
final class Backdrop extends View {
    Bitmap bitmap;
    float zoom=1, fx=.5f, fy=.5f;
    int opacity=25;
    int baseColor=0xfff4f7fc;
    boolean editable=false;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF destination=new RectF();
    private final ScaleGestureDetector scale;
    private float lastX,lastY;
    Backdrop(Context c) {
        super(c);
        scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            public boolean onScale(ScaleGestureDetector d){ zoom=Logic.clamp(zoom*d.getScaleFactor(),1,4);invalidate();return true; }
        });
        setContentDescription("背景预览；双指等比缩放，单指拖动选择显示区域");
    }
    void settings(Bitmap b,float z,float x,float y,int alpha){bitmap=b;zoom=Logic.clamp(z,1,4);fx=Logic.clamp(x,0,1);fy=Logic.clamp(y,0,1);opacity=Math.max(0,Math.min(100,alpha));invalidate();}
    protected void onDraw(Canvas c){
        super.onDraw(c); c.drawColor(baseColor);
        if(bitmap==null||bitmap.isRecycled()||getWidth()==0||getHeight()==0)return;
        float s=Logic.coverScale(bitmap.getWidth(),bitmap.getHeight(),getWidth(),getHeight(),zoom);
        float w=bitmap.getWidth()*s,h=bitmap.getHeight()*s;
        float x=(getWidth()-w)*Logic.clamp(fx,0,1),y=(getHeight()-h)*Logic.clamp(fy,0,1);
        paint.setAlpha(Math.round(255*Logic.clamp(opacity/100f,0,1)));
        destination.set(x,y,x+w,y+h);c.drawBitmap(bitmap,null,destination,paint);
    }
    public boolean onTouchEvent(android.view.MotionEvent e){
        if(!editable||bitmap==null)return false;
        scale.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();getParent().requestDisallowInterceptTouchEvent(true);return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
            if(!scale.isInProgress()&&e.getPointerCount()==1){
                float s=Logic.coverScale(bitmap.getWidth(),bitmap.getHeight(),getWidth(),getHeight(),zoom);
                float dx=bitmap.getWidth()*s-getWidth(),dy=bitmap.getHeight()*s-getHeight();
                if(dx>0)fx=Logic.clamp(fx-(e.getX()-lastX)/dx,0,1);
                if(dy>0)fy=Logic.clamp(fy-(e.getY()-lastY)/dy,0,1);
                invalidate();
            }
            lastX=e.getX();lastY=e.getY();return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_UP){lastX=e.getX(e.getActionIndex()==0?1:0);lastY=e.getY(e.getActionIndex()==0?1:0);}
        if(e.getActionMasked()==MotionEvent.ACTION_UP){performClick();getParent().requestDisallowInterceptTouchEvent(false);}
        if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
