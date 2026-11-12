package com.immunoblockers.features

import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.immunoblockers.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseUpdate(val version:String,val url:String,val sha256:String,val size:Long,val notes:String)
object Updates {
    private val hosts=setOf("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com")
    fun newer(candidate:String,current:String):Boolean {
        fun parse(s:String)=s.removePrefix("v").split('.').takeIf {it.size==3}?.map {it.toIntOrNull()?:return null}
        val a=parse(candidate)?:return false;val b=parse(current)?:return false
        for(i in 0..2)if(a[i]!=b[i])return a[i]>b[i]
        return false
    }
    private fun connect(url:String):HttpURLConnection {
        var next=URL(url)
        repeat(6){
            require(next.protocol=="https"&&next.host in hosts)
            val c=next.openConnection() as HttpURLConnection
            c.connectTimeout=15000;c.readTimeout=60000;c.instanceFollowRedirects=false
            c.setRequestProperty("User-Agent","Immunoblockers/${BuildConfig.VERSION_NAME}")
            if(c.responseCode in 300..399){val location=c.getHeaderField("Location")?:error("redirect");c.disconnect();next=URL(next,location)}
            else {if(c.responseCode!=200){c.disconnect();error("HTTP")};return c}
        }
        error("redirect")
    }
    fun check():ReleaseUpdate? {
        val c=connect("https://api.github.com/repos/PANDORA615/Immunoblockers/releases/latest")
        val json=try {c.inputStream.bufferedReader().use {it.readText().also {s->require(s.length<2_000_000)}}}finally{c.disconnect()}
        val r=JSONObject(json);val version=r.getString("tag_name")
        if(r.optBoolean("draft")||r.optBoolean("prerelease")||!newer(version,BuildConfig.VERSION_NAME))return null
        val assets=r.getJSONArray("assets")
        val expected="Immunoblockers-${version.removePrefix("v")}-universal.apk"
        val asset=(0 until assets.length()).map {assets.getJSONObject(it)}.single {it.getString("name")==expected}
        val digest=asset.getString("digest").removePrefix("sha256:")
        require(digest.matches(Regex("[0-9a-f]{64}")))
        val url=asset.getString("browser_download_url")
        require(url.startsWith("https://github.com/PANDORA615/Immunoblockers/releases/download/"))
        return ReleaseUpdate(version,url,digest,asset.getLong("size"),r.optString("body"))
    }
    fun download(context:Context,release:ReleaseUpdate,progress:(Float)->Unit):File {
        require(release.size in 1..100_000_000)
        val folder=File(context.cacheDir,"updates").apply {mkdirs()}
        val temp=File(folder,"download.apk");val apk=File(folder,"update.apk")
        try {
            val c=connect(release.url)
            val hash=MessageDigest.getInstance("SHA-256")
            var count=0L
            try {c.inputStream.use {input->temp.outputStream().use {out->
                val buffer=ByteArray(32768)
                while(true){val n=input.read(buffer);if(n<0)break;count+=n;require(count<=release.size);hash.update(buffer,0,n);out.write(buffer,0,n);progress(count.toFloat()/release.size)}
            }}}finally{c.disconnect()}
            require(count==release.size&&hash.digest().joinToString(""){"%02x".format(it)}==release.sha256)
            verify(context,temp)
            if(apk.exists())check(apk.delete())
            check(temp.renameTo(apk));return apk
        }catch(e:Exception){temp.delete();throw e}
    }
    @Suppress("DEPRECATION")
    internal fun verify(context:Context,file:File) {
        val pm=context.packageManager
        val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val remote=pm.getPackageArchiveInfo(file.absolutePath,flags)?:error("APK")
        val installed=pm.getPackageInfo(context.packageName,flags)
        require(remote.packageName==context.packageName)
        val newer=if(Build.VERSION.SDK_INT>=28)remote.longVersionCode>installed.longVersionCode else remote.versionCode>installed.versionCode
        require(newer)
        val a=if(Build.VERSION.SDK_INT>=28)remote.signingInfo!!.apkContentsSigners else remote.signatures
        val b=if(Build.VERSION.SDK_INT>=28)installed.signingInfo!!.apkContentsSigners else installed.signatures
        require(a!=null&&b!=null&&a.size==1&&b.size==1&&a[0]==b[0])
    }
    fun install(context:Context,file:File) {
        if(!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))
            return
        }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
