package dev.fusion.widget;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import org.json.JSONObject;

final class DeviceIcons {
    static Bitmap make(Context ctx, JSONObject device) {
        return make(ctx, device, Color.rgb(74, 119, 205));
    }
    static Bitmap make(Context ctx, JSONObject device, int fallbackColor) {
        Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        try {
            JSONObject icon = new JSONObject(device.optString("icon"));
            JSONObject data = icon.optJSONObject("d");
            if (icon.optInt("t") == 1 && data != null && Protocol.TARGET.equals(data.optString("pkg"))) {
                Drawable d = ctx.getPackageManager().getResourcesForApplication(Protocol.TARGET).getDrawable(data.getInt("id"), null);
                d.setBounds(4, 4, 76, 76); d.draw(c); return b;
            }
        } catch (Exception ignored) {}
        Paint p = new Paint(3); p.setColor(fallbackColor); p.setStrokeWidth(4); p.setStyle(Paint.Style.STROKE);
        String t = device.optString("deviceType").toLowerCase(java.util.Locale.ROOT);
        if ("fusion_hidden".equals(t)) {
            Path folder = new Path(); folder.moveTo(10, 25); folder.lineTo(10, 66); folder.lineTo(70, 66); folder.lineTo(70, 25); folder.lineTo(37, 25); folder.lineTo(30, 15); folder.lineTo(10, 15); folder.close(); c.drawPath(folder, p); c.drawLine(30, 43, 50, 43, p);
        } else if (t.contains("phone") || t.contains("pad")) {
            c.drawRoundRect(t.contains("pad") ? 13 : 22, 9, t.contains("pad") ? 67 : 58, 71, 7, 7, p); c.drawLine(35, 62, 45, 62, p);
        } else if (t.contains("windows") || t.contains("tv")) {
            c.drawRoundRect(7, 13, 73, 56, 5, 5, p); c.drawLine(40, 56, 40, 67, p); c.drawLine(25, 68, 55, 68, p);
        } else if (t.contains("headset")) {
            c.drawArc(14, 10, 66, 68, 180, 180, false, p); c.drawRoundRect(12, 37, 24, 66, 5, 5, p); c.drawRoundRect(56, 37, 68, 66, 5, 5, p);
        } else if (t.contains("watch") || t.contains("band")) {
            c.drawRoundRect(21, 21, 59, 59, 10, 10, p); c.drawLine(29, 9, 51, 9, p); c.drawLine(29, 71, 51, 71, p);
        } else {
            c.drawRoundRect(13, 15, 67, 69, 13, 13, p); c.drawCircle(40, 39, 10, p); c.drawLine(29, 59, 51, 59, p);
        }
        return b;
    }
}
