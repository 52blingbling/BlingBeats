package com.localbeats

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalBeatsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 清空上一次日志，保留最新一次
        logFile.writeText("==== LocalBeats Debug Log ====\n")

        //region debug-point app-uncaught
        // 安装全局未捕获异常处理器，将崩溃堆栈写入 logcat 和文件
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val msg = "[UNCAUGHT] thread=${thread.name}\n$sw"
            Log.e(TAG, msg)
            appendLog("FATAL", msg)
            previous?.uncaughtException(thread, throwable)
        }
        //endregion
        Dbg.log("APP onCreate")
    }

    companion object {
        private const val TAG = "LocalBeats"
        lateinit var instance: LocalBeatsApp
            private set

        private val logFile: File by lazy {
            File(instance.getExternalFilesDir(null), "debug.log").also { it.parentFile?.mkdirs() }
        }

        private val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        fun appendLog(level: String, msg: String) {
            try {
                val time = ts.format(Date())
                // 文件写入加锁防止并发写坏
                synchronized(logFile) {
                    logFile.appendText("[$time][$level] $msg\n")
                }
            } catch (_: Throwable) {
                // 日志本身失败不能影响主流程
            }
        }
    }
}

/** 极简调试日志工具：同时写入 logcat 和应用私有文件 */
object Dbg {
    fun log(msg: String) {
        Log.d("LocalBeats", msg)
        LocalBeatsApp.appendLog("D", msg)
    }
    fun err(msg: String, t: Throwable? = null) {
        if (t != null) {
            Log.e("LocalBeats", msg, t)
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            LocalBeatsApp.appendLog("E", "$msg\n$sw")
        } else {
            Log.e("LocalBeats", msg)
            LocalBeatsApp.appendLog("E", msg)
        }
    }
}
