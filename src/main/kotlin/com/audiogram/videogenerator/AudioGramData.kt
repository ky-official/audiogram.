package com.audiogram.videogenerator

import com.google.gson.Gson
import javax.servlet.http.Part

/*
* Manages the data and objects that are received from the server.
* Stores blob objects in a temp storage.
* Handles get calls on data ( serves as a data object).
* */
class AudioGramData {

    lateinit var id: String
        private set
    lateinit var audioUrl: String
        private set
    lateinit var meta: AudiogramMeta
        private set
    lateinit var background: AudiogramBackground
        private set
    val isBackgroundInitialized get() = this::background.isInitialized
    var staticLayers: ArrayList<Layer> = ArrayList()
        private set
    var animatedLayers: ArrayList<Layer> = ArrayList()
        private set
    var waveforms: ArrayList<AudiogramWaveform> = ArrayList()
        private set
    var effects: ArrayList<AudiogramEffect> = ArrayList()
        private set
    var animations: LinkedHashMap<String, AnimationModel> = LinkedHashMap()
        private set
    var trackLength: Double? = null


    fun initialize(data: Collection<Part>) {

        try {

            data.forEach {
                if (it.name == "id") {
                    this.id = String(it.inputStream.readBytes())
                    AudioGramFileManager.createTaskDirectory(id)
                    return@forEach
                }
            }
            data.forEach { it ->

                if (it.contentType != null && (it.contentType.substringBefore("/") == "audio" || it.name == "audio")) {
                    val path = AudioGramAudioConverter.convert(AudioGramFileManager.saveResource("audio", this.id, it)!!)
                    this.audioUrl = path!!
                }
                if (it.contentType != null && (it.contentType.substringBefore("/") == "video" || it.name == "background")) {
                    AudioGramFileManager.saveResource("background", this.id, it)!!
                }
                if (it.contentType != null && it.name == "image") {
                    AudioGramFileManager.saveResource("image", this.id, it)
                }
                if (it.name.substringBefore("_") == "model") {

                    val model = Gson().fromJson(String(it.inputStream.readBytes()), AudiogramRenderModel().javaClass)

                    model.animations?.let {
                        it.forEach { animation ->
                            this.animations[animation.id!!] = animation
                        }
                    }
                    model.images?.let {
                        it.forEach { image ->
                            image.file = "${AudioGramFileManager.ROOT}/tasks/task_${this.id}/resources/images/${image.file}"
                            if (image.animated!!) this.animatedLayers.add(image)
                            else this.staticLayers.add(image)
                        }

                    }
                    model.shapes?.let {
                        it.forEach { shapes ->
                            shapes
                            if (shapes.animated!!) this.animatedLayers.add(shapes)
                            else this.staticLayers.add(shapes)
                        }
                    }
                    model.texts?.let {
                        it.forEach { text ->
                            text
                            if (text.animated!!) this.animatedLayers.add(text)
                            else this.staticLayers.add(text)
                        }
                    }
                    model.background?.let { it.file = "${AudioGramFileManager.ROOT}/tasks/task_$id/resources/background/${it.file}";this.background = it }
                    model.effects?.let { this.effects.addAll(it) }
                    model.waveforms?.let { this.waveforms.addAll(it); this.meta = model.meta!! }


                    println("initialization completed")
                    //AudioGramRenderer.loadApplicationFonts()
                }
            }
            //AudioGramDBManager.addTask(this.id)
        } catch (e: Exception) {
            e.printStackTrace()
            throw AudioGramException("Missing files or invalid render model:  ${e.message}")
        }
    }

}