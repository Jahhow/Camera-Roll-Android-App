package com.davemorrissey.labs.subscaleview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.Paint.Style
import android.net.Uri
import android.os.AsyncTask
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.Interpolator
import android.widget.ImageView
import java.io.File
import java.io.UnsupportedEncodingException
import java.lang.ref.WeakReference
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.PI

open class SubsamplingScaleImageView @JvmOverloads constructor(context: Context, attr: AttributeSet? = null) : ImageView(context, attr) {
    companion object {
        const val FILE_SCHEME = "file://"
        const val ASSET_PREFIX = "$FILE_SCHEME/android_asset/"
        const val debug = true

        private val TAG = SubsamplingScaleImageView::class.simpleName

        private const val ORIENTATION_0 = 0
        private const val ORIENTATION_90 = 90
        private const val ORIENTATION_180 = 180
        private const val ORIENTATION_270 = 270

        const val SCALE_TYPE_CENTER_CROP = 2

        private val interpolator = SigmoidInterpolator()
        private val easeOutInterpolator = SigmoidInterpolator(7.0, 0.0)

        private const val TILE_SIZE_AUTO = Integer.MAX_VALUE
        private const val ANIMATION_DURATION = 366L
        private const val FLING_DURATION = 1000L
        private val ROTATION_THRESHOLD = Math.toRadians(10.0).toFloat()
    }

    var maxScale = 2f
    var isOneToOneZoomEnabled = false
    var rotationEnabled = true
    var triggeredRotation = false
    var eagerLoadingEnabled = false
    var onImageEventListener: OnImageEventListener? = null
    var doubleTapZoomScale = 1f
    var bitmapDecoderFactory: DecoderFactory<out ImageDecoder> = CompatDecoderFactory(SkiaImageDecoder::class.java)
    var regionDecoderFactory: DecoderFactory<out ImageRegionDecoder> = CompatDecoderFactory(SkiaImageRegionDecoder::class.java)
    var scale = 0f
    var sWidth = 0
    var sHeight = 0
    var orientation = ORIENTATION_0
    var minimumScaleType = 0;
    var retainXSwipe = true
    var retainYSwipe = false

    private var vCenterX = 0f
    private var vCenterY = 0f

    private var bitmap: Bitmap? = null
    private var uri: Uri? = null
    private var fullImageSampleSize = 0
    private var tileMap: MutableMap<Int, List<Tile>>? = null
    private var minimumTileDpi = -1
    private var maxTileWidth = TILE_SIZE_AUTO
    private var maxTileHeight = TILE_SIZE_AUTO
    private var scaleStart = 0f

    private var rotationRadian = 0f
    private var cos = Math.cos(0.0)
    private var sin = Math.sin(0.0)

    private var vTranslate = PointF(0f, 0f)
    private var vTranslateStart: PointF? = null
    private var diffMove = PointF(0f, 0f)

    private var pendingScale: Float? = null
    private var sPendingCenter: PointF? = null

    private var sOrientation = 0

    private var twoFingerZooming = false
    private var isPanning = false
    private var isQuickScaling = false
    private var maxTouchCount = 0
    private var did2FingerZoomIn = false
    private var prevDegreesInt = 0

    private var detector: GestureDetector? = null

    private var decoder: ImageRegionDecoder? = null
    private val decoderLock = ReentrantReadWriteLock(true)

    private var sCenterStart: PointF? = null
    private var vCenterStart: PointF? = null
    private var vCenterStartNow: PointF? = null
    private var vCenterBefore = PointF()
    private var vDistStart = 0f
    private var originRadian = 0f

    private val quickScaleThreshold: Float
    private var quickScaleLastDistance = 0f
    private var quickScaleMoved = false
    private var quickScaleVLastPoint: PointF? = null
    private var quickScaleSCenter: PointF? = null
    private var quickScaleVStart: PointF? = null

    private var anim: Anim? = null
    var isReady = false
        private set
    var isImageLoaded = false
        private set
    private var recycleOtherSampleSize = false
    private var recycleOtherTiles = false

    private var bitmapPaint: Paint? = null
    private var debugTextPaint: Paint? = null
    private var debugLinePaint: Paint? = null

    private var satTemp = ScaleTranslateRotate(0f, PointF(0f, 0f), 0f)
    private var objectMatrix = Matrix()
    private val srcArray = FloatArray(8)
    private val dstArray = FloatArray(8)

    private val density = resources.displayMetrics.density

    init {
        setMinimumDpi(160)
        setDoubleTapZoomDpi(160)
        setMinimumTileDpi(320)
        setGestureDetector(context)
        quickScaleThreshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, context.resources.displayMetrics)
    }

    private fun getIsBaseLayerReady(): Boolean {
        if (bitmap != null) {
            return true
        } else if (tileMap != null) {
            var baseLayerReady = true
            for (tile in tileMap!![fullImageSampleSize]!!) {
                if (tile.bitmap == null) {
                    baseLayerReady = false
                    break
                }
            }
            return baseLayerReady
        }
        return false
    }

    private fun getRequiredRotation() = orientation

    fun getCenter(): PointF? {
        return viewToSourceCoord(vCenterX, vCenterY)
    }

    @JvmOverloads
    fun setImage(path: String, tile: Boolean = true) {
        reset(true)

        var newPath = path
        if (!newPath.contains("://")) {
            if (newPath.startsWith("/")) {
                newPath = path.substring(1)
            }
            newPath = "$FILE_SCHEME/$newPath"
        }

        if (newPath.startsWith(FILE_SCHEME)) {
            val uriFile = File(newPath.substring(FILE_SCHEME.length))
            if (!uriFile.exists()) {
                try {
                    newPath = URLDecoder.decode(newPath, "UTF-8")
                } catch (e: UnsupportedEncodingException) {
                }
            }
        }

        uri = Uri.parse(newPath)
        execute(
                if (tile) TilesInitTask(this, context, regionDecoderFactory, uri!!)
                else BitmapLoadTask(this, context, bitmapDecoderFactory, uri!!)
        )
    }

    private fun reset(newImage: Boolean) {
        scale = 0f
        scaleStart = 0f
        rotationRadian = 0f
        vTranslate.set(0f, 0f)
        vTranslateStart = null
        pendingScale = null
        sPendingCenter = null
        twoFingerZooming = false
        isPanning = false
        isQuickScaling = false
        maxTouchCount = 0
        fullImageSampleSize = 0
        sCenterStart = null
        vCenterStart = null
        vCenterStartNow = null
        vDistStart = 0f
        originRadian = 0f
        quickScaleLastDistance = 0f
        quickScaleMoved = false
        quickScaleSCenter = null
        quickScaleVLastPoint = null
        quickScaleVStart = null
        anim = null

        if (newImage) {
            uri = null
            decoderLock.writeLock().lock()
            try {
                decoder?.recycle()
                decoder = null
            } finally {
                decoderLock.writeLock().unlock()
            }

            if (bitmap != null) {
                bitmap!!.recycle()
                bitmap = null
            }

            prevDegreesInt = 0
            sWidth = 0
            sHeight = 0
            sOrientation = 0
            isReady = false
            isImageLoaded = false
            cos = Math.cos(0.0)
            sin = Math.sin(0.0)
        }

        tileMap?.values?.forEach {
            for (tile in it) {
                tile.visible = false
                tile.bitmap?.recycle()
                tile.bitmap = null
            }
        }
        tileMap = null
        setGestureDetector(context)
    }

    private fun setGestureDetector(context: Context) {
        detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                if (isReady && e1 != null && e2 != null && (Math.abs(e1.x - e2.x) > 50 || Math.abs(e1.y - e2.y) > 50) && (Math.abs(velocityX) > 500 || Math.abs(velocityY) > 500) && !did2FingerZoomIn) {
                    val vX = (velocityX * cos - velocityY * -sin).toFloat()
                    val vY = (velocityX * -sin + velocityY * cos).toFloat()

                    val vTranslateEnd = PointF(vTranslate.x + vX * 0.25f, vTranslate.y + vY * 0.25f)
                    val targetSFocus = vTranslateEnd.apply {
                        x = (vCenterX - x) / scale
                        y = (vCenterY - y) / scale
                    }
                    AnimationBuilder(targetSFocus, getClosestRightAngle(Math.toDegrees(rotationRadian.toDouble()))).apply {
                        interruptible = true
                        interpolator = easeOutInterpolator
                        duration = FLING_DURATION
                        start()
                    }
                    return true
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                performClick()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (isReady) {
                    setGestureDetector(context)
                    vCenterStart = PointF(event.x, event.y)
                    vTranslateStart = PointF(vTranslate.x, vTranslate.y)
                    scaleStart = scale
                    isQuickScaling = true
                    quickScaleLastDistance = -1f
                    quickScaleSCenter = viewToSourceCoord(vCenterStart!!)
                    quickScaleVStart = PointF(event.x, event.y)
                    quickScaleVLastPoint = PointF(quickScaleSCenter!!.x, quickScaleSCenter!!.y)
                    quickScaleMoved = false
                    return false
                }
                return super.onDoubleTapEvent(event)
            }
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val sCenter = getCenter()
        if (isReady && sCenter != null) {
            stopAnimation()
            pendingScale = scale
            sPendingCenter = sCenter
        }
    }

    override fun layout(l: Int, t: Int, r: Int, b: Int) {
        super.layout(l, t, r, b)
        vCenterX = width / 2f
        vCenterY = height / 2f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpecMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSpecMode = MeasureSpec.getMode(heightMeasureSpec)
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val resizeWidth = widthSpecMode != MeasureSpec.EXACTLY
        val resizeHeight = heightSpecMode != MeasureSpec.EXACTLY
        var width = parentWidth
        var height = parentHeight
        if (sWidth > 0 && sHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = sWidth()
                height = sHeight()
            } else if (resizeHeight) {
                height = (sHeight().toDouble() / sWidth().toDouble() * width).toInt()
            } else if (resizeWidth) {
                width = (sWidth().toDouble() / sHeight().toDouble() * height).toInt()
            }
        }
        width = Math.max(width, suggestedMinimumWidth)
        height = Math.max(height, suggestedMinimumHeight)
        setMeasuredDimension(width, height)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector?.onTouchEvent(event)
        if (vTranslateStart == null) {
            vTranslateStart = PointF(0f, 0f)
        }

        if (sCenterStart == null) {
            sCenterStart = PointF(0f, 0f)
        }

        if (vCenterStart == null) {
            vCenterStart = PointF(0f, 0f)
        }

        if (vCenterStartNow == null) {
            vCenterStartNow = PointF(0f, 0f)
        }

        return onTouchEventInternal(event) || super.onTouchEvent(event)
    }

    private fun onTouchEventInternal(event: MotionEvent): Boolean {
        val touchCount = event.pointerCount
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                maxTouchCount = Math.max(maxTouchCount, touchCount)
                did2FingerZoomIn = false
                if (!isQuickScaling) {
                    vTranslateStart!!.set(vTranslate.x, vTranslate.y)
                    vCenterStart!!.set(event.x, event.y)
                }
                if (anim?.interruptible == true)
                    stopAnimation()
                return true
            }
            MotionEvent.ACTION_POINTER_1_DOWN, MotionEvent.ACTION_POINTER_2_DOWN -> {
                maxTouchCount = Math.max(maxTouchCount, touchCount)
                stopAnimation()
                triggeredRotation = false
                scaleStart = scale
                vDistStart = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                vTranslateStart!!.set(vTranslate)
                vCenterStart!!.set((event.getX(0) + event.getX(1)) / 2f, (event.getY(0) + event.getY(1)) / 2f)
                viewToSourceCoord(vCenterStart!!, sCenterStart!!)

                if (rotationEnabled) {
                    originRadian = Math.atan2((event.getY(0) - event.getY(1)).toDouble(), (event.getX(0) - event.getX(1)).toDouble()).toFloat()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                var consumed = false
                if (maxTouchCount > 0) {
                    if (touchCount >= 2) {
                        if (rotationEnabled) {
                            val angle = Math.atan2((event.getY(0) - event.getY(1)).toDouble(), (event.getX(0) - event.getX(1)).toDouble()).toFloat()
                            if (triggeredRotation) {
                                setRotationInternal(angle - originRadian)
                                consumed = true
                            } else {
                                if (Math.abs(diffRadian(angle, originRadian)) > ROTATION_THRESHOLD) {
                                    triggeredRotation = true
                                    originRadian = diffRadian(angle, rotationRadian)
                                }
                            }
                        }

                        val vDistEnd = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                        val vCenterEndX = (event.getX(0) + event.getX(1)) / 2f
                        val vCenterEndY = (event.getY(0) + event.getY(1)) / 2f
                        if (isPanning || distance(vCenterStart!!.x, vCenterEndX, vCenterStart!!.y, vCenterEndY) > 5 || Math.abs(vDistEnd - vDistStart) > 5) {
                            did2FingerZoomIn = true
                            twoFingerZooming = true
                            isPanning = true
                            consumed = true

                            val previousScale = scale.toDouble()
                            scale = vDistEnd / vDistStart * scaleStart

                            sourceToViewCoord(sCenterStart!!, vCenterStartNow!!)

                            val dx = vCenterEndX - vCenterStartNow!!.x
                            val dy = vCenterEndY - vCenterStartNow!!.y

                            val dxR = (dx * cos - dy * -sin).toFloat()
                            val dyR = (dx * -sin + dy * cos).toFloat()

                            vTranslate.x += dxR
                            vTranslate.y += dyR

                            if (previousScale * sHeight() < height && scale * sHeight() >= height || previousScale * sWidth() < width && scale * sWidth() >= width) {
                                vCenterStart!!.set(vCenterEndX, vCenterEndY)
                                vTranslateStart!!.set(vTranslate)
                                scaleStart = scale
                                vDistStart = vDistEnd
                            }

                            refreshRequiredTiles(eagerLoadingEnabled)
                        }
                    } else if (isQuickScaling) {
                        var dist = Math.abs(quickScaleVStart!!.y - event.y) * 2 + quickScaleThreshold

                        if (quickScaleLastDistance == -1f) {
                            quickScaleLastDistance = dist
                        }

                        val isUpwards = event.y > quickScaleVLastPoint!!.y
                        quickScaleVLastPoint!!.set(0f, event.y)

                        val spanDiff = Math.abs(1 - dist / quickScaleLastDistance) * 0.5f
                        if (spanDiff > 0.03f || quickScaleMoved) {
                            quickScaleMoved = true

                            var multiplier = 1f
                            if (quickScaleLastDistance > 0) {
                                multiplier = if (isUpwards) 1 + spanDiff else 1 - spanDiff
                            }

                            val previousScale = scale.toDouble()
                            scale = scale * multiplier

                            val vLeftStart = vCenterStart!!.x - vTranslateStart!!.x
                            val vTopStart = vCenterStart!!.y - vTranslateStart!!.y
                            val vLeftNow = vLeftStart * (scale / scaleStart)
                            val vTopNow = vTopStart * (scale / scaleStart)
                            vTranslate.x = vCenterStart!!.x - vLeftNow
                            vTranslate.y = vCenterStart!!.y - vTopNow
                            if (previousScale * sHeight() < height && scale * sHeight() >= height || previousScale * sWidth() < width && scale * sWidth() >= width) {
                                vCenterStart!!.set(sourceToViewCoord(quickScaleSCenter!!))
                                vTranslateStart!!.set(vTranslate)
                                scaleStart = scale
                                dist = 0f
                            }
                        }

                        quickScaleLastDistance = dist

                        refreshRequiredTiles(eagerLoadingEnabled)
                        consumed = true
                    } else if (!twoFingerZooming) {
                        val dx = event.x - vCenterStart!!.x
                        val dy = event.y - vCenterStart!!.y
                        val dxA = Math.abs(dx)
                        val dyA = Math.abs(dy)

                        val offset = density * 5
                        if (isPanning || dxA > offset || dyA > offset) {
                            consumed = true
                            val dxR = (dx * cos + dy * sin).toFloat()
                            val dyR = (dx * -sin + dy * cos).toFloat()

                            val lastX = vTranslate.x
                            val lastY = vTranslate.y

                            val newX = vTranslateStart!!.x + dxR
                            val newY = vTranslateStart!!.y + dyR

                            if (anim == null || !isPanning) {
                                vTranslate.x = newX
                                vTranslate.y = newY
                            }

                            if (isPanning) {
                                if (anim != null) {
                                    diffMove.x = dx
                                    diffMove.y = dy
                                    vCenterBefore.set(event.x, event.y)
                                }
                            } else {
                                val fullScale = getFullScale()
                                val leFullScale = (anim != null && anim!!.scaleEnd <= fullScale) || (anim == null && scale <= fullScale)
                                var edgeXSwipe: Boolean = dxA > dyA
                                var edgeYSwipe: Boolean = dyA > dxA
                                if (!leFullScale) {
                                    fitToBounds(false)
                                    val degrees = Math.toDegrees(rotationRadian.toDouble())
                                    val rightAngle = getClosestRightAngle(degrees)
                                    val atXEdge = if (rightAngle == 90.0 || rightAngle == 270.0) newY != satTemp.vTranslate.y else newX != satTemp.vTranslate.x
                                    val atYEdge = if (rightAngle == 90.0 || rightAngle == 270.0) newX != satTemp.vTranslate.x else newY != satTemp.vTranslate.y
                                    edgeXSwipe = edgeXSwipe && atXEdge
                                    edgeYSwipe = edgeYSwipe && atYEdge
                                }
                                edgeXSwipe = edgeXSwipe && retainXSwipe
                                edgeYSwipe = edgeYSwipe && retainYSwipe

                                if (edgeXSwipe || edgeYSwipe) {
                                    vTranslate.x = lastX
                                    vTranslate.y = lastY
                                    animateToBounds()
                                    maxTouchCount = 0
                                    parent?.requestDisallowInterceptTouchEvent(false)
                                } else {
                                    isPanning = true
                                    diffMove.set(0f, 0f)
                                }
                            }

                            refreshRequiredTiles(eagerLoadingEnabled)
                        }
                    }
                }

                if (consumed) {
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_1_UP, MotionEvent.ACTION_POINTER_2_UP -> {
                if (isQuickScaling) {
                    isQuickScaling = false
                    if (quickScaleMoved) {
                        animateToBounds()
                    } else {
                        doubleTapZoom(quickScaleSCenter)
                    }
                }

                if (touchCount == 1) {
                    if (did2FingerZoomIn || isPanning) {
                        animateToBounds()
                    }
                }

                if (maxTouchCount > 0 && (twoFingerZooming || isPanning)) {
                    if (touchCount == 2) {
                        twoFingerZooming = false
                        animateToBounds()
                        val i = if (event.actionIndex == 0) 1 else 0
                        vCenterStart!!.set(event.getX(i), event.getY(i))
                        vTranslateStart!!.set(vTranslate.x, vTranslate.y)
                        diffMove.set(0f, 0f)
                    } else if (touchCount == 1) {
                        maxTouchCount = 0
                        isPanning = false
                    }

                    if (anim == null)
                        refreshRequiredTiles(true)
                    return true
                }

                if (touchCount == 1) {
                    maxTouchCount = 0
                    isPanning = false
                }
                return true
            }
        }
        return false
    }

    /*
     * r1 and r2 must both be in [-PI,PI]
     * return an equivalent radian for r1 - r2 but in [-PI,PI] */
    private fun diffRadian(r1: Float, r2: Float): Float {
        var a = r1 - r2
        if (a > PI) a -= (2 * PI).toFloat()
        else if (a < -PI) a += (2 * PI).toFloat()
        return a
    }

    private fun getClosestRightAngle(degrees: Double) = Math.round(degrees / 90f) * 90.0

    private fun doubleTapZoom(sCenter: PointF?) {
        val fullScale = getFullScale()
        var doubleTapZoomScale = limitedScale(doubleTapZoomScale)
        if (doubleTapZoomScale <= fullScale) doubleTapZoomScale = 1f
        val isToFullScale = isZoomedOut() || anim?.scaleEnd == fullScale
        val targetScale =
                if (isOneToOneZoomEnabled) {
                    if (isToFullScale) {
                        doubleTapZoomScale
                    } else {
                        val isToDoubleTapScale = scale == doubleTapZoomScale || anim?.scaleEnd == doubleTapZoomScale
                        if (isToDoubleTapScale) {
                            1f
                        } else {
                            fullScale
                        }
                    }
                } else {
                    if (isToFullScale) doubleTapZoomScale else fullScale
                }
        AnimationBuilder(sCenter!!, targetScale, getClosestRightAngle(Math.toDegrees(rotationRadian.toDouble()))).start()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        createPaints()

        if (sWidth == 0 || sHeight == 0 || width == 0 || height == 0) {
            return
        }

        if (tileMap == null && decoder != null) {
            initialiseBaseLayer(getMaxBitmapDimensions(canvas))
        }

        if (!checkReady()) {
            return
        }

        fun getRotationDegrees() = Math.toDegrees(rotationRadian.toDouble()).toFloat()
        val degrees: Float
        if (anim != null) {
            val timeElapsed = System.currentTimeMillis() - anim!!.time
            val elapsed = Math.min(timeElapsed.toFloat() / anim!!.duration, 1f)
            val finished = elapsed == 1f
            val interpolation = anim!!.interpolator.getInterpolation(elapsed)
            scale = ease(interpolation, anim!!.scaleStart, anim!!.scaleEnd - anim!!.scaleStart, anim!!.scaleEnd)
            val newVFocusX = ease(interpolation, anim!!.vFocusStart!!.x, anim!!.vFocusEnd!!.x - anim!!.vFocusStart!!.x, anim!!.vFocusEnd!!.x)
            val newVFocusY = ease(interpolation, anim!!.vFocusStart!!.y, anim!!.vFocusEnd!!.y - anim!!.vFocusStart!!.y, anim!!.vFocusEnd!!.y)
            val rotation = ease(interpolation, anim!!.rotationStart, anim!!.rotationEnd - anim!!.rotationStart, anim!!.rotationEnd)
            setRotationInternal(rotation)
            degrees = getRotationDegrees()
            // Find out where the focal point is at this scale/rotation then adjust its position to follow the animation path
            val vFocus = sourceToViewCoord(anim!!.sFocus!!)
            var dX = vFocus.x - newVFocusX
            var dY = vFocus.y - newVFocusY
            if (isPanning) {
                dX -= diffMove.x
                dY -= diffMove.y
            }
            vTranslate.x -= (dX * cos + dY * sin).toFloat()
            vTranslate.y -= (dX * -sin + dY * cos).toFloat()

            if (finished) {
                stopAnimation()
                if (isPanning) {
                    vTranslateStart!!.set(vTranslate)
                    vCenterStart!!.set(vCenterBefore)
                }
                val degreesInt = Math.round(degrees)
                if (degreesInt != prevDegreesInt) {
                    var diff = degreesInt - prevDegreesInt
                    if (diff == 270) {
                        diff = -90
                    } else if (diff == -270) {
                        diff = 90
                    }
                    onImageEventListener?.onImageRotation(diff)
                    prevDegreesInt = degreesInt
                }
            }
            invalidate()
        } else {
            degrees = getRotationDegrees()
        }

        val tileMap = tileMap
        if (tileMap != null && getIsBaseLayerReady()) {
            val sampleSize = Math.min(fullImageSampleSize, calculateInSampleSize(scale))
            var drawFullImage = false
            if (sampleSize != fullImageSampleSize) {
                drawFullImage = anim != null
                if (!drawFullImage)
                    for (tile in tileMap[sampleSize]!!) {
                        if (tile.visible && tile.bitmap == null) {
                            drawFullImage = true
                            break
                        }
                    }
            }

            fun _setMatrixArray(tile: Tile) = when (getRequiredRotation()) {
                ORIENTATION_90 -> setMatrixArray(dstArray, tile.vRect!!.right, tile.vRect!!.top, tile.vRect!!.right, tile.vRect!!.bottom, tile.vRect!!.left, tile.vRect!!.bottom, tile.vRect!!.left, tile.vRect!!.top)
                ORIENTATION_180 -> setMatrixArray(dstArray, tile.vRect!!.right, tile.vRect!!.bottom, tile.vRect!!.left, tile.vRect!!.bottom, tile.vRect!!.left, tile.vRect!!.top, tile.vRect!!.right, tile.vRect!!.top)
                ORIENTATION_270 -> setMatrixArray(dstArray, tile.vRect!!.left, tile.vRect!!.bottom, tile.vRect!!.left, tile.vRect!!.top, tile.vRect!!.right, tile.vRect!!.top, tile.vRect!!.right, tile.vRect!!.bottom)
                else -> setMatrixArray(dstArray, tile.vRect!!.left, tile.vRect!!.top, tile.vRect!!.right, tile.vRect!!.top, tile.vRect!!.right, tile.vRect!!.bottom, tile.vRect!!.left, tile.vRect!!.bottom)
            }

            fun drawTiles(tiles: List<Tile>) {
                for (tile in tiles) {
                    if (tile.visible) {
                        sourceToViewRect(tile.sRect!!, tile.vRect!!)
                        if (tile.bitmap != null) {
                            objectMatrix.reset()
                            val bw = tile.bitmap!!.width.toFloat()
                            val bh = tile.bitmap!!.height.toFloat()
                            setMatrixArray(srcArray, 0f, 0f, bw, 0f, bw, bh, 0f, bh)
                            _setMatrixArray(tile)
                            objectMatrix.setPolyToPoly(srcArray, 0, dstArray, 0, 4)
                            canvas.drawBitmap(tile.bitmap!!, objectMatrix, bitmapPaint)
                        }
                        if (debug) {
                            canvas.drawRect(tile.vRect!!, debugLinePaint!!)
                            canvas.drawText("ISS ${tile.sampleSize} RECT ${tile.sRect!!.top}, ${tile.sRect!!.left}, ${tile.sRect!!.bottom}, ${tile.sRect!!.right}", (tile.vRect!!.left + px(5)), (tile.vRect!!.top + px(15)), debugTextPaint!!)
                            if (tile.loading) {
                                canvas.drawText("LOADING", tile.vRect!!.left + px(5), tile.vRect!!.top + px(35), debugTextPaint!!)
                            }
                        }
                    }
                }
            }

            canvas.rotate(degrees, vCenterX, vCenterY)
            if (drawFullImage) drawTiles(tileMap[fullImageSampleSize]!!)
            drawTiles(tileMap[sampleSize]!!)
            if (debug)
                canvas.rotate(-degrees, vCenterX, vCenterY)
        } else if (bitmap?.isRecycled == false) {
            val xScale = scale
            val yScale = scale

            objectMatrix.apply {
                reset()
                postScale(xScale, yScale)
                postRotate(getRequiredRotation().toFloat())
                postTranslate(vTranslate.x, vTranslate.y)

                when (getRequiredRotation()) {
                    ORIENTATION_90 -> postTranslate(scale * sHeight, 0f)
                    ORIENTATION_180 -> postTranslate(scale * sWidth, scale * sHeight)
                    ORIENTATION_270 -> postTranslate(0f, scale * sWidth)
                }
                postRotate(degrees, vCenterX, vCenterY)
            }

            canvas.drawBitmap(bitmap!!, objectMatrix, bitmapPaint)
        }

        if (debug) {
            canvas.drawText(String.format(Locale.ENGLISH, "Scale (%.2f - %.2f): %.2f", limitedScale(0f), maxScale, scale), px(5).toFloat(), px(15).toFloat(), debugTextPaint!!)
            canvas.drawText(String.format(Locale.ENGLISH, "Translate: %.0f, %.0f", vTranslate.x, vTranslate.y), px(5).toFloat(), px(30).toFloat(), debugTextPaint!!)
            canvas.drawText(String.format(Locale.ENGLISH, "Rotate: %.0f", Math.toDegrees(rotationRadian.toDouble())), px(5).toFloat(), px(45).toFloat(), debugTextPaint!!)
            val center = getCenter()
            canvas.drawText(String.format(Locale.ENGLISH, "Source Center: %.0f, %.0f", center!!.x, center.y), px(5).toFloat(), px(60).toFloat(), debugTextPaint!!)
            if (anim != null) {
                val vCenterStart = sourceToViewCoord(anim!!.svCenterStart!!)
                val vCenterEndRequested = sourceToViewCoord(anim!!.sCenterEndRequested!!)
                val vCenterEnd = sourceToViewCoord(anim!!.sFocus!!)

                canvas.drawCircle(vCenterStart!!.x, vCenterStart.y, px(10).toFloat(), debugLinePaint!!)
                debugLinePaint!!.color = Color.RED

                canvas.drawCircle(vCenterEndRequested!!.x, vCenterEndRequested.y, px(20).toFloat(), debugLinePaint!!)
                debugLinePaint!!.color = Color.BLUE

                canvas.drawCircle(vCenterEnd!!.x, vCenterEnd.y, px(25).toFloat(), debugLinePaint!!)
                debugLinePaint!!.color = Color.CYAN
                canvas.drawCircle(vCenterX, vCenterY, px(30).toFloat(), debugLinePaint!!)
            }

            if (vCenterStart != null) {
                debugLinePaint!!.color = Color.RED
                canvas.drawCircle(vCenterStart!!.x, vCenterStart!!.y, px(20).toFloat(), debugLinePaint!!)
            }

            if (quickScaleSCenter != null) {
                debugLinePaint!!.color = Color.BLUE
                val a = sourceToViewCoord(quickScaleSCenter!!)!!
                canvas.drawCircle(a.x, a.y, px(35).toFloat(), debugLinePaint!!)
            }

            if (quickScaleVStart != null && isQuickScaling) {
                debugLinePaint!!.color = Color.CYAN
                canvas.drawCircle(quickScaleVStart!!.x, quickScaleVStart!!.y, px(30).toFloat(), debugLinePaint!!)
            }

            debugLinePaint!!.color = Color.MAGENTA
        }
    }

    private fun setMatrixArray(array: FloatArray, f0: Float, f1: Float, f2: Float, f3: Float, f4: Float, f5: Float, f6: Float, f7: Float) {
        array[0] = f0
        array[1] = f1
        array[2] = f2
        array[3] = f3
        array[4] = f4
        array[5] = f5
        array[6] = f6
        array[7] = f7
    }

    private fun checkReady(): Boolean {
        if (isReady) {
            return true
        }

        isReady = width > 0 && height > 0 && sWidth > 0 && sHeight > 0 && (bitmap != null || getIsBaseLayerReady())
        if (isReady) {
            preDraw()
            onReady()
            onImageEventListener?.onReady()
        }
        return isReady
    }

    private fun setRotationInternal(rot: Float) {
        rotationRadian = rot % (Math.PI * 2).toFloat()
        if (rotationRadian < 0) {
            rotationRadian += (Math.PI * 2).toFloat()
        }

        val rotD = rot.toDouble()
        cos = Math.cos(rotD)
        sin = Math.sin(rotD)
    }

    private fun checkImageLoaded(): Boolean {
        if (isImageLoaded)
            return true

        isImageLoaded = getIsBaseLayerReady()
        if (isImageLoaded) {
            preDraw()
            onImageLoaded()
            onImageEventListener?.onImageLoaded()
        }
        return isImageLoaded
    }

    protected open fun onImageLoaded() {}

    fun setFilterBitmap(filterBitmap: Boolean) {
        createBitmapPaint()
        bitmapPaint!!.isFilterBitmap = filterBitmap
        invalidate()
    }

    open fun createBitmapPaint() {
        if (bitmapPaint == null) {
            bitmapPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }
        }
    }

    private fun createPaints() {
        createBitmapPaint()

        if (debug && (debugTextPaint == null || debugLinePaint == null)) {
            debugTextPaint = Paint().apply {
                textSize = px(12).toFloat()
                color = Color.MAGENTA
                style = Style.FILL
            }

            debugLinePaint = Paint().apply {
                color = Color.MAGENTA
                style = Style.STROKE
                strokeWidth = px(1).toFloat()
            }
        }
    }

    @Synchronized
    private fun initialiseBaseLayer(maxTileDimensions: Point) {
        debug("initialiseBaseLayer maxTileDimensions=${maxTileDimensions.x}x${maxTileDimensions.y}")

        scale = getFullScale()
        fitToBounds()

        fullImageSampleSize = calculateInSampleSize(satTemp.scale)
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2
        }

        if (uri == null) {
            return
        }

        if (fullImageSampleSize == 1 && sWidth() < maxTileDimensions.x && sHeight() < maxTileDimensions.y) {
            decoder!!.recycle()
            decoder = null
            val task = BitmapLoadTask(this, context, bitmapDecoderFactory, uri!!)
            execute(task)
        } else {
            initialiseTileMap(maxTileDimensions)

            val baseGrid = tileMap!![fullImageSampleSize]
            for (baseTile in baseGrid!!) {
                val task = TileLoadTask(this, decoder!!, baseTile)
                execute(task)
            }
            refreshRequiredTiles(true)
        }
    }

    private fun refreshRequiredTiles(load: Boolean) {
        if (decoder == null || tileMap == null) {
            return
        }

        val sampleSize = Math.min(fullImageSampleSize, calculateInSampleSize(scale))

        for ((iSampleSize, tiles) in tileMap!!) {
            if (iSampleSize != fullImageSampleSize) {
                if (iSampleSize == sampleSize)
                    for (tile in tiles) {
                        tile.visible = tileVisible(tile)
                        if (tile.visible) {
                            if (!tile.loading && tile.bitmap == null && load) {
                                val task = TileLoadTask(this, decoder!!, tile)
                                execute(task)
                            }
                        } else if (recycleOtherTiles) {
                            tile.bitmap?.recycle()
                            tile.bitmap = null
                        }
                    }
                else if (recycleOtherSampleSize) {
                    for (tile in tiles) {
                        tile.visible = false
                        tile.bitmap?.recycle()
                        tile.bitmap = null
                    }
                }
            }
        }
    }

    private fun tileVisible(tile: Tile): Boolean {
        if (this.rotationRadian == 0f) {
            val sVisLeft = viewToSourceX(0f)
            val sVisRight = viewToSourceX(width.toFloat())
            val sVisTop = viewToSourceY(0f)
            val sVisBottom = viewToSourceY(height.toFloat())
            return !(sVisLeft > tile.sRect!!.right || tile.sRect!!.left > sVisRight || sVisTop > tile.sRect!!.bottom || tile.sRect!!.top > sVisBottom)
        }

        val corners = arrayOf(
                sourceToViewCoord(tile.sRect!!.left, tile.sRect!!.top),
                sourceToViewCoord(tile.sRect!!.right, tile.sRect!!.top),
                sourceToViewCoord(tile.sRect!!.right, tile.sRect!!.bottom),
                sourceToViewCoord(tile.sRect!!.left, tile.sRect!!.bottom))

        for (pointF in corners) {
            if (pointF == null) {
                return false
            }
        }

        val rotation = this.rotationRadian

        return when {
            rotation < Math.PI / 2 -> !(corners[0]!!.y > height || corners[1]!!.x < 0 || corners[2]!!.y < 0 || corners[3]!!.x > width)
            rotation < Math.PI -> !(corners[3]!!.y > height || corners[0]!!.x < 0 || corners[1]!!.y < 0 || corners[2]!!.x > width)
            rotation < Math.PI * 3 / 2 -> !(corners[2]!!.y > height || corners[3]!!.x < 0 || corners[0]!!.y < 0 || corners[1]!!.x > width)
            else -> !(corners[1]!!.y > height || corners[2]!!.x < 0 || corners[3]!!.y < 0 || corners[0]!!.x > width)
        }
    }

    private fun preDraw() {
        if (width == 0 || height == 0 || sWidth <= 0 || sHeight <= 0) {
            return
        }

        if (sPendingCenter != null && pendingScale != null) {
            scale = pendingScale!!
            vTranslate.x = vCenterX - scale * sPendingCenter!!.x
            vTranslate.y = vCenterY - scale * sPendingCenter!!.y
            sPendingCenter = null
            pendingScale = null
            refreshRequiredTiles(true)
        }

        fitToBounds()
    }

    private fun calculateInSampleSize(scale: Float): Int {
        var newScale = scale
        if (minimumTileDpi > 0) {
            val metrics = resources.displayMetrics
            val averageDpi = (metrics.xdpi + metrics.ydpi) / 2f
            newScale *= minimumTileDpi / averageDpi
        }

        val reqWidth = (sWidth() * newScale).toInt()
        val reqHeight = (sHeight() * newScale).toInt()

        var inSampleSize = 1
        if (reqWidth == 0 || reqHeight == 0) {
            return 32
        }

        if (sHeight() > reqHeight || sWidth() > reqWidth) {
            val heightRatio = Math.round(sHeight().toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(sWidth().toFloat() / reqWidth.toFloat())
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }

        var power = 1
        while (power * 2 < inSampleSize) {
            power *= 2
        }

        if ((sWidth > 3000 || sHeight > 3000) && power == 2 && minimumTileDpi == 280 && scale == getFullScale()) {
            power = 4
        }

        return power
    }

    private fun fitToBounds(sat: ScaleTranslateRotate) {
        val vTranslate = sat.vTranslate
        val scale = limitedScale(sat.scale)
        val scaledSWidth = scale * sWidth()
        val scaledSHeight = scale * sHeight()
        val degrees = Math.toDegrees(rotationRadian.toDouble())
        val rightAngle = getClosestRightAngle(degrees)

        val width: Float
        val height: Float
        val wh: Float
        if (rightAngle == 90.0 || rightAngle == 270.0) {
            // Convert all variables to visual coordinates
            width = this.height.toFloat()
            height = this.width.toFloat()
            wh = (width - height) / 2f
            vTranslate.x += wh
            vTranslate.y -= wh
        } else {
            wh = 0f
            width = this.width.toFloat()
            height = this.height.toFloat()
        }

        val wSW = width - scaledSWidth
        val hSH = height - scaledSHeight

        // right, bottom
        vTranslate.x = Math.max(vTranslate.x, wSW - paddingRight)
        vTranslate.y = Math.max(vTranslate.y, hSH - paddingBottom)

        // left, top
        val maxTx = Math.max(paddingLeft.toFloat(), paddingLeft + (wSW - paddingRight - paddingLeft) / 2f)
        val maxTy = Math.max(paddingTop.toFloat(), paddingTop + (hSH - paddingBottom - paddingTop) / 2f)
        vTranslate.x = Math.min(vTranslate.x, maxTx)
        vTranslate.y = Math.min(vTranslate.y, maxTy)

        if (rightAngle == 90.0 || rightAngle == 270.0) {
            vTranslate.x -= wh
            vTranslate.y += wh
        }

        sat.scale = scale
    }

    private fun fitToBounds(apply: Boolean = true) {
        satTemp.scale = scale
        satTemp.vTranslate.set(vTranslate)
        satTemp.rotate = rotationRadian
        fitToBounds(satTemp)
        if (apply) {
            scale = satTemp.scale
            vTranslate.set(satTemp.vTranslate)
            setRotationInternal(satTemp.rotate)
        }
    }

    private fun animateToBounds() {
        val degrees = Math.toDegrees(rotationRadian.toDouble())
        val rightAngle = getClosestRightAngle(degrees)

        if (anim != null) {
            AnimationBuilder(anim!!).start()
        } else {
            val center = viewToSourceCoord(vCenterX, vCenterY)
            AnimationBuilder(center, rightAngle).start()
        }
    }

    private fun getFullScale(): Float {
        val degrees = Math.toDegrees(rotationRadian.toDouble()) + orientation
        val rightAngle = getClosestRightAngle(degrees) % 360
        val innerWidth = getInnerWidth()
        val innerHeight = getInnerHeight()
        return if (rightAngle == 0.0 || rightAngle == 180.0) {
            Math.min(innerWidth / sWidth.toFloat(), innerHeight / sHeight.toFloat())
        } else {
            Math.min(innerWidth / sHeight.toFloat(), innerHeight / sWidth.toFloat())
        }
    }

    fun getInnerWidth() = width - paddingLeft - paddingRight
    fun getInnerHeight() = height - paddingTop - paddingBottom

    private fun getCenterCropScale(): Float {
        val degrees = Math.toDegrees(rotationRadian.toDouble()) + orientation
        val rightAngle = getClosestRightAngle(degrees) % 360
        return if (rightAngle == 0.0 || rightAngle == 180.0) {
            Math.max(width / sWidth.toFloat(), height / sHeight.toFloat())
        } else {
            Math.max(width / sHeight.toFloat(), height / sWidth.toFloat())
        }
    }

    private fun getRotatedFullScale(): Float {
        val degrees = Math.toDegrees(rotationRadian.toDouble()) + orientation
        val rightAngle = getClosestRightAngle(degrees)
        return if (rightAngle % 360 == 0.0 || rightAngle == 180.0) {
            Math.min(width / sHeight.toFloat(), height / sWidth.toFloat())
        } else {
            Math.min(width / sWidth.toFloat(), height / sHeight.toFloat())
        }
    }

    private fun initialiseTileMap(maxTileDimensions: Point) {
        debug("initialiseTileMap maxTileDimensions=${maxTileDimensions.x}x${maxTileDimensions.y}")
        tileMap = LinkedHashMap()
        var sampleSize = fullImageSampleSize
        var xTiles = 1
        var yTiles = 1

        while (true) {
            var sTileWidth = sWidth() / xTiles
            var sTileHeight = sHeight() / yTiles
            var subTileWidth = sTileWidth / sampleSize
            var subTileHeight = sTileHeight / sampleSize
            while (subTileWidth + xTiles + 1 > maxTileDimensions.x || subTileWidth > width * 1.25 && sampleSize < fullImageSampleSize) {
                xTiles += 1
                sTileWidth = sWidth() / xTiles
                subTileWidth = sTileWidth / sampleSize
            }

            while (subTileHeight + yTiles + 1 > maxTileDimensions.y || subTileHeight > height * 1.25 && sampleSize < fullImageSampleSize) {
                yTiles += 1
                sTileHeight = sHeight() / yTiles
                subTileHeight = sTileHeight / sampleSize
            }

            val tileGrid = ArrayList<Tile>(xTiles * yTiles)
            for (x in 0 until xTiles) {
                for (y in 0 until yTiles) {
                    val tile = Tile()
                    tile.sampleSize = sampleSize
                    tile.visible = sampleSize == fullImageSampleSize
                    tile.vRect = RectF(0f, 0f, 0f, 0f)
                    tile.fileSRect = Rect(
                            x * sTileWidth,
                            y * sTileHeight,
                            if (x == xTiles - 1) sWidth() else (x + 1) * sTileWidth,
                            if (y == yTiles - 1) sHeight() else (y + 1) * sTileHeight)
                    tile.sRect = RectF(
                            tile.fileSRect!!.left.toFloat(),
                            tile.fileSRect!!.top.toFloat(),
                            tile.fileSRect!!.right.toFloat(),
                            tile.fileSRect!!.bottom.toFloat())
                    tileGrid.add(tile)
                }
            }
            tileMap!![sampleSize] = tileGrid
            if (sampleSize == 1) {
                break
            } else {
                sampleSize /= 2
            }
        }
    }

    private class TilesInitTask internal constructor(view: SubsamplingScaleImageView, context: Context, decoderFactory: DecoderFactory<out ImageRegionDecoder>, private val source: Uri) : AsyncTask<Void, Void, IntArray>() {
        private val viewRef = WeakReference(view)
        private val contextRef = WeakReference(context)
        private val decoderFactoryRef = WeakReference(decoderFactory)
        private var decoder: ImageRegionDecoder? = null
        private var exception: Exception? = null

        override fun doInBackground(vararg params: Void): IntArray? {
            try {
                val context = contextRef.get()
                val decoderFactory = decoderFactoryRef.get()
                val view = viewRef.get()
                if (context != null && decoderFactory != null && view != null) {
                    view.debug("TilesInitTask.doInBackground")
                    decoder = decoderFactory.make()
                    val dimensions = decoder!!.init(context, source)
                    val sWidth = dimensions.x
                    val sHeight = dimensions.y
                    val exifOrientation = view.orientation
                    return intArrayOf(sWidth, sHeight, exifOrientation)
                }
            } catch (e: Exception) {
                exception = e
            }

            return null
        }

        override fun onPostExecute(xyo: IntArray?) {
            val view = viewRef.get()
            if (view != null) {
                if (decoder != null && xyo != null && xyo.size == 3) {
                    view.onTilesInited(decoder!!, xyo[0], xyo[1], xyo[2])
                } else if (exception != null) {
                    view.onImageEventListener?.onImageLoadError(exception!!)
                }
            }
        }
    }

    @Synchronized
    private fun onTilesInited(decoder: ImageRegionDecoder, sWidth: Int, sHeight: Int, sOrientation: Int) {
        debug("onTilesInited sWidth=$sWidth, sHeight=$sHeight, sOrientation=$orientation")
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != sWidth || this.sHeight != sHeight)) {
            reset(false)
            bitmap?.recycle()
            bitmap = null
        }
        this.decoder = decoder
        this.sWidth = sWidth
        this.sHeight = sHeight
        this.sOrientation = sOrientation
        checkReady()
        if (!checkImageLoaded() && maxTileWidth > 0 && maxTileWidth != TILE_SIZE_AUTO && maxTileHeight > 0 && maxTileHeight != TILE_SIZE_AUTO && width > 0 && height > 0) {
            initialiseBaseLayer(Point(maxTileWidth, maxTileHeight))
        }

        invalidate()
        requestLayout()
    }

    private class TileLoadTask internal constructor(view: SubsamplingScaleImageView, decoder: ImageRegionDecoder, tile: Tile) : AsyncTask<Void, Void, Bitmap>() {
        private val viewRef = WeakReference(view)
        private val decoderRef = WeakReference(decoder)
        private val tileRef = WeakReference(tile)
        private var exception: Exception? = null

        init {
            tile.loading = true
        }

        override fun doInBackground(vararg params: Void): Bitmap? {
            val view = viewRef.get()
            try {
                val decoder = decoderRef.get()
                val tile = tileRef.get()
                if (decoder != null && tile != null && view != null && decoder.isReady() && tile.visible) {
                    view.debug("TileLoadTask.doInBackground, tile.sRect=${tile.sRect}, tile.sampleSize=${tile.sampleSize}")
                    view.decoderLock.readLock().lock()
                    try {
                        if (decoder.isReady()) {
                            view.fileSRect(tile.sRect!!, tile.fileSRect!!)
                            return decoder.decodeRegion(tile.fileSRect!!, tile.sampleSize)
                        } else {
                            tile.loading = false
                        }
                    } finally {
                        view.decoderLock.readLock().unlock()
                    }
                } else {
                    tile?.loading = false
                }
            } catch (e: Exception) {
                //Log.e(TAG, "Failed to decode tile $e")
                exception = e
            } catch (e: OutOfMemoryError) {
                //Log.e(TAG, "Failed to decode tile - OutOfMemoryError $e")
                exception = RuntimeException(e)
            }

            return null
        }

        override fun onPostExecute(bitmap: Bitmap?) {
            val view = viewRef.get()
            val tile = tileRef.get()
            if (view != null && tile != null) {
                if (bitmap != null) {
                    tile.bitmap = bitmap
                    tile.loading = false
                    view.onTileLoaded()
                } else if (exception?.cause is OutOfMemoryError) {
                    if (!view.recycleOtherSampleSize) {
                        view.recycleOtherSampleSize = true
                        view.refreshRequiredTiles(true)
                    } else if (!view.recycleOtherTiles) {
                        view.recycleOtherTiles = true
                        view.refreshRequiredTiles(true)
                    }
                }
            }
        }
    }

    @Synchronized
    private fun onTileLoaded() {
        debug("onTileLoaded")
        checkReady()
        checkImageLoaded()
        if (getIsBaseLayerReady()) {
            bitmap?.recycle()
            bitmap = null
        }
        invalidate()
    }

    private class BitmapLoadTask internal constructor(view: SubsamplingScaleImageView, context: Context, decoderFactory: DecoderFactory<out ImageDecoder>, private val source: Uri) : AsyncTask<Void, Void, Int>() {
        private val viewRef = WeakReference(view)
        private val contextRef = WeakReference(context)
        private val decoderFactoryRef = WeakReference(decoderFactory)
        private var bitmap: Bitmap? = null
        private var exception: Exception? = null

        override fun doInBackground(vararg params: Void): Int? {
            try {
                val context = contextRef.get()
                val decoderFactory = decoderFactoryRef.get()
                val view = viewRef.get()

                if (context != null && decoderFactory != null && view != null) {
                    view.debug("BitmapLoadTask.doInBackground")
                    bitmap = decoderFactory.make().decode(context, source)
                    return view.orientation
                }
            } catch (e: Exception) {
                //Log.e(TAG, "Failed to load bitmap", e)
                exception = e
            } catch (e: OutOfMemoryError) {
                //Log.e(TAG, "Failed to load bitmap - OutOfMemoryError $e")
                exception = RuntimeException(e)
            }

            return null
        }

        override fun onPostExecute(orientation: Int?) {
            val subsamplingScaleImageView = viewRef.get()
            if (bitmap != null && orientation != null) {
                subsamplingScaleImageView?.onImageLoaded(bitmap!!, orientation)
            } else if (exception != null) {
                subsamplingScaleImageView?.onImageEventListener?.onImageLoadError(exception!!)
            }
        }
    }

    @Synchronized
    private fun onImageLoaded(bitmap: Bitmap, sOrientation: Int) {
        debug("onImageLoaded")
        if (sWidth > 0 && sHeight > 0 && (sWidth != bitmap.width || sHeight != bitmap.height)) {
            reset(false)
        }

        this.bitmap?.recycle()
        this.bitmap = bitmap
        sWidth = bitmap.width
        sHeight = bitmap.height
        this.sOrientation = sOrientation
        val ready = checkReady()
        val imageLoaded = checkImageLoaded()
        if (ready || imageLoaded) {
            invalidate()
            requestLayout()
        }
    }

    private fun execute(asyncTask: AsyncTask<Void, Void, *>) {
        asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    fun setScaleAndCenter(scale: Float, sCenter: PointF) {
        this.scale = scale
        val vFocus = sourceToViewCoord(sCenter)
        val dX = vFocus!!.x - vCenterX
        val dY = vFocus.y - vCenterY
        vTranslate.x -= (dX * cos + dY * sin).toFloat()
        vTranslate.y -= (dX * -sin + dY * cos).toFloat()
        stopAnimation()
        invalidate()
    }

    fun getState(): ImageViewState? {
        return if (isReady)
            ImageViewState(scale, getCenter()!!, orientation)
        else null
    }

    fun setMaxTileSize(maxPixels: Int) {
        maxTileWidth = maxPixels
        maxTileHeight = maxPixels
    }

    fun setMaxTileSize(maxPixelsX: Int, maxPixelsY: Int) {
        maxTileWidth = maxPixelsX
        maxTileHeight = maxPixelsY
    }

    private fun getMaxBitmapDimensions(canvas: Canvas) = Point(Math.min(canvas.maximumBitmapWidth, maxTileWidth), Math.min(canvas.maximumBitmapHeight, maxTileHeight))

    private fun sWidth(): Int {
        val rotation = getRequiredRotation()
        return if (rotation == 90 || rotation == 270) {
            sHeight
        } else {
            sWidth
        }
    }

    private fun sHeight(): Int {
        val rotation = getRequiredRotation()
        return if (rotation == 90 || rotation == 270) {
            sWidth
        } else {
            sHeight
        }
    }

    private fun fileSRect(sRect: RectF, target: Rect) {
        when (getRequiredRotation()) {
            0 -> target.set(sRect.left.toInt(), sRect.top.toInt(), sRect.right.toInt(), sRect.bottom.toInt())
            90 -> target.set(sRect.top.toInt(), sHeight - sRect.right.toInt(), sRect.bottom.toInt(), sHeight - sRect.left.toInt())
            180 -> target.set(sWidth - sRect.right.toInt(), sHeight - sRect.bottom.toInt(), sWidth - sRect.left.toInt(), sHeight - sRect.top.toInt())
            else -> target.set(sWidth - sRect.bottom.toInt(), sRect.left.toInt(), sWidth - sRect.top.toInt(), sRect.right.toInt())
        }
    }

    private fun distance(x0: Float, x1: Float, y0: Float, y1: Float): Float {
        val x = x0 - x1
        val y = y0 - y1
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    fun recycle() {
        reset(true)
        bitmapPaint = null
        debugTextPaint = null
        debugLinePaint = null
    }

    private fun viewToSourceX(vx: Float, tx: Float = vTranslate.x, scale: Float = this.scale): Float {
        return (vx - tx) / scale
    }

    private fun viewToSourceY(vy: Float, ty: Float = vTranslate.y, scale: Float = this.scale): Float {
        return (vy - ty) / scale
    }

    fun viewToSourceCoord(vxy: PointF, sTarget: PointF) = viewToSourceCoord(vxy.x, vxy.y, sTarget)

    fun viewToSourceCoord(vxy: PointF) = viewToSourceCoord(vxy.x, vxy.y)

    @JvmOverloads
    fun viewToSourceCoord(vx: Float, vy: Float, sTarget: PointF = PointF()): PointF {

        var sXPreRotate = viewToSourceX(vx)
        var sYPreRotate = viewToSourceY(vy)

        if (rotationRadian == 0f) {
            sTarget.set(sXPreRotate, sYPreRotate)
        } else {
            val sourceVCenterX = viewToSourceX(vCenterX)
            val sourceVCenterY = viewToSourceY(vCenterY)
            sXPreRotate -= sourceVCenterX
            sYPreRotate -= sourceVCenterY
            sTarget.x = (sXPreRotate * cos + sYPreRotate * sin).toFloat() + sourceVCenterX
            sTarget.y = (-sXPreRotate * sin + sYPreRotate * cos).toFloat() + sourceVCenterY
        }

        return sTarget
    }

    private fun sourceToViewX(sx: Float): Float {
        return sx * scale + vTranslate.x
    }

    private fun sourceToViewY(sy: Float): Float {
        return sy * scale + vTranslate.y
    }

    fun sourceToViewCoord(sxy: PointF, vTarget: PointF) = sourceToViewCoord(sxy.x, sxy.y, vTarget)

    fun sourceToViewCoord(sxy: PointF) = sourceToViewCoord(sxy.x, sxy.y, PointF())

    @JvmOverloads
    fun sourceToViewCoord(sx: Float, sy: Float, vTarget: PointF = PointF()): PointF {
        var xPreRotate = sourceToViewX(sx)
        var yPreRotate = sourceToViewY(sy)

        if (rotationRadian == 0f) {
            vTarget.set(xPreRotate, yPreRotate)
        } else {
            val vCenterX = vCenterX
            val vCenterY = vCenterY
            xPreRotate -= vCenterX
            yPreRotate -= vCenterY
            vTarget.x = (xPreRotate * cos - yPreRotate * sin).toFloat() + vCenterX
            vTarget.y = (xPreRotate * sin + yPreRotate * cos).toFloat() + vCenterY
        }

        return vTarget
    }

    private fun sourceToViewRect(sRect: RectF, vTarget: RectF) {
        vTarget.set(
                sourceToViewX(sRect.left),
                sourceToViewY(sRect.top),
                sourceToViewX(sRect.right),
                sourceToViewY(sRect.bottom)
        )
    }

    private fun vTranslateForSCenter(sCenterX: Float, sCenterY: Float, scale: Float): PointF {
        satTemp.scale = scale
        satTemp.vTranslate.set(vCenterX - sCenterX * scale, vCenterY - sCenterY * scale)
        fitToBounds(satTemp)
        return satTemp.vTranslate
    }

    private fun limitSCenter(scale: Float, sTarget: PointF) {
        val vTranslate = vTranslateForSCenter(sTarget.x, sTarget.y, scale)
        val sx = (vCenterX - vTranslate.x) / scale
        val sy = (vCenterY - vTranslate.y) / scale
        sTarget.set(sx, sy)
    }

    private fun limitedScale(targetScale: Float): Float {
        var newTargetScale = targetScale
        val lowerBound =
                if (minimumScaleType == SCALE_TYPE_CENTER_CROP) {
                    getCenterCropScale()
                } else Math.min(getFullScale(), 1f)
        newTargetScale = Math.max(lowerBound, newTargetScale)
        return newTargetScale
    }

    // interpolation should be in range of [0,1]
    private fun ease(interpolation: Float, from: Float, change: Float, finalValue: Float): Float {
        if (interpolation == 1f) return finalValue
        return from + change * interpolation
    }

    private fun debug(message: String) {
        if (debug) {
            Log.d(TAG, message)
        }
    }

    private fun px(px: Int) = (density * px).toInt()

    fun setMinimumDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2f
        maxScale = averageDpi / dpi
    }

    fun setMinimumTileDpi(minimumTileDpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2f
        this.minimumTileDpi = Math.min(averageDpi, minimumTileDpi.toFloat()).toInt()
        if (isReady) {
            reset(false)
            invalidate()
        }
    }

    protected fun onReady() {}

    fun setDoubleTapZoomDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2f
        doubleTapZoomScale = averageDpi / dpi
    }

    fun isZoomedOut() = scale == getFullScale()

    fun rotateBy(degrees: Int) {
        if (anim != null) {
            return
        }

        val oldDegrees = Math.toDegrees(rotationRadian.toDouble())
        val rightAngle = getClosestRightAngle(oldDegrees)
        val newDegrees = ((rightAngle + degrees).toInt())
        val center = PointF(sWidth() / 2f, sHeight() / 2f)
        val scale = if (degrees == -90 || degrees == 90 || degrees == 270) getRotatedFullScale() else scale
        AnimationBuilder(center, scale, newDegrees.toDouble()).start(true)
    }

    inner class AnimationBuilder {
        private val targetScale: Float
        private var sFocus: PointF
        private var targetRotation = rotationRadian
        var duration: Long = (ANIMATION_DURATION * Settings.Global.getFloat(context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)).toLong()
        var interpolator: Interpolator = SubsamplingScaleImageView.interpolator
        var interruptible = false

        constructor(sCenter: PointF, scale: Float) {
            targetScale = scale
            sFocus = sCenter
        }

        constructor(sFocus: PointF, degrees: Double) {
            targetScale = limitedScale(scale)
            this.sFocus = sFocus
            targetRotation = Math.toRadians(degrees).toFloat()
        }

        constructor(sCenter: PointF, scale: Float, degrees: Double) {
            targetScale = scale
            sFocus = sCenter
            targetRotation = Math.toRadians(degrees).toFloat()
        }

        constructor(anim: Anim) : this(anim.sFocus!!, anim.scaleEnd) {
            targetRotation = anim.rotationEnd
            interruptible = anim.interruptible
            interpolator = anim.interpolator
            duration = anim.duration
        }

        @JvmOverloads
        fun start(skipCenterLimiting: Boolean = false) {
            if (!skipCenterLimiting) {
                limitSCenter(targetScale, sFocus)
            }
            if (scale == targetScale && rotationRadian == targetRotation && getCenter() == sFocus)
                return

            anim = Anim().apply {
                scaleStart = scale
                scaleEnd = targetScale
                rotationStart = rotationRadian
                rotationEnd = targetRotation
                sCenterEndRequested = this@AnimationBuilder.sFocus
                svCenterStart = getCenter()
                sFocus = this@AnimationBuilder.sFocus
                vFocusStart = sourceToViewCoord(this@AnimationBuilder.sFocus)
                vFocusEnd = PointF(vCenterX, vCenterY)
                interpolator = this@AnimationBuilder.interpolator
                duration = this@AnimationBuilder.duration
                interruptible = this@AnimationBuilder.interruptible
            }

            val tx = vTranslate.x
            val ty = vTranslate.y

            scale = targetScale
            setRotationInternal(targetRotation)
            val vFocus = sourceToViewCoord(anim!!.sFocus!!)
            var dX = vFocus!!.x - anim!!.vFocusEnd!!.x
            var dY = vFocus.y - anim!!.vFocusEnd!!.y
            vTranslate.x -= (dX * cos + dY * sin).toFloat()
            vTranslate.y -= (dX * -sin + dY * cos).toFloat()
            refreshRequiredTiles(true)

            scale = anim!!.scaleStart
            setRotationInternal(anim!!.rotationStart)
            vTranslate.set(tx, ty)

            invalidate()
        }
    }

    data class ScaleTranslateRotate(var scale: Float, var vTranslate: PointF, var rotate: Float)

    class Tile {
        var sRect: RectF? = null
        var sampleSize = 0
        var bitmap: Bitmap? = null
        var loading = false
        var visible = false
        var vRect: RectF? = null
        var fileSRect: Rect? = null
    }

    class Anim {
        var scaleStart = 0f
        var scaleEnd = 0f
        var rotationStart = 0f
        var rotationEnd = 0f
        var sFocus: PointF? = null
        var sCenterEndRequested: PointF? = null
        var vFocusStart: PointF? = null
        var vFocusEnd: PointF? = null
        var duration: Long = 0
        var svCenterStart: PointF? = null
        var interruptible = false
        lateinit var interpolator: Interpolator
        var time = System.currentTimeMillis()
    }

    fun stopAnimation() {
        anim = null
        refreshRequiredTiles(true)
    }

    class SigmoidInterpolator @JvmOverloads
    constructor(easeOut: Double = 6.0, easeIn: Double = 1.0) : Interpolator {
        private val xStart = -easeIn
        private val xEnd = easeOut
        private val xDiff = xEnd - xStart
        private val yStart = sigmoid(xStart)
        private val yEnd = sigmoid(xEnd)
        private val yDiff = yEnd - yStart
        private val yScale = 1 / yDiff

        override fun getInterpolation(input: Float): Float {
            if (input == 1f) return 1f
            val x = xStart + (xDiff * input)
            return ((sigmoid(x) - yStart) * yScale).toFloat()
        }

        fun sigmoid(x: Double): Double {
            if (x <= 0) return Math.exp(x)
            else return 2 - Math.exp(-x)
        }
    }

    interface OnImageEventListener {
        fun onReady()
        fun onImageLoaded()
        fun onImageLoadError(e: Exception)
        fun onImageRotation(degrees: Int)
    }

    open class DefaultOnImageEventListener : OnImageEventListener {
        override fun onReady() {}
        override fun onImageLoaded() {}
        override fun onImageLoadError(e: Exception) {}
        override fun onImageRotation(degrees: Int) {}
    }
}
