package com.hezb.live;

import android.content.Intent;
import com.hezb.live.RecorderCallback;
import com.hezb.live.recorder.RecorderConfig;

interface RecorderAidlInterface {

   void onPermissionCallback(int flag, int resultCode, in Intent data);

   void setCallback(in RecorderCallback callback);

   int getCurrentState();

   boolean isRunning();

   void startRecord(in RecorderConfig recorderConfig);

   void startLive(in RecorderConfig recorderConfig, String rtmpUrl);

   void startLiveRecord(in RecorderConfig recorderConfig, String rtmpUrl);

   void pauseRecord();

   void resumeRecord();

   void stop();

   void release();

   void testAudioFilter();

   void testVideoFilter();

}