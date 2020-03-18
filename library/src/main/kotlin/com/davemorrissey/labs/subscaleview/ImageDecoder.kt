package com.davemorrissey.labs.subscaleview

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.lang.Exception

interface ImageDecoder {
    @Throws(Exception::class)
    fun decode(context: Context, uri: Uri): Bitmap?
}
