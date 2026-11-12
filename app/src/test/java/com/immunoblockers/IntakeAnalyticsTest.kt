package com.immunoblockers

import com.immunoblockers.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class IntakeAnalyticsTest {
    private val today=LocalDate.of(2026,11,12)
    private fun row(id:String,med:String="a",dose:String="1.25",unit:String="мг",state:String=Occurrence.TAKEN,date:String="2026-11-12")=
        Occurrence(id,med,1,date,"09:00",0,"Лекарство",dose,unit,state,if(state==Occurrence.PENDING)null else 1)
    @Test fun totalsOnlyIncludeConfirmedDosesAndKeepUnitsSeparate() {
        val result=IntakeAnalytics.totals(listOf(row("1"),row("2",dose="0.75"),row("3",state=Occurrence.SKIPPED),row("4",state=Occurrence.PENDING),row("5",unit="мл")))
        assertEquals(2,result.size)
        val mg=result.first {it.unit=="мг"}
        assertEquals(2,mg.count);assertEquals("2",decimalLabel(mg.amount!!))
        assertEquals("1,25",decimalLabel(result.first {it.unit=="мл"}.amount!!))
    }
    @Test fun identicalNamesDoNotMergeDifferentMedicines() {
        assertEquals(2,IntakeAnalytics.totals(listOf(row("1"),row("2",med="b"))).size)
    }
    @Test fun invalidSnapshotNeverBecomesAZeroDose() {
        assertNull(IntakeAnalytics.totals(listOf(row("1",dose="bad"))).single().amount)
    }
    @Test fun pendingTodayDiffersFromNoMarkYesterday() {
        val result=IntakeAnalytics.days(listOf(row("1",state=Occurrence.PENDING,date="2026-11-11"),row("2",state=Occurrence.PENDING)),today.minusDays(1),today,today)
        assertEquals(1,result[0].unmarked);assertEquals(0,result[0].waiting)
        assertEquals(0,result[1].unmarked);assertEquals(1,result[1].waiting)
    }
    @Test fun chartFillsEmptyDatesAndKeepsOriginalDateAcrossMidnight() {
        val r=row("1",date="2026-11-11").copy(markedAt=Long.MAX_VALUE,snoozedUntil=Long.MAX_VALUE)
        val days=IntakeAnalytics.days(listOf(r),today.minusDays(2),today,today)
        assertEquals(3,days.size);assertEquals(0,days[0].planned);assertEquals(1,days[1].taken);assertEquals(0,days[2].taken)
    }
    @Test fun decimalAmountsStayExact() {
        assertEquals("0,3",decimalLabel(IntakeAnalytics.totals(listOf(row("1",dose="0.1"),row("2",dose="0,2"))).single().amount!!))
    }
}
