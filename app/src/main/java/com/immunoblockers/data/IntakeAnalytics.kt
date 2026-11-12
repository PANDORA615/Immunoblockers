package com.immunoblockers.data

import java.math.BigDecimal
import java.time.LocalDate

data class IntakeDay(val date: LocalDate, val planned: Int, val taken: Int, val skipped: Int, val unmarked: Int, val waiting: Int)
data class MedicationTotal(val medicationId: String, val name: String, val unit: String, val count: Int, val amount: BigDecimal?)
object IntakeAnalytics {
    /** Dates refer to the original schedule, including doses snoozed across midnight. */
    fun days(rows: List<Occurrence>, from: LocalDate, through: LocalDate, today: LocalDate): List<IntakeDay> {
        if (from > through) return emptyList()
        val byDate = rows.groupBy { it.date }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= through }.map { date ->
            val group = byDate[date.toString()].orEmpty()
            val pending = group.count { it.state == Occurrence.PENDING }
            IntakeDay(date, group.size, group.count { it.state == Occurrence.TAKEN },
                group.count { it.state == Occurrence.SKIPPED }, if (date < today) pending else 0,
                if (date >= today) pending else 0)
        }.toList()
    }
    /** Only confirmed doses, grouped by identity, historical name and unit; never mix mg and ml. */
    fun totals(rows: List<Occurrence>): List<MedicationTotal> = rows.filter { it.state == Occurrence.TAKEN }
        .groupBy { Triple(it.medicationId, it.name, it.unit) }.map { (key, group) ->
            val quantities = group.map { it.dose.replace(',', '.').toBigDecimalOrNull() }
            val amount = if (quantities.any { it == null || it.signum() <= 0 }) null
                else quantities.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add)
            MedicationTotal(key.first, key.second, key.third, group.size, amount)
        }.sortedWith(compareBy({ it.name.lowercase() }, { it.unit }, { it.medicationId }))
}
fun decimalLabel(value: BigDecimal) = value.stripTrailingZeros().toPlainString().replace('.', ',')
