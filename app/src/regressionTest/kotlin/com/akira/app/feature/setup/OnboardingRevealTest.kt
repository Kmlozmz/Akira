package com.akira.app.feature.setup

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class OnboardingRevealTest {
    @Test fun `entry reveal reaches every corner on phones and tablets in either orientation`() {
        for ((width, height) in listOf(320f to 640f, 1080f to 2400f, 2400f to 1080f, 2560f to 1600f)) {
            val radius = onboardingRevealRadius(width, height)
            for (x in listOf(0f, width)) for (y in listOf(0f, height)) {
                assertTrue("Uncovered corner $x,$y on $width x $height",
                    hypot(x - width / 2, y - height * .82f) <= radius + .001f)
            }
        }
    }
}
