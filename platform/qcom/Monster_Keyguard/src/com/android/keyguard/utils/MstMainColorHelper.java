package com.android.keyguard.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.support.v7.graphics.Palette;
import android.util.Log;

import com.android.keyguard.R;

/**
 * Created by vito on 16-10-12.
 */
public class MstMainColorHelper {

    /*
    获取壁纸背景色
    */
    public static int bitmapToRGB(Bitmap bm) {
        int w = bm.getWidth();
        int h = bm.getHeight();
        Log.i("pppp", "bm.getWidth() = " + bm.getWidth() + "  bm.getHeight() = " + bm.getHeight());
        w = bm.getWidth();
        h = bm.getHeight();
        int r = 0;
        int g = 0;
        int b = 0;
        Integer color;
        int radius = 40;
        int detaX = (w - 6 * radius - 21) / 6;
        int detaY = (h - 2 * radius - 21) / 2;
        int countX = -1;
        for (int k = 0; k < 12; k++) {
            int startX = (k % 6) * detaX + 10;
            int startY = (k % 2) * detaY + 5;
            for (int j = startX; j < startX + radius; j++) {
                for (int i = startY; i < startY + radius; i++) {
                    int pixel = bm.getPixel(j, i);
                    r += Color.red(pixel);
                    g += Color.green(pixel);
                    b += Color.blue(pixel);
                    countX++;
                }
            }
        }
        //int count = w*h;
        int count = countX + 1;
        r = r / count;
        g = g / count;
        b = b / count;
        return Color.argb(0xff, r, g, b);
    }


    public static int generateBitmapYAverage(Bitmap bitmap) {
        bitmap = zoom(bitmap, 0.1f);
        int[] pixels = getBitmapPixels(bitmap, true);
        long totalY = 0;
        for (int pixel : pixels) {
            totalY += (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f);
        }
        return (int) (totalY / pixels.length);
    }

    private static int generateColor(float factor, int color1, int color2) {
        return Color.rgb((int) (Color.red(color1) * (1.0F - factor) + factor * Color.red(color2)), (int) (Color.red(color1) * (1.0F - factor) + factor * Color.green(color2)), (int) (Color.red(color1) * (1.0F - factor) + factor * Color.blue(color2)));
    }

    public static int[] getBitmapPixels(Bitmap srcDst, boolean b) {
        int[] srcBuffer = new int[srcDst.getWidth() * srcDst.getHeight()];
        srcDst.getPixels(srcBuffer,
                0, srcDst.getWidth(), 0, 0, srcDst.getWidth(), srcDst.getHeight());
        return srcBuffer;
    }

    /*
       获取壁纸对应的文字颜色
     */
    public static int calcTextColor(Bitmap bm, Context context) {
        //Palette p = Palette.generate(bm,12);
        Palette.Builder builder = new Palette.Builder(bm).maximumColorCount(12);
        Palette p = builder.generate();
        Palette.Swatch swatch1 = p.getMutedSwatch();
        Palette.Swatch swatch2 = p.getVibrantSwatch();
        int color = -1;
        if (generateBitmapYAverage(bm) > 170) {
            if (swatch1 != null && swatch2 != null) {
                color = generateColor(0.45F, 0xFF000000, generateColor(0.5F, swatch1.getRgb(), swatch2.getRgb()));
            } else if (swatch1 == null && swatch2 == null) {
//                color = WindowGlobalValue.CC_TEXT_BLACK_DEFAULT_COLOR;//0xE5000000
                color = context.getColor(R.color.tcl_keyguard_text_black_default);//0xE5000000
            } else if (swatch1 != null) {
                color = generateColor(0.45F, 0xFF000000, swatch1.getRgb());
            } else if (swatch2 != null) {
                color = generateColor(0.45F, 0xFF000000, swatch2.getRgb());
            } else {
                color = context.getColor(R.color.tcl_keyguard_text_white_default);//0xFFFFFFFF
            }

        } else {
            color = context.getColor(R.color.tcl_keyguard_text_white_default);//0xFFFFFFFF
        }
        if (color != -1) {
            color = context.getColor(R.color.tcl_keyguard_text_black_default);//0xE5000000
        }
        Log.d("0917", "calcTextColor: color=" + color);
        return color;
    }

    /// kth add 20161014 start @{
    public static boolean isTextColorDark(Bitmap bm, Context context) {
        if (bm == null) {
            Log.d("MstMainColorHelper", "isTextColorDark: bitmap is null, bitmap=" + bm + ", return default WHITE");
            return false;
        }
        int color = calcTextColor(bm, context);
        if (color == Color.parseColor("#FFFFFF")) {
            Log.d("MstMainColorHelper", "isTextColorDark: text color is white");
            return false;
        } else {
            return true;
        }
    }
    /// kth add 20161014 end @}


    public static Bitmap zoom(Bitmap bmpBg, float scale) {
        if (bmpBg == null) return null;
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        Bitmap bm = Bitmap.createBitmap(bmpBg, 0, 0, bmpBg.getWidth(), bmpBg.getHeight(), matrix,
                true);
        return bm;
    }

}
