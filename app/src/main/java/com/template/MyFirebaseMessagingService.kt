package com.template

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


class MyFirebaseMessagingService: FirebaseMessagingService() {
    private val TAG = "MyFirebaseMsgService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Обрабатываем полученное сообщение
        Log.d(TAG, "From: " + remoteMessage.from)
        Log.d(TAG, "Notification Message Body: " + remoteMessage.notification!!.body)
    }

    override fun onNewToken(token: String) {
        // Обновляем токен Firebase Cloud Messaging
        Log.d(TAG, "New Token: $token")
    }
}