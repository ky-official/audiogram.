package com.audiogram.videogenerator

import com.audiogram.videogenerator.utility.GifDecoder
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameUtils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.highgui.HighGui
import org.opencv.imgproc.Imgproc
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import kotlin.math.roundToInt


class AudioGramFrameGrabber(data: AudioGramData, fpsOut: Int) {

    private var data = data
    private var grabberCounter: Int = 0
    private var outputCounter: Int = 0
    private var currentFrameIndex: Int = 0

    private var fpsIn = 0
    private var type = data.background.type
    private var mode = AudioGramScaleMode.EQUI_SCALING

    private lateinit var gifDecoder: GifDecoder
    private lateinit var scaleArray: IntArray
    private lateinit var frameGrabber: FFmpegFrameGrabber
    private var outputImage: BufferedImage? = null


    init {

        when (type) {
            AudiogramBackgroundType.GIF -> {
                gifDecoder = GifDecoder()
                gifDecoder.read(data.background.file!!)
                fpsIn = (1000.0 / gifDecoder.getDelay(1)).roundToInt()
            }
            AudiogramBackgroundType.VIDEO -> {
                frameGrabber = FFmpegFrameGrabber(File(data.background.file!!))
                frameGrabber.start()
                fpsIn = frameGrabber.videoFrameRate.roundToInt()

            }
        }
        when {
            fpsOut > fpsIn -> {
                mode = AudioGramScaleMode.UP_SCALING
                scaleArray = findIndexes(fpsOut, fpsIn)

            }
            fpsOut < fpsIn -> {
                mode = AudioGramScaleMode.DOWN_SCALING
                scaleArray = findIndexes(fpsIn, fpsOut)
            }
            else -> mode = AudioGramScaleMode.EQUI_SCALING
        }
        loadLibraries()
    }

    private fun findIndexes(n: Int, r: Int): IntArray {

        val values = IntArray(n) { 0 }
        val offset = r - 1

        for (i in 0 until r) {
            val pos = (offset + (i * n)) / r
            values[pos % n] = 1
        }
        return values
    }

    fun grabNext(): BufferedImage? {

        when (type) {
            AudiogramBackgroundType.GIF -> {
                if (grabberCounter == gifDecoder.frameCount) {
                    gifDecoder.restart()
                    grabberCounter = 0
                }

                when (mode) {
                    AudioGramScaleMode.DOWN_SCALING -> {
                        if (currentFrameIndex >= scaleArray.size - 1) {
                            currentFrameIndex = 0
                        }
                        for (i in currentFrameIndex until scaleArray.size) {
                            if (scaleArray[i] != 0) {
                                gifDecoder.grabImage()?.let { outputImage = it }
                                currentFrameIndex = i + 1
                                break
                            }
                        }
                    }
                    AudioGramScaleMode.UP_SCALING -> {
                        if (currentFrameIndex >= scaleArray.size - 1) {
                            currentFrameIndex = 0
                        }

                        if (scaleArray[currentFrameIndex] != 0) {
                            gifDecoder.grabImage()?.let { outputImage = it }
                            currentFrameIndex++
                            grabberCounter++

                        } else {
                            currentFrameIndex++
                        }
                    }
                    AudioGramScaleMode.EQUI_SCALING -> {
                        outputImage = gifDecoder.grabImage()
                        grabberCounter++
                    }
                }

            }
            AudiogramBackgroundType.VIDEO -> {
                if (grabberCounter == frameGrabber.lengthInVideoFrames) {
                    frameGrabber.restart()
                    grabberCounter = 0
                }
                when (mode) {
                    AudioGramScaleMode.EQUI_SCALING -> {
                        val frame = frameGrabber.grabImage()
                        if (frame != null)
                            outputImage = Java2DFrameUtils.toBufferedImage(frame)
                        grabberCounter++
                    }
                    AudioGramScaleMode.DOWN_SCALING -> {

                        if (currentFrameIndex >= scaleArray.size - 1) {
                            currentFrameIndex = 0
                        }
                        for (i in currentFrameIndex until scaleArray.size) {

                            if (scaleArray[i] != 0) {
                                val frame = frameGrabber.grabImage()
                                if (frame != null)
                                    outputImage = Java2DFrameUtils.toBufferedImage(frame)
                                currentFrameIndex = i + 1
                                break
                            }
                            frameGrabber.grabImage()
                        }
                    }
                    AudioGramScaleMode.UP_SCALING -> {

                        if (currentFrameIndex >= scaleArray.size - 1) {
                            currentFrameIndex = 0
                        }

                        if (scaleArray[currentFrameIndex] != 0) {
                            val frame = frameGrabber.grabImage()
                            if (frame != null)
                                outputImage = Java2DFrameUtils.toBufferedImage(frame)
                            currentFrameIndex++
                            grabberCounter++

                        } else {
                            currentFrameIndex++
                        }
                    }
                }
            }
        }


        outputCounter++
        return resizeImage(outputImage!!, data.background.width!!, data.background.height!!)
        //  return outputImage
    }

    private fun loadLibraries() {
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
            println(opencvpath)
            System.load(opencvpath + Core.NATIVE_LIBRARY_NAME + ".dll")
        } catch (e: Exception) {
            throw RuntimeException("Failed to load opencv native library", e)
        }
    }

    private fun resizeImage(sourceIn: BufferedImage, width: Double, height: Double): BufferedImage {
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

    private fun convertToType(sourceImage: BufferedImage, targetType: Int): BufferedImage {

        val image: BufferedImage
        if (sourceImage.type == targetType) image = sourceImage
        else {
            image = BufferedImage(sourceImage.width,
                    sourceImage.height, targetType)
            image.graphics.drawImage(sourceImage, 0, 0, null)
        }

        return image
    }

}