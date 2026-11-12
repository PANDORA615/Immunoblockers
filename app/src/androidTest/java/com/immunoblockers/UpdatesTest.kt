package com.immunoblockers

import androidx.test.core.app.ApplicationProvider
import com.immunoblockers.features.Updates
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdatesTest {
    @Test fun installedApkIsNotAcceptedAsAnUpdate() {
        val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
        assertThrows(IllegalArgumentException::class.java) {
            Updates.verify(app,File(app.applicationInfo.sourceDir))
        }
    }
}
