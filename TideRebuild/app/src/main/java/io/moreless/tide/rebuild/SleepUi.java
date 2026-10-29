package io.moreless.tide.rebuild;
import android.app.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.io.*;

final class SleepUi {
    private static Typeface clockMedium,clockLight;
    static android.graphics.drawable.Icon notificationIcon(android.content.Context c){
        try(InputStream in=c.getAssets().open("art/ic_stat_tide.webp")){return android.graphics.drawable.Icon.createWithBitmap(BitmapFactory.decodeStream(in));}
        catch(IOException e){return android.graphics.drawable.Icon.createWithResource(c,android.R.drawable.ic_lock_idle_alarm);}
    }
    static CharSequence clock(Activity a){
        if(clockMedium==null)clockMedium=Typeface.createFromAsset(a.getAssets(),"fonts/inter_medium.ttf");
        String now=new java.text.SimpleDateFormat("HH\nmm",java.util.Locale.getDefault()).format(new java.util.Date());
        android.text.SpannableString value=new android.text.SpannableString(now);
        value.setSpan(new android.text.style.ForegroundColorSpan(0xffdddde4),0,2,0);
        value.setSpan(new android.text.style.ForegroundColorSpan(0xff9095a6),3,5,0);
        value.setSpan(new android.text.style.MetricAffectingSpan(){
            public void updateDrawState(android.text.TextPaint p){p.setTypeface(clockMedium);}
            public void updateMeasureState(android.text.TextPaint p){updateDrawState(p);}
        },0,2,0);
        return value;
    }
    static void styleClock(Activity a,TextView clock){if(clockLight==null)clockLight=Typeface.createFromAsset(a.getAssets(),"fonts/inter_light.ttf");clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(a,52));clock.setTypeface(clockLight);clock.setIncludeFontPadding(false);clock.setLineSpacing(dp(a,12),1f);clock.setFontFeatureSettings("tnum");}
    static int dp(Activity a,float x){return Math.round(x*a.getResources().getDisplayMetrics().density);}
    static Bitmap bitmap(Activity a,String path){try(InputStream in=a.getAssets().open(path)){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;return BitmapFactory.decodeStream(in,null,o);}catch(IOException e){return null;}}
    static Bitmap thumbnail(Activity a,String path,int target){
        try{
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(InputStream in=a.getAssets().open(path)){BitmapFactory.decodeStream(in,null,options);}
            options.inJustDecodeBounds=false;options.inSampleSize=1;
            while(Math.min(options.outWidth,options.outHeight)/(options.inSampleSize*2)>=target)options.inSampleSize*=2;
            try(InputStream in=a.getAssets().open(path)){return BitmapFactory.decodeStream(in,null,options);}
        }catch(IOException e){return null;}
    }
    static FrameLayout backdrop(Activity a,String art){
        a.getWindow().setStatusBarColor(0xff101627);a.getWindow().setNavigationBarColor(0xff12151a);
        FrameLayout root=new FrameLayout(a);root.setBackgroundColor(0xff11182a);
        ImageView image=new ImageView(a);image.setImageBitmap(bitmap(a,art));image.setScaleType(ImageView.ScaleType.CENTER_CROP);root.addView(image,new FrameLayout.LayoutParams(-1,-1));a.setContentView(root);return root;
    }
    static LinearLayout column(Activity a,FrameLayout root){
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);
        int width=Math.min(a.getResources().getDisplayMetrics().widthPixels,dp(a,520));
        FrameLayout.LayoutParams box=new FrameLayout.LayoutParams(width,-1,Gravity.CENTER_HORIZONTAL);root.addView(scroll,box);
        LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);col.setGravity(Gravity.CENTER_HORIZONTAL);col.setPadding(dp(a,24),dp(a,12),dp(a,22),dp(a,16));scroll.addView(col,new ScrollView.LayoutParams(-1,-1));return col;
    }
    static TextView text(Activity a,LinearLayout col,String text,float size,int color){TextView v=new TextView(a);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);v.setFontFeatureSettings("tnum");v.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));v.setPadding(0,dp(a,5),0,dp(a,5));col.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    static void space(Activity a,LinearLayout col,float weight){View v=new View(a);col.addView(v,new LinearLayout.LayoutParams(1,dp(a,12),weight));}
    static android.graphics.drawable.Drawable surface(Activity a,int color,int border){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(a,28));if(border!=0)bg.setStroke(dp(a,1),border);return bg;}
    static TextView button(Activity a,LinearLayout col,String text,boolean primary,Runnable action){
        TextView b=new TextView(a);b.setText(text);b.setTextSize(16);b.setGravity(Gravity.CENTER);b.setTextColor(primary?0xff192435:Color.WHITE);b.setContentDescription(text);b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x336e839e),surface(a,primary?0xffedf0f6:0x183d485e,primary?0:0x447c8aa0),null));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,56));p.setMargins(0,dp(a,6),0,dp(a,6));col.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    /** Symmetric inline slots keep label centers aligned without pushing icons to the edge. */
    static SleepIcons icon(Activity a,TextView view,String kind,String label){
        SleepIcons drawable=new SleepIcons(kind,dp(a,22),view.getCurrentTextColor());
        int gap=dp(a,10),size=dp(a,22);
        android.text.SpannableString text=new android.text.SpannableString("\ufffc"+label+"\ufffc");
        text.setSpan(new InlineIcon(drawable,size,gap,false),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new InlineIcon(null,size,gap,true),text.length()-1,text.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        drawable.setCallback(new Drawable.Callback(){
            public void invalidateDrawable(Drawable d){view.invalidate();}
            public void scheduleDrawable(Drawable d,Runnable task,long when){view.postDelayed(task,Math.max(0,when-android.os.SystemClock.uptimeMillis()));}
            public void unscheduleDrawable(Drawable d,Runnable task){view.removeCallbacks(task);}
        });
        view.setCompoundDrawablesRelative(null,null,null,null);view.setText(text);view.setContentDescription(label);view.setPadding(dp(a,16),0,dp(a,16),0);
        return drawable;
    }
    private static final class InlineIcon extends android.text.style.ReplacementSpan {
        private final Drawable drawable;private final int size,gap;private final boolean trailing;
        InlineIcon(Drawable drawable,int size,int gap,boolean trailing){this.drawable=drawable;this.size=size;this.gap=gap;this.trailing=trailing;}
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){return size+gap;}
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int baseline,int bottom,Paint paint){
            if(drawable==null)return;Paint.FontMetrics metrics=paint.getFontMetrics();
            canvas.save();canvas.translate(x+(trailing?gap:0),baseline+(metrics.ascent+metrics.descent-size)/2f);drawable.draw(canvas);canvas.restore();
        }
    }
    static void settings(Activity a,FrameLayout root,Runnable action){
        ImageButton button=new ImageButton(a);button.setImageDrawable(new SleepIcons("settings",dp(a,24),0xffe8edf7));
        button.setContentDescription("设置");button.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x337f9bb4),surface(a,0x48212f43,0x336c829e),null));
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(dp(a,48),dp(a,48),Gravity.TOP|Gravity.END);
        position.setMargins(dp(a,16),dp(a,12),dp(a,16),0);root.addView(button,position);button.setOnClickListener(v->action.run());
    }
    /** A blurred snapshot keeps the glass effect available even before Android 12. */
    static Drawable glass(Activity a){
        View content=a.findViewById(android.R.id.content);int w=Math.max(1,content.getWidth()/12),h=Math.max(1,content.getHeight()/12);
        Bitmap small=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(small);
        canvas.scale(w/(float)Math.max(1,content.getWidth()),h/(float)Math.max(1,content.getHeight()));content.draw(canvas);
        int[] src=new int[w*h],dst=new int[w*h];small.getPixels(src,0,w,0,0,w,h);
        for(int pass=0;pass<3;pass++){
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int r=0,g=0,b=0,n=0;
                for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
                    int c=src[Math.max(0,Math.min(h-1,y+dy))*w+Math.max(0,Math.min(w-1,x+dx))];
                    r+=Color.red(c);g+=Color.green(c);b+=Color.blue(c);n++;
                }
                dst[y*w+x]=Color.rgb(r/n,g/n,b/n);
            }
            int[] swap=src;src=dst;dst=swap;
        }
        small.setPixels(src,0,w,0,0,w,h);BitmapDrawable blurred=new BitmapDrawable(a.getResources(),small);blurred.setFilterBitmap(true);blurred.setGravity(Gravity.FILL);
        return new LayerDrawable(new Drawable[]{blurred,surface(a,0xb32a354b,0x557d91ac)});
    }
    static TextView holdEnd(Activity a,LinearLayout col,Runnable action){
        TextView end=button(a,col,"长按结束",false,()->Toast.makeText(a,"按住直到圆环填满",Toast.LENGTH_SHORT).show());
        end.setTextColor(0xffd2dae8);SleepIcons ring=icon(a,end,"end","长按结束");
        io.moreless.tide.rebuild.core.HoldGesture hold=new io.moreless.tide.rebuild.core.HoldGesture();
        android.os.Handler clock=new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable frame=new Runnable(){public void run(){
            if(!hold.active())return;
            long now=android.os.SystemClock.uptimeMillis();ring.progress=hold.progress(now);ring.invalidateSelf();
            if(hold.complete(now)){end.setPressed(false);end.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);action.run();}
            else clock.postDelayed(this,16);
        }};
        Runnable reset=()->{hold.cancel();clock.removeCallbacks(frame);ring.progress=0;ring.invalidateSelf();end.setPressed(false);};
        end.setTag(reset);
        end.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                reset.run();hold.begin(android.os.SystemClock.uptimeMillis());end.setPressed(true);v.getParent().requestDisallowInterceptTouchEvent(true);clock.post(frame);return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN || (event.getActionMasked()==MotionEvent.ACTION_MOVE && (event.getX()<0 || event.getX()>v.getWidth() || event.getY()<0 || event.getY()>v.getHeight()))){reset.run();return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                boolean hint=hold.active()&&event.getActionMasked()==MotionEvent.ACTION_UP;reset.run();v.getParent().requestDisallowInterceptTouchEvent(false);if(hint)v.performClick();return true;
            }return true;
        });
        // Accessible/keyboard long-click remains available without a touch gesture.
        end.setOnLongClickListener(v->{reset.run();action.run();return true;});
        end.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){reset.run();}});
        return end;
    }

}
