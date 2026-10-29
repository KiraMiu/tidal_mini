package io.moreless.tide.rebuild;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** App-owned settings sheets; system permission/file-picker windows remain system UI. */
final class SleepDialogs {
    private final Activity activity;
    private final Dialog dialog;
    private final LinearLayout body;
    private SleepDialogs(Activity a,String title){
        activity=a;dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout panel=new FrameLayout(a);panel.setBackground(SleepUi.glass(a));
        panel.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline out){out.setRoundRect(0,0,v.getWidth(),v.getHeight(),SleepUi.dp(a,30));}});panel.setClipToOutline(true);
        LinearLayout column=new LinearLayout(a);column.setOrientation(LinearLayout.VERTICAL);column.setPadding(SleepUi.dp(a,16),SleepUi.dp(a,12),SleepUi.dp(a,16),SleepUi.dp(a,18));panel.addView(column,new FrameLayout.LayoutParams(-1,-2));
        FrameLayout header=new FrameLayout(a);column.addView(header,new LinearLayout.LayoutParams(-1,SleepUi.dp(a,64)));
        TextView heading=new TextView(a);heading.setText(title);heading.setTextColor(Color.WHITE);heading.setTextSize(19);heading.setGravity(Gravity.CENTER);heading.setMaxLines(2);heading.setPadding(SleepUi.dp(a,54),0,SleepUi.dp(a,54),0);header.addView(heading,new FrameLayout.LayoutParams(-1,-1));
        ImageButton back=new ImageButton(a);back.setImageDrawable(new SleepIcons("back",SleepUi.dp(a,24),Color.WHITE));back.setContentDescription("返回");back.setBackground(SleepUi.surface(a,0x225f7898,0));header.addView(back,new FrameLayout.LayoutParams(SleepUi.dp(a,48),SleepUi.dp(a,48),Gravity.START|Gravity.CENTER_VERTICAL));back.setOnClickListener(v->dialog.dismiss());
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);column.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,SleepUi.dp(a,8),0,0);scroll.addView(body);
        dialog.setContentView(panel);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.25f);
    }
    private void row(String label,Runnable action){
        TextView row=new TextView(activity);row.setText(label+"  ›");row.setContentDescription(label);row.setTextColor(Color.WHITE);row.setTextSize(16);row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setMinHeight(SleepUi.dp(activity,56));row.setPadding(SleepUi.dp(activity,18),SleepUi.dp(activity,14),SleepUi.dp(activity,18),SleepUi.dp(activity,14));
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x337c9bb5),SleepUi.surface(activity,0x22293950,0x335c7189),null));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=SleepUi.dp(activity,8);body.addView(row,p);row.setOnClickListener(v->{dialog.dismiss();action.run();});
    }
    private void show(){
        dialog.show();View window=activity.findViewById(android.R.id.content);int width=Math.min(Math.max(1,window.getWidth()-SleepUi.dp(activity,32)),SleepUi.dp(activity,460));
        int maxHeight=Math.max(1,window.getHeight()-SleepUi.dp(activity,40));
        View panel=((ViewGroup)dialog.findViewById(android.R.id.content)).getChildAt(0);
        panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(maxHeight,View.MeasureSpec.AT_MOST));
        dialog.getWindow().setLayout(width,Math.min(maxHeight,panel.getMeasuredHeight()));
    }
    static void choices(Activity a,String title,String[] labels,IntConsumer action){SleepDialogs sheet=new SleepDialogs(a,title);for(int i=0;i<labels.length;i++){final int index=i;sheet.row(labels[i],()->action.accept(index));}sheet.show();}
    static void message(Activity a,String title,String message,String[] labels,Runnable[] actions){
        SleepDialogs sheet=new SleepDialogs(a,title);TextView text=SleepUi.text(a,sheet.body,message,14,0xffcbd6e5);text.setGravity(Gravity.START);text.setLineSpacing(SleepUi.dp(a,4),1);text.setPadding(SleepUi.dp(a,8),SleepUi.dp(a,10),SleepUi.dp(a,8),SleepUi.dp(a,20));
        for(int i=0;i<labels.length;i++)sheet.row(labels[i],actions[i]);sheet.show();
    }
}
