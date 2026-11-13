

package com.thirtydegreesray.openhub.util;

import android.content.Context;
import androidx.annotation.NonNull;

/**
 * 手机窗口工具类
 */
public class WindowUtil {

    /**
     * 屏幕宽度
     */
    public static int screenWidth = 0;

    /**
     * 屏幕高度
     */
    public static int screenHeight = 0;

    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    public static int dipToPx(@NonNull Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    /**
     * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
     */
    public static int pxToDip(@NonNull Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

}
