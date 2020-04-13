package com.davemorrissey.labs.subscaleview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.AnyThread
import java.lang.Exception

interface ImageRegionDecoder {
    fun isReady(): Boolean

    @Throws(Exception::class)
    fun init(context: Context, uri: Uri): Point

    @Throws(Exception::class)
    @AnyThread
    fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap

    fun recycle()
}
