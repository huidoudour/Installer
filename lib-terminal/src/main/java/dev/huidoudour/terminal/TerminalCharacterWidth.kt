package dev.huidoudour.terminal

import android.icu.lang.UCharacter
import android.icu.lang.UProperty

/** Terminal columns, independent of the font used for fallback glyphs. */
internal fun terminalCharacterWidth(codePoint: Int): Int {
    if (codePoint in 0x20..0x7E) return 1
    when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(),
        Character.FORMAT.toInt() -> return 0
    }
    return when (UCharacter.getIntPropertyValue(codePoint, UProperty.EAST_ASIAN_WIDTH)) {
        UCharacter.EastAsianWidth.WIDE, UCharacter.EastAsianWidth.FULLWIDTH -> 2
        else -> 1
    }
}