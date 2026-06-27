package io.github.huidoudour.Installer.util

import android.content.IIntentReceiver
import android.content.IIntentSender
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.os.IBinder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/**
 * 本地 IntentReceiver — 用于接收 PackageInstaller.Session.commit() 的安装结果回调。
 * 通过实现 IIntentSender.Stub 拦截系统回传的安装结果 Intent。
 *
 * 设计参考 InstallerX-Revived 项目 (GPL-3.0)
 */
class LocalIntentReceiver {
    private val channel = Channel<Intent>(Channel.BUFFERED)

    private val localSender = object : IIntentSender.Stub() {
        override fun send(
            code: Int,
            intent: Intent?,
            resolvedType: String?,
            whitelistToken: IBinder?,
            finishedReceiver: IIntentReceiver?,
            requiredPermission: String?,
            options: Bundle?
        ) {
            if (intent != null) {
                channel.trySend(intent)
            }
        }
    }

    /**
     * 获取 IntentSender，用于 PackageInstaller.Session.commit()
     */
    fun getIntentSender(): IntentSender {
        val constructor = IntentSender::class.java.getDeclaredConstructor(IIntentSender::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(localSender)
    }

    /**
     * 阻塞等待安装结果 Intent，超时 300 秒
     */
    @Throws(Exception::class)
    fun getResult(): Intent = runBlocking {
        withTimeout(300_000L) {
            channel.receive()
        }
    }
}
