package com.akira.app.core.updates

import java.math.BigInteger

/** SemVer precedence, including numeric prerelease identifiers and stable releases. */
internal class ReleaseVersion private constructor(
    private val major: BigInteger,
    private val minor: BigInteger,
    private val patch: BigInteger,
    private val prerelease: List<String>,
) : Comparable<ReleaseVersion> {
    val isPrerelease get() = prerelease.isNotEmpty()

    override fun compareTo(other: ReleaseVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }).takeIf { it != 0 }?.let { return it }
        if (prerelease.isEmpty() || other.prerelease.isEmpty())
            return other.prerelease.size.compareTo(prerelease.size)
        prerelease.zip(other.prerelease).forEach { (left, right) ->
            val leftNumber = left.all(Char::isDigit)
            val rightNumber = right.all(Char::isDigit)
            val difference = when {
                leftNumber && rightNumber -> left.toBigInteger().compareTo(right.toBigInteger())
                leftNumber -> -1
                rightNumber -> 1
                else -> left.compareTo(right)
            }
            if (difference != 0) return difference
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val pattern = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?")

        fun parse(value: String): ReleaseVersion? {
            val match = pattern.matchEntire(value) ?: return null
            val prerelease = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.').orEmpty()
            if (prerelease.any { it.isEmpty() || it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
            if (match.groupValues[5].takeIf(String::isNotEmpty)?.split('.')?.any(String::isEmpty) == true) return null
            return ReleaseVersion(match.groupValues[1].toBigInteger(), match.groupValues[2].toBigInteger(),
                match.groupValues[3].toBigInteger(), prerelease)
        }
    }
}

internal fun isNewerReleaseVersion(candidate: String, installed: String): Boolean {
    val next = ReleaseVersion.parse(candidate) ?: return false
    val current = ReleaseVersion.parse(installed) ?: return false
    return next > current
}
