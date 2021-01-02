package com.audiogram.videogenerator

import java.awt.AlphaComposite
import java.awt.Graphics2D
import javax.imageio.ImageIO

class AudioGramAnimator(val data: AudioGramData) {

    private val shapeRenderer = AudioGramShapeRenderer()

    fun render(g2d: Graphics2D) {
        tick()
        draw(g2d)
    }

    private fun tick() {
        for (layer in data.animatedLayers) {
            when (layer) {
                is AudiogramImage -> {

                    var model = data.animations[layer.animationModel]!!

                    model.posX?.let {
                        layer.posX = it.interpolate()
                    }

                    model.posY?.let {
                        layer.posY = it.interpolate()
                    }

                    model.opacity?.let {
                        layer.opacity = it.interpolate().toInt()
                    }

                }
                is AudiogramShape -> {

                    var model = data.animations[layer.animationModel]!!

                    model.posX?.let {
                        layer.posX = it.interpolate()
                    }

                    model.posY?.let {
                        layer.posY = it.interpolate()
                    }

                    model.opacity?.let {
                        layer.opacity = it.interpolate().toInt()
                    }
                }
                is AudiogramText -> {
                    var model = data.animations[layer.animationModel]!!

                    model.posX?.let {
                        layer.posX = it.interpolate()
                    }

                    model.posY?.let {
                        layer.posY = it.interpolate()
                    }

                    model.opacity?.let {
                        layer.opacity = it.interpolate().toInt()
                    }
                }

            }
        }
    }

    private fun draw(g2d: Graphics2D) {
        for (layer in data.animatedLayers) {
            when (layer) {
                is AudiogramImage -> {
                    var source = ImageIO.read(AudioGramFileManager.getResource(layer.file))

                    if (layer.width != 0.0 || layer.height != 0.0) {
                        source = AudioGramRenderer.fastResizeImage(source, layer.width!!, layer.height!!)
                    }
                    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (layer.opacity!! / 100f))
                    g2d.drawImage(source, null, layer.posX!!.toInt(), layer.posY!!.toInt())
                    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)
                }
                is AudiogramShape -> {
                    if (layer.shapeType != AudioGramShapeType.SVG) shapeRenderer.drawBasicShape(layer, g2d)
                    else shapeRenderer.drawVectorShape(layer, g2d)
                }
                is AudiogramText -> {
                    AudioGramRenderer.drawText(layer, g2d)
                }
            }
        }
    }


}