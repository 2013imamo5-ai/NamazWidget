package com.namazwidget.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Images are static, but the overlaid Chronometer is an Android system view:
 * it counts down every second without a background polling service.
 */
public class PrayerWidget extends AppWidgetProvider {
    private static final int GOLD=Color.rgb(231,201,130);
    private static final String[] NAMES={"Утренний","Обеденный","Послеобед.","Вечерний","Ночной"};
    private static final String[] SHORT={"Утро","Обед","После","Вечер","Ночь"};
    private static final String[] GENITIVE={"утра","обеда","послеобед.","вечера","ночи"};
    private static final int WHITE=Color.WHITE, MUTED=Color.rgb(185,213,201);
    private static final String BOUNDARY="com.namazwidget.app.PRAYER_BOUNDARY";

    public static class Strip extends PrayerWidget {}
    public static class Small extends PrayerWidget {}
    public static class Medium extends PrayerWidget {}
    public static class Large extends PrayerWidget {}

    private static final class Upcoming {
        final long when; final int index; final int dayOffset;
        Upcoming(long when,int index,int dayOffset) {
            this.when=when;this.index=index;this.dayOffset=dayOffset;
        }
    }

    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids) {
        update(c,m,ids,getClass().getSimpleName());
    }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options) {
        update(c,m,new int[]{id},getClass().getSimpleName());
    }
    @Override public void onReceive(Context c,Intent i) {
        super.onReceive(c,i);
        String action=i.getAction();
        if(BOUNDARY.equals(action)) {
            refreshEveryWidget(c);
        } else if(Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            refreshEveryWidget(c);
        }
    }

    public static void refreshEveryWidget(Context c) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        Class<?>[] types={Strip.class,Small.class,Medium.class,Large.class};
        for(Class<?> type:types) {
            int[] ids=manager.getAppWidgetIds(new ComponentName(c,type));
            if(ids.length>0)update(c,manager,ids,type.getSimpleName());
        }
    }

    public static void update(Context c,AppWidgetManager manager,int[] ids,String size) {
        if(ids==null||ids.length==0)return;
        Calendar now=Calendar.getInstance();
        Upcoming next=nextPrayer(now);
        for(int id:ids) {
            int w=320,h=155;
            switch(size) {
                case "Strip":w=320;h=78;break;
                case "Small":w=155;h=155;break;
                case "Large":w=320;h=235;break;
            }
            Bundle opts=manager.getAppWidgetOptions(id);
            if(opts!=null && opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,0)>0) {
                int ow=opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                int oh=opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                if(ow>40)w=Math.max(110,Math.min(400,ow));
                if(oh>40)h=Math.max(70,Math.min(350,oh));
            }
            Bitmap bmp=draw(c,w,h,size,now,next);
            RemoteViews rv=new RemoteViews(c.getPackageName(),R.layout.widget_canvas);
            rv.setImageViewBitmap(R.id.widget_picture,bmp);

            boolean atTop="Strip".equals(size)||"Medium".equals(size);
            int visible=atTop?R.id.countdown_top:R.id.countdown_bottom;
            rv.setViewVisibility(R.id.countdown_top,atTop?View.VISIBLE:View.GONE);
            rv.setViewVisibility(R.id.countdown_bottom,atTop?View.GONE:View.VISIBLE);
            if(next!=null) {
                long delta=Math.max(0,next.when-System.currentTimeMillis());
                long base=SystemClock.elapsedRealtime()+delta;
                String fmt=atTop?"До "+GENITIVE[next.index]+": %s":"%s";
                rv.setChronometer(visible,base,fmt,true);
                rv.setChronometerCountDown(visible,true);
            } else {
                rv.setViewVisibility(visible,View.GONE);
            }

            Intent open=new Intent(c,MainActivity.class);
            PendingIntent click=PendingIntent.getActivity(c,0,open,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.widget_root,click);
            manager.updateAppWidget(id,rv);
        }
        scheduleBoundaryAlarm(c,next);
    }

    private static Upcoming nextPrayer(Calendar now) {
        long instant=now.getTimeInMillis();
        Calendar local=(Calendar)now.clone();
        for(int dayOffset=0;dayOffset<=2;dayOffset++) {
            String[] entries=Schedule.times(local.get(Calendar.MONTH)+1,local.get(Calendar.DAY_OF_MONTH));
            for(int i=0;i<entries.length;i++) {
                String s=entries[i];
                if(s==null||!s.matches("\\d{2}:\\d{2}"))continue;
                String[] pieces=s.split(":");
                Calendar event=(Calendar)local.clone();
                event.set(Calendar.HOUR_OF_DAY,Integer.parseInt(pieces[0]));
                event.set(Calendar.MINUTE,Integer.parseInt(pieces[1]));
                event.set(Calendar.SECOND,0);
                event.set(Calendar.MILLISECOND,0);
                if(event.getTimeInMillis()>instant)return new Upcoming(event.getTimeInMillis(),i,dayOffset);
            }
            local.add(Calendar.DAY_OF_MONTH,1);
        }
        return null;
    }

    private static void scheduleBoundaryAlarm(Context c,Upcoming next) {
        if(next==null)return;
        AlarmManager alarm=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(alarm==null)return;
        Intent intent=new Intent(c,Strip.class).setAction(BOUNDARY);
        PendingIntent pi=PendingIntent.getBroadcast(c,81,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        long when=next.when+1000L;
        // No exact-alarm permission required. Android may postpone this alarm in Doze;
        // the Chronometer continues ticking while visible.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
    }

    private static Paint paint(int color) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);return p;
    }
    private static void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,boolean center) {
        Paint p=paint(color);
        p.setTypeface(bold?Typeface.create("sans-serif-medium",Typeface.BOLD)
            :Typeface.create("sans-serif",Typeface.NORMAL));
        p.setTextSize(size);p.setTextAlign(center?Paint.Align.CENTER:Paint.Align.LEFT);
        c.drawText(s,x,y,p);
    }
    private static void line(Canvas c,float x,float y,float x2,float y2,int color) {
        Paint p=paint(color);p.setStrokeWidth(.65f);c.drawLine(x,y,x2,y2,p);
    }
    private static void highlight(Canvas c,float x,float y,float w,float h) {
        Paint p=paint(Color.argb(62,158,177,133));
        c.drawRoundRect(x,y,x+w,y+h,9,9,p);
    }

    private static Bitmap draw(Context context,int width,int height,String size,Calendar cal,Upcoming next) {
        final int scale=3;
        Bitmap bmp=Bitmap.createBitmap(width*scale,height*scale,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bmp);canvas.scale(scale,scale);
        int opacity=context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).getInt("opacity",73);
        boolean haze=context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).getBoolean("haze",true);
        float radius=18;
        Paint bg=paint(Color.argb(Math.round(opacity*2.55f),20,43,39));
        canvas.drawRoundRect(1,1,width-1,height-1,radius,radius,bg);
        Path clip=new Path();
        clip.addRoundRect(1,1,width-1,height-1,radius,radius,Path.Direction.CW);
        canvas.save();canvas.clipPath(clip);
        if(haze) {
            Paint glow=paint(Color.WHITE);
            glow.setShader(new RadialGradient(width*.15f,0,width*.95f,
                new int[]{Color.argb(55,135,177,153),Color.argb(17,85,134,113),Color.TRANSPARENT},
                null,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,width,height,glow);
            glow.setShader(new RadialGradient(width*.90f,height,width*.7f,
                new int[]{Color.argb(29,11,88,71),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,width,height,glow);
        }
        Paint edge=paint(Color.argb(60,221,251,232));
        edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(1);
        canvas.drawRoundRect(1,1,width-1,height-1,radius,radius,edge);
        String[] times=Schedule.times(cal.get(Calendar.MONTH)+1,cal.get(Calendar.DAY_OF_MONTH));
        String date=cal.get(Calendar.DAY_OF_MONTH)+" "+
            new SimpleDateFormat("MMMM",new Locale("ru")).format(cal.getTime());
        int highlightIdx=(next!=null&&next.dayOffset==0)?next.index:-1;
        if("Strip".equals(size)) {
            text(canvas,"☪ "+date,12,18,11,GOLD,true,false);
            float col=(width-20)/5f;
            for(int i=0;i<5;i++) {
                float x=10+i*col+col/2;
                if(i==highlightIdx)highlight(canvas,x-col*.45f,25,col*.9f,height-30);
                text(canvas,SHORT[i],x,40,9,i==highlightIdx?GOLD:MUTED,false,true);
                text(canvas,times[i],x,Math.min(height-9,61),13,i==highlightIdx?GOLD:WHITE,true,true);
            }
        } else if("Small".equals(size)) {
            text(canvas,"☪ "+date,12,19,12,GOLD,true,false);
            line(canvas,11,26,width-11,26,Color.argb(60,195,223,211));
            float row=(height-76)/5f;
            for(int i=0;i<5;i++) {
                float y=42+i*row;
                if(i==highlightIdx)highlight(canvas,8,y-12,width-16,Math.max(row,17));
                text(canvas,NAMES[i],12,y,Math.min(11,width/15f),i==highlightIdx?GOLD:MUTED,false,false);
                Paint p=paint(i==highlightIdx?GOLD:WHITE);
                p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(12);
                p.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(times[i],width-12,y,p);
            }
            line(canvas,10,height-27,width-10,height-27,Color.argb(60,200,222,211));
            String label=next==null?"Нет распис.":"До "+GENITIVE[next.index];
            text(canvas,label,12,height-11,9,GOLD,true,false);
        } else if("Large".equals(size)) {
            text(canvas,"РАСПИСАНИЕ НАМАЗА",17,24,11,MUTED,true,false);
            text(canvas,date,17,47,19,WHITE,true,false);
            text(canvas,"☪",width-42,43,25,GOLD,true,false);
            line(canvas,16,58,width-16,58,Color.argb(65,208,226,214));
            float row=(height-108)/5f;
            for(int i=0;i<5;i++) {
                float y=68+i*row;
                if(i==highlightIdx)highlight(canvas,12,y-1,width-24,Math.max(20,row-3));
                text(canvas,NAMES[i],23,y+row*.62f,14,i==highlightIdx?GOLD:MUTED,false,false);
                Paint p=paint(i==highlightIdx?GOLD:WHITE);
                p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(19);
                p.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(times[i],width-24,y+row*.68f,p);
            }
            line(canvas,16,height-31,width-16,height-31,Color.argb(65,208,226,214));
            text(canvas,next==null?"Нет расписания":"◷ До "+GENITIVE[next.index],
                 22,height-12,12,GOLD,true,false);
        } else {
            text(canvas,"☪ "+date,15,23,16,WHITE,true,false);
            line(canvas,14,31,width-14,31,Color.argb(55,200,222,211));
            float cw=(width-26)/3f;
            for(int i=0;i<5;i++) {
                int row=i/3,col=i%3;
                float x=13+col*cw,y=42+row*(height-63)/2f;
                if(i==highlightIdx)highlight(canvas,x,y-3,cw-5,Math.max(28,(height-63)/2f-2));
                text(canvas,NAMES[i],x+6,y+12,10,i==highlightIdx?GOLD:MUTED,false,false);
                text(canvas,times[i],x+6,y+33,18,i==highlightIdx?GOLD:WHITE,true,false);
            }
            line(canvas,13,height-23,width-13,height-23,Color.argb(50,200,222,211));
            text(canvas,next==null?"Нет расписания":"◷ До "+GENITIVE[next.index],
                 16,height-7,10,GOLD,true,false);
        }
        canvas.restore();
        return bmp;
    }
}
