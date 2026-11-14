package com.akira.app.core.media

import android.graphics.Shader
import android.os.Build
import com.akira.app.core.design.rendering.BlurEffectCache
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BlurEffectApiCompatibilityTest {
    @Test fun transparentEdgesBlurPreservesThePlatformFallbackAndSupportedEffect() {
        if (Build.VERSION.SDK_INT >= 31) {
            assertSame(BlurEffectCache.get(12f, Shader.TileMode.DECAL), BlurEffectCache.getDecal(12f))
        } else {
            // DECAL does not exist on these Android versions. Even a zero-radius
            // presentation must avoid evaluating that enum constant at its call site.
            assertNull(BlurEffectCache.getDecal(0f))
            assertNull(BlurEffectCache.getDecal(12f))
        }
    }
}
