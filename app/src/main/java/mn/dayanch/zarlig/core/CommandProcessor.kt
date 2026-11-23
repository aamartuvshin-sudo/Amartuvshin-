package mn.dayanch.zarlig.core

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings

/**
 * Монгол хэлний командыг таньж, Android үйлдлүүдийг гүйцэтгэдэг тархи.
 *
 * "Энгийн түвшин" — root шаардахгүй. Суулгах/устгах зэрэг аюултай үйлдэлд
 * систем хэрэглэгчээс нэг товч баталгаажуулалт асууна.
 */
class CommandProcessor(private val context: Context) {

    /** Гол оролт: түүхий текстийг аваад гүйцэтгэнэ. */
    fun process(rawInput: String): CommandResult {
        val text = rawInput.trim().lowercase()
        if (text.isEmpty()) {
            return CommandResult("Би сонссонгүй. Дахин хэлээрэй.", success = false)
        }

        return when {
            // --- Тусламж ---
            containsAny(text, "тусламж", "заавар", "яаж", "юу хийж чадах") -> helpText()

            // --- Цаг асуух ---
            containsAny(text, "цаг хэд", "хэдэн цаг", "цаг хэдэн") -> tellTime()

            // --- Залгах ---
            containsAny(text, "залга", "дуудлага", "ярь", "холбогд") -> makeCall(text)

            // --- Мессеж илгээх ---
            containsAny(text, "мессеж", "бичиг илгээ", "зурвас", "смс", "sms") -> sendSms(text)

            // --- Сэрүүлэг / сануулга ---
            containsAny(text, "сануул", "сэрүүлэг", "сэрүүл", "сэрээ") -> setAlarm(text)

            // --- Таймер ---
            containsAny(text, "таймер", "хугацаа заа") -> setTimer(text)

            // --- Апп суулгах ---
            containsAny(text, "суулга", "татаж ав", "татах", "суулгая") -> installApp(text)

            // --- Апп устгах ---
            containsAny(text, "устга", "устгая", "хасъя", "арилга апп") -> uninstallApp(text)

            // --- Цэвэрлэх ---
            containsAny(text, "цэвэрл", "цэвэр хий", "хог арил", "сан чөлөөл") -> openStorageCleaner()

            // --- Апп нээх (хамгийн сүүлд, учир нь өргөн тохирол) ---
            containsAny(text, "нээ", "онго", "асаа", "эхлүүл") -> openApp(text)

            // --- Дуу чангалах/намсгах ---
            containsAny(text, "дуу чанга", "дуу нэм", "чанга бол", "чангал") -> changeVolume(up = true)
            containsAny(text, "дуу намс", "дуу хас", "намсга", "аяар") -> changeVolume(up = false)
            containsAny(text, "дуугүй", "чимээгүй", "хаа дуу") -> muteVolume()

            // --- Тохиргоо ---
            containsAny(text, "wifi", "вайфай", "вай фай", "интернэт тохир") -> openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi тохиргоо")
            containsAny(text, "bluetooth", "блютүз", "блютуз") -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth тохиргоо")
            containsAny(text, "тохиргоо нээ", "settings", "тохиргоо руу") -> openSettings(Settings.ACTION_SETTINGS, "Тохиргоо")

            else -> CommandResult(
                "Уучлаарай, «$rawInput» гэдгийг ойлгосонгүй.\n" +
                        "«тусламж» гэж бичвэл боломжит командуудыг харуулна.",
                success = false
            )
        }
    }

    // ---------------------------------------------------------------------
    // Үйлдлүүд
    // ---------------------------------------------------------------------

    private fun makeCall(text: String): CommandResult {
        val digits = extractDigits(text)
        val number: String? = if (digits.length >= 5) {
            digits
        } else {
            // Нэрээр харилцагч хайх
            val name = cleanAfterKeyword(text, "залга", "дуудлага", "ярь", "холбогд")
            if (name.isBlank()) null else findContactNumber(name)
        }

        if (number.isNullOrBlank()) {
            return CommandResult(
                "Хэнд залгахыг олсонгүй. Жишээ: «Болд руу залга» эсвэл «99112233 руу залга».",
                success = false
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult("📞 $number руу залгаж байна...")
        } catch (e: SecurityException) {
            // CALL_PHONE зөвшөөрөл байхгүй бол зүгээр залгах цонх нээнэ
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dial)
            CommandResult("📞 $number дугаарыг залгах цонхонд гаргалаа (ногоон товчийг дарна уу).")
        } catch (e: Exception) {
            CommandResult("Залгаж чадсангүй: ${e.message}", success = false)
        }
    }

    private fun sendSms(text: String): CommandResult {
        val digits = extractDigits(text)
        return try {
            val uri = if (digits.length >= 5) Uri.parse("smsto:$digits") else Uri.parse("sms:")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult("✉️ Мессеж бичих цонхыг нээлээ.")
        } catch (e: Exception) {
            CommandResult("Мессеж цонх нээж чадсангүй: ${e.message}", success = false)
        }
    }

    private fun setAlarm(text: String): CommandResult {
        val time = extractTime(text)
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (time != null) {
                    putExtra(AlarmClock.EXTRA_HOUR, time.first)
                    putExtra(AlarmClock.EXTRA_MINUTES, time.second)
                }
                putExtra(AlarmClock.EXTRA_MESSAGE, "Зарлиг сэрүүлэг")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            context.startActivity(intent)
            if (time != null) {
                CommandResult("⏰ %02d:%02d цагт сэрүүлэг тавилаа.".format(time.first, time.second))
            } else {
                CommandResult("⏰ Сэрүүлэг тавих цонхыг нээлээ. Цагаа оруулна уу.")
            }
        } catch (e: ActivityNotFoundException) {
            CommandResult("Сэрүүлгийн апп олдсонгүй.", success = false)
        }
    }

    private fun setTimer(text: String): CommandResult {
        val minutes = extractNumber(text) ?: 5
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Зарлиг таймер")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            context.startActivity(intent)
            CommandResult("⏲️ $minutes минутын таймер тавилаа.")
        } catch (e: ActivityNotFoundException) {
            CommandResult("Таймерын апп олдсонгүй.", success = false)
        }
    }

    private fun openApp(text: String): CommandResult {
        val query = cleanAfterKeyword(text, "нээ", "онго", "асаа", "эхлүүл")
        if (query.isBlank()) {
            return CommandResult("Ямар апп нээхийг хэлнэ үү. Жишээ: «Facebook нээ».", success = false)
        }
        val app = findInstalledApp(query)
            ?: return CommandResult("«$query» нэртэй апп олдсонгүй.", success = false)

        val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
        return if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            CommandResult("▶️ ${app.label} нээлээ.")
        } else {
            CommandResult("${app.label}-ыг нээж чадсангүй.", success = false)
        }
    }

    private fun installApp(text: String): CommandResult {
        val query = cleanAfterKeyword(text, "суулга", "татаж ав", "татах", "суулгая")
        if (query.isBlank()) {
            return CommandResult("Ямар апп суулгахыг хэлнэ үү. Жишээ: «TikTok суулга».", success = false)
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$query")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult("⬇️ Play Store дээр «$query»-г хайлаа. «Install» товчийг дарна уу.")
        } catch (e: ActivityNotFoundException) {
            // Play Store байхгүй бол браузер
            val web = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=$query")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(web)
            CommandResult("⬇️ Браузераар «$query»-г хайлаа.")
        }
    }

    private fun uninstallApp(text: String): CommandResult {
        val query = cleanAfterKeyword(text, "устга", "устгая", "хасъя", "арилга апп", "арилга", "хас")
        if (query.isBlank()) {
            return CommandResult("Ямар апп устгахыг хэлнэ үү. Жишээ: «TikTok устга».", success = false)
        }
        val app = findInstalledApp(query)
            ?: return CommandResult("«$query» нэртэй апп олдсонгүй.", success = false)

        return try {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult("🗑️ ${app.label}-ыг устгах цонхыг нээлээ. «OK» дарна уу.")
        } catch (e: Exception) {
            CommandResult("Устгах цонх нээж чадсангүй: ${e.message}", success = false)
        }
    }

    private fun openStorageCleaner(): CommandResult {
        return try {
            // Санах ойн тохиргоо руу (хэрэглэгч cache-ээ цэвэрлэнэ)
            val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult("🧹 Санах ойн тохиргоог нээлээ. Эндээс кэш, хогийг цэвэрлэж болно.")
        } catch (e: Exception) {
            openSettings(Settings.ACTION_SETTINGS, "Тохиргоо")
        }
    }

    private fun changeVolume(up: Boolean): CommandResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val dir = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return CommandResult(if (up) "🔊 Дууг чангаллаа." else "🔉 Дууг багаслаа.")
    }

    private fun muteVolume(): CommandResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        return CommandResult("🔇 Дууг хаалаа.")
    }

    private fun openSettings(action: String, label: String): CommandResult {
        return try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            CommandResult("⚙️ $label-г нээлээ.")
        } catch (e: Exception) {
            CommandResult("$label нээж чадсангүй.", success = false)
        }
    }

    private fun tellTime(): CommandResult {
        val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        return CommandResult("🕐 Одоо цаг $now байна.")
    }

    private fun helpText(): CommandResult {
        return CommandResult(
            """
            Боломжит командууд:
            • «Болд руу залга» / «99112233 руу залга»
            • «Facebook нээ»
            • «TikTok суулга»
            • «TikTok устга»
            • «8 цагт сануул» (сэрүүлэг)
            • «10 минутын таймер»
            • «Мессеж бич 99112233»
            • «Дуу чангал» / «Дуу намсга» / «Дуугүй бол»
            • «Wi-Fi нээ» / «Bluetooth нээ»
            • «Цэвэрлэ» (санах ой)
            • «Цаг хэд вэ»
            """.trimIndent()
        )
    }

    // ---------------------------------------------------------------------
    // Туслах функцүүд
    // ---------------------------------------------------------------------

    private fun containsAny(text: String, vararg keys: String): Boolean =
        keys.any { text.contains(it) }

    /** Текстээс зөвхөн цифрүүдийг салгаж авна (утасны дугаар). */
    private fun extractDigits(text: String): String =
        text.filter { it.isDigit() }

    /** Текстээс анхны бүхэл тоог олно. */
    private fun extractNumber(text: String): Int? =
        Regex("\\d+").find(text)?.value?.toIntOrNull()

    /** «8 цагт», «14:30» зэргээс цаг:минут салгана. */
    private fun extractTime(text: String): Pair<Int, Int>? {
        // HH:MM хэлбэр
        Regex("(\\d{1,2}):(\\d{2})").find(text)?.let {
            val h = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            if (h in 0..23 && m in 0..59) return h to m
        }
        // «8 цаг», «8 цагт» хэлбэр
        Regex("(\\d{1,2})\\s*цаг").find(text)?.let {
            val h = it.groupValues[1].toInt()
            if (h in 0..23) return h to 0
        }
        return null
    }

    /** Түлхүүр үгийн дараах үлдсэн текстийг цэвэрлэж авна (аппын нэр, хүний нэр). */
    private fun cleanAfterKeyword(text: String, vararg keywords: String): String {
        var result = text
        // Нийтлэг туслах үгсийг хасах
        val stopWords = listOf("руу", "рүү", "гэдэг", "гэдгийг", "нэртэй", "аппыг", "апп", "-ыг", "-ийг", "ыг", "ийг", "уруу")
        for (kw in keywords) {
            result = result.replace(kw, " ")
        }
        for (sw in stopWords) {
            result = result.replace(sw, " ")
        }
        return result.trim().trim(',', '.', '!', '?').trim()
    }

    /** Нэрээр харилцагчийн утасны дугаар хайна. */
    private fun findContactNumber(name: String): String? {
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIdx) ?: continue
                    if (displayName.lowercase().contains(name.lowercase())) {
                        return cursor.getString(numIdx)
                    }
                }
                null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /** Нэрээр суулгасан аппыг хайна (ойролцоо тохирол). */
    private fun findInstalledApp(query: String): AppInfo? {
        val apps = listInstalledApps()
        val q = query.lowercase().trim()
        // 1. Яг таарах
        apps.firstOrNull { it.label.lowercase() == q }?.let { return it }
        // 2. Эхлэл таарах
        apps.firstOrNull { it.label.lowercase().startsWith(q) }?.let { return it }
        // 3. Агуулж байх
        apps.firstOrNull { it.label.lowercase().contains(q) }?.let { return it }
        // 4. Пакежийн нэр агуулах
        apps.firstOrNull { it.packageName.lowercase().contains(q) }?.let { return it }
        return null
    }

    /** Нээж болох бүх аппын жагсаалт. */
    fun listInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            val label = ri.loadLabel(pm).toString()
            AppInfo(label, pkg)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}
