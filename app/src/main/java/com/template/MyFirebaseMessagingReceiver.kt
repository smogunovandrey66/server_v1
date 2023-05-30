package com.template

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


class MyFirebaseMessagingReceiver: FirebaseMessagingService() {
    private val TAG = "MyFirebaseMsgReceiver"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Обрабатываем полученное сообщение
        Log.d(TAG, "From: " + remoteMessage.from)
        Log.d(TAG, "Notification Message Body: " + remoteMessage.notification!!.body)

        // Отправляем уведомление
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // ... Другой код вашего получателя
    }

    override fun onNewToken(token: String) {
        // Обновляем токен Firebase Cloud Messaging
        Log.d(TAG, "New Token: $token")
    }
}