package com.hezb.live

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import com.hezb.live.recorder.RecorderClient
import com.hezb.live.recorder.filter.audio.VolumeAudioFilter
import com.hezb.live.recorder.filter.video.BaseVideoFilter
import com.hezb.live.recorder.filter.video.IconVideoFilter
import com.hezb.live.recorder.filter.video.VideoGroupFilter
import com.hezb.live.recorder.filter.video.ViewVideoFilter

/**
 * Project Name: AndroidScreenLive
 * File Name:    TestFilterHelper
 *
 * Description: filter test.
 *
 * @author  hezhubo
 * @date    2022年09月19日 17:07
 */
object TestFilterHelper {

    private var i = 0

    fun setVolumeAudioFilter(client: RecorderClient) {
        i++
        if (i % 2 == 1) {
            client.setAudioFilter(VolumeAudioFilter().apply { setVolumeScale(0f) })
        } else {
            client.setAudioFilter(null)
        }
    }

    private var j = 0

    fun setVideoFilter(client: RecorderClient, context: Context) {
        j++
        if (j % 2 == 0) {
            client.setVideoFilter(null)
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(displayMetrics)
        // 横屏，获取屏幕尺寸
        val screenWidth = displayMetrics.heightPixels
        val screenHeight = displayMetrics.widthPixels
        val videoSize = client.getVideoSize()
        val filterList = ArrayList<BaseVideoFilter>()
        filterList.add(
            IconVideoFilter(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.app_png_shuiyin
                ),
                IconVideoFilter.getFitCenterRectF(
                    Rect(screenWidth - 100, 0, screenWidth, 100),
                    screenWidth,
                    screenHeight,
                    videoSize.width,
                    videoSize.height
                )
            )
        )
        val fakeView = object : ViewVideoFilter.FakeView(context) {
            var textView: TextView? = null
            var anim: ObjectAnimator? = null

            override fun addView() {
                textView = TextView(context).apply {
                    layoutParams = LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setTextColor(Color.WHITE)
                    x = 30f
                    y = 130f
                    text = "这是文本"
                    addView(this)
                }
            }

            override fun startAnim() {
                super.startAnim()
                // TODO 添加动画非常消耗内存！！！
                anim = ObjectAnimator.ofFloat(textView, View.ROTATION, 0f, 360f).also {
                    it.duration = 3000
                    it.repeatCount = ValueAnimator.INFINITE
                    it.start()
                }
            }

            override fun stopAnim() {
                super.stopAnim()
                anim?.cancel()
            }
        }
        val viewSize = ViewVideoFilter.getCenterScaleViewSize(
            screenWidth,
            screenHeight,
            videoSize.width,
            videoSize.height
        )
        filterList.add(ViewVideoFilter(fakeView, viewSize.width, viewSize.height))
        val videoGroupFilter = VideoGroupFilter(filterList)
        client.setVideoFilter(videoGroupFilter)
    }

}