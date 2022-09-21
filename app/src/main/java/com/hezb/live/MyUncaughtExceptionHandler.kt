package com.hezb.live

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import com.hezb.live.recorder.config.RecorderConfigHelper
import java.io.*
import java.text.SimpleDateFormat
import java.util.regex.Pattern

/**
 * Project Name: AndroidScreenLive
 * File Name:    MyUncaughtExceptionHandler
 *
 * Description: 手机崩溃日志.
 *
 * @author  hezhubo
 * @date    2022年09月20日 14:37
 */
class MyUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {

    private val ueh = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        saveException(t, e)
        ueh?.uncaughtException(t, e)
    }

    private fun saveException(t: Thread, e: Throwable) {
        createFilePath()?.let { path ->
            val file = File(path)
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    return
                }
            }
            try {
                val fileWriter = FileWriter(file)
                val printWriter = PrintWriter(BufferedWriter(fileWriter))
                printWriter.println("device: ${Build.MANUFACTURER} ${Build.MODEL}")
                printWriter.println("version: ${Build.VERSION.RELEASE}")
                printWriter.println("cpu abi: ${Build.CPU_ABI}")
                val windowManager = IApplication.getInstance().getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(metrics)
                printWriter.println("resolution: ${metrics.widthPixels}x${metrics.heightPixels}, dpi: ${metrics.densityDpi}")
                printWriter.println("memory: ${getAvailMemory(IApplication.getInstance())}/${getTotalMemory()}")
                printWriter.println(RecorderConfigHelper.readConfig(IApplication.getInstance()))
                printWriter.println("thread: ${t.name}")
                var cause: Throwable? = e
                do {
                    cause!!.printStackTrace(printWriter)
                    cause = e.cause
                } while (cause != null)
                printWriter.close()
            } catch (e: Exception) {}
        }
    }

    private fun createFilePath(): String? {
        val outputDir = Environment.getExternalStorageDirectory().path + "/HezbRecorder/"
        val dirFile = File(outputDir)
        if (!dirFile.exists()) {
            if (!dirFile.mkdirs()) {
                return null
            }
        }
        return "${outputDir}crash_${SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())}.txt"
    }

    private fun getTotalMemory(): Long {
        var totalMemory: Long = 0
        try {
            val fr = FileReader("/proc/meminfo")
            val br = BufferedReader(fr)
            br.use {
                while (true) {
                    var line = it.readLine() ?: break
                    if (line.startsWith("MemTotal")) {
                        val pattern = Pattern.compile("[0-9]+")
                        val matcher = pattern.matcher(line)
                        if (matcher.find()) {
                            line = matcher.group(0)
                            totalMemory = line.toLong()
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return totalMemory
    }

    private fun getAvailMemory(context: Context?): Long {
        var availMemory: Long = 0
        context?.let {
            (it.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let { am ->
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                availMemory = mi.availMem / 1024
            }
        }
        return availMemory
    }

}