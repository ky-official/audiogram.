package com.audiogram.videogenerator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.modelmapper.internal.util.CopyOnWriteLinkedHashMap

sealed class AudioGramTaskManager {

    companion object {

        private const val MAX = 2
        private var tasks = CopyOnWriteLinkedHashMap<String, AudioGramVideoTask>().also {
            CoroutineScope(Dispatchers.Default).launch {

                while (true) {
                    delay(10)
                    var parent = launch {
                        var count = 0
                        for (task in it) {
                            launch { task.value.render();it.remove(task.key) }
                            count++
                            if (count >= MAX)
                                break
                        }
                    }
                    parent.join()
                }
            }
        }

        fun addTask(taskData: AudioGramData) {
            tasks[taskData.id] = AudioGramVideoTask(taskData)
        }

        fun cancelTask(id: String) {
            this.tasks.remove(id)
            AudioGramDBManager.updateStatus(id, "CANCELED")
        }

        fun taskIsRunning(id: String): Boolean {
            if (this.tasks[id] == null) {
                return false
            }
            return true
        }
    }
}