package com.draftlens.heroes

import com.draftlens.vision.heroes.HeroRedirectPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroRedirectPolicyTest {

    @Test fun `an absolute https redirect target is accepted`() {
        assertEquals(
            "https://cdn.example.com/hero.png",
            HeroRedirectPolicy.resolveTarget("https://a.example.com/", "https://cdn.example.com/hero.png"),
        )
    }

    @Test fun `a relative redirect target is resolved against the current url`() {
        assertEquals(
            "https://a.example.com/dir/asset.png",
            HeroRedirectPolicy.resolveTarget("https://a.example.com/dir/list", "asset.png"),
        )
    }

    @Test fun `an https to http downgrade redirect is refused`() {
        assertNull(HeroRedirectPolicy.resolveTarget("https://a.example.com/", "http://a.example.com/hero.png"))
        assertNull(HeroRedirectPolicy.resolveTarget("https://a.example.com/", "http://127.0.0.1:8080/x"))
        assertNull(HeroRedirectPolicy.resolveTarget("https://a.example.com/", "http://169.254.169.254/latest"))
    }

    @Test fun `non-http schemes are refused`() {
        assertNull(HeroRedirectPolicy.resolveTarget("https://a.example.com/", "ftp://a.example.com/x"))
        assertNull(HeroRedirectPolicy.resolveTarget("https://a.example.com/", "file:///etc/passwd"))
        assertNull(HeroRedirectPolicy.resolveTarget("https://a.example.com/", "javascript:alert(1)"))
    }

    @Test fun `missing or blank locations are refused`() {
        assertNull(HeroRedirectPolicy.resolveTarget("https://a.example.com/", null))
        assertNull(HeroRedirectPolicy.resolveTarget("https://a.example.com/", ""))
        assertNull(HeroRedirectPolicy.resolveTarget("https://a.example.com/", "   "))
    }

    @Test fun `an over-long redirect target is refused`() {
        val huge = "https://a.example.com/" + "a".repeat(4096)
        assertNull(HeroRedirectPolicy.resolveTarget("https://a.example.com/", huge))
    }
}
