package com.davemorrissey.labs.subscaleview

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlin.Exception

interface ImageDecoder {
    @Throws(Exception::class)
    fun decode(context: Context, uri: Uri, orientationDegrees: Int): Bitmap?
}
