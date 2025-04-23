
import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Створює м'який зіркоподібний полігон з заданими параметрами
 *
 * @param centerX центр зірки по осі X
 * @param centerY центр зірки по осі Y
 * @param outerRadius зовнішній радіус зірки
 * @param innerRadius внутрішній радіус зірки (визначає гостроту кутів)
 * @param points кількість променів зірки
 * @return Polygon об'єкт, що представляє зірку
 */
fun createSoftStarPolygon(
    centerX: Int,
    centerY: Int,
    outerRadius: Int,
    innerRadius: Int = (outerRadius * 0.7).toInt(),
    points: Int = 5
): Polygon {
    val polygon = Polygon()

    // Використовуємо більше точок для більш виразної форми зірки
    val numPoints = points + Random.nextInt(0, 2) // Від points до points+1 променів

    for (i in 0 until numPoints * 2) {  // Множимо на 2, бо у зірки внутрішні і зовнішні точки чергуються
        val angle = Math.PI * i / numPoints

        // Чергуємо зовнішній і внутрішній радіуси для створення кутів
        // Збільшуємо внутрішній радіус для більш м'яких кутів
        val currentRadius = if (i % 2 == 0) {
            // Зовнішні точки з випадковою варіацією для унікальності
            outerRadius * (0.9 + Random.nextDouble(0.0, 0.15))
        } else {
            // Внутрішні точки (створюють м'якші куточки)
            innerRadius * (0.85 + Random.nextDouble(0.0, 0.25))
        }

        val x = centerX + (currentRadius * cos(angle)).toInt()
        val y = centerY + (currentRadius * sin(angle)).toInt()
        polygon.addPoint(x, y)
    }

    return polygon
}

/**
 * Створює набір кольорів для іконки Kubernetes
 */
data class KubernetesColors(
    // Основні кольори
    val k8sBlue: Color,
    val k8sLightBlue: Color,
    val k8sDarkBlue: Color,

    // Акцентні кольори у групах
    val accentColors: Array<Color>,

    // Кольорові групи
    val colorGroups: List<IntRange>
) {
    companion object {
        /**
         * Створює набір кольорів для Kubernetes іконки
         */
        fun createColors(): KubernetesColors {
            // Синя палітра (основний колір)
            val k8sBlue = Color(33, 125, 223)         // Основний синій, близький до оригінального
            val k8sLightBlue = Color(63, 155, 243)    // Світліший синій
            val k8sDarkBlue = Color(23, 85, 183)      // Темніший синій

            // Акцентні кольори для полігонів з трьома групами кольорів
            val accentColors = arrayOf(
                // Червоні відтінки (0-2)
                Color(200, 50, 50),     // Яскраво-червоний
                Color(170, 60, 60),     // Темно-червоний
                Color(230, 90, 70),     // Червоно-оранжевий

                // Жовті відтінки (3-4)
                Color(240, 180, 20),    // Золотисто-жовтий
                Color(255, 210, 60),    // Яскраво-жовтий

                // Зелені відтінки (5-7)
                Color(50, 160, 70),     // Насичений зелений
                Color(100, 180, 50),    // Яскраво-зелений
                Color(70, 140, 80)      // Темно-зелений
            )

            // Визначаємо кольорові групи для вибору контрастних кольорів
            val colorGroups = listOf(
                0..2,  // Червоні (індекси 0-2)
                3..4,  // Жовті (індекси 3-4)
                5..7   // Зелені (індекси 5-7)
            )

            return KubernetesColors(k8sBlue, k8sLightBlue, k8sDarkBlue, accentColors, colorGroups)
        }
    }
}

/**
 * Створює градієнти для променів іконки
 */
data class SpokeGradients(
    val spokeColor1: LinearGradientPaint, // Червоний градієнт
    val spokeColor2: LinearGradientPaint  // Зелений градієнт
) {
    companion object {
        /**
         * Створює градієнти для променів на базі основних кольорів
         */
        fun create(k8sBlue: Color): SpokeGradients {
            // Кольори для спиць
            val spokeAccentColor1 = Color(200, 60, 60)  // Червоний
            val spokeAccentColor2 = Color(60, 160, 60)  // Зелений

            val spokeColor1 = LinearGradientPaint(
                0f, 0f, 100f, 100f,
                floatArrayOf(0.0f, 1.0f),
                arrayOf(k8sBlue, spokeAccentColor1)  // Синій -> Червоний
            )

            val spokeColor2 = LinearGradientPaint(
                0f, 0f, 100f, 100f,
                floatArrayOf(0.0f, 1.0f),
                arrayOf(k8sBlue, spokeAccentColor2)  // Синій -> Зелений
            )

            return SpokeGradients(spokeColor1, spokeColor2)
        }
    }
}

/**
 * Ініціалізує графічний контекст для малювання
 */
fun initializeGraphics(image: BufferedImage): Graphics2D {
    val g2d = image.createGraphics()

    // Налаштування якості рендерингу
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    // Створюємо прозорий фон
    g2d.composite = AlphaComposite.Clear
    g2d.fillRect(0, 0, image.width, image.height)
    g2d.composite = AlphaComposite.SrcOver

    return g2d
}

// IconGenerator.kt - Частина 2: Функції для малювання елементів іконки

/**
 * Малює фон іконки з градієнтним заповненням
 */
fun drawBackground(g2d: Graphics2D, size: Int, centerX: Int, centerY: Int) {
    // Радіальний градієнт для фону з стриманими кольорами
    val radius = size * 0.85f

    val gp = RadialGradientPaint(
        centerX.toFloat(),
        centerY.toFloat(),
        radius,
        floatArrayOf(0.0f, 0.7f, 1.0f),
        arrayOf(
            Color(240, 248, 255),   // Майже білий у центрі
            Color(225, 240, 255),   // Світло-блакитний
            Color(205, 230, 250)    // Блакитний
        )
    )

    // Малюємо круглий фон з градієнтом
    g2d.paint = gp
    g2d.fillOval(0, 0, size, size)
}

/**
 * Створює полігон із заданої кількості точок
 */
fun createPolygon(centerX: Int, centerY: Int, radius: Int, numPoints: Int,
                  randomSeed: Int, variationRange: Double = 0.05): Polygon {
    val points = Array(numPoints) { i ->
        val angle = 2 * Math.PI * i / numPoints
        // Додаємо невелику випадкову варіацію для асиметрії
        val variation = 1.0 + Random(i + randomSeed).nextDouble(-variationRange, variationRange)
        val x = centerX + (radius * variation * cos(angle)).toInt()
        val y = centerY + (radius * variation * sin(angle)).toInt()
        Point(x, y)
    }

    val polygon = Polygon()
    points.forEach { polygon.addPoint(it.x, it.y) }
    return polygon
}

/**
 * Малює зовнішній полігон з тінню та градієнтом
 */
fun drawOuterPolygon(g2d: Graphics2D, outerPolygon: Polygon, wheelGradient: Paint, k8sDarkBlue: Color, size: Int) {
    // Спочатку додаємо тінь для ефекту глибини
    g2d.color = Color(0, 0, 0, 40)
    g2d.translate(3, 3)
    g2d.fill(outerPolygon)
    g2d.translate(-3, -3)

    // Тепер малюємо сам полігон
    g2d.paint = wheelGradient
    g2d.fill(outerPolygon)

    // Додаємо обведення до полігону
    g2d.stroke = BasicStroke((size * 0.01).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g2d.color = k8sDarkBlue
    g2d.draw(outerPolygon)
}

/**
 * Малює промені та зірки на кінцях променів
 */
fun drawSpokesAndStars(
    g2d: Graphics2D,
    innerPoints: Array<Point>,
    outerPoints: Array<Point>,
    spokeWidth: Int,
    spokeGradients: SpokeGradients,
    colors: KubernetesColors
) {
    // Промальовуємо асиметричні промені з контрастними кольорами зірок
    for (i in 0 until innerPoints.size) {
        val innerX = innerPoints[i].x
        val innerY = innerPoints[i].y
        val outerX = outerPoints[i].x
        val outerY = outerPoints[i].y

        // Визначаємо групу кольорів для спиць
        val isRedSpoke = i % 2 == 0
        val spokeColorGroup = if (isRedSpoke) 0 else 2  // 0 = червоний, 2 = зелений

        // Вибираємо контрастні групи для зірок (якщо промінь червоний, зірки будуть зелені і жовті)
        val innerStarColorGroup = (spokeColorGroup + 1) % 3  // Жовтий для червоного променя, Червоний для зеленого
        val outerStarColorGroup = (spokeColorGroup + 2) % 3  // Зелений для червоного променя, Жовтий для зеленого

        // Варіюємо товщину спиць
        val spokeVariation = 0.8 + Random(i + 200).nextDouble(0.0, 0.5)
        val spokeThickness = (spokeWidth * spokeVariation).toFloat()

        // Додаємо напівпрозорість до променів (між 50% та 80% непрозорості)
        val spokeAlpha = 0.5f + Random(i + 900).nextFloat() * 0.3f
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, spokeAlpha)

        // Чергуємо кольори спиць
        val spokeGradient = if (isRedSpoke) spokeGradients.spokeColor1 else spokeGradients.spokeColor2
        g2d.paint = spokeGradient
        g2d.stroke = BasicStroke(spokeThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2d.drawLine(innerX, innerY, outerX, outerY)

        // Зіркоподібні полігони на кінцях спиць з новою палітрою кольорів
        val innerNodeSize = (spokeWidth * (0.8 + Random(i + 300).nextDouble(0.4, 0.8))).toInt()
        val outerNodeSize = (spokeWidth * 1.2 * spokeVariation).toInt()

        drawStar(g2d, innerX, innerY, innerNodeSize, colors, colors.colorGroups[innerStarColorGroup], i + 400, 0.6f + Random(i + 1000).nextFloat() * 0.3f, 4 + i % 2)
        drawStar(g2d, outerX, outerY, outerNodeSize, colors, colors.colorGroups[outerStarColorGroup], i + 500, 0.4f + Random(i + 1100).nextFloat() * 0.35f, 5 + i % 2)
    }

    // Повертаємо звичайну непрозорість для решти елементів
    g2d.composite = AlphaComposite.SrcOver
}

/**
 * Малює зірку із заданими параметрами
 */
fun drawStar(
    g2d: Graphics2D,
    x: Int,
    y: Int,
    size: Int,
    colors: KubernetesColors,
    colorRange: IntRange,
    seed: Int,
    alpha: Float,
    numPoints: Int
) {
    // Вибираємо випадковий колір із відповідної групи
    val colorIdx = colorRange.random(Random(seed))

    // Встановлюємо напівпрозорість для зірки
    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)

    // Створюємо зірку з м'якшими кутами
    val star = createSoftStarPolygon(
        x, y,
        size,
        (size * 0.7).toInt(),  // М'якші кути - 70% від зовнішнього радіусу
        numPoints
    )

    // Додаємо тінь для об'ємності
    val shadowOffset = if (size > 20) 2 else 1
    g2d.color = Color(0, 0, 0, 40)
    g2d.translate(shadowOffset, shadowOffset)
    g2d.fill(star)
    g2d.translate(-shadowOffset, -shadowOffset)

    // Малюємо зірку
    val starColor = colors.accentColors[colorIdx]
    g2d.color = starColor
    g2d.fill(star)
    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f) // Обведення трохи більш помітне
    g2d.color = Color(0, 0, 0, 80)
    g2d.stroke = BasicStroke(1f)
    g2d.draw(star)
}

// IconGenerator.kt - Частина 3: Малювання центрального елемента і мережевого символу

/**
 * Малює центральний елемент з градієнтом і тінню
 */
fun drawCenterPolygon(
    g2d: Graphics2D,
    centerX: Int,
    centerY: Int,
    centerPolygonRadius: Int,
    colors: KubernetesColors
) {
    // Створюємо 7-кутний полігон для центрального елемента
    val centerPoints = Array(7) { i ->
        val angle = 2 * Math.PI * i / 7 + Math.PI / 7 // зміщення для кращого вигляду
        // Додаємо помірну варіацію для асиметрії
        val variation = 1.0 + Random(i + 400).nextDouble(-0.08, 0.08)
        val x = centerX + (centerPolygonRadius * variation * cos(angle)).toInt()
        val y = centerY + (centerPolygonRadius * variation * sin(angle)).toInt()
        Point(x, y)
    }

    val centerPolygon = Polygon()
    centerPoints.forEach { centerPolygon.addPoint(it.x, it.y) }

    // Спочатку малюємо тінь для центрального полігону
    val glowComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f)
    g2d.composite = glowComposite
    g2d.color = Color.DARK_GRAY
    g2d.translate(3, 3)
    g2d.fill(centerPolygon)
    g2d.translate(-3, -3)
    g2d.composite = AlphaComposite.SrcOver

    // Створюємо складніший градієнт для центрального елемента - додаємо жовтий відтінок
    val centerGradient = RadialGradientPaint(
        (centerX - centerPolygonRadius*0.2).toFloat(),
        (centerY - centerPolygonRadius*0.2).toFloat(),
        (centerPolygonRadius * 2).toFloat(),
        floatArrayOf(0.0f, 0.3f, 0.6f, 1.0f),
        arrayOf(
            Color.WHITE,
            Color(255, 230, 150),  // Світло-жовтий
            colors.k8sLightBlue,
            colors.k8sDarkBlue
        )
    )

    // Малюємо основний центральний полігон
    g2d.paint = centerGradient
    g2d.fill(centerPolygon)

    // Додаємо обведення до центрального полігона
    g2d.stroke = BasicStroke((centerPolygonRadius * 0.05).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g2d.color = Color(colors.k8sBlue.red, colors.k8sBlue.green, colors.k8sBlue.blue, 150)
    g2d.draw(centerPolygon)
}

/**
 * Генерує позиції для вузлів мережевого символу
 */
fun generateNetworkNodePositions(symbolSize: Float): Array<Pair<Float, Float>> {
    return arrayOf(
        Pair(0f, 0f),              // Центр
        Pair(-symbolSize*0.5f * (1 + Random.nextFloat() * 0.2f),
            -symbolSize*0.3f * (1 + Random.nextFloat() * 0.2f)),  // Верхній лівий
        Pair(symbolSize*0.5f * (1 + Random.nextFloat() * 0.1f),
            -symbolSize*0.3f * (1 - Random.nextFloat() * 0.1f)),   // Верхній правий
        Pair(-symbolSize*0.4f * (1 - Random.nextFloat() * 0.1f),
            symbolSize*0.4f * (1 + Random.nextFloat() * 0.15f)),   // Нижній лівий
        Pair(symbolSize*0.4f * (1 + Random.nextFloat() * 0.1f),
            symbolSize*0.4f * (1 - Random.nextFloat() * 0.1f)),    // Нижній правий
        Pair(0f, -symbolSize*0.5f * (1 + Random.nextFloat() * 0.15f))  // Верхній центр
    )
}

/**
 * Малює вузли мережевого символу та з'єднання між ними
 */
fun drawNetworkSymbol(
    g2d: Graphics2D,
    centerX: Int,
    centerY: Int,
    symbolSize: Float,
    spokeWidth: Int,
    colors: KubernetesColors
) {
    // Перевіряємо, що symbolSize достатньо велика
    if (symbolSize < 2.0f) return

    // Створюємо форму сузір'я/хмари для центру (асиметричну)
    val dotSize = maxOf(symbolSize * 0.12f, 1.0f)

    // Генеруємо розташування вузлів
    val dotPositions = generateNetworkNodePositions(symbolSize)

    // Кольори для вузлів мережі
    val nodeColors = arrayOf(
        Color.WHITE,                  // Центральний вузол білий
        colors.accentColors[0],       // Червоний вузол
        colors.accentColors[3],       // Жовтий вузол
        colors.accentColors[5],       // Зелений вузол
        colors.accentColors[1],       // Інший червоний
        colors.accentColors[6]        // Інший зелений
    )

    // Малюємо вузли мережевого символу
    for (i in dotPositions.indices) {
        val pos = dotPositions[i]
        val pointVariation = 0.8 + Random(i + 500).nextDouble(0.0, 0.4)
        val currentDotSize = maxOf(1, (dotSize * pointVariation).toInt())

        // Напівпрозорість для вузлів мережі (від 65% до 95% непрозорості)
        val nodeAlpha = 0.65f + Random(i + 1200).nextFloat() * 0.3f
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, nodeAlpha)

        // Якщо розмір дуже малий, просто малюємо точки замість зірок
        if (currentDotSize <= 2) {
            g2d.color = nodeColors[i]
            g2d.fillOval(
                (centerX + pos.first).toInt() - currentDotSize/2,
                (centerY + pos.second).toInt() - currentDotSize/2,
                currentDotSize,
                currentDotSize
            )
        } else {
            // Замінюємо круги на маленькі зірки з м'якшими кутами
            val nodeStar = createSoftStarPolygon(
                (centerX + pos.first).toInt(),
                (centerY + pos.second).toInt(),
                currentDotSize,
                (currentDotSize * 0.7).toInt(),  // М'якші кути
                4 + (i % 2)  // Від 4 до 5 променів
            )

            g2d.color = nodeColors[i]
            g2d.fill(nodeStar)

            // Додаємо обведення до вузлів
            g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f)
            g2d.color = Color(0, 0, 0, 60)
            g2d.stroke = BasicStroke(1f)
            g2d.draw(nodeStar)
        }
    }

    // Малюємо з'єднувальні лінії вузлів, якщо розмір достатньо великий
    if (symbolSize > 5.0f) {
        drawNetworkConnections(g2d, centerX, centerY, dotPositions, symbolSize)
    }

    // Додаємо ефект блиску на центральному елементі
    addCenterHighlight(g2d, centerX, centerY, (symbolSize * 0.7).toInt())
}

/**
 * Малює з'єднання між вузлами мережевого символу
 */
fun drawNetworkConnections(
    g2d: Graphics2D,
    centerX: Int,
    centerY: Int,
    dotPositions: Array<Pair<Float, Float>>,
    symbolSize: Float
) {
    // Базова товщина ліній з'єднання
    val baseLineThickness = symbolSize * 0.05f

    // З'єднуємо центр з іншими точками різної товщини і прозорості
    for (i in 1 until dotPositions.size) {
        // Варіюємо товщину ліній
        val lineVariation = 0.7 + Random(i + 600).nextDouble(0.0, 0.6)
        g2d.stroke = BasicStroke(baseLineThickness * lineVariation.toFloat(),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

        // Напівпрозорість ліній (від 60% до 80% непрозорості)
        val lineAlpha = 0.6f + Random(i + 1300).nextFloat() * 0.2f
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, lineAlpha)

        g2d.color = Color.WHITE
        g2d.drawLine(
            (centerX + dotPositions[0].first).toInt(),
            (centerY + dotPositions[0].second).toInt(),
            (centerX + dotPositions[i].first).toInt(),
            (centerY + dotPositions[i].second).toInt()
        )
    }

    // З'єднуємо точки по периметру з різною товщиною
    for (i in 1 until dotPositions.size - 1) {
        val lineVariation = 0.7 + Random(i + 700).nextDouble(0.0, 0.6)
        g2d.stroke = BasicStroke(baseLineThickness * lineVariation.toFloat(),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

        // Напівпрозорість ліній (від 50% до 75% непрозорості)
        val lineAlpha = 0.5f + Random(i + 1400).nextFloat() * 0.25f
        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, lineAlpha)

        g2d.color = Color.WHITE
        g2d.drawLine(
            (centerX + dotPositions[i].first).toInt(),
            (centerY + dotPositions[i].second).toInt(),
            (centerX + dotPositions[i+1].first).toInt(),
            (centerY + dotPositions[i+1].second).toInt()
        )
    }

    // З'єднуємо останню з першою периметра (замикаємо цикл)
    val lineVariation = 0.7 + Random(800).nextDouble(0.0, 0.6)
    g2d.stroke = BasicStroke(baseLineThickness * lineVariation.toFloat(),
        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

    // Напівпрозорість для останньої лінії
    val lastLineAlpha = 0.55f + Random(1500).nextFloat() * 0.25f
    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, lastLineAlpha)

    g2d.color = Color.WHITE
    g2d.drawLine(
        (centerX + dotPositions.last().first).toInt(),
        (centerY + dotPositions.last().second).toInt(),
        (centerX + dotPositions[1].first).toInt(),
        (centerY + dotPositions[1].second).toInt()
    )
}

/**
 * Додає ефект блиску на центральному елементі
 */
fun addCenterHighlight(g2d: Graphics2D, centerX: Int, centerY: Int, radius: Int) {
    // Перевіряємо, що радіус більше нуля
    if (radius <= 0) return

    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f)

    // Забезпечуємо, що радіус градієнта завжди більше 1.0
    val gradientRadius = maxOf(radius * 0.7f, 1.0f)

    val highlightCenterGradient = RadialGradientPaint(
        (centerX - radius * 0.3).toFloat(),
        (centerY - radius * 0.3).toFloat(),
        gradientRadius,
        floatArrayOf(0.0f, 1.0f),
        arrayOf(Color.WHITE, Color(255, 255, 255, 0))
    )

    g2d.paint = highlightCenterGradient
    g2d.fillOval(
        (centerX - radius * 0.7).toInt(),
        (centerY - radius * 0.7).toInt(),
        maxOf(1, (radius * 1.0).toInt()),
        maxOf(1, (radius * 1.0).toInt())
    )
    g2d.composite = AlphaComposite.SrcOver
}


// IconGenerator.kt - Частина 4: Малювання кнопки та основна функція генерації

/**
 * Малює кнопку з текстом і градієнтом
 */
fun drawButton(g2d: Graphics2D, size: Int, centerX: Int, colors: KubernetesColors) {
    val buttonHeight = (size * 0.09).toInt()
    val buttonWidth = (size * 0.55).toInt() // Трохи збільшуємо, щоб вмістити "Kube Manager"
    val buttonY = size - (buttonHeight * 2)
    val buttonX = centerX - buttonWidth / 2

    // Новий градієнт для кнопки - темно-синій з більш насиченим синім
    val buttonGradient = LinearGradientPaint(
        buttonX.toFloat(), buttonY.toFloat(),  // Початкова точка
        buttonX.toFloat(), (buttonY + buttonHeight).toFloat(),  // Кінцева точка
        floatArrayOf(0.0f, 1.0f),
        arrayOf(colors.k8sBlue, Color(20, 50, 150))  // Більш насичений темно-синій
    )

    // Малюємо кнопку з закругленими кутами
    val roundRect = RoundRectangle2D.Float(
        buttonX.toFloat(), buttonY.toFloat(),
        buttonWidth.toFloat(), buttonHeight.toFloat(),
        buttonHeight.toFloat(), buttonHeight.toFloat()
    )

    // Тінь для кнопки
    val origComposite = g2d.composite
    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f)
    g2d.color = Color.DARK_GRAY
    g2d.fill(RoundRectangle2D.Float(
        buttonX.toFloat() + 3, buttonY.toFloat() + 3,
        buttonWidth.toFloat(), buttonHeight.toFloat(),
        buttonHeight.toFloat(), buttonHeight.toFloat()
    ))
    g2d.composite = origComposite

    // Малюємо градієнтну кнопку
    g2d.paint = buttonGradient
    g2d.fill(roundRect)

    // Додаємо помірне світіння навколо кнопки
    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f)
    g2d.stroke = BasicStroke(2f)
    g2d.color = Color.WHITE
    g2d.draw(RoundRectangle2D.Float(
        buttonX.toFloat() - 2, buttonY.toFloat() - 2,
        buttonWidth.toFloat() + 4, buttonHeight.toFloat() + 4,
        buttonHeight.toFloat() + 4, buttonHeight.toFloat() + 4
    ))
    g2d.composite = origComposite

    // Додаємо текст з гарним шрифтом
    drawButtonText(g2d, "Kube Manager", buttonX, buttonY, buttonWidth, buttonHeight)

    // Додаємо ефект блиску на кнопці
    addButtonHighlight(g2d, buttonX, buttonY, buttonWidth, buttonHeight)
}

/**
 * Малює текст на кнопці
 */
fun drawButtonText(g2d: Graphics2D, text: String, buttonX: Int, buttonY: Int, buttonWidth: Int, buttonHeight: Int) {
    val fontSize = (buttonHeight * 0.6).toInt() // Трохи зменшуємо, щоб вмістити довший текст

    try {
        g2d.font = Font("Segoe UI", Font.BOLD, fontSize)
    } catch (e: Exception) {
        // Fallback для Linux
        try {
            g2d.font = Font("DejaVu Sans", Font.BOLD, fontSize)
        } catch (e: Exception) {
            // Остаточний fallback
            g2d.font = Font(Font.SANS_SERIF, Font.BOLD, fontSize)
        }
    }

    val metrics = g2d.fontMetrics
    val textWidth = metrics.stringWidth(text)
    val textX = buttonX + (buttonWidth - textWidth) / 2
    val textY = buttonY + (buttonHeight - metrics.height) / 2 + metrics.ascent

    // Додаємо тінь до тексту
    g2d.color = Color(0, 0, 0, 80)
    g2d.drawString(text, textX + 1, textY + 1)

    // Малюємо текст
    g2d.color = Color.WHITE
    g2d.drawString(text, textX, textY)
}

/**
 * Додає ефект блиску до кнопки
 */
fun addButtonHighlight(g2d: Graphics2D, buttonX: Int, buttonY: Int, buttonWidth: Int, buttonHeight: Int) {
    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f)

    // Переконаємося, що координати не співпадають:
    val highlightGradient = LinearGradientPaint(
        buttonX.toFloat(), buttonY.toFloat(),  // Початкова точка
        buttonX.toFloat(), (buttonY + buttonHeight/3 + 1).toFloat(),  // +1 щоб уникнути рівності
        floatArrayOf(0.0f, 1.0f),
        arrayOf(Color.WHITE, Color(255, 255, 255, 0))
    )

    g2d.paint = highlightGradient
    g2d.fill(RoundRectangle2D.Float(
        buttonX.toFloat() + 4, buttonY.toFloat() + 2,
        buttonWidth.toFloat() - 8, (buttonHeight/3).toFloat(),
        (buttonHeight/2).toFloat(), (buttonHeight/4).toFloat()
    ))
    g2d.composite = AlphaComposite.SrcOver
}

/**
 * Генерує збалансовану іконку для Kubernetes Manager та зберігає її як PNG файл
 */
fun generateKubernetesIcon(
    filePath: String = "kubernetes_manager_icon.png",
    size: Int = 512
) {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g2d = initializeGraphics(image)

    val centerX = size / 2
    val centerY = size / 2

    // Створюємо кольори та градієнти
    val colors = KubernetesColors.createColors()
    val spokeGradients = SpokeGradients.create(colors.k8sBlue)

    // Малюємо фон
    drawBackground(g2d, size, centerX, centerY)

    // Параметри для основних елементів
    val wheelSize = (size * 0.7).toInt()
    val outerRadius = maxOf(1, wheelSize / 2)
    val innerRadius = maxOf(1, (outerRadius * 0.4).toInt())
    val spokeWidth = maxOf(1, (size * 0.035).toInt())

    // Створюємо градієнтну заливку для зовнішнього полігону
    val wheelGradient = LinearGradientPaint(
        centerX.toFloat(), (centerY - outerRadius).toFloat(),
        centerX.toFloat(), (centerY + outerRadius).toFloat(),
        floatArrayOf(0.0f, 0.5f, 1.0f),
        arrayOf(colors.k8sLightBlue, colors.k8sBlue, colors.k8sDarkBlue)
    )

    // Для дуже малих розмірів спрощуємо іконку
    if (size < 32) {
        // Малюємо простий круг замість складної іконки
        g2d.paint = colors.k8sBlue
        g2d.fillOval(0, 0, size, size)

        // Додаємо просту мережеву структуру
        g2d.color = Color.WHITE
        g2d.drawLine(centerX - size/4, centerY, centerX + size/4, centerY)
        g2d.drawLine(centerX, centerY - size/4, centerX, centerY + size/4)
        g2d.fillOval(centerX - 1, centerY - 1, 3, 3)
    } else {
        // Створюємо і малюємо зовнішній полігон
        val outerPolygon = createPolygon(centerX, centerY, outerRadius, 7, 0, 0.05)
        drawOuterPolygon(g2d, outerPolygon, wheelGradient, colors.k8sDarkBlue, size)

        // Створюємо точки для внутрішнього полігону
        val innerPoints = Array(7) { i ->
            val angle = 2 * Math.PI * i / 7
            // Додаємо варіацію для асиметрії, більш виражену
            val variation = 1.0 + Random(i + 100).nextDouble(-0.15, 0.15)
            val x = centerX + (innerRadius * variation * cos(angle)).toInt()
            val y = centerY + (innerRadius * variation * sin(angle)).toInt()
            Point(x, y)
        }

        // Малюємо промені та зірки
        val outerPoints = Array(outerPolygon.npoints) { i ->
            Point(outerPolygon.xpoints[i], outerPolygon.ypoints[i])
        }
        drawSpokesAndStars(g2d, innerPoints, outerPoints, spokeWidth, spokeGradients, colors)

        // Малюємо центральний елемент
        val centerPolygonRadius = maxOf(1, (innerRadius * 0.9).toInt())
        drawCenterPolygon(g2d, centerX, centerY, centerPolygonRadius, colors)

        // Малюємо мережевий символ
        val symbolSize = centerPolygonRadius * 1.2f
        drawNetworkSymbol(g2d, centerX, centerY, symbolSize, spokeWidth, colors)

        // Малюємо кнопку з текстом тільки якщо розмір достатньо великий
        if (size >= 64) {
            drawButton(g2d, size, centerX, colors)
        }
    }

    // Завершуємо роботу з графічним контекстом
    g2d.dispose()

    // Збереження у файл
    File(filePath).parentFile?.mkdirs()
    ImageIO.write(image, "PNG", File(filePath))

    println("Збалансовану іконку було успішно створено: $filePath")
}

/**
 * Функція для створення іконок різних розмірів
 */
fun generateAllIcons() {
    // Основна іконка
    generateKubernetesIcon("kubernetes_manager_icon.png", 512)

    // Додаткові розміри
    val sizes = listOf(16, 32, 64, 128, 256)
    for (size in sizes) {
        generateKubernetesIcon("kubernetes_manager_icon_${size}x${size}.png", size)
    }
}

/**
 * Для тестування
 */
fun main() {
    generateAllIcons()
    println("Всі іконки успішно згенеровано!")
}