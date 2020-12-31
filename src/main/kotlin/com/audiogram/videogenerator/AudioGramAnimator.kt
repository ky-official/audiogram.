package com.audiogram.videogenerator

import java.awt.Graphics2D
import javax.imageio.ImageIO

class AudioGramAnimator(val data: AudioGramData, val fps: Int) {

    var frameCount = 0

    init {

    }

    fun render(g2d: Graphics2D) {
        tick()
        draw(g2d)
        frameCount++
    }

    private fun tick() {
        for (layer in data.animatedLayers) {
            when (layer) {
                is AudiogramImage -> {

                    var model = data.animations[layer.animationModel]!!


                    /*val frameRange = (model.posX!!.duration!! * fps) / 1000

                    var absoluteProgress = frameCount / frameRange
                    if (frameCount >= frameRange.roundToInt()) absoluteProgress = 1.0

                    val range = model!!.posX!!.end!!.toDouble() - model!!.posX!!.start!!.toDouble()
                    layer.posX = model!!.posX!!.start!!.toDouble() + (range * easeIn(absoluteProgress))
*/
                }
            }
        }
    }

    private fun draw(g2d: Graphics2D) {
        for (layer in data.animatedLayers) {
            when (layer) {
                is AudiogramImage -> {
                    var source = ImageIO.read(AudioGramFileManager.getResource(layer.url))
                    if (layer.width != 0.0 || layer.height != 0.0) {
                        // source = Scalr.resize(source, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, layer.width!!.toInt(), layer.height!!.toInt(), Scalr.OP_ANTIALIAS)
                    }
                    g2d.drawImage(source, null, layer.posX!!.toInt(), layer.posY!!.toInt())
                }
            }
        }
    }

    private fun easeIn(x: Double): Double {
        return x * x * x
    }

}