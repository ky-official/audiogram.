package com.audiogram.videogenerator

import com.kitfox.svg.SVGUniverse
import java.awt.*
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets


class AudioGramShapeRenderer {

    private var universe = SVGUniverse()
    private var shapeCount = 0

    fun drawBasicShape(audiogramShape: AudiogramShape, g2d: Graphics2D) {

        if (audiogramShape.shapeType != AudioGramShapeType.SVG) {
            when (audiogramShape.shapeType) {
                AudioGramShapeType.BOX -> {
                    var box = Rectangle(audiogramShape.posX!!.toInt(), audiogramShape.posY!!.toInt(), audiogramShape.width!!, audiogramShape.height!!)
                    if (audiogramShape.outline!!) {
                        g2d.color = Color.decode(audiogramShape.outlineColor)
                        g2d.stroke = BasicStroke(audiogramShape.outlineWidth!!.toFloat())
                        g2d.draw(box)


                        val color = Color.decode(audiogramShape.fill)
                        g2d.color = Color(color.red, color.green, color.blue, (255 * (audiogramShape.opacity!! / 100.0)).toInt())
                        g2d.fill(box)
                        g2d.stroke = BasicStroke(0f)
                        shapeCount++
                    } else {
                        val color = Color.decode(audiogramShape.fill)
                        g2d.color = Color(color.red, color.green, color.blue, (255 * (audiogramShape.opacity!! / 100.0)).toInt())
                        g2d.fill(box)
                        shapeCount++
                    }
                }
                AudioGramShapeType.CIRCLE -> {
                    val ellipse = Ellipse2D.Double(audiogramShape.posX!!, audiogramShape.posY!!, audiogramShape.width!!.toDouble(), audiogramShape.height!!.toDouble())
                    if (audiogramShape.outline!!) {
                        g2d.color = Color.decode(audiogramShape.outlineColor)
                        g2d.stroke = BasicStroke(audiogramShape.outlineWidth!!.toFloat())
                        g2d.draw(ellipse)
                        val color = Color.decode(audiogramShape.fill)
                        g2d.color = Color(color.red, color.green, color.blue, (255 * (audiogramShape.opacity!! / 100.0)).toInt())
                        g2d.fill(ellipse)
                        g2d.stroke = BasicStroke(0f)
                        shapeCount++
                    } else {
                        val color = Color.decode(audiogramShape.fill)
                        g2d.color = Color(color.red, color.green, color.blue, (255 * (audiogramShape.opacity!! / 100.0)).toInt())
                        g2d.fill(ellipse)
                        shapeCount++
                    }
                }
                AudioGramShapeType.LINE -> {
                    val x1 = audiogramShape.posX!!
                    val x2 = x1 + audiogramShape.width!!.toDouble()
                    val y1 = audiogramShape.posY!!
                    val y2 = y1

                    val line = Line2D.Double(x1, y1, x2, y2)
                    val color = Color.decode(audiogramShape.fill)
                    g2d.color = Color(color.red, color.green, color.blue, (255 * (audiogramShape.opacity!! / 100.0)).toInt())
                    g2d.stroke = BasicStroke(audiogramShape.outlineWidth!!.toFloat())
                    g2d.draw(line)
                    g2d.stroke = BasicStroke(0f)
                    shapeCount++
                }
            }
        } else {
            println("Invalid basic shape passed to the render")
        }
    }


    fun drawVectorShape(audiogramShape: AudiogramShape, g2d: Graphics2D) {

        val diagram = universe.getDiagram(universe.loadSVG(audiogramShape.svg!!.byteInputStream(StandardCharsets.UTF_8), "shape_${++shapeCount}"))
        val svgBuffer = BufferedImage(diagram.width.toInt(), diagram.height.toInt(), BufferedImage.TYPE_INT_ARGB)

        var g2dSvgBuffer = svgBuffer.createGraphics()
        AudioGramRenderer.applyQualityRenderingHints(g2dSvgBuffer)
        g2dSvgBuffer.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (audiogramShape.opacity!! / 100f))

        diagram.render(g2dSvgBuffer)
        g2d.drawImage(svgBuffer, null, audiogramShape.posX!!.toInt(), audiogramShape.posY!!.toInt())
    }

    override fun toString(): String {
        return "${super.toString()} :: Total number of shapes rendered: $shapeCount"
    }
}