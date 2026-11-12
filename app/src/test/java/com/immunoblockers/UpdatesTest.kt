package com.immunoblockers

import com.immunoblockers.features.Updates
import org.junit.Assert.*
import org.junit.Test

class UpdatesTest {
    @Test fun otaRecognizes040From030AndRejectsDowngradesAndNonReleaseTags() {
        assertTrue(Updates.newer("v0.4.0", "0.3.0"))
        assertTrue(Updates.newer("v1.0.0", "0.9.9"))
        assertFalse(Updates.newer("v0.3.0", "0.4.0"))
        assertFalse(Updates.newer("v0.4.0-beta", "0.3.0"))
        assertFalse(Updates.newer("v0.4.0", "0.4.0"))
    }
}
