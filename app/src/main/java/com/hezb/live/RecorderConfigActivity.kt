package com.hezb.live

import android.content.Intent
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hezb.live.recorder.config.RecorderConfig
import com.hezb.live.recorder.config.RecorderConfigHelper
import com.hezb.live.recorder.model.Size
import com.hezb.live.recorder.util.LogUtil
import com.hezb.live.recorder.util.MediaCodecUtil

/**
 * Project Name: AndroidScreenLive
 * File Name:    RecorderConfigActivity
 *
 * Description: 录制配置.
 *
 * @author  hezhubo
 * @date    2022年09月08日 11:41
 */
class RecorderConfigActivity : AppCompatActivity() {

    private val videoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC
    private val codecName = "AVC"
    private val audioMimeType = MediaFormat.MIMETYPE_AUDIO_AAC

    private val videoEncoderInfo = ArrayList<MediaCodecInfo>()
    private val audioEncoderInfo = ArrayList<MediaCodecInfo>()

    private var recorderConfig = RecorderConfig()

    private fun initEncoders() {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder) {
                continue
            }
            try {
                val videoCapabilities = codecInfo.getCapabilitiesForType(videoMimeType)
                if (videoCapabilities != null) {
                    videoEncoderInfo.add(codecInfo)
                    continue
                }
            } catch (e: Exception) {}
            try {
                val audioCapabilities = codecInfo.getCapabilitiesForType(audioMimeType)
                if (audioCapabilities != null) {
                    audioEncoderInfo.add(codecInfo)
                }
            } catch (e: Exception) {}
        }
    }

    private fun initConfig() {
        val sourceConfig = intent.getParcelableExtra<RecorderConfig>("recorderConfig")
        if (sourceConfig != null) {
            recorderConfig = sourceConfig
        }
    }

    @Composable
    fun VideoEncoderGroup(encoderSelectedState : MutableState<MediaCodecInfo>) {
        Column {
            videoEncoderInfo.forEach { mediaCodecInfo ->
                val isSelected = mediaCodecInfo == encoderSelectedState.value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { encoderSelectedState.value = mediaCodecInfo }) {
                    RadioButton(selected = isSelected, onClick = null)
                    Text(mediaCodecInfo.name)
                }
            }
        }
    }

    @Composable
    fun VideoBitrateModeGroup(
        encoderCapabilities: MediaCodecInfo.EncoderCapabilities?,
        bitrateModeSelectedState: MutableState<Int>
    ) {
        // TODO framework代码写死了只支持VBR模式，isBitrateModeSupported无效判断代码
        Column {
            encoderCapabilities?.let {
//                if (it.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { bitrateModeSelectedState.value = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ }) {
                        RadioButton(selected = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ == bitrateModeSelectedState.value, onClick = null)
                        Text("CQ")
                    }
//                }
//                if (it.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { bitrateModeSelectedState.value = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR }) {
                        RadioButton(selected = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR == bitrateModeSelectedState.value, onClick = null)
                        Text("VBR")
                    }
//                }
//                if (it.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { bitrateModeSelectedState.value = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR }) {
                        RadioButton(selected = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR == bitrateModeSelectedState.value, onClick = null)
                        Text("CBR")
                    }
//                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                    if (it.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { bitrateModeSelectedState.value = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD }) {
                            RadioButton(selected = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD == bitrateModeSelectedState.value, onClick = null)
                            Text("CBR-FD")
                        }
//                    }
                }
            }
        }
    }

    @Composable
    fun VideoProfileLevelGroup(
        codecProfileLevels: Array<MediaCodecInfo.CodecProfileLevel>,
        profileSelectedState: MutableState<MediaCodecInfo.CodecProfileLevel?>
    ) {
        Column {
            codecProfileLevels.forEach { codecProfileLevel ->
                val isSelected = codecProfileLevel == profileSelectedState.value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { profileSelectedState.value = codecProfileLevel }) {
                    RadioButton(selected = isSelected, onClick = null)
                    val profileName = MediaCodecUtil.findProfileName(codecProfileLevel.profile, codecName)
                    val levelName = MediaCodecUtil.findLevelName(codecProfileLevel.level, codecName)
                    Text("$profileName\n$levelName")
                }
            }
        }
    }

    @Composable
    fun AudioEncoderGroup(encoderSelectedState: MutableState<MediaCodecInfo>) {
        Column {
            audioEncoderInfo.forEach { mediaCodecInfo ->
                val isSelected = mediaCodecInfo == encoderSelectedState.value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { encoderSelectedState.value = mediaCodecInfo }) {
                    RadioButton(selected = isSelected, onClick = null)
                    Text(mediaCodecInfo.name)
                }
            }
        }
    }

    @Composable
    fun AudioSampleRateGroup(
        supportedSampleRates: IntArray,
        sampleRateSelectedState: MutableState<Int>
    ) {
        Column {
            supportedSampleRates.forEach { sampleRate ->
                val isSelected = sampleRate == sampleRateSelectedState.value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { sampleRateSelectedState.value = sampleRate }) {
                    RadioButton(selected = isSelected, onClick = null)
                    Text(sampleRate.toString())
                }
            }
        }
    }

    @Composable
    fun AudioProfileLevelGroup(
        codecProfileLevels: Array<MediaCodecInfo.CodecProfileLevel>,
        profileSelectedState: MutableState<MediaCodecInfo.CodecProfileLevel?>
    ) {
        Column {
            codecProfileLevels.forEach { codecProfileLevel ->
                val isSelected = codecProfileLevel == profileSelectedState.value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { profileSelectedState.value = codecProfileLevel }) {
                    RadioButton(selected = isSelected, onClick = null)
                    val profileName = MediaCodecUtil.findAACObjectProfileName(codecProfileLevel.profile)
                    Text("$profileName")
                }
            }
        }
    }

    @Composable
    fun AudioSourceGroup(sourceSelectedState: MutableState<Int>) {
        Column {
            if (RecorderConfigHelper.supportRecordPlaybackAudio()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { sourceSelectedState.value = RecorderConfigHelper.AUDIO_SOURCE_TYPE_ALL }) {
                    RadioButton(selected = sourceSelectedState.value == RecorderConfigHelper.AUDIO_SOURCE_TYPE_ALL, onClick = null)
                    Text("麦克风+系统输出声音")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { sourceSelectedState.value = RecorderConfigHelper.AUDIO_SOURCE_TYPE_PLAYBACK }) {
                    RadioButton(selected = sourceSelectedState.value == RecorderConfigHelper.AUDIO_SOURCE_TYPE_PLAYBACK, onClick = null)
                    Text("仅系统输出声音")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { sourceSelectedState.value = RecorderConfigHelper.AUDIO_SOURCE_TYPE_MIC }) {
                RadioButton(selected = sourceSelectedState.value == RecorderConfigHelper.AUDIO_SOURCE_TYPE_MIC, onClick = null)
                Text("仅麦克风")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initEncoders()

        initConfig()

        val videoEncoder = if(recorderConfig.videoCodecName.isNullOrEmpty()) {
            videoEncoderInfo.first()
        } else {
            var findEncoder = videoEncoderInfo.first()
            for (encoder in videoEncoderInfo) {
                if (encoder.name == recorderConfig.videoCodecName) {
                    findEncoder = encoder
                    break
                }
            }
            findEncoder
        }
        val audioEncoder = if (recorderConfig.audioCodecName.isNullOrEmpty()) {
            audioEncoderInfo.first()
        } else {
            var findEncoder = audioEncoderInfo.first()
            for (encoder in audioEncoderInfo) {
                if (encoder.name == recorderConfig.audioCodecName) {
                    findEncoder = encoder
                    break
                }
            }
            findEncoder
        }

        setContent {
            val videoEncoderSelectedState: MutableState<MediaCodecInfo> = remember { mutableStateOf(videoEncoder) }
            val videoCodecCapabilities = videoEncoderSelectedState.value.getCapabilitiesForType(videoMimeType)
            val resolutionState: MutableState<String> = remember { mutableStateOf("${recorderConfig.videoSize.width}x${recorderConfig.videoSize.height}") }
            val videoBitrateState: MutableState<Int> = remember { mutableStateOf(recorderConfig.videoBitrate) }
            val videoBitrateModeState: MutableState<Int> = remember { mutableStateOf(recorderConfig.videoBitrateMode) }
            val frameRateState: MutableState<Int> = remember { mutableStateOf(recorderConfig.videoFrameRate) }
            val frameIntervalState: MutableState<Int> = remember { mutableStateOf(recorderConfig.videoFrameInterval) }
            var videoProfile: MediaCodecInfo.CodecProfileLevel? = null
            if (recorderConfig.videoCodecProfile > 0 && recorderConfig.videoCodecProfileLevel > 0) {
                videoCodecCapabilities?.profileLevels?.forEach {
                    if (it.profile == recorderConfig.videoCodecProfile && it.level == recorderConfig.videoCodecProfileLevel) {
                        videoProfile = it
                        return@forEach
                    }
                }
            }
            val videoProfileSelectedState: MutableState<MediaCodecInfo.CodecProfileLevel?> = remember { mutableStateOf(videoProfile) }
            val videoMaxBFramesState: MutableState<Int> = remember { mutableStateOf(recorderConfig.videoMaxBFrames) }

            val audioEncoderSelectedState: MutableState<MediaCodecInfo> = remember { mutableStateOf(audioEncoder) }
            val audioCodecCapabilities = audioEncoderSelectedState.value.getCapabilitiesForType(audioMimeType)
            val audioBitrateState: MutableState<Int> = remember { mutableStateOf(recorderConfig.audioBitrate) }
            val audioSampleRateState: MutableState<Int> = remember { mutableStateOf(recorderConfig.audioSampleRate) }
            val audioChannelCountState: MutableState<String> = remember { mutableStateOf(recorderConfig.audioChannelCount.toString()) }
            var audioProfile: MediaCodecInfo.CodecProfileLevel? = null
            if (recorderConfig.audioCodecAACProfile > 0) {
                audioCodecCapabilities?.profileLevels?.forEach {
                    if (it.profile == recorderConfig.audioCodecAACProfile) {
                        audioProfile = it
                        return@forEach
                    }
                }
            }
            val audioProfileSelectedState: MutableState<MediaCodecInfo.CodecProfileLevel?> = remember { mutableStateOf(audioProfile) }

            val virtualDisplayDpiState: MutableState<Int> = remember { mutableStateOf(recorderConfig.virtualDisplayDpi) }
            val audioRecordState: MutableState<Boolean> = remember { mutableStateOf(recorderConfig.recordAudio) }
            val audioSourceSelectedState: MutableState<Int> = remember { mutableStateOf(recorderConfig.audioSourceType) }

            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    Text(text = "VideoEncoder：")
                    VideoEncoderGroup(videoEncoderSelectedState)
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Text(text = "是否支持硬编码：${!videoEncoderSelectedState.value.isSoftwareOnly}")
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    videoCodecCapabilities?.colorFormats?.let {
                        for (color in it) {
                            if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                                Text(text = "是否支持录屏：true")
                                return@let
                            }
                        }
                        Text(text = "是否支持录屏：false")
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "分辨率 ")
                    videoCodecCapabilities?.videoCapabilities?.let {
                        Text(text = "${it.supportedWidths}x${it.supportedHeights}：")
                    }
                    TextField(
                        value = resolutionState.value,
                        onValueChange = { resolutionState.value = it },
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "码率 ")
                    videoCodecCapabilities?.videoCapabilities?.let { videoCapabilities ->
                        Text(text = "${videoCapabilities.bitrateRange}：")
                        TextField(
                            value = videoBitrateState.value.toString(),
                            onValueChange = {
                                try {
                                    val bitrate = it.toInt()
                                    if (videoCapabilities.bitrateRange.contains(bitrate)) {
                                        videoBitrateState.value = bitrate
                                    } else {
                                        showToast("码率超出限定区间！")
                                    }
                                } catch (e: Exception) {
                                    showToast("无效码率！")
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Bitrate Mode：")
                    videoCodecCapabilities?.encoderCapabilities?.let {
                        VideoBitrateModeGroup(it, videoBitrateModeState)
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "帧率 ")
                    videoCodecCapabilities?.videoCapabilities?.let { videoCapabilities ->
                        Text(text = "${videoCapabilities.supportedFrameRates}：")
                        TextField(
                            value = frameRateState.value.toString(),
                            onValueChange = {
                                try {
                                    val frameRate = it.toInt()
                                    if (videoCapabilities.supportedFrameRates.contains(frameRate)) {
                                        frameRateState.value = frameRate
                                    } else {
                                        showToast("帧率超出限定区间！")
                                    }
                                } catch (e: Exception) {
                                    showToast("无效帧率！")
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "关键帧间隔(单位：秒)：")
                    TextField(
                        value = frameIntervalState.value.toString(),
                        onValueChange = {
                            try {
                                val frameInterval = it.toInt()
                                if (frameInterval <= 0) {
                                    throw IllegalArgumentException()
                                }
                                frameIntervalState.value = frameInterval
                            } catch (e: Exception) {
                                showToast("无效关键帧间隔！")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "AVCProfile：")
                    videoCodecCapabilities?.profileLevels?.let {
                        VideoProfileLevelGroup(it, videoProfileSelectedState)
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "max-bframes：")
                        TextField(
                            value = videoMaxBFramesState.value.toString(),
                            onValueChange = {
                                try {
                                    val maxBFrames = it.toInt()
                                    if (maxBFrames <= 0) {
                                        throw IllegalArgumentException()
                                    }
                                    videoMaxBFramesState.value = maxBFrames
                                } catch (e: Exception) {
                                    showToast("无效最大的B帧数量！")
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row {
                    Text(text = "AudioEncoder：")
                    AudioEncoderGroup(audioEncoderSelectedState)
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Text(text = "是否支持硬编码：${!audioEncoderSelectedState.value.isSoftwareOnly}")
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "码率 ")
                    audioCodecCapabilities?.audioCapabilities?.let { audioCapabilities ->
                        Text(text = "${audioCapabilities.bitrateRange}：")
                        TextField(
                            value = audioBitrateState.value.toString(),
                            onValueChange = {
                                try {
                                    val bitrate = it.toInt()
                                    if (audioCapabilities.bitrateRange.contains(bitrate)) {
                                        audioBitrateState.value = bitrate
                                    } else {
                                        showToast("码率超出限定区间！")
                                    }
                                } catch (e: Exception) {
                                    showToast("无效码率！")
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "采样率：")
                    audioCodecCapabilities?.audioCapabilities?.supportedSampleRates?.let {
                        AudioSampleRateGroup(
                            supportedSampleRates = it,
                            sampleRateSelectedState = audioSampleRateState
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "声道数 ")
                    audioCodecCapabilities?.audioCapabilities?.let { audioCapabilities ->
                        Text(text = "[1,${audioCapabilities.maxInputChannelCount}]：")
                        TextField(
                            value = audioChannelCountState.value,
                            onValueChange = {
                                if (it.isNotEmpty()) {
                                    try {
                                        val channelCount = it.toInt()
                                        if (channelCount >= 1 && channelCount <= audioCapabilities.maxInputChannelCount) {
                                            audioChannelCountState.value = channelCount.toString()
                                        } else {
                                            showToast("声道数超出限定区间！")
                                        }
                                    } catch (e: Exception) {
                                        showToast("无效声道数！")
                                    }
                                } else {
                                    audioChannelCountState.value = ""
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "AACObjectProfile：")
                    audioCodecCapabilities.profileLevels?.let {
                        AudioProfileLevelGroup(it, audioProfileSelectedState)
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "虚拟屏幕的密度：")
                    TextField(
                        value = virtualDisplayDpiState.value.toString(),
                        onValueChange = {
                            try {
                                val dpi = it.toInt()
                                if (dpi <= 0) {
                                    throw IllegalArgumentException()
                                }
                                virtualDisplayDpiState.value = dpi
                            } catch (e: Exception) {
                                showToast("无效虚拟屏幕密度！")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "是否录音：")
                    Checkbox(checked = audioRecordState.value, onCheckedChange = {
                        audioRecordState.value = it
                        recorderConfig.recordAudio = it
                    })
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "声音源：")
                    AudioSourceGroup(sourceSelectedState = audioSourceSelectedState)
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = {
                        try {
                            val resolutions = resolutionState.value.split("x")
                            val width = resolutions[0].toInt()
                            val height = resolutions[1].toInt()
                            val min = videoCodecCapabilities?.videoCapabilities?.supportedWidths?.lower ?: 0
                            val max = videoCodecCapabilities?.videoCapabilities?.supportedWidths?.upper ?: 0
                            if (width < min || width > max || height < min || height > max) {
                                throw IllegalArgumentException()
                            }
                            val size = Size(width, height)
                            recorderConfig.videoSize = size
                        } catch (e: Exception) {
                            showToast("分辨率错误！")
                            return@Button
                        }
                        recorderConfig.videoCodecName = videoEncoderSelectedState.value.name
                        recorderConfig.videoBitrate = videoBitrateState.value
                        recorderConfig.videoFrameRate = frameRateState.value
                        recorderConfig.videoFrameInterval = frameIntervalState.value
                        // 校验profile是否支持
                        videoProfileSelectedState.value?.let { targetProfileLevel ->
                            var hasProfile = false
                            videoCodecCapabilities?.profileLevels?.forEach {
                                if (targetProfileLevel.profile == it.profile && targetProfileLevel.level == it.level) {
                                    hasProfile = true
                                    return@forEach
                                }
                            }
                            if (hasProfile) {
                                recorderConfig.videoCodecProfile = targetProfileLevel.profile
                                recorderConfig.videoCodecProfileLevel = targetProfileLevel.level
                            } else {
                                recorderConfig.videoCodecProfile = 0
                                recorderConfig.videoCodecProfileLevel = 0
                            }
                        }

                        recorderConfig.virtualDisplayDpi = virtualDisplayDpiState.value

                        recorderConfig.audioCodecName = audioEncoderSelectedState.value.name
                        recorderConfig.audioBitrate = audioBitrateState.value
                        val availableSampleRate =
                            audioCodecCapabilities?.audioCapabilities?.supportedSampleRates?.contains(
                                audioSampleRateState.value
                            ) ?: false
                        if (availableSampleRate) {
                            recorderConfig.audioSampleRate = audioSampleRateState.value
                        } else {
                            showToast("采样率错误！")
                            return@Button
                        }
                        audioChannelCountState.value.let {
                            if (it.isEmpty()) {
                                showToast("声道数错误！")
                                return@Button
                            }
                            recorderConfig.audioChannelCount = audioChannelCountState.value.toInt()
                        }
                        // 校验profile是否支持
                        audioProfileSelectedState.value?.let { targetProfileLevel ->
                            var hasProfile = false
                            audioCodecCapabilities?.profileLevels?.forEach {
                                if (targetProfileLevel.profile == it.profile) {
                                    hasProfile = true
                                    return@forEach
                                }
                            }
                            if (hasProfile) {
                                recorderConfig.audioCodecAACProfile = targetProfileLevel.profile
                            } else {
                                recorderConfig.audioCodecAACProfile = 0
                            }
                        }
                        recorderConfig.audioSourceType = audioSourceSelectedState.value

                        returnResult()
                    }) {
                        Text(text = "确定修改配置")
                    }
                }
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun returnResult() {
        LogUtil.i(msg = recorderConfig.toString())
        val intent = Intent()
        intent.putExtra("recorderConfig", recorderConfig)
        RecorderConfigHelper.saveConfig(this, recorderConfig)
        setResult(RESULT_OK, intent)
        finish()
    }

}