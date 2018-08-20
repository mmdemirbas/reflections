package org.reflections

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

open class DefaultThreadFactory(private val namePrefix: String, private val daemon: Boolean) : ThreadFactory {
    private val group = System.getSecurityManager()?.threadGroup ?: Thread.currentThread().threadGroup
    private val threadNumber = AtomicInteger(1)

    override fun newThread(r: Runnable) = Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0).apply {
        isDaemon = daemon
        priority = Thread.NORM_PRIORITY
    }
}