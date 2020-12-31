package com.audiogram.videogenerator

import com.audiogram.videogenerator.utility.TextAlignment

class AudiogramMeta {
    lateinit var video: Video
    lateinit var tracker: AudioTracker
}

class AudiogramRenderModel {
    var images: Array<AudiogramImage>? = null
    var texts: Array<AudiogramText>? = null
    var shapes: Array<AudiogramShape>? = null
    var waveforms: Array<AudiogramWaveform>? = null
    var effects: Array<AudiogramEffect>? = null
    var background: Array<AudiogramBackground>? = null
    var meta: Array<AudiogramMeta>? = null
}

class AudiogramBackground {
    var type: AudiogramBackgroundType? = null
    var posX: Int? = null
    var posY: Int? = null
    var width: Int? = null
    var height: Int? = null
    var url: String? = null
}

class AudiogramShape : Layer {
    var animated: Boolean? = null
    var animationModel: String? = null
    var shapeType: AudioGramShapeType? = null
    var posX: Int? = null
    var posY: Int? = null
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
    var url: String? = null
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
    var posX: Int? = null
    var posY: Int? = null
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
    var scale: AnimationParameter? = null
    var opacity: AnimationParameter? = null
    var rotation: AnimationParameter? = null
    var fill: AnimationParameter? = null
}

class AnimationParameter {
    var start: String? = null
    var end: String? = null
    var duration: Double? = null
    var delay: Double? = null
    var direction: AnimationDirection? = null
    var interpolation: AnimationInterpolation? = null
    var delta: Double? = null
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

