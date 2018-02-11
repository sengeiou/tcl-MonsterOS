package cn.tcl.music.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.tcl.framework.log.NLog;
import com.tcl.framework.notification.NotificationCenter;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

import cn.tcl.music.R;
import cn.tcl.music.activities.PlayingActivity;
import cn.tcl.music.fragments.live.ATabTitlePagerFragment;
import cn.tcl.music.model.MediaInfo;
import cn.tcl.music.service.MusicPlayBackService;
import cn.tcl.music.util.FileManager;
import cn.tcl.music.util.MixUtil;
import cn.tcl.music.view.PlayMusicButton;
import cn.tcl.music.widget.lyric.DefaultLrcBuilder;
import cn.tcl.music.widget.lyric.ILrcBuilder;
import cn.tcl.music.widget.lyric.LrcRow;
import cn.tcl.music.widget.lyric.LrcView;


public class PortaitPlayLyricFragment extends APortaitPlayFragment {
    public static final String TAG = PortaitPlayLyricFragment.class.getSimpleName() ;
    private LrcView lyricView;
    private TextView mSongTextView;
    private TextView mSingerTextView;
    private MediaInfo mOldMedia;        //记录上一首歌曲
    private RelativeLayout mRelativeLayout;

    public static PortaitPlayLyricFragment newInstance(ATabTitlePagerFragment.TabTitlePagerBean bean){
        PortaitPlayLyricFragment fragment = new PortaitPlayLyricFragment();
        Bundle args = new Bundle();
        args.putSerializable("titlePagerBean", bean);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    protected int inflateContentView() {
        return R.layout.fragment_play_lyric_v2;
    }


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshView();
    }

    @Override
    protected void layoutInit(LayoutInflater inflater, Bundle savedInstanceSate) {
        super.layoutInit(inflater, savedInstanceSate);
        initView();
    }

    @Override
    protected void _layoutInit(LayoutInflater inflater, Bundle savedInstanceSate) {
        super._layoutInit(inflater, savedInstanceSate);
        mRelativeLayout = (RelativeLayout) rootView.findViewById(R.id.lyric_ll);
        int statusBarHeight = getStatusBarHeight();
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0,statusBarHeight,0,0);
        mRelativeLayout.setLayoutParams(layoutParams);

        initView();
        //不能放到onStart里面 否则进入系统屏保界面后 会重新回调setCurrentMediaInfo
        NLog.d(TAG, "onStart registerListener");
    }

    private int getStatusBarHeight() {
        Class<?> clazz;
        Object object;
        Field field;
        int x;
        int statusBarHeight = 0;

        try {
            clazz = Class.forName("com.android.internal.R$dimen");
            object = clazz.newInstance();
            field = clazz.getField("status_bar_height");
            x = Integer.parseInt(field.get(object).toString());
            statusBarHeight = getResources().getDimensionPixelSize(x);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return statusBarHeight;
    }

    private void initView(){
        lyricView = (LrcView) rootView.findViewById(R.id.mylrc);
        mSongTextView = (TextView) rootView.findViewById(R.id.playing_song_name_lyrics);
        mSingerTextView = (TextView) rootView.findViewById(R.id.playing_singer_name_lyrics);
    }


    private HashMap<String, List<LrcRow>> map = new HashMap<>();
    public void setCurrentMediaInfo(MediaInfo media ){
        boolean isnew = true;
        if(null != mOldMedia && MusicPlayBackService.getCurrentMediaInfo() == mOldMedia){
            isnew = false;
        }
        this.mOldMedia = media;
        NLog.d(TAG, "setCurrentMediaInfo mCurrentMediaInfo = " + media);
        String lrc = FileManager.readLrcFileBySongTitleAndArtist(MixUtil.getSongNameWithNoSuffix(MusicPlayBackService.getTitle()),
                MusicPlayBackService.getArtistName());
        NLog.d(TAG, "setCurrentMediaInfo lrc file name = " + lrc);
        ILrcBuilder builder = new DefaultLrcBuilder();
        List<LrcRow> rows = builder.getLrcRows(lrc);
        if (!TextUtils.isEmpty(lrc) && rows != null && rows.size() > 0
                && MusicPlayBackService.getCurrentMediaInfo() !=null && !TextUtils.isEmpty(MusicPlayBackService.getCurrentMediaInfo().songRemoteId)){
            map.put(MusicPlayBackService.getCurrentMediaInfo().songRemoteId,rows);
        }

        if (lyricView != null){
            if(isnew){
                lyricView.setLrc(rows,0);
            }else{
                lyricView.setLrc(rows);
            }
        }
        mHandler.removeCallbacks(updateRunable);
        mHandler.post(updateRunable);
    }


    int count = 0;

    public void onCurrentMediaIsPreparing(MediaInfo media, int deckIndex) {
        Activity activity = getActivity();
        if(isDetached() || !isAdded() || activity == null){
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!PortaitPlayLyricFragment.this.isAdded()) {
                    return;
                }

            }
        });
    }

    public void onCurrentMediaChanged(final MediaInfo media, int deckIndex) {
        Log.d(TAG,"onCurrentMediaChanged and media null = " + (media == null) + " and getActivity is null is " + (getActivity() == null));
        if(!isAdded()){
            return;
        }
        final Activity activity = getActivity();
        if(activity == null || isDetached()) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshView();
            }
        });
    }

    private void refreshView() {
        if (null != MusicPlayBackService.getCurrentMediaInfo()
                && !TextUtils.isEmpty(MixUtil.getSongNameWithNoSuffix(MusicPlayBackService.getTitle()))) {
            NotificationCenter.defaultCenter().publish(PlayMusicButton.TAG, PlayMusicButton.CURRENT_MEDIA_CHANGED);
            mSongTextView.setText(MusicPlayBackService.getCurrentMediaInfo().title);
            mSingerTextView.setText(MusicPlayBackService.getCurrentMediaInfo().artist);
            setCurrentMediaInfo(MusicPlayBackService.getCurrentMediaInfo());
        }
    }

    public void onCurrentMediaPlayStateChanged(boolean isPlaying) {

    }

    public void onCurrentMediaPositionChanged(double position) {
        count++;
        currentPlayTime =  (int) position;
        if (false && count >= 100){
            count = 0;
            NLog.d(TAG, "onCurrentMediaPlayStateChanged before position = " + position +", currentPlayTime = " + currentPlayTime);
        }

    }


    private int currentPlayTime = 0;
    public static final int DELAY_TIME = 1000;
    Handler mHandler = new Handler();
    Runnable updateRunable = new Runnable() {
        public void run() {
            lyricView.seekLrcToTime(currentPlayTime);

            mHandler.removeCallbacks(updateRunable);
            mHandler.postDelayed(updateRunable, DELAY_TIME);
        }
    };



    @Override
    public void onDestroy() {
        super.onDestroy();
        NLog.d(TAG, "onDestroy unRegisterListener");
    }

    @Override
    public void onResume() {
        super.onResume();
        NLog.e(TAG, "onResume postDelayed updateRunable");
        mHandler.removeCallbacks(updateRunable);
        mHandler.postDelayed(updateRunable, 1000);
        ((PlayingActivity)getActivity()).setStatusBarState(2);
    }

    @Override
    public void onStop() {
        super.onStop();
        NLog.e(TAG, "onStop removeCallbacks ");
        mHandler.removeCallbacks(updateRunable);
    }


    public void automixStateChanged(boolean automixStarted) {

    }
}
