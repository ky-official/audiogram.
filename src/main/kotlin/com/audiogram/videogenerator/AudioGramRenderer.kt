package com.audiogram.videogenerator

import com.audiogram.videogenerator.utility.ShadowFactory
import com.audiogram.videogenerator.utility.TextFormat
import com.audiogram.videogenerator.utility.TextRenderer
import com.jhlabs.image.GrayscaleFilter
import com.jhlabs.image.NoiseFilter
import com.twelvemonkeys.image.ConvolveWithEdgeOp
import com.xuggle.mediatool.IMediaWriter
import org.imgscalr.Scalr
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.highgui.HighGui
import org.opencv.imgproc.Imgproc
import java.awt.*
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.*
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.DataBufferByte
import java.awt.image.Kernel
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.*


class AudioGramRenderer(private val freqAmpData: ArrayList<FloatArray>, private val sigAmpData: ArrayList<FloatArray>, private val data: AudioGramData, private val writer: IMediaWriter) {

    suspend fun start() {

        loadOpenCvLibraries()

        var fps = 30.0
        if (data.meta.video.optimisation!!) fps = 24.0

        val startTime = System.currentTimeMillis()
        val points = freqAmpData.size
        var index = 1
        var progress = 0
        var currentPoint = 0

        val staticImage = createStaticImage(data)
        var alphaBuffer = BufferedImage(data.meta.video.width!!.toInt(), data.meta.video.height!!.toInt(), BufferedImage.TYPE_3BYTE_BGR)
        val g2d = alphaBuffer.createGraphics().also { applyQualityRenderingHints(it) }

        var audioGramFrameGrabber: AudioGramFrameGrabber? = null
        var audioGramAnimator: AudioGramAnimator? = null

        val plotters = AudioGramPlotter().getPlotters(data)
        val effectsManager = EffectsManager(data)

        if (data.isBackgroundInitialized) audioGramFrameGrabber = AudioGramFrameGrabber(data, fps.toInt())
        if (data.animatedLayers.isNotEmpty()) audioGramAnimator = AudioGramAnimator(data)

        while (AudioGramTaskManager.taskIsRunning(data.id)) {

            Thread.sleep(0)
            if (currentPoint < points) {


                g2d.clearRect(0, 0, data.meta.video.width!!.toInt(), data.meta.video.height!!.toInt())
                audioGramFrameGrabber?.let { g2d.drawImage(it.grabNext(), null, data.background.posX!!, data.background.posY!!) }

                g2d.drawRenderedImage(staticImage, null)
                audioGramAnimator?.let { it.render(g2d) }
                effectsManager.render(freqAmpData, currentPoint, g2d)

                for (plotter in plotters) {
                    if (plotter.waveform.type == AudioGramWaveformType.SAD)
                        plotter.plot(sigAmpData, currentPoint, g2d)
                    else
                        plotter.plot(freqAmpData, currentPoint, g2d)
                }
                ////------------ progress logic -----------\\\\

                val trackProgress = (currentPoint / points.toDouble()) * 100

                if (trackProgress.roundToInt() != progress) {

                    println("task with id:${data.id} at $progress%")
                    progress = trackProgress.roundToInt()

                }
                if (data.meta.tracker.display!!) renderTrackProgress(trackProgress, g2d, data.meta.tracker)

                currentPoint++
                index++

                if (data.meta.video.optimisation!!) {
                    writer.encodeVideo(0, fastResizeImage(alphaBuffer, 0.5), ((1000000000.0 / fps) * index).roundToLong(), TimeUnit.NANOSECONDS)
                } else {
                    writer.encodeVideo(0, alphaBuffer, ((1000000000.0 / fps) * index).roundToLong(), TimeUnit.NANOSECONDS)
                }
                alphaBuffer.flush()


            } else break
        }
        writer.close()
        writer.flush()
        Runtime.getRuntime().gc()
        System.gc()
        // AudioGramDBManager.updateProgress(data.id, 100)
        // AudioGramDBManager.updateStatus(data.id, "FINISHED")
        println("Render completed in ${(System.currentTimeMillis() - startTime) / 1000.0} secs")

    }

    private fun renderTrackProgress(percent: Double, g2d: Graphics2D, meta: AudioTracker) {
        when (meta.type) {
            AudioGramAudioTrackerType.HORIZONTAL_BAR -> {
                val bar = RoundRectangle2D.Double(meta.posX!!, meta.posY!!, meta.length!! * (percent / 100), 10.0, 0.0, 0.0)
                val color = Color.decode(meta.fill)
                val opacity = (255 * (meta.opacity!! / 100.0)).toInt()

                g2d.color = Color(color.red, color.green, color.blue, opacity)
                g2d.fill(bar)
            }
        }
    }

    private fun createStaticImage(data: AudioGramData): BufferedImage {

        val shapeRenderer = AudioGramShapeRenderer()
        val layers: List<Layer> = data.staticLayers.sortedWith(compareBy { it.zIndex })
        val bufferedImage = BufferedImage(data.meta.video.width!!.toInt(), data.meta.video.height!!.toInt(), BufferedImage.TYPE_INT_ARGB)
        val g2d = bufferedImage.createGraphics()
        applyQualityRenderingHints(g2d)


        for (layer in layers) {

            when (layer) {
                is AudiogramImage -> {
                    var source = ImageIO.read(AudioGramFileManager.getResource(layer.file))
                    if (layer.width != 0.0 || layer.height != 0.0) {
                        source = Scalr.resize(source, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, layer.width!!.toInt(), layer.height!!.toInt(), Scalr.OP_ANTIALIAS)
                    }

                    if (layer.imageEffect != AudioGramImageEffect.NONE) {
                        when (layer.imageEffect) {

                            AudioGramImageEffect.BLUR -> {
                                source = blurImage(source)
                            }
                            AudioGramImageEffect.MONOCHROME -> {
                                var effect = GrayscaleFilter()
                                val dest = effect.createCompatibleDestImage(source, ColorModel.getRGBdefault())
                                effect.filter(source, dest)
                                source = dest
                                dest.flush()
                            }
                            AudioGramImageEffect.JITTER -> {
                                var effect = NoiseFilter()
                                val dest = effect.createCompatibleDestImage(source, ColorModel.getRGBdefault())
                                effect.filter(source, dest)
                                source = dest
                                dest.flush()
                            }
                        }
                    }
                    if (layer.filter != AudioGramFilterType.NONE) {
                        when (layer.filter) {
                            AudioGramFilterType.SCREEN -> {
                                screenImage(source, layer.filterFill!!)
                            }

                        }
                    }
                    when (layer.align) {
                        AudioGramImageAlign.CENTER -> layer.posX = (data.meta.video.width!! - source.width) / 2
                        AudioGramImageAlign.RIGHT -> layer.posX = (data.meta.video.width!! - source.width) * 3 / 4
                        AudioGramImageAlign.LEFT -> layer.posX = (data.meta.video.width!! - source.width) / 4
                    }
                    if (layer.mask != AudioGramMaskType.NONE) {
                        when (layer.mask) {
                            AudioGramMaskType.CIRCLE -> {
                                source = maskToCircle(source)
                            }
                            AudioGramMaskType.SQUARE -> {

                            }
                        }
                    }
                    if (layer.transform != null && layer.transform != "none") {
                        val degree = layer.transform!!.substringAfterLast(":").trim().toDouble()
                        source = rotateImage(source, degree)
                    }

                    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (layer.opacity!! / 100f))
                    g2d.drawImage(source, null, layer.posX!!.toInt(), layer.posY!!.toInt())
                    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)

                    if (layer.frame != AudioGramFrameType.NONE && layer.mask != AudioGramMaskType.CIRCLE) {
                        val frameColor = Color.decode(layer.frameColor)
                        var frameWidth = 0f
                        when (layer.frame) {
                            AudioGramFrameType.THIN -> {
                                frameWidth = 2f
                            }
                            AudioGramFrameType.NORMAL -> {
                                frameWidth = 5f
                            }
                            AudioGramFrameType.SOLID -> {
                                frameWidth = 10f
                            }
                        }
                        g2d.color = frameColor
                        g2d.stroke = BasicStroke(frameWidth)
                        g2d.drawRect(layer.posX!!.roundToInt() + frameWidth.toInt() / 2, layer.posY!!.roundToInt(), source.width, source.height)
                    }
                }
                is AudiogramText -> {
                    drawText(layer, g2d)
                }
                is AudiogramShape -> {
                    if (layer.shapeType != AudioGramShapeType.SVG)
                        shapeRenderer.drawBasicShape(layer, g2d)
                    else
                        shapeRenderer.drawVectorShape(layer, g2d)
                }
            }

        }

        return bufferedImage
    }

    private fun screenImage(source: BufferedImage, fill: String) {

        val g2d = source.createGraphics()
        val screen = Rectangle(0, 0, source.width, source.height)
        val fill = Color.decode(fill)
        val opacity = (255 * (50 / 100.0)).toInt()

        g2d.color = Color(fill.red, fill.green, fill.blue, opacity)
        g2d.fill(screen)
    }

    private fun rotateImage(img: BufferedImage, angle: Double): BufferedImage {

        val rads = Math.toRadians(angle)
        val sin = abs(sin(rads))
        val cos = abs(cos(rads))
        val w = img.width
        val h = img.height
        val newWidth = floor(w * cos + h * sin).toInt()
        val newHeight = floor(h * cos + w * sin).toInt()

        val rotated = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)

        val at = AffineTransform()
        val x = w / 2
        val y = h / 2
        at.rotate(rads, x.toDouble(), y.toDouble())

        val g2d = rotated.createGraphics()
        applyQualityRenderingHints(g2d)
        g2d.transform = at
        g2d.drawRenderedImage(img, null)
        g2d.dispose()

        return rotated
    }

    private fun blurFilter(radius: Int, horizontal: Boolean): ConvolveWithEdgeOp {
        if (radius < 1) {
            throw IllegalArgumentException("Radius must be >= 1")
        }

        val size = radius * 2 + 1
        val data = FloatArray(size)

        val sigma = radius / 3.0f
        val twoSigmaSquare = 2.0f * sigma * sigma
        val sigmaRoot = sqrt(twoSigmaSquare * Math.PI).toFloat()
        var total = 0.0f

        for (i in -radius..radius) {
            val distance = (i * i).toFloat()
            val index = i + radius
            data[index] = exp((-distance / twoSigmaSquare).toDouble()).toFloat() / sigmaRoot
            total += data[index]
        }

        for (i in data.indices) {
            data[i] /= total
        }

        var kernel: Kernel?
        kernel = if (horizontal) {
            Kernel(size, 1, data)
        } else {
            Kernel(1, size, data)
        }
        return ConvolveWithEdgeOp(kernel, ConvolveWithEdgeOp.EDGE_REFLECT, null)
    }

    private fun blurImage(bufferedImage: BufferedImage): BufferedImage {
        var bi = blurFilter(200, true).filter(bufferedImage, null)
        bi = blurFilter(200, false).filter(bi, null)
        return bi
    }

    private fun maskToCircle(img: BufferedImage): BufferedImage {

        val width = img.width
        val height = img.height
        var diameter = 0
        var oval: Area?
        diameter = if (width > height || width == height) {
            height
        } else {
            width
        }
        oval = if (width > height) {
            Area(Ellipse2D.Double((width - diameter.toDouble()) / 2, 0.0, diameter.toDouble(), diameter.toDouble()))
        } else {
            Area(Ellipse2D.Double((width - diameter.toDouble()) / 2, 0.0, diameter.toDouble(), diameter.toDouble()))
        }
        var masked = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = masked.createGraphics()
        applyQualityRenderingHints(g2d)
        g2d.clip(oval)
        g2d.drawRenderedImage(img, null)
        g2d.dispose()
        return masked
    }

    companion object {

        private val factory1 = ShadowFactory(5, 1f, Color.white)
        private val factory2 = ShadowFactory(5, 1f, Color.white)
        private var loadedAppFont = false

        fun applyQualityRenderingHints(g2d: Graphics2D) {
            g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE)
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        }

        /*  fun loadApplicationFonts() {

              if (!loadedAppFont) {
                  try {
                      val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
                      val storage = StorageOptions.newBuilder().setProjectId("audiogram-292422").build().service
                      val blobs = storage.list("audiogram_resources", Storage.BlobListOption.prefix("fonts/"))

                      for (f in blobs.iterateAll()) {
                          if (f.name.substringAfter("/") != "") {
                              var file = File("${AudioGramFileManager.ROOT}/${f.name}")
                              file.parentFile.mkdirs()
                              file.createNewFile()
                              f.downloadTo(file.toPath())
                              graphicsEnvironment.registerFont(Font.createFont(Font.TRUETYPE_FONT, file))
                          }
                      }
                      loadedAppFont = true
                  } catch (e: Exception) {
                      e.printStackTrace()
                  }
              }
          }*/
        fun drawText(layer: AudiogramText, g2d: Graphics2D) {
            val attributes = HashMap<TextAttribute, Any>()
            attributes[TextAttribute.POSTURE] = if (layer.fontStyle == AudioGramFontStyle.ITALIC) TextAttribute.POSTURE_OBLIQUE else TextAttribute.POSTURE_REGULAR
            attributes[TextAttribute.SIZE] = layer.fontSize!!
            attributes[TextAttribute.TRACKING] = layer.spacing!!

            when (layer.fontWeight) {
                AudioGramFontWeight.BOLD -> attributes[TextAttribute.WEIGHT] = TextAttribute.WEIGHT_BOLD
                AudioGramFontWeight.NORMAL -> attributes[TextAttribute.WEIGHT] = TextAttribute.WEIGHT_REGULAR
                AudioGramFontWeight.THIN -> attributes[TextAttribute.WEIGHT] = TextAttribute.WEIGHT_LIGHT
            }
            var color = Color.decode(layer.color)
            TextRenderer.drawString(
                    g2d,
                    layer.value,
                    Font.decode(layer.font!!).deriveFont(attributes),
                    Color(color.red, color.green, color.blue, (255 * (layer.opacity!! / 100.0)).toInt()),
                    Rectangle(layer.posX!!.toInt(), layer.posY!!.toInt(), layer.width!!, 100),
                    layer.align,
                    TextFormat.FIRST_LINE_VISIBLE
            )
        }

        fun drawCurve(points: java.util.ArrayList<Point>, path: GeneralPath, inBend: Int, outBend: Int) {

            /*control points*/
            var cpOneX: Double
            var cpOneY: Double
            var cpTwoX: Double
            var cpTwoY: Double

            path.moveTo(points[0].x, points[0].y)
            for (point in 1 until points.size) {

                val cpx = points[point].x
                val cpy = points[point].y


                if (point == 1) {

                    //sp will be the same as move coordinates

                    val spx = points[0].x
                    val spy = points[0].y

                    val npx = points[2].x
                    val npy = points[2].y

                    cpOneX = spx + (cpx - spx) / outBend
                    cpOneY = spy + (cpy - spy) / inBend

                    cpTwoX = cpx - (npx - spx) / outBend
                    cpTwoY = cpy - (npy - spy) / inBend

                    path.curveTo(cpOneX, cpOneY, cpTwoX, cpTwoY, cpx, cpy)

                } else if (point > 1 && point <= points.size - 2) {

                    var pp0x: Double
                    var pp0y: Double

                    if (point == 2) {
                        pp0x = points[0].x
                        pp0y = points[0].y
                    } else {
                        pp0x = points[point - 2].x
                        pp0y = points[point - 2].y
                    }

                    val ppx = points[point - 1].x
                    val ppy = points[point - 1].y

                    val npx = points[point + 1].x
                    val npy = points[point + 1].y

                    cpOneX = ppx + (cpx - pp0x) / outBend
                    cpOneY = ppy + (cpy - pp0y) / inBend

                    cpTwoX = cpx - (npx - ppx) / outBend
                    cpTwoY = cpy - (npy - ppy) / inBend

                    path.curveTo(cpOneX, cpOneY, cpTwoX, cpTwoY, cpx, cpy)

                } else {
                    val pp0x = points[point - 2].x
                    val pp0y = points[point - 2].y

                    val ppx = points[point - 1].x
                    val ppy = points[point - 1].y



                    cpOneX = ppx + (cpx - pp0x) / outBend
                    cpOneY = ppy + (cpy - pp0y) / inBend

                    cpTwoX = cpx - (cpx - ppx) / outBend
                    cpTwoY = cpy - (cpy - ppy) / inBend

                    path.curveTo(cpOneX, cpOneY, cpTwoX, cpTwoY, cpx, cpy)

                }
            }
        }

        fun fillGradient(g2d: Graphics2D, shape: Shape, fill1: Color, fill2: Color, fill3: Color?, mode: Int) {

            var shapePath = GeneralPath(shape)
            var x = shapePath.bounds.x.toFloat()
            var y = shapePath.bounds.y.toFloat()
            var height = shapePath.bounds.height
            var width = shapePath.bounds.width

            var gradientPaint: GradientPaint = GradientPaint(0f, 0f, fill1, 0f, 0f, fill2)

            if (fill3 == null) {

                when (mode) {
                    1 -> gradientPaint = GradientPaint(x + width / 2, y, fill1, x + width / 2, y + height, fill2)
                    2 -> gradientPaint = GradientPaint(x, y, fill1, x + width, y + height, fill2)
                    3 -> gradientPaint = GradientPaint(x + width, y, fill1, x, y + height, fill2)

                }

            } else {
                when (mode) {
                    1 -> {
                        g2d.paint = GradientPaint(x + width / 2, y, fill1, x + width / 2, y + height * 0.66f, fill2)
                        g2d.fill(shape)
                        gradientPaint = GradientPaint(x + width / 2, y + height * 0.66f, Color(fill2.red, fill2.green, fill2.blue, 0), x + width / 2, y + height, fill3)
                    }
                    2 -> {
                        g2d.paint = GradientPaint(x, y, fill1, x + width, y + height * 0.66f, fill2)
                        g2d.fill(shape)
                        gradientPaint = GradientPaint(x + width, y + height * 0.66f, Color(fill2.red, fill2.green, fill2.blue, 0), x, y + height, fill3)
                    }
                    3 -> {
                        g2d.paint = GradientPaint(x + width, y, fill1, x, y + height * 0.66f, fill2)
                        g2d.fill(shape)
                        gradientPaint = GradientPaint(x, y + height * 0.66f, Color(fill2.red, fill2.green, fill2.blue, 0), x + width, y + height, fill3)
                    }
                }
            }

            g2d.paint = gradientPaint
            g2d.fill(shape)
        }

        fun generateGlow(shape: Shape, g2: Graphics2D, color: Color, size: Int, opacity: Float) {


            val x = shape.bounds.x.toDouble()
            val y = shape.bounds.y.toDouble()

            val shape2 = GeneralPath(shape)
            shape2.transform(AffineTransform.getTranslateInstance(-x, -y))

            val buffer = BufferedImage(shape2.bounds.width, shape2.bounds.height, BufferedImage.TYPE_INT_ARGB)
            val graphics2D = buffer.createGraphics()

            graphics2D.color = color
            graphics2D.stroke = BasicStroke(5f)
            graphics2D.fill(shape2)

            factory1.color = Color.white
            factory1.opacity = opacity
            factory1.size = size

            factory2.color = color
            factory2.size = size

            val glowLayer = factory2.createShadow(AudioGramRenderer.factory1.createShadow(buffer))
            val deltaX = x - (glowLayer.width - shape.bounds.width) / 2.0
            val deltaY = y - (glowLayer.height - shape.bounds.height) / 2.0

            g2.drawImage(glowLayer, AffineTransform.getTranslateInstance(deltaX, deltaY), null)

        }

        fun loadOpenCvLibraries() {
            try {
                val osName = System.getProperty("os.name")
                var opencvpath = System.getProperty("user.dir")
                if (osName.startsWith("Windows")) {
                    val bitness = System.getProperty("sun.arch.data.model").toInt()
                    opencvpath = if (bitness == 32) {
                        "$opencvpath\\opencv\\x86\\"
                    } else if (bitness == 64) {
                        "$opencvpath\\opencv\\x64\\"
                    } else {
                        "$opencvpath\\opencv\\x86\\"
                    }
                } else if (osName == "Mac OS X") {
                    opencvpath += "Your path to .dylib"
                }
                System.load(opencvpath + Core.NATIVE_LIBRARY_NAME + ".dll")
            } catch (e: Exception) {
                throw RuntimeException("Failed to load opencv native library", e)
            }
        }

        fun fastResizeImage(sourceIn: BufferedImage, width: Double, height: Double): BufferedImage {
            val source = convertToType(sourceIn, BufferedImage.TYPE_3BYTE_BGR)
            val scale: Double
            val scaledWidth: Double
            val scaledHeight: Double

            if (width >= height) {
                scale = width / source.width
                scaledWidth = width
                scaledHeight = source.height * scale
            } else {
                scale = height / source.height
                scaledHeight = height
                scaledWidth = source.width * scale
            }
            val pixels = (source.raster.dataBuffer as DataBufferByte).data
            val matImg = Mat(source.height, source.width, CvType.CV_8UC3)
            matImg.put(0, 0, pixels)

            val resizeImage = Mat()
            val sz = Size(scaledWidth, scaledHeight)

            Imgproc.resize(matImg, resizeImage, sz)
            return HighGui.toBufferedImage(resizeImage) as BufferedImage
        }

        fun fastResizeImage(sourceIn: BufferedImage, scale: Double): BufferedImage {
            val source = convertToType(sourceIn, BufferedImage.TYPE_3BYTE_BGR)
            val scaledWidth: Double
            val scaledHeight: Double


            scaledWidth = source.width * scale
            scaledHeight = source.height * scale

            val pixels = (source.raster.dataBuffer as DataBufferByte).data
            val matImg = Mat(source.height, source.width, CvType.CV_8UC3)
            matImg.put(0, 0, pixels)

            val resizeImage = Mat()
            val sz = Size(scaledWidth, scaledHeight)

            Imgproc.resize(matImg, resizeImage, sz)
            return HighGui.toBufferedImage(resizeImage) as BufferedImage
        }

        private fun convertToType(sourceImage: BufferedImage, targetType: Int): BufferedImage {

            val image: BufferedImage
            if (sourceImage.type == targetType) return sourceImage
            else {
                image = BufferedImage(sourceImage.width,
                        sourceImage.height, targetType)
                image.graphics.drawImage(sourceImage, 0, 0, null)
            }

            return image
        }

    }

}
