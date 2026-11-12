package com.immunoblockers.features

import android.content.Context
import android.security.keystore.*
import android.util.Base64
import com.immunoblockers.data.*
import org.json.*
import java.net.*
import java.security.*
import javax.crypto.*
import javax.crypto.spec.*
import java.util.UUID

object PairCipher {
    fun seal(key:ByteArray,text:String,aad:ByteArray=byteArrayOf()):ByteArray {
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"))
        c.updateAAD(aad);return c.iv+c.doFinal(text.toByteArray(Charsets.UTF_8))
    }
    fun open(key:ByteArray,data:ByteArray,aad:ByteArray=byteArrayOf()):String {
        require(data.size>=28)
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,data.copyOfRange(0,12)))
        c.updateAAD(aad);return c.doFinal(data.copyOfRange(12,data.size)).toString(Charsets.UTF_8)
    }
}
class PairLink(private val context:Context,storeName:String="pair") {
    private val p=context.getSharedPreferences(storeName,Context.MODE_PRIVATE)
    private fun b64(b:ByteArray)=Base64.encodeToString(b,Base64.NO_WRAP or Base64.URL_SAFE)
    private fun bytes(s:String)=Base64.decode(s,Base64.NO_WRAP or Base64.URL_SAFE)
    private fun master():javax.crypto.SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        return (store.getKey("pair-local",null) as? javax.crypto.SecretKey)?:KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("pair-local",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun protect(raw:ByteArray):String {val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,master());return b64(c.iv+c.doFinal(raw))}
    private fun unprotect(value:String):ByteArray {
        val data=bytes(value);val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE,master(),GCMParameterSpec(128,data.copyOfRange(0,12)))
        return c.doFinal(data.copyOfRange(12,data.size))
    }
    private fun key()=unprotect(p.getString("key",null)?:error("not linked"))
    private fun writer():String {
        val stored=p.getString("writer",null)
        val raw=if(stored!=null)unprotect(stored)else ByteArray(32).also {SecureRandom().nextBytes(it);p.edit().putString("writer",protect(it)).commit()}
        return raw.joinToString(""){"%02x".format(it)}
    }
    fun observe(change:()->Unit):()->Unit {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,_->change()}
        p.registerOnSharedPreferenceChangeListener(listener)
        return {p.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val stopped get()=p.getBoolean("stopped",false)
    val configured get()=p.contains("key")
    val server get()=p.getString("server","").orEmpty()
    val lastSync get()=p.getLong("last_sync",0)
    var shareMedicines:Boolean
        get()=p.getBoolean("share_meds",false)
        set(value){p.edit().putBoolean("share_meds",value).apply()}
    var shareMood:Boolean
        get()=p.getBoolean("share_mood",false)
        set(value){p.edit().putBoolean("share_mood",value).apply()}
    private val actor get()=p.getString("actor",null)?:UUID.randomUUID().toString().replace("-","").also {p.edit().putString("actor",it).commit()}
    private fun validate(url:String) {val u=URI(url);require(u.scheme=="https"&&!u.host.isNullOrBlank()&&u.userInfo==null&&u.query==null&&u.fragment==null&&(u.path.isNullOrEmpty()||u.path=="/"))}
    fun create(url:String):String {
        validate(url);check(!configured)
        val k=ByteArray(32).also {SecureRandom().nextBytes(it)}
        p.edit().putString("server",url.trimEnd('/')).putString("room",UUID.randomUUID().toString().replace("-",""))
            .putString("key",protect(k)).putBoolean("stopped",false).putBoolean("share_meds",false).putBoolean("share_mood",false).commit()
        return invite()
    }
    fun invite():String = "IBPAIR1:"+b64(JSONObject().put("server",server).put("room",p.getString("room","")).put("key",b64(key())).toString().toByteArray())
    fun join(invite:String) {
        check(!configured);require(invite.startsWith("IBPAIR1:")&&invite.length<4096)
        val j=JSONObject(bytes(invite.removePrefix("IBPAIR1:")).toString(Charsets.UTF_8))
        val url=j.getString("server");validate(url);val k=bytes(j.getString("key"));require(k.size==32)
        val room=j.getString("room");require(room.matches(Regex("[a-f0-9]{32}")))
        p.edit().putString("server",url.trimEnd('/')).putString("room",room).putString("key",protect(k)).putBoolean("stopped",false).putBoolean("share_meds",false).putBoolean("share_mood",false).commit()
    }
    private fun request(method:String,data:JSONObject?=null):JSONObject {
        val k=key();val token=MessageDigest.getInstance("SHA-256").digest("relay-auth".toByteArray()+k).joinToString(""){"%02x".format(it)}
        validate(server)
        val c=URL("$server/rooms/${p.getString("room","")}").openConnection() as java.net.HttpURLConnection
        c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=15000;c.instanceFollowRedirects=false
        c.setRequestProperty("Authorization","Bearer $token");if(method=="PUT")c.setRequestProperty("X-Actor-Key",writer());c.setRequestProperty("Content-Type","application/json")
        try {
            if(data!=null){val body=data.toString().toByteArray();c.doOutput=true;c.setFixedLengthStreamingMode(body.size);c.outputStream.use {it.write(body)}}
            if(method=="DELETE"&&c.responseCode==404)return JSONObject()
            if(c.responseCode==410){p.edit().putBoolean("stopped",true).remove("partner").apply();error("revoked")}
            check(c.responseCode==200)
            return c.inputStream.bufferedReader().use {JSONObject(it.readText().also {s->require(s.length<500000)})}
        }finally{c.disconnect()}
    }
    @Synchronized fun sync(rows:List<Occurrence>,meds:List<Medication>) {
        if(!configured||stopped)return
        val book=LocalBook(context);val now=System.currentTimeMillis()
        val sequence=p.getLong("sequence",0)+1
        p.edit().putLong("sequence",sequence).commit()
        val j=JSONObject().put("sequence",sequence).put("updated",now).put("care",book.care).put("careAt",book.careAt)
        if(shareMood)j.put("mood",book.mood).put("note",book.note).put("moodAt",book.moodAt)
        if(shareMedicines){
            j.put("medicines",JSONArray(meds.filter {!it.archived}.map {JSONObject().put("name",it.name).put("dose",it.dose).put("unit",it.unit).put("paused",it.paused).put("times",it.times).put("days",it.days)}))
            j.put("records",JSONArray(rows.filter {it.markedAt!=null||it.date==java.time.LocalDate.now().toString()}.sortedByDescending {it.markedAt}.take(30).map {JSONObject().put("name",it.name).put("dose",it.dose).put("unit",it.unit).put("state",it.state).put("time",it.markedAt)}))
        }
        request("PUT",JSONObject().put("actor",actor).put("payload",b64(PairCipher.seal(key(),j.toString(),"${p.getString("room","")}|$actor".toByteArray()))))
        val slots=request("GET").getJSONArray("slots")
        val other=(0 until slots.length()).map {slots.getJSONObject(it)}.firstOrNull {it.getString("actor")!=actor}
        val edit=p.edit().putLong("last_sync",now)
        if(other!=null) {
            val plaintext=PairCipher.open(key(),bytes(other.getString("payload")),"${p.getString("room","")}|${other.getString("actor")}".toByteArray())
            val incoming=JSONObject(plaintext)
            if(incoming.optLong("sequence")>=(partner()?.optLong("sequence")?:0))edit.putString("partner",protect(plaintext.toByteArray()))
        }
        else edit.remove("partner")
        edit.apply()
    }
    fun partner():JSONObject? = runCatching {
        val data=bytes(p.getString("partner",null)?:return null)
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,master(),GCMParameterSpec(128,data.copyOfRange(0,12)))
        JSONObject(c.doFinal(data.copyOfRange(12,data.size)).toString(Charsets.UTF_8))
    }.getOrNull()
    @Synchronized fun disconnect() {
        p.edit().putBoolean("stopped",true).remove("partner").commit()
        shareMedicines=false;shareMood=false
        request("DELETE");p.edit().clear().commit()
    }
}
