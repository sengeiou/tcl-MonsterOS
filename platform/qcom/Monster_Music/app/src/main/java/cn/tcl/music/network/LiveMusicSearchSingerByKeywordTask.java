package cn.tcl.music.network;

import android.content.Context;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cn.tcl.music.R;
import cn.tcl.music.model.live.BaseSong;
import cn.tcl.music.model.live.LiveMusicSinger;
import cn.tcl.music.util.LogUtil;


/**
 * Created by xiangxiang.liu on 2015/11/5.
 */
public class LiveMusicSearchSingerByKeywordTask extends RequestSongTask {
    public static final String DEFAULT_PAGE_LIMIT = "20";
    private static final String TAG = LiveMusicSearchSingerByKeywordTask.class.getSimpleName();
    private ILoadData mListener;
    private String mLimit = DEFAULT_PAGE_LIMIT;
    private String mKey;
    private String mPage;

    public LiveMusicSearchSingerByKeywordTask(Context context, ILoadData listener, String key, String page) {
        super(DataRequest.Method.METHOD_LIVE_SEARCH_SINGER, context.getApplicationContext(), LiveMusicSinger.class);
        mListener = listener;
        mKey = key;
        mPage = page;
    }

    @Override
    public HashMap<String, Object> getParams() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("key", mKey);
        values.put("limit", mLimit);
        values.put("page", mPage);
        return values;
    }

    @Override
    public void postInBackground(BaseSong baseSong) {
    }

    @Override
    protected void onPostExecute(BaseSong baseSong) {
        super.onPostExecute(baseSong);
        if (mListener != null) {
            if (baseSong == null) {
                mListener.onLoadFail(DataRequest.Type.TYPE_LIVE_SEARCH_SINGER, mContext.getString(R.string.load_fail));
                return;
            }
            if (baseSong.returnCode == 0) {
                List<LiveMusicSinger> list = new ArrayList<>();
                list.add((LiveMusicSinger) baseSong);
                mListener.onLoadSuccess(DataRequest.Type.TYPE_LIVE_SEARCH_SINGER, list);
            } else {
                mListener.onLoadFail(DataRequest.Type.TYPE_LIVE_SEARCH_SINGER, baseSong.returnMessage);
            }
        } else {
            LogUtil.d(TAG, "mListener is null");
        }
    }
}
