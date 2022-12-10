package watermark

import java.awt.Color
import java.awt.Point
import java.awt.Transparency
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess


fun main() {
    val image = checkImage("image")
    val watermark = checkImage("watermark")

    checkImagesDimensions(image, watermark)

    when (watermark.transparency) {
        // watermark has alpha channel
        Transparency.TRANSLUCENT -> blendingImagesWithAlphaChannel(image, watermark)
        else -> blendingImagesWithTransparencyColor(image, watermark)
    }
}

fun blendingImagesWithTransparencyColor(image: BufferedImage, watermark: BufferedImage) {
    println("Do you want to set a transparency color?")
    val useTransparencyColor = readln().lowercase() == "yes"

    if (useTransparencyColor) {
        println("Input a transparency color ([Red] [Green] [Blue]):")

        // Reading transparency color in RGB format and checking if input is correct
        val rgb = readln().split(" ")
        if (rgb.size != 3) {
            println("The transparency color input is invalid.")
            exitProcess(0)
        }
        val (red, green, blue) = rgb.map { it.toInt() }

        if (intArrayOf(red, green, blue).any { it !in 0..255 }) {
            exitProgram("The transparency color input is invalid.")
        }

        val weight: Int = getWatermarkPercentage()
        val point = getWatermarkPosition(image, watermark)
        val fileName = getOutputFileName()

        blendingImages(image, watermark, weight, fileName, point, Color(red, green, blue))
    }
}

fun blendingImagesWithAlphaChannel(image: BufferedImage, watermark: BufferedImage) {
    println("Do you want to use the watermark's Alpha channel?")
    val useAlpha = readln().lowercase() == "yes"

    val weight = getWatermarkPercentage()
    val point = getWatermarkPosition(image, watermark)
    val fileName = getOutputFileName()

    when (useAlpha) {
        true -> blendingImages(image, watermark, weight, fileName, point, useAlpha = true)
        else -> blendingImages(image, watermark, weight, fileName, point)
    }
}

fun checkImage(name: String): BufferedImage {
    println(
        when (name) {
            "file" -> "Input the image filename:"
            "watermark" -> "Input the watermark image filename:"
            else -> exitProgram("Incorrect image name input")
        }
    )
    val file = File(readln())
    if (file.exists()) {
        val image = ImageIO.read(file)
        if (image.colorModel.numColorComponents != 3) {
            println("The number of $name color components isn't 3.")
            exitProcess(0)
        }
        if (image.colorModel.pixelSize != 24 && image.colorModel.pixelSize != 32) {
            println("The $name isn't 24 or 32-bit.")
            exitProcess(0)
        }
        require(image != null)
        return image
    } else {
        print("The file ${file.path} doesn't exist.")
        exitProcess(0)
    }
}

fun checkImagesDimensions(image: BufferedImage, watermark: BufferedImage) {
    if (image.width < watermark.width || image.height < watermark.height) {
        exitProgram("The watermark's dimensions are larger.")
    }
}

fun blendingImages(
    image: BufferedImage,
    watermark: BufferedImage,
    weight: Int,
    fileName: String,
    watermarkPosition: Point? = null,
    transparencyColor: Color? = null,
    useAlpha: Boolean = false
) {
    var newImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until newImage.width) {
        for (y in 0 until newImage.height) {
            newImage =
                setPixel(watermark, image, newImage, weight, x, y, watermarkPosition, transparencyColor, useAlpha)
        }
    }
    val resultingImage = File(fileName)

    //  save resulting image to file
    ImageIO.write(newImage, fileName.substring(fileName.length - 3, fileName.length), resultingImage)
    println("The watermarked image $fileName has been created.")
}


fun getOutputFileName(): String {
    println("Input the output image filename (jpg or png extension):")
    val fileName = readln()

    fun checkFileNameFormat(substr: String, format: List<String>) {
        if (format.all { substr != it }) exitProgram("The output file extension isn't \"jpg\" or \"png\".")
    }
    checkFileNameFormat(fileName.substring(fileName.length - 4, fileName.length), listOf(".jpg", ".png"))

    return fileName
}

fun getWatermarkPercentage(): Int {
    println("Input the watermark transparency percentage (Integer 0-100):")
    return when (val percentage = readln().toIntOrNull()) {
        null -> {
            exitProgram("The transparency percentage isn't an integer number.")
            0
        }

        !in 0..100 -> {
            exitProgram("The transparency percentage is out of range.")
            0
        }

        else -> percentage
    }
}

fun getColorByLinearCombination(weight: Int, w: Color, i: Color): Color {
    return Color(
        (weight * w.red + (100 - weight) * i.red) / 100,
        (weight * w.green + (100 - weight) * i.green) / 100,
        (weight * w.blue + (100 - weight) * i.blue) / 100
    )
}

fun exitProgram(message: String = "") {
    println(message)
    exitProcess(0)
}


fun getWatermarkPosition(image: BufferedImage, watermark: BufferedImage): Point? {
    println("Choose the position method (single, grid):")
    val position = readln().lowercase()
    return when (position) {
        "single" -> {
            val diffX: Int = image.width - watermark.width
            val diffY: Int = image.height - watermark.height

            println("Input the watermark position ([x 0-$diffX] [y 0-$diffY]):")
            val (x_pos: Int, y_pos: Int) = try {
                readln().split(" ").map { it.toInt() }
            } catch (ex: Exception) {
                exitProgram("The position input is invalid.")
                throw Exception() // TODO() not necessary, how to remove this?
            }
            if (x_pos !in 0..diffX || y_pos !in 0..diffY) {
                exitProgram("The position input is out of range.")
            }
            Point(x_pos, y_pos)
        }
        "grid" -> {
            null
        }
        else -> {
            exitProgram("The position method input is invalid.")
            null
        }
    }
}

fun checkPixelPosition(watermarkPosition: Point, watermark: BufferedImage, currPoint: Point): Boolean {
    return currPoint.x in watermarkPosition.x until watermarkPosition.x + watermark.width &&
            currPoint.y in watermarkPosition.y until watermarkPosition.y + watermark.height
}

fun setPixel(
    watermark: BufferedImage,
    image: BufferedImage,
    newImage: BufferedImage,
    weight: Int,
    xPos: Int,
    yPos: Int,
    watermarkPosition: Point? = null,
    transparencyColor: Color? = null,
    useAlpha: Boolean = false
): BufferedImage {

    // getting current watermark pixel depending on it's properties
    val w =
        if (watermarkPosition is Point && checkPixelPosition(watermarkPosition, watermark, Point(xPos, yPos))) {
            Color(watermark.getRGB(xPos - watermarkPosition.x, yPos - watermarkPosition.y), useAlpha)
        } else if (watermarkPosition == null) Color(
            watermark.getRGB(
                xPos % watermark.width,
                yPos % watermark.height
            ), useAlpha
        )
        else Color(image.getRGB(xPos, yPos), useAlpha)

    // getting current image pixel
    val i = Color(image.getRGB(xPos, yPos), useAlpha)
    val color = getColorByLinearCombination(weight, w, i)

    // swapping pixel with necessary restrictions
    when (transparencyColor) {

        is Color -> {
            if (w != transparencyColor && w.alpha == 255) {
                newImage.setRGB(xPos, yPos, color.rgb)
            } else {
                newImage.setRGB(xPos, yPos, Color(image.getRGB(xPos, yPos)).rgb)
            }
        }
        null -> {
            if (w.alpha == 255) {
                newImage.setRGB(xPos, yPos, color.rgb)
            } else newImage.setRGB(xPos, yPos, Color(image.getRGB(xPos, yPos)).rgb)
        }
    }
    return newImage
}