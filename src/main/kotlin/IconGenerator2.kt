import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import java.util.Base64

fun drawGitBranchForkIcon(
filePath: String = "tryzub_icon.png",
size: Int = 512
){
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g2d = image.createGraphics()
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.color = Color(0, 0, 0, 0)
    g2d.fillRect(0, 0, size, size)

    // Фоновий колір (темно-синій)
    val blue = Color(0, 87, 184)
    g2d.color = blue
    g2d.fillRect(0, 0, size, size)

    // Параметри хексагона
    val yellow = Color(255, 221, 41)
    val hexRadius = size * 0.38
    val centerX = size / 2.0
    val centerY = size / 2.0

    // Малюємо жовтий хексагон
    val hex = Polygon()
    for (i in 0 until 6) {
        val angle = Math.PI / 3 * i - Math.PI / 2
        val x = centerX + hexRadius * Math.cos(angle)
        val y = centerY + hexRadius * Math.sin(angle)
        hex.addPoint(x.toInt(), y.toInt())
    }
    g2d.color = yellow
    g2d.fillPolygon(hex)

    // Дві вертикальні "лінії-розрізи"
    val lineColor = blue // співпадає з фоном
    val lineWidth = size * 0.074

    g2d.color = lineColor
    g2d.stroke = BasicStroke(lineWidth.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

    // Перша лінія (зліва від центру)
    val leftX = centerX - hexRadius / 3
    g2d.drawLine(leftX.toInt(), (centerY - hexRadius).toInt(), leftX.toInt(), (centerY + hexRadius).toInt())

    // Друга лінія (справа від центру)
    val rightX = centerX + hexRadius / 3
    g2d.drawLine(rightX.toInt(), (centerY - hexRadius).toInt(), rightX.toInt(), (centerY + hexRadius).toInt())

    g2d.dispose()
    ImageIO.write(image, "png", File(filePath))
}







//=============================================

fun resizeImage(sourceImagePath: String, outputPath: String, targetSize: Int) {
    try {
        // Завантажуємо оригінальне зображення
        val sourceFile = File(sourceImagePath)
        val sourceImage = ImageIO.read(sourceFile)

        // Створюємо новий BufferedImage потрібного розміру
        val resizedImage = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB)

        // Отримуємо графічний контекст
        val g2d = resizedImage.createGraphics()

        // Встановлюємо налаштування для найвищої якості масштабування
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Масштабуємо зображення
        g2d.drawImage(sourceImage, 0, 0, targetSize, targetSize, null)
        g2d.dispose()

        // Зберігаємо результат
        File(outputPath).parentFile?.mkdirs()
        ImageIO.write(resizedImage, "PNG", File(outputPath))

        println("Створено зменшену версію ${targetSize}x${targetSize}: $outputPath")
    } catch (e: Exception) {
        println("Помилка при масштабуванні зображення: ${e.message}")
        e.printStackTrace()
    }
}


/**
 * Удосконалена функція для створення іконок різних розмірів.
 * Генерує одну іконку високої якості, а потім масштабує її до інших розмірів.
 */
fun generateAllIcons() {
    // Шлях до основної іконки
    val mainIconPath = "kubernetes_manager_icon.png"
    val mainIconSize = 64

    // Спочатку генеруємо головну іконку високої якості
    drawGitBranchForkIcon(mainIconPath, mainIconSize)

    // Додаткові розміри
    val sizes = listOf(16, 32)

    // Тепер створюємо масштабовані версії з основної іконки
    for (size in sizes) {
        val outputPath = "kubernetes_manager_icon_${size}x${size}.png"
        resizeImage(mainIconPath, outputPath, size)
    }

    // Створюємо іконку розміром 1024x1024 для використання в .App Store (якщо потрібно)
    // resizeImage(mainIconPath, "kubernetes_manager_icon_1024x1024.png", 1024)

    println("Всі іконки успішно згенеровано!")
}
/**
 * Створює набір іконок для додатку для різних платформ
 */
fun generatePlatformIcons() {
    // Генеруємо основну іконку
    generateAllIcons()

    // Додаткові дії для ICO (Windows)
    createWindowsIcon()

    // Додаткові дії для ICNS (macOS)
    createMacOSIcon()

    println("Згенеровано іконки для всіх платформ")
}
/**
 * Створення ICO файлу для Windows (спрощено, на практиці потрібна додаткова бібліотека)
 */
fun createWindowsIcon() {
    // Тут мало б бути об'єднання PNG файлів у формат ICO
    // Це вимагає зовнішньої бібліотеки або утиліти командного рядка
    println("Для повноцінного створення ICO файлу потрібно використовувати додаткову бібліотеку, наприклад ImageMagick")
    println("Команда ImageMagick: convert kubernetes_manager_icon_16x16.png kubernetes_manager_icon_32x32.png kubernetes_manager_icon_48x48.png kubernetes_manager_icon_256x256.png kubernetes_manager_icon.ico")
}

/**
 * Створення ICNS файлу для macOS (спрощено, на практиці потрібна додаткова утиліта)
 */
fun createMacOSIcon() {
    // На macOS використовується утиліта iconutil для створення .icns файлів
    println("Для повноцінного створення ICNS файлу на macOS потрібно використовувати iconutil")
    println("1. Створіть папку MyIcon.iconset")
    println("2. Розмістіть PNG файли з правильними іменами:")
    println("   icon_16x16.png, icon_16x16@2x.png, icon_32x32.png, icon_32x32@2x.png, ...")
    println("3. Виконайте команду: iconutil -c icns MyIcon.iconset")
}
/**
 * Конвертує зображення іконки у Base64-представлення для вбудовування в код.
 *
 * @param imagePath шлях до файлу зображення
 * @return String Base64-представлення зображення
 */
fun imageToBase64(imagePath: String): String {
    try {
        val file = File(imagePath)
        val image = ImageIO.read(file)
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "png", outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(imageBytes)
    } catch (e: Exception) {
        println("Помилка при конвертації зображення в Base64: ${e.message}")
        e.printStackTrace()
        return ""
    }
}
fun generateIconsBase64Code(generateToFile: Boolean = false, outputFilePath: String = "IconsBase64.kt") {
    // Шлях до основної іконки
    val mainIconPath = "kubernetes_manager_icon.png"
    val mainIconSize = 64

    // Якщо іконки ще не згенеровані, генеруємо їх
    if (!File(mainIconPath).exists()) {
        drawGitBranchForkIcon(mainIconPath, mainIconSize)

        // Додаткові розміри
        val sizes = listOf(16, 32)

        // Створюємо масштабовані версії з основної іконки
        for (size in sizes) {
            val outputPath = "kubernetes_manager_icon_${size}x${size}.png"
            resizeImage(mainIconPath, outputPath, size)
        }
    }

    val sb = StringBuilder()
    sb.appendLine("/**")
    sb.appendLine(" * Файл з Base64 представленнями іконок додатку для вбудовування в код.")
    sb.appendLine(" * Автоматично згенеровано за допомогою IconGenerator.")
    sb.appendLine(" */")
    sb.appendLine("")
    sb.appendLine("object IconsBase64 {")

    // Генеруємо Base64 для основної іконки
    val mainIconBase64 = imageToBase64(mainIconPath)
    sb.appendLine("    /** Base64 основної іконки 64x64. */")
    sb.appendLine("    const val ICON_64 = \"$mainIconBase64\"")
    sb.appendLine("")

    // Генеруємо Base64 для різних розмірів
    val sizes = listOf(16, 32)
    for (size in sizes) {
        val iconPath = "kubernetes_manager_icon_${size}x${size}.png"
        if (File(iconPath).exists()) {
            val iconBase64 = imageToBase64(iconPath)
            sb.appendLine("    /** Base64 іконки ${size}x${size}. */")
            sb.appendLine("    const val ICON_$size = \"$iconBase64\"")
            sb.appendLine("")
        }
    }

    // Додаємо допоміжні методи для декодування
    sb.appendLine("    /**")
    sb.appendLine("     * Декодує Base64-рядок у BufferedImage.")
    sb.appendLine("     * @param base64 Base64-рядок із зображенням")
    sb.appendLine("     * @return BufferedImage об'єкт зображення або null при помилці")
    sb.appendLine("     */")
    sb.appendLine("    fun decodeToImage(base64: String): java.awt.image.BufferedImage? {")
    sb.appendLine("        return try {")
    sb.appendLine("            val imageBytes = java.util.Base64.getDecoder().decode(base64)")
    sb.appendLine("            val inputStream = java.io.ByteArrayInputStream(imageBytes)")
    sb.appendLine("            javax.imageio.ImageIO.read(inputStream)")
    sb.appendLine("        } catch (e: Exception) {")
    sb.appendLine("            e.printStackTrace()")
    sb.appendLine("            null")
    sb.appendLine("        }")
    sb.appendLine("    }")
    sb.appendLine("    ")
    sb.appendLine("    /**")
    sb.appendLine("     * Отримує іконку вказаного розміру. Якщо запитаний розмір недоступний,")
    sb.appendLine("     * повертає найближчий доступний розмір або null при помилці.")
    sb.appendLine("     * @param size бажаний розмір іконки")
    sb.appendLine("     * @return BufferedImage зображення або null при помилці")
    sb.appendLine("     */")
    sb.appendLine("    fun getIcon(size: Int): java.awt.image.BufferedImage? {")
    sb.appendLine("        return when (size) {")
    sb.appendLine("            16 -> decodeToImage(ICON_16)")
    sb.appendLine("            32 -> decodeToImage(ICON_32)")
    sb.appendLine("            64 -> decodeToImage(ICON_64)")
    sb.appendLine("            else -> {")
    sb.appendLine("                // Повертаємо найближчий доступний розмір")
    sb.appendLine("                when {")
    sb.appendLine("                    size < 16 -> decodeToImage(ICON_16)")
    sb.appendLine("                    size < 32 -> decodeToImage(ICON_16)")
    sb.appendLine("                    size < 64 -> decodeToImage(ICON_32)")
    sb.appendLine("                    else -> decodeToImage(ICON_32)")
    sb.appendLine("                }")
    sb.appendLine("            }")
    sb.appendLine("        }")
    sb.appendLine("    }")
    sb.appendLine("    ")
    sb.appendLine("    /**")
    sb.appendLine("     * Встановлює іконку додатку для вікна Swing/AWT з Base64.")
    sb.appendLine("     * @param window вікно, для якого встановлюється іконка")
    sb.appendLine("     */")
    sb.appendLine("    fun setWindowIcon(window: java.awt.Window) {")
    sb.appendLine("        try {")
    sb.appendLine("            // Створюємо список іконок різних розмірів")
    sb.appendLine("            val icons = listOf(16, 32, 64)")
    sb.appendLine("                .mapNotNull { size -> getIcon(size) }")
    sb.appendLine("            ")
    sb.appendLine("            // Встановлюємо іконки для вікна")
    sb.appendLine("            if (icons.isNotEmpty()) {")
    sb.appendLine("                window.iconImages = icons")
    sb.appendLine("            }")
    sb.appendLine("        } catch (e: Exception) {")
    sb.appendLine("            println(\"Помилка при встановленні іконки: \${e.message}\")")
    sb.appendLine("        }")
    sb.appendLine("    }")
    sb.appendLine("}")

    val generatedCode = sb.toString()

    // Виводимо результат у консоль
    println("\n--- Початок згенерованого коду ---")
    println(generatedCode)
    println("--- Кінець згенерованого коду ---\n")

    // Зберігаємо у файл, якщо потрібно
    if (generateToFile) {
        try {
            File(outputFilePath).writeText(generatedCode)
            println("Код успішно збережено у файл: $outputFilePath")
        } catch (e: Exception) {
            println("Помилка при збереженні коду у файл: ${e.message}")
        }
    }
}

fun generateAllIconsWithBase64() {
    // Генеруємо всі іконки
    generateAllIcons()
    // Генеруємо Base64-код
    generateIconsBase64Code(true)
    println("Всі іконки і Base64-код успішно згенеровано!")
}

// Для перегляду результату
fun main() {
    generateAllIconsWithBase64()
    //drawGitBranchForkIcon()
}