package dev.fusion.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.*;

final class AppUi {
    static final int REFRESH=0, SETTINGS=1, MORE=2, HELP=3, DOWN=4, UP=5, RIGHT=6, OUT=7, NEGATIVE=8, DESKTOP=9, DEVICES=10, BACK=11, HIDDEN=12, DRAG=13;
    final Context context;
    final int background, surface, text, muted, accent, pale, line;
    AppUi(Context context) {
        this.context=context; background=context.getColor(R.color.app_background); surface=context.getColor(R.color.app_card);
        text=context.getColor(R.color.app_text); muted=context.getColor(R.color.app_muted); accent=context.getColor(R.color.app_accent);
        pale=context.getColor(R.color.app_pale); line=context.getColor(R.color.app_line);
    }
    int dp(float value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    TextView text(String value, int size, int color, boolean medium) {
        TextView t=new TextView(context); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif", Typeface.NORMAL));
        t.setFontFeatureSettings("kern"); return t;
    }
    LinearLayout column() { LinearLayout l=new LinearLayout(context); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout row() { LinearLayout l=new LinearLayout(context); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(android.view.Gravity.CENTER_VERTICAL); return l; }
    GradientDrawable shape(int color, int radius) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d; }
    Drawable ripple(int color, int radius) { return new RippleDrawable(ColorStateList.valueOf((accent&0xffffff)|0x20000000),shape(color,radius),shape(Color.WHITE,radius)); }
    void space(LinearLayout parent,int height) { parent.addView(new View(context),new LinearLayout.LayoutParams(1,dp(height))); }
    View icon(int kind,int color,int size) { Icon view=new Icon(context,kind,color);view.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return view; }
    FrameLayout badge(int kind) {
        FrameLayout frame=new FrameLayout(context);frame.setBackground(shape(pale,11));
        frame.addView(new Icon(context,kind,accent),new FrameLayout.LayoutParams(dp(19),dp(19),android.view.Gravity.CENTER));
        frame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return frame;
    }
    static final class Icon extends View {
        final int kind,color; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c,int kind,int color) { super(c);this.kind=kind;this.color=color;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);float side=Math.min(getWidth(),getHeight());c.save();c.translate((getWidth()-side)/2,(getHeight()-side)/2);c.scale(side/24f,side/24f);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            Path path=new Path();
            switch(kind) {
                case REFRESH:c.drawArc(4,4,20,20,35,275,false,p);path.moveTo(20,4);path.lineTo(20,9);path.lineTo(15,9);break;
                case SETTINGS:for(int i=0;i<3;i++){float y=6+i*6;c.drawLine(3,y,21,y,p);p.setStyle(Paint.Style.FILL);c.drawCircle(i==1?8:16,y,2.5f,p);p.setStyle(Paint.Style.STROKE);}break;
                case MORE:p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)c.drawCircle(5+i*7,12,1.5f,p);break;
                case HELP:c.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);p.setTextSize(16);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextAlign(Paint.Align.CENTER);c.drawText("?",12,18,p);break;
                case DOWN:path.moveTo(6,9);path.lineTo(12,15);path.lineTo(18,9);break;
                case UP:path.moveTo(6,15);path.lineTo(12,9);path.lineTo(18,15);break;
                case RIGHT:path.moveTo(9,5);path.lineTo(16,12);path.lineTo(9,19);break;
                case BACK:path.moveTo(15,5);path.lineTo(8,12);path.lineTo(15,19);break;
                case HIDDEN:path.moveTo(3,7);path.lineTo(3,20);path.lineTo(21,20);path.lineTo(21,7);path.lineTo(11,7);path.lineTo(9,4);path.lineTo(3,4);path.close();c.drawLine(9,13,15,13,p);break;
                case DRAG:c.drawLine(5,7,19,7,p);c.drawLine(5,12,19,12,p);c.drawLine(5,17,19,17,p);break;
                case OUT:path.moveTo(6,18);path.lineTo(18,6);path.moveTo(8,6);path.lineTo(18,6);path.lineTo(18,16);break;
                case NEGATIVE:c.drawRoundRect(3,3,10,10,1.5f,1.5f,p);c.drawRoundRect(14,3,21,10,1.5f,1.5f,p);c.drawRoundRect(3,14,10,21,1.5f,1.5f,p);c.drawLine(17.5f,14,17.5f,21,p);c.drawLine(14,17.5f,21,17.5f,p);break;
                case DESKTOP:c.drawRoundRect(2,4,22,20,2,2,p);c.drawLine(2,9,22,9,p);c.drawRoundRect(6,12,11,17,1,1,p);c.drawLine(14,13,18,13,p);c.drawLine(14,16,18,16,p);break;
                case DEVICES:c.drawRoundRect(2,3,17,16,2,2,p);c.drawLine(9,16,9,21,p);c.drawLine(5,21,13,21,p);c.drawRoundRect(17,10,23,22,1.5f,1.5f,p);break;
            }
            c.drawPath(path,p);c.restore();
        }
    }
}
