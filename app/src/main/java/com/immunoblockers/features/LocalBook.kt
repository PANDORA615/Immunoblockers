package com.immunoblockers.features

import android.content.Context
import com.immunoblockers.data.*
import org.json.*
import java.math.BigDecimal

data class StockLine(val id:String,val name:String,val unit:String,val remaining:BigDecimal,val threshold:BigDecimal)
class LocalBook(context:Context) {
    private val prefs=context.getSharedPreferences("personal_book",Context.MODE_PRIVATE)
    @Synchronized fun setStock(med:Medication,amount:BigDecimal,threshold:BigDecimal,rows:List<Occurrence>) {
        require(amount.signum()>=0&&threshold.signum()>=0)
        val root=JSONObject(prefs.getString("stocks","{}")!!)
        root.put(med.id,JSONObject().put("name",med.name).put("unit",med.unit).put("amount",amount.toPlainString()).put("threshold",threshold.toPlainString())
            .put("baseline",JSONArray(rows.filter {it.medicationId==med.id&&it.state==Occurrence.TAKEN}.map {it.id})))
        prefs.edit().putString("stocks",root.toString()).apply()
    }
    @Synchronized fun stocks(rows:List<Occurrence>):List<StockLine> {
        val root=JSONObject(prefs.getString("stocks","{}")!!)
        return root.keys().asSequence().map {id->
            val s=root.getJSONObject(id);val base=s.getJSONArray("baseline");val known=(0 until base.length()).map {base.getString(it)}.toSet()
            val used=rows.filter {it.medicationId==id&&it.unit==s.getString("unit")&&it.state==Occurrence.TAKEN&&it.id !in known}
                .fold(BigDecimal.ZERO){acc,row->acc+(row.dose.toBigDecimalOrNull()?:BigDecimal.ZERO)}
            StockLine(id,s.getString("name"),s.getString("unit"),s.getString("amount").toBigDecimal()-used,s.getString("threshold").toBigDecimal())
        }.toList()
    }
    fun removeStock(id:String){val root=JSONObject(prefs.getString("stocks","{}")!!);root.remove(id);prefs.edit().putString("stocks",root.toString()).apply()}
}
