package com.nahuanotoca.superapp.core.update

/** Tiny semantic version helper used to compare the installed app with a GitHub release tag. */
object Version {

    fun parse(value: String): IntArray {
        val cleaned = value.trim().removePrefix("v")
        val numbers = cleaned.split(".", "-")
            .mapNotNull { it.toIntOrNull() }
            .take(3)
            .toMutableList()
        while (numbers.size < 3) numbers.add(0)
        return numbers.toIntArray()
    }

    /** Returns a positive number when [left] is newer than [right]. */
    fun compare(left: String, right: String): Int {
        val a = parse(left)
        val b = parse(right)
        for (index in 0 until 3) {
            if (a[index] != b[index]) return a[index].compareTo(b[index])
        }
        return 0
    }
}
