package com.companionstudio.nyx.setup

import android.os.Build
import java.util.Locale

/** Brands with their own battery steps on S-03 (D-60). Everyone else gets the generic text. */
enum class PhoneBrand { XIAOMI, SAMSUNG, VIVO, OPPO, OTHER }

fun phoneBrand(manufacturer: String = Build.MANUFACTURER): PhoneBrand = when (manufacturer.lowercase(Locale.ROOT)) {
    "xiaomi" -> PhoneBrand.XIAOMI
    "samsung" -> PhoneBrand.SAMSUNG
    "vivo" -> PhoneBrand.VIVO
    "oppo" -> PhoneBrand.OPPO
    else -> PhoneBrand.OTHER
}
