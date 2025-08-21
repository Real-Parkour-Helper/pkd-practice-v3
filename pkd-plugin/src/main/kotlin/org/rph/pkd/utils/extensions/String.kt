package org.rph.pkd.utils.extensions

import java.util.*

fun String.upperCaseWords() =
    this.split(" ")
        .joinToString(" ") { it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }