package com.example

import android.content.Context
import com.example.data.media.FactoryPack
import com.example.synth.domain.ProjectStore
import com.example.synth.engine.EngineController
import com.example.synth.engine.EnginePrefs
import com.example.synth.engine.EngineReadback
import com.example.synth.engine.EngineSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-scoped composition root: the one ProjectStore and the engine trio
 * (controller / readback / sync) live here, not in any activity, so audio
 * survives rotation and navigation (blueprint spec Part 1 §15). The engine
 * session ends with the process; a foreground service takes over ownership
 * when recording lands (M6).
 *
 * [ensureStarted] is called from the main thread (activity onCreate) and is
 * idempotent. While libdawcore.so is absent (no-compile phase) the controller
 * reports UNAVAILABLE and the app runs UI-only - by design.
 */
object DawRuntime {

    val store: ProjectStore by lazy { ProjectStore() }
    val controller: EngineController by lazy { EngineController() }
    val readback: EngineReadback by lazy { EngineReadback(controller) }

    private val sync: EngineSync by lazy { EngineSync(store, controller) }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun ensureStarted(context: Context) {
        if (started) return
        started = true
        sync.attach()               // queues the initial param sync
        controller.start(EnginePrefs())
        readback.start()
        // First run synthesizes the factory WAVs (~1 MB) - IO, off-main.
        val app = context.applicationContext
        ioScope.launch { FactoryPack.ensureInstalled(app) }
    }
}
