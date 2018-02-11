package cn.com.xy.sms.sdk.ui.notification;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;
import cn.com.xy.sms.sdk.ui.R;
import cn.com.xy.sms.sdk.log.LogManager;
import cn.com.xy.sms.sdk.util.StringUtils;

public abstract class BasePopupNotificationViewBak {

    private static final String TAG = "BasePopupNotificationView";
    protected RemoteViews mRemoteViews = null;
    MessageItem mMessage = null;
    private final int DUOQU_POPUP_BUTTON_ONE_CLICK_MAX_ID = 599999;
    private final int DUOQU_POPUP_BUTTON_TWO_CLICK_MAX_ID = 699999;
    private final int DUOQU_POPUP_LAYOUT_CLICK_MAX_ID = 799999;
    private final int DUOQU_POPUP_READ_BUTTON_CLICK_MAX_ID = 899999;
    static int mRequestBtnOneClick = 500000;
    static int mRequestBtnTwoClick = 600000;
    static int mRequestLayoutClick = 700000;
    static int mRequestReadBtnClick = 800000;

    public RemoteViews getRemoteViews(Context ctx) {
        if (ctx == null) {
            return null;
        }
        int layoutId = getLayoutId();
        if (layoutId != 0) {
            mRemoteViews = new RemoteViews(ctx.getPackageName(), layoutId);
        }

        return mRemoteViews;
    }

    public void bindViewData(Context ctx, Bitmap logoBitmap, String contentTitle, String contentText,
            JSONArray buttonName, MessageItem message) {
        if (mRemoteViews == null || message == null || ctx == null) {
            return;
        }

        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
        Date curDate = new Date(System.currentTimeMillis());
        String contentTime = formatter.format(curDate);

        this.mMessage = message;
        setLogoBitmap(logoBitmap);
        setContentTitle(contentTitle);
        setContentTime(contentTime);
        setContentText(contentText);
        initButtonListener(ctx, message, buttonName);

    }

    protected void setContentTime(String contentTime) {
        mRemoteViews.setTextViewText(R.id.duoqu_popup_time, contentTime);
    }

    protected void setLogoBitmap(Bitmap logoBitmap) {
        mRemoteViews.setImageViewBitmap(R.id.duoqu_popup_logo_img, logoBitmap);
    }

    protected void setContentTitle(String contentTitle) {
        mRemoteViews.setTextViewText(R.id.duoqu_popup_content_title, contentTitle);
    }

    protected void setContentText(String contentText) {
        mRemoteViews.setTextViewText(R.id.duoqu_popup_content_text, contentText);
    }

    protected void initButtonListener(Context ctx, MessageItem message, JSONArray btnName) {
        if (btnName == null || btnName.length() < 1) {
            return;
        }
        if (mRequestLayoutClick == DUOQU_POPUP_LAYOUT_CLICK_MAX_ID) {
            mRequestLayoutClick = 700000;
        }
        if (mRequestReadBtnClick == DUOQU_POPUP_READ_BUTTON_CLICK_MAX_ID) {
            mRequestReadBtnClick = 800000;
        }
        if (mRequestBtnOneClick == DUOQU_POPUP_BUTTON_ONE_CLICK_MAX_ID) {
            mRequestBtnOneClick = 500000;
        }
        if (mRequestBtnTwoClick == DUOQU_POPUP_BUTTON_TWO_CLICK_MAX_ID) {
            mRequestBtnTwoClick = 600000;
        }

        if (btnName.length() == 1) {
            mRemoteViews.setOnClickPendingIntent(
                    R.id.duoqu_popup_btn_two,
                    getNotifyActionIntent(ctx, mRequestBtnOneClick++, message,
                            btnName.optJSONObject(0).optString("action_data")));
        } else if (btnName.length() == 2) {
            mRemoteViews.setOnClickPendingIntent(
                    R.id.duoqu_popup_btn_two,
                    getNotifyActionIntent(ctx, mRequestBtnOneClick++, message,
                            btnName.optJSONObject(0).optString("action_data")));

            mRemoteViews.setOnClickPendingIntent(
                    R.id.duoqu_popup_btn_three,
                    getNotifyActionIntent(ctx, mRequestBtnTwoClick++, message,
                            btnName.optJSONObject(1).optString("action_data")));
        }
    }

    protected static String getButtonName(JSONObject btnDataJson) {
        if (btnDataJson == null) {
            return "";
        }

        String buttonName = btnDataJson.optString("btn_short_name");
        if (StringUtils.isNull(buttonName)) {
            buttonName = btnDataJson.optString("btn_name");
        }
        return buttonName;
    }

    protected PendingIntent getNotifyActionIntent(Context context, int id, MessageItem message, String actionType) {
        Intent contentIntent = new Intent();
        contentIntent.setClassName(context, DoActionActivity.class.getName());
        contentIntent.putExtra("action_data", actionType);
        contentIntent.putExtra("msgId", message.mMsgId);
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendIntent = PendingIntent.getActivity(context, id, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return pendIntent;
    }

    protected PendingIntent getHasReadBtnActionIntent(Context context, int id, MessageItem message, String actionType) {
        Intent contentIntent = new Intent(actionType);
        contentIntent.putExtra("message", message);
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendIntent = PendingIntent.getBroadcast(context, id, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return pendIntent;
    }

    /**
     * 布局文件ID
     * 
     * @return
     */
    protected int getLayoutId() {
        return R.layout.duoqu_popup_notification;
    }

}