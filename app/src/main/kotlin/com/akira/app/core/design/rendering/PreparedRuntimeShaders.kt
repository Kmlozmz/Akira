package com.akira.app.core.design.rendering

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

/** Compilation is staged before Glass is selected; geometry, uniforms and effects remain local. */
@RequiresApi(33)
internal object PreparedRuntimeShaders {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); task.run() },
            "Akira-shader-prepare").apply { isDaemon = true }
    }
    private val pool = PreparedShaderPool(executor, ::RuntimeShader)
    fun prepare(source: String) = pool.prepare(source)
    fun acquire(source: String): RuntimeShader = pool.acquire(source)
}
