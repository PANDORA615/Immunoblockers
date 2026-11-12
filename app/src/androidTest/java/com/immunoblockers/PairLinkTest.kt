package com.immunoblockers

import androidx.test.core.app.ApplicationProvider
import com.immunoblockers.features.*
import com.immunoblockers.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class PairLinkTest {
    @Test fun encryptedTwoWayConsentAndRevocation() {
        val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
        val suffix=UUID.randomUUID().toString()
        val a=PairLink(app,"test_a_$suffix");val b=PairLink(app,"test_b_$suffix")
        val code=a.create("https://10.0.2.2:8080");b.join(code)
        assertFalse(a.shareMedicines);assertFalse(b.shareMood)
        a.shareMedicines=true;a.shareMood=true
        val now=System.currentTimeMillis();val date=LocalDate.now().toString()
        val med=Medication("a","Тест обмена","1.25","мг",start=date,times="09:00",createdAt=now,updatedAt=now)
        val row=Occurrence("a-dose","a",1,date,"09:00",now,"Тест обмена","1.25","мг",Occurrence.TAKEN,now)
        try {
            a.sync(listOf(row),listOf(med));b.sync(emptyList(),emptyList())
            assertEquals("1.25",b.partner()!!.getJSONArray("records").getJSONObject(0).getString("dose"))
            a.sync(listOf(row),listOf(med));assertFalse(a.partner()!!.has("medicines"))
            a.shareMedicines=false;a.sync(listOf(row),listOf(med));b.sync(emptyList(),emptyList())
            assertFalse(b.partner()!!.has("medicines"))
            a.disconnect()
            runCatching{b.sync(emptyList(),emptyList())}
            assertTrue(b.stopped);assertNull(b.partner())
        }finally {runCatching{if(a.configured)a.disconnect()}}
    }
    @Test fun oldOrOtherPackageApkIsNotAcceptedAsAnUpdate() {
        val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
        assertThrows(IllegalArgumentException::class.java){Updates.verify(app,java.io.File(app.applicationInfo.sourceDir))}
    }
}
