package com.template

import android.util.Log

const val GLOBAL_TAG_LOG = "GLOBAL_TAG_LOG"
fun log(aMessage: String){
    Log.d(GLOBAL_TAG_LOG, aMessage)
}