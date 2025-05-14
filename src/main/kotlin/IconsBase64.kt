/**
 * Файл з Base64 представленнями іконок додатку для вбудовування в код.
 * Автоматично згенеровано за допомогою IconGenerator.
 */

object IconsBase64 {
    /** Base64 основної іконки 64x64. */
    const val ICON_64 = "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAC0klEQVR4XuXbwYtNcRQH8CElNuIfICtEKTsrC8qSldGYsDESC6UsxtJCVkO2Ck1sZEoyw9bCShZKCeUfsJfS0Z0aje/X+b537zm/lzqLz+Y788753jvNva/efVNTp1asNAqqoaAaCqqhoBoKqqGgGgom5ODV+/b61QlbXjlpey4/pJ9PDAWN7Zx7Yo+eztqvL/vMvu5d9fPzfru7eMF2nH9Gv98cBY1sP7dktx9csh+fDvw5cPT9wyG7sjBvm06/pNc3Q0ED284urR4cHrDn49sjtvXMc5rTBAUN7L64SAc5SvcanNMEBQm2zPz918s4ARunG/1bUBCwdmXH8hknYNfc4zbXBwoGwCs7ls84AWszuuvD8RsL1GEwCnrwruxe+T5GzUh7/0DBGDbPvLBr9667V/ZR5ccxzoyU9w8UCBuml2321k379u4wlelbfpQ+M0LvHyhwHJu/Y+/fHKXl/9KnvGfIjEHXBwocuEwZUh5FZmB3iQIHLlEi5TNmYHeJAgcuUSLlM2Zgd4kCBy5RIuUzZmB3iQIHLlEi5TNmYHeJAgcuUSLlM2Zgd4kCBy5RIuUzZmB3iQIHLlEi5TNmYHeJAgcuUSLlM2Zgd4kCBy5RIuUzZmB3iQIHLlEi5TNmYHeJAgcuUSLlM2Zgd4kCBy5RIuUzZmB3iQIHLlEi5TNmYHeJAgcuUSLlM2Zgd4kCBy5RIuUzZmB3iQIHLlEi5TNmYHeJAgcuUSLlM2Zgd4kCBy5RIuUzZmB3iQIHLlEi5TNmYHeJAgcuUSLlM2Zgd4kCBy5RIuUzZmB3iQIHLlEi5TNmYHeJAgcuUSLlM2Zgd4kCBy5RIuUzZmB3iQJH+U+GOqU/G1yv7KfDqOzzAajsEyKo7DNCqOxTYp6ME9AMBQ2Uf1K0490x1gvdz4eioDG8Y3RS7udDUTAhZb8v8N+hoBoKqqGgGgqqoaAaCqqhoJjfMDlychZF+XIAAAAASUVORK5CYII="

    /** Base64 іконки 16x16. */
    const val ICON_16 = "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAsklEQVR4XmNgCN/xnyKMIYCMw3ZiiqFjDAEotquY+v//PfX//++r/zcqnIMhj9MApcyl/1kjt/yfvCT1//+7mmAMYnNEb/7PiK4Z2QD2qC3//z9SAWtgjtiGYQBIDMR+et4c1WswRsOsQrgGfAaAMIP/wVEDaGIAKGpmrUjEb8A9jf+hzR0IzSgGwHDwHnCaQDcAJIY1aWMIIBn046bu/8/XDf4zBO3FlCdoALEYQ4BEDABYjVF3IxgJWwAAAABJRU5ErkJggg=="

    /** Base64 іконки 32x32. */
    const val ICON_32 = "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAB5ElEQVR4Xu2XTSgEYRjHpygH7ooU6ytJkXKQfBw3Iutzkws7hz0QWz5OuOCyUlhEiuym1gWxWbt2ceDg4oByWN8iJ0e3v3eGLTvPMLO2xmUOv8t/nnme3/Q+te9yXMsu/hUSaA0JtIYEamnwo9i2iMKeJXCNPvpcLSRQQXLnOjweE/CQKeLeNCOxfYvUqYIESjR7gZc0IJQXyVM6uCYvrVeCBHI0+VA+4BCHx7V6gMcMKsAy4ZlQk2V1gqsP0j5ykOA79QHw9mG8XxVgxmlRLTC/1oHz4yrkdy8r7wcJvgYbhybxfFYC3OSKA6advGoBoVbMvvYjSdiP5h+OhwSmAC5PKoG77IgBfxIIc5uNuhE7HS4rUHcoLpR0QEwCjJGFXjpcF9AFdAFdQBeQFahlAq+ppEGsAmNLXXS4rABrUNo/h9BpGfsRyYld4D4TMy4eCW3bdLisQBh2oWgZHcfbRRFwnRuVwNQqE7jPwoG/Bim8m11o9mh/RYEwpn3YpgcxsWJVLTDg6EPFoEN8l/STQgI5WFOu8fMr4lt32JLKCLAs3rzzWf/bF0shgRKsuXvDzG47hohznnVZxLsjqVeCBGpg9zyD1YXjoBFH+9XK5/wbJIgG9udERJpHAwm0hgRaQwKN+QDGe1q+H0JVlQAAAABJRU5ErkJggg=="

    /**
     * Декодує Base64-рядок у BufferedImage.
     * @param base64 Base64-рядок із зображенням
     * @return BufferedImage об'єкт зображення або null при помилці
     */
    fun decodeToImage(base64: String): java.awt.image.BufferedImage? {
        return try {
            val imageBytes = java.util.Base64.getDecoder().decode(base64)
            val inputStream = java.io.ByteArrayInputStream(imageBytes)
            javax.imageio.ImageIO.read(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Отримує іконку вказаного розміру. Якщо запитаний розмір недоступний,
     * повертає найближчий доступний розмір або null при помилці.
     * @param size бажаний розмір іконки
     * @return BufferedImage зображення або null при помилці
     */
    fun getIcon(size: Int): java.awt.image.BufferedImage? {
        return when (size) {
            16 -> decodeToImage(ICON_16)
            32 -> decodeToImage(ICON_32)
            64 -> decodeToImage(ICON_64)
            else -> {
                // Повертаємо найближчий доступний розмір
                when {
                    size < 16 -> decodeToImage(ICON_16)
                    size < 32 -> decodeToImage(ICON_16)
                    size < 64 -> decodeToImage(ICON_32)
                    else -> decodeToImage(ICON_32)
                }
            }
        }
    }
    
    /**
     * Встановлює іконку додатку для вікна Swing/AWT з Base64.
     * @param window вікно, для якого встановлюється іконка
     */
    fun setWindowIcon(window: java.awt.Window) {
        try {
            // Створюємо список іконок різних розмірів
            val icons = listOf(16, 32, 64)
                .mapNotNull { size -> getIcon(size) }
            
            // Встановлюємо іконки для вікна
            if (icons.isNotEmpty()) {
                window.iconImages = icons
            }
        } catch (e: Exception) {
            println("Помилка при встановленні іконки: ${e.message}")
        }
    }
}
