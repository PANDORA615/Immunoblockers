package com.immunoblockers
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.immunoblockers.features.LocalBook
import com.immunoblockers.data.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class NewFeaturesTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun materialAndGlassRemainSelectable() {
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithTag("settingsList").performScrollToNode(hasText("Material 3"))
        compose.onNodeWithText("Material 3").performClick()
        compose.onNodeWithText("Material 3").assertIsSelected()
        compose.onNodeWithText("Liquid Glass + Glassmorphism").performClick()
        compose.onNodeWithText("Liquid Glass + Glassmorphism").assertIsSelected()
    }
    @Test fun stockIsCalculatedOnceAndUndoRestoresTheAmount() {
        val app=ApplicationProvider.getApplicationContext<ImmunoblockersApp>()
        val book=LocalBook(app);val id=UUID.randomUUID().toString();val now=System.currentTimeMillis()
        val med=Medication(id,"Тест запаса","0.5","таблетка",start=LocalDate.now().toString(),times="09:00",createdAt=now,updatedAt=now)
        val old=Occurrence("old",id,1,med.start,"09:00",now,med.name,"1","таблетка",Occurrence.TAKEN,now)
        val fresh=old.copy(id="new",dose="0.5")
        try {
            book.setStock(med,BigDecimal.TEN,BigDecimal.ONE,listOf(old))
            assertEquals(BigDecimal("9.5"),book.stocks(listOf(old,fresh)).first {it.id==id}.remaining)
            assertEquals(BigDecimal("9.5"),book.stocks(listOf(old,fresh)).first {it.id==id}.remaining)
            assertEquals(BigDecimal.TEN,book.stocks(listOf(old,fresh.copy(state=Occurrence.PENDING))).first {it.id==id}.remaining)
            assertEquals(BigDecimal.TEN,book.stocks(listOf(old,fresh.copy(unit="мг"))).first {it.id==id}.remaining)
        } finally {book.removeStock(id)}
    }
}
