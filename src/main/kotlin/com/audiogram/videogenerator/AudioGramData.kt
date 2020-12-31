package com.audiogram.videogenerator

import com.google.gson.Gson
import org.springframework.web.util.UriUtils
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
    var videoUrl: String? = null
        private set
    lateinit var meta: AudiogramMeta
        private set
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
            if (it.contentType != null && (it.contentType.substringBefore("/") == "video" || it.name == "video")) {
                val path = AudioGramFileManager.saveResource("video", this.id, it)!!
                this.videoUrl = path
            }
            if (it.contentType != null && (it.contentType.substringBefore("/") == "image" || it.name.substringBefore("_") == "image")) {
                val string = it.getHeader("content-disposition")
                val json = UriUtils.decode(string.substring(string.indexOf("{"), string.indexOf("}") + 1), "UTF-8")
                val image = Gson().fromJson(json, AudiogramImage().javaClass)
                image.url = AudioGramFileManager.saveResource("image", this.id, it)

                if (image.animated!!) {
                    this.animatedLayers.add(image)
                } else {
                    this.staticLayers.add(image)
                }
            }
            if (it.name.substringBefore("_") == "shape") {
                val shape = Gson().fromJson(String(it.inputStream.readBytes()), AudiogramShape().javaClass)

                if (shape.animated!!) {
                    this.animatedLayers.add(shape)
                } else {
                    this.staticLayers.add(shape)
                }
            }
            if (it.name.substringBefore("_") == "text") {
                val text = Gson().fromJson(String(it.inputStream.readBytes()), AudiogramText().javaClass)

                if (text.animated!!) {
                    this.animatedLayers.add(text)
                } else {
                    this.staticLayers.add(text)
                }
                //  AudioGramRenderer.loadApplicationFonts()
            }
            if (it.name.substringBefore("_") == "effect") {
                val effect = Gson().fromJson(String(it.inputStream.readBytes()), AudiogramEffect().javaClass)
                this.effects.add(effect)
            }
            if (it.name.substringBefore("_") == "animation") {
                val animation = Gson().fromJson(String(it.inputStream.readBytes()), AnimationModel().javaClass)
                this.animations[animation.id!!] = animation
            }
            if (it.name.substringBefore("_") == "waveform") {
                val waveform = Gson().fromJson(String(it.inputStream.readBytes()), AudiogramWaveform().javaClass)
                this.waveforms.add(waveform)
            }
            if (it.name == "meta") {
                val meta = Gson().fromJson(String(it.inputStream.readBytes()), AudiogramMeta().javaClass)
                this.meta = meta
            }

        }

        //  AudioGramDBManager.addTask(this.id)

    }

}