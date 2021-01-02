package com.audiogram.videogenerator

import com.audiogram.videogenerator.utility.TextAlignment
import kotlin.math.pow
import kotlin.math.roundToInt

class AudiogramMeta {
    lateinit var video: Video
    lateinit var tracker: AudioTracker
}

class AudiogramRenderModel {
    var images: Array<AudiogramImage>? = null
    var texts: Array<AudiogramText>? = null
    var shapes: Array<AudiogramShape>? = null
    var waveforms: Array<AudiogramWaveform>? = null
    var animations: Array<AnimationModel>? = null
    var effects: Array<AudiogramEffect>? = null
    var background: AudiogramBackground? = null
    var meta: AudiogramMeta? = null
}

class AudiogramBackground {
    var type: AudiogramBackgroundType? = null
    var posX: Int? = null
    var posY: Int? = null
    var width: Double? = null
    var height: Double? = null
    var file: String? = null
}

class AudiogramShape : Layer {
    var animated: Boolean? = null
    var animationModel: String? = null
    var shapeType: AudioGramShapeType? = null
    var posX: Double? = null
    var posY: Double? = null
    var width: Int? = null
    var height: Int? = null
    val fill: String? = null
    var svg: String? = null
    var outline: Boolean? = false
    var outlineWidth: Int? = null
    var outlineColor: String? = null
    override var zIndex: Int? = null
    var opacity: Int? = null
}

class AudiogramImage : Layer {
    var animated: Boolean? = null
    var animationModel: String? = null
    var file: String? = null
    var frame: AudioGramFrameType? = null
    var frameColor: String? = null
    var width: Double? = null
    var height: Double? = null
    var mask: AudioGramMaskType? = null
    var transform: String? = null
    var posX: Double? = null
    var posY: Double? = null
    override var zIndex: Int? = null
    var align: AudioGramImageAlign? = null
    var imageEffect: AudioGramImageEffect? = null
    var filter: AudioGramFilterType? = null
    var filterFill: String? = null
    var opacity: Int? = null
}

class AudiogramText : Layer {
    var animated: Boolean? = null
    var animationModel: String? = null
    var value: String? = null
    var font: String? = null
    var fontSize: Int? = null
    var fontStyle: AudioGramFontStyle? = null
    var fontWeight: AudioGramFontWeight? = null
    var color: String? = null
    var posX: Double? = null
    var posY: Double? = null
    override var zIndex: Int? = null
    var align: TextAlignment? = null
    var width: Int? = null
    var spacing: Double? = null
    var opacity: Int? = null
}

class AudiogramEffect {
    var effectType: AudioGramEffectType? = null
    var effectMode: AudioGramEffectMode? = null
    var posX: Int? = null
    var posY: Int? = null
    var width: Double? = null
    var height: Double? = null
    val fill: String? = null
}


class Video {
    val fill: String? = null
    var width: Double? = null
    var height: Double? = null
    var quality: Int? = null
}

class AudiogramWaveform {
    var type: AudioGramWaveformType? = null
    var design: AudioGramWaveformDesign? = null
    var fillMode: FillMode? = null
    var fill1: String? = null
    var fill2: String? = null
    var fill3: String? = null
    var stroke: Boolean? = null
    var strokeFill: String? = null
    var strokeWidth: Double? = null
    var strokeOpacity: Int? = null
    var width: Double? = null
    var height: Double? = null
    var posX: Double? = null
    var posY: Double? = null
    var opacity: Int? = null
}

class AnimationModel {
    var id: String? = null
    var posX: AnimationParameter? = null
    var posY: AnimationParameter? = null
    var opacity: AnimationParameter? = null
}

class AnimationParameter {
    var start: Double? = null
    var end: Double? = null
    var duration: Double? = null
    var delay: Double? = null
    var direction: AnimationDirection? = null
    var interpolation: AnimationInterpolation? = null

    private var frameRange = 0.0
    private var frameDelay = 0.0
    private var frameCount = 0
    private var absoluteProgress = 0.0
    private var range = 0.0
    private var foward = true
    private var initiated = false

    private fun init() {

        frameDelay = (delay!! * 30) / 1000
        frameRange = ((duration!! * 30) / 1000)
        range = end!! - start!!
    }

    private fun easeIn(x: Double): Double {
        return x * x * x
    }

    private fun easeOut(x: Double): Double {
        return 1 - (1 - x).pow(3)
    }

    fun interpolate(): Double {
        if (!initiated) init().also { initiated = true }
        if (frameCount >= frameDelay) absoluteProgress = (frameCount - frameDelay) / frameRange
        if (frameCount >= (frameRange + frameDelay).roundToInt()) absoluteProgress = 1.0


        var value = 0.0
        when (direction) {
            AnimationDirection.FORWARD -> {
                when (interpolation) {
                    AnimationInterpolation.EASE_IN -> value = start!! + range * easeIn(absoluteProgress)
                    AnimationInterpolation.EASE_OUT -> value = start!! + range * easeOut(absoluteProgress)
                    AnimationInterpolation.LINEAR -> value = start!! + range * absoluteProgress
                }
            }
            AnimationDirection.REVERSE -> {
                when (interpolation) {
                    AnimationInterpolation.EASE_IN -> value = end!! - range * easeIn(absoluteProgress)
                    AnimationInterpolation.EASE_OUT -> value = end!! - range * easeOut(absoluteProgress)
                    AnimationInterpolation.LINEAR -> value = end!! - range * absoluteProgress
                }
            }
            AnimationDirection.CIRCLE -> {
                when (interpolation) {
                    AnimationInterpolation.EASE_IN -> {
                        if (foward) {
                            value = start!! + range * easeIn(absoluteProgress)
                            if (absoluteProgress == 1.0) {
                                foward = false
                                frameDelay = frameCount.toDouble()
                            }
                        } else {
                            value = end!! - range * easeIn(absoluteProgress)
                            if (absoluteProgress == 1.0) {
                                foward = true
                                frameDelay = frameCount.toDouble()
                            }
                        }
                    }
                    AnimationInterpolation.EASE_OUT -> {
                        if (foward) {
                            value = start!! + range * easeOut(absoluteProgress)
                            if (absoluteProgress == 1.0) {
                                foward = false
                                frameDelay = frameCount.toDouble()
                            }
                        } else {
                            value = end!! - range * easeOut(absoluteProgress)
                            if (absoluteProgress == 1.0) {
                                foward = true
                                frameDelay = frameCount.toDouble()
                            }
                        }
                    }
                    AnimationInterpolation.LINEAR -> {
                        if (foward) {
                            value = start!! + range * absoluteProgress
                            if (absoluteProgress == 1.0) {
                                foward = false
                                frameDelay = frameCount.toDouble()
                            }
                        } else {
                            value = end!! - range * absoluteProgress
                            if (absoluteProgress == 1.0) {
                                foward = true
                                frameDelay = frameCount.toDouble()
                            }
                        }
                    }

                }
            }

        }

        frameCount++
        return value
    }
}

class AudioTracker {
    var display: Boolean? = null
    var type: AudioGramAudioTrackerType? = null
    var fill: String? = null
    var posX: Double? = null
    var posY: Double? = null
    var opacity: Int? = null
    var length: Double? = null
}

class Point(x: Double, y: Double) {
    val x = x
    val y = y
}

interface Layer {
    var zIndex: Int?

}

