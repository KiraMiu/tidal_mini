package io.moreless.tide.rebuild;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small monochrome vector controls, consistent across Android font sets. */
final class SleepIcons extends Drawable {
    private final String kind;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size,color;
    float progress;
    SleepIcons(String kind,int size,int color){this.kind=kind;this.size=size;this.color=color;setBounds(0,0,size,size);}
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void draw(Canvas canvas){
        canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
        paint.setColor(color);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);
        if(kind.equals("back")){canvas.drawLine(15,5,8,12,paint);canvas.drawLine(8,12,15,19,paint);}
        else if(kind.equals("settings")){
            canvas.drawCircle(12,12,6.7f,paint);canvas.drawCircle(12,12,2.5f,paint);
            for(int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,12,12);canvas.drawLine(12,2,12,4.5f,paint);canvas.restore();}
        } else if(kind.equals("end")){
            paint.setColor(0x558eabba);canvas.drawCircle(12,12,10,paint);
            paint.setColor(color);canvas.drawArc(2,2,22,22,-90,360*progress,false,paint);
            paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(8,8,16,16,2,2,paint);
        } else if(kind.equals("pause")){
            paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(6,5,9.5f,19,1,1,paint);canvas.drawRoundRect(14.5f,5,18,19,1,1,paint);
        } else {
            paint.setStyle(Paint.Style.FILL);Path path=new Path();path.moveTo(7,4);path.lineTo(20,12);path.lineTo(7,20);path.close();canvas.drawPath(path,paint);
        }
        canvas.restore();
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
