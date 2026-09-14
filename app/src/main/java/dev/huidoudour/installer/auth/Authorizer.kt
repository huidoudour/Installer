package dev.huidoudour.installer.auth

import dev.huidoudour.installer.R

/**
 * 可用的授权方式（安装器）。
 * - [Shizuku] / [Dhizuku] 为特权授权器
 * - [None] 表示回退到系统安装器（无特权）
 */
enum class Authorizer(val value: String, val displayNameRes: Int) {
    Shizuku("shizuku", R.string.shizuku),
    Dhizuku("dhizuku", R.string.dhizuku),
    None("none", R.string.authorizer_none),
    ;

    companion object {
        fun fromValueOrDefault(value: String): Authorizer =
            entries.find { it.value == value } ?: Shizuku
    }
}
