package com.namazwidget.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.*;

public class PrayerWidget extends AppWidgetProvider {
    private static final int GREEN=Color.rgb(20,43,39), GOLD=Color.rgb(231,201,130);
    private static final String[] NAMES={"Утренний","Обеденный","Послеобед.","Вечерний","Ночной"};
    private static final String[] SHORT={"Утро","Обед","После","Вечер","Ночь"};
    private static final int WHITE=Color.WHITE, MUTED=Color.rgb(185,213,201);

    public static class Strip extends PrayerWidget {}
    public static class Small extends PrayerWidget {}
    public static class Medium extends PrayerWidget {}
    public static class Large extends PrayerWidget {}
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { update(c,m,ids,getClass().getSimpleName()); }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options) {
        update(c,m,new int[]{id},getClass().getSimpleName());
    }
    @Override public void onReceive(Context c,Intent i) {
        super.onReceive(c,i);
        String a=i.getAction();
        if(Intent.ACTION_DATE_CHANGED.equals(a)||Intent.ACTION_TIME_CHANGED.equals(a)||Intent.ACTION_TIMEZONE_CHANGED.equals(a)){
            AppWidgetManager m=AppWidgetManager.getInstance(c);
            int[] ids=m.getAppWidgetIds(new ComponentName(c,getClass()));
            update(c,m,ids,getClass().getSimpleName());
        }
    }
    static void update(Context c,AppWidgetManager manager,int[] ids,String size) {
        for(int id:ids) {
            int w=320,h=155;
            switch(size){
                case "Strip":w=320;h=78;break;
                case "Small":w=155;h=155;break;
                case "Large":w=320;h=235;break;
            }
            Bundle options=manager.getAppWidgetOptions(id);
            if(options!=null && options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,0)>0){
                int ow=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                int oh=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                // Preserve selected layout proportions while supporting resizing.
                if(ow>40) w=Math.max(110,Math.min(400,ow));
                if(oh>40) h=Math.max(70,Math.min(350,oh));
            }
            Bitmap bmp=draw(c,w,h,size);
            RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.widget_canvas);
            views.setImageViewBitmap(R.id.widget_picture,bmp);
            Intent open=new Intent(c,MainActivity.class);
            PendingIntent pi=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_picture,pi);
            manager.updateAppWidget(id,views);
        }
    }
    private static Paint paint(int color) { Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);return p; }
    private static void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,boolean center) {
        Paint p=paint(color);p.setTypeface(bold?Typeface.create("sans-serif-medium",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
        p.setTextSize(size);p.setTextAlign(center?Paint.Align.CENTER:Paint.Align.LEFT);
        c.drawText(s,x,y,p);
    }
    private static void line(Canvas c,float x,float y,float x2,float y2,int color) {
        Paint p=paint(color);p.setStrokeWidth(0.65f);c.drawLine(x,y,x2,y2,p);
    }
    private static Bitmap draw(Context context,int width,int height,String size) {
        int scale=3;
        Bitmap bmp=Bitmap.createBitmap(width*scale,height*scale,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bmp);
        canvas.scale(scale,scale);
        int opacity=context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).getInt("opacity",73);
        boolean haze=context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).getBoolean("haze",true);
        float radius=18;
        Paint bg=paint(Color.argb(Math.round(opacity*2.55f),20,43,39));
        canvas.drawRoundRect(1,1,width-1,height-1,radius,radius,bg);
        Path clip=new Path();clip.addRoundRect(1,1,width-1,height-1,radius,radius,Path.Direction.CW);
        canvas.save();canvas.clipPath(clip);
        if(haze){
            Paint glow=paint(Color.WHITE);
            glow.setShader(new RadialGradient(width*.15f,0,width*.95f,
                new int[]{Color.argb(55,135,177,153),Color.argb(17,85,134,113),Color.TRANSPARENT},
                null,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,width,height,glow);
            glow.setShader(new RadialGradient(width*.90f,height,
                width*.7f,new int[]{Color.argb(29,11,88,71),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,width,height,glow);
        }
        Paint edge=paint(Color.argb(60,221,251,232));edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(1);
        canvas.drawRoundRect(1,1,width-1,height-1,radius,radius,edge);
        Calendar cal=Calendar.getInstance();
        int month=cal.get(Calendar.MONTH)+1,day=cal.get(Calendar.DAY_OF_MONTH);
        String[] times=Schedule.times(month,day);
        String date=day+" "+new SimpleDateFormat("MMMM",new Locale("ru")).format(cal.getTime());
        int next=-1,now=cal.get(Calendar.HOUR_OF_DAY)*60+cal.get(Calendar.MINUTE);
        for(int i=0;i<5;i++){
            String t=times[i];if(t==null||!t.contains(":"))continue;
            String[] pair=t.split(":");int v=Integer.parseInt(pair[0])*60+Integer.parseInt(pair[1]);
            if(v>now) {next=i;break;}
        }
        if("Strip".equals(size)) {
            text(canvas,"☪ "+date,12,18,11,GOLD,true,false);
            float col=(width-20)/5f;
            for(int i=0;i<5;i++){
                float x=10+i*col+col/2;
                if(i==next) highlight(canvas,x-col*.45f,25,col*.9f,height-30);
                text(canvas,SHORT[i],x,40,9,i==next?GOLD:MUTED,false,true);
                text(canvas,times[i],x,Math.min(height-9,61),13,i==next?GOLD:WHITE,true,true);
            }
        } else if("Small".equals(size)) {
            text(canvas,"☪ "+date,12,20,12,GOLD,true,false);
            line(canvas,11,28,width-11,28,Color.argb(60,195,223,211));
            for(int i=0;i<5;i++){
                float y=46+i*(height-55)/5f;
                if(i==next)highlight(canvas,8,y-13,width-16,21);
                text(canvas,NAMES[i],13,y,Math.min(11,width/15f),i==next?GOLD:MUTED,false,false);
                Paint p=paint(i==next?GOLD:WHITE);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(12);
                p.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(times[i],width-12,y,p);
            }
        } else if("Large".equals(size)) {
            text(canvas,"РАСПИСАНИЕ НАМАЗА",17,24,11,MUTED,true,false);
            text(canvas,date,17,47,19,WHITE,true,false);
            text(canvas,"☪",width-42,43,25,GOLD,true,false);
            line(canvas,16,58,width-16,58,Color.argb(65,208,226,214));
            float row=(height-75)/5f;
            for(int i=0;i<5;i++){
                float y=72+i*row;
                if(i==next)highlight(canvas,12,y-1,width-24,row-3);
                text(canvas,NAMES[i],23,y+row*.62f,14,i==next?GOLD:MUTED,false,false);
                Paint p=paint(i==next?GOLD:WHITE);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(19);p.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(times[i],width-24,y+row*.68f,p);
            }
        } else {
            text(canvas,"☪ "+date,15,23,16,WHITE,true,false);
            text(canvas,"НАМАЗ",width-58,21,10,GOLD,true,false);
            line(canvas,14,31,width-14,31,Color.argb(55,200,222,211));
            float cw=(width-26)/3f;
            for(int i=0;i<5;i++){
                int row=i/3,col=i%3;
                float x=13+col*cw,y=42+row*(height-46)/2f;
                if(i==next)highlight(canvas,x,y-3,cw-5,(height-46)/2f-2);
                text(canvas,NAMES[i],x+6,y+12,10,i==next?GOLD:MUTED,false,false);
                text(canvas,times[i],x+6,y+33,18,i==next?GOLD:WHITE,true,false);
            }
        }
        canvas.restore();
        return bmp;
    }
    private static void highlight(Canvas c,float x,float y,float w,float h){
        Paint p=paint(Color.argb(62,158,177,133));
        c.drawRoundRect(x,y,x+w,y+h,9,9,p);
    }
}
