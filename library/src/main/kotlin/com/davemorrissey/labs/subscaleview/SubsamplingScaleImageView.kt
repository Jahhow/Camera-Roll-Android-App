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
import androidx.annotation.AnyThread
import androidx.annotation.FloatRange
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.UnsupportedEncodingException
import java.lang.ref.WeakReference
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock

open class SubsamplingScaleImageView @JvmOverloads constructor(context: Context, attr: AttributeSet? = null) : ImageView(context, attr) {
    companion object {
        private val TAG = SubsamplingScaleImageView::class.simpleName

        const val FILE_SCHEME = "file://"
        const val ASSET_PREFIX = "$FILE_SCHEME/android_asset/"
        const val debug = true

        const val SCALE_TYPE_UNSPECIFIED = 0
        const val SCALE_TYPE_CENTER_CROP = 2

        val interpolator = SigmoidInterpolator()
        val easeEndInterpolator = SigmoidInterpolator(7.0, 0.0)

        private const val TILE_SIZE_AUTO = Integer.MAX_VALUE
        private const val ANIMATION_DURATION = 366L
        private const val FLING_DURATION = 1000L
        private const val ROTATION_THRESHOLD_DEGREES = 10f

        const val ANIM_FINISH_EDGE_NONE = 0.toByte()
        const val ANIM_FINISH_EDGE_LEFT = 0b1000.toByte()
        const val ANIM_FINISH_EDGE_TOP = 0b100.toByte()
        const val ANIM_FINISH_EDGE_RIGHT = 0b10.toByte()
        const val ANIM_FINISH_EDGE_BOTTOM = 0b1.toByte()
        const val ANIM_FINISH_EDGE_ALL = 0b1111.toByte()
    }

    var maxScale = 2f
    var isOneToOneZoomEnabled = false
    var rotationEnabled = true
    var triggeredRotation = false
    var eagerLoadingEnabled = false
    var onImageEventListener: OnImageEventListener? = null
    var doubleTapZoomScale = 1f
    var regionDecoderFactory: DecoderFactory<out ImageRegionDecoder> = CompatDecoderFactory(SkiaImageRegionDecoder::class.java)
    var decoderFactory: DecoderFactory<out ImageDecoder>? = CompatDecoderFactory(SkiaImageDecoder::class.java)
    var previewDecoderFactory: DecoderFactory<out ImageDecoder>? = null
    var scale = 0f
    var sWidth = 0
    var sHeight = 0
    var orientationDegrees = 0
        set(degrees) {
            stopAnimation()
            setRotationDegrees(degrees - field + rotationDegrees)
            field = degrees
            invalidate()
        }
    var initialScaleType = SCALE_TYPE_UNSPECIFIED
    var minimumScaleType = SCALE_TYPE_UNSPECIFIED
    var retainXSwipe = true
    var retainYSwipe = false

    private var vCenterX = 0f
    private var vCenterY = 0f

    //protected var bitmapIsPreview = false
    protected var bitmap: Bitmap? = null
    private var uri: Uri? = null
    private var fullImageSampleSize = 0
    private var tileMap: MutableMap<Int, List<Tile>>? = null
    private var minimumTileDpi = -1
    private var maxTileWidth = TILE_SIZE_AUTO
    private var maxTileHeight = TILE_SIZE_AUTO
    private var scaleStart = 0f
    protected var rotationDegrees = 0f
        private set
    private var sin = 0f
    private var cos = 1f

    private var vTranslate = PointF(0f, 0f)
    private var vTranslateStart = PointF(0f, 0f)
    private var diffMove = PointF(0f, 0f)

    private var pendingScale: Float? = null
    private var sPendingCenter: PointF? = null

    private var twoFingerZooming = false
    private var isPanning = false
    private var isQuickScaling = false
    private var maxTouchCount = 0
    private var did2FingerZoomIn = false
    private var prevDegreesInt = 0

    private var detector: GestureDetector? = null

    protected var regionDecoder: ImageRegionDecoder? = null
    private val decoderLock = ReentrantReadWriteLock(true)

    private var sCenterStart = PointF(0f, 0f)
    private var vCenterStart = PointF(0f, 0f)
    private var vCenterStartNow = PointF(0f, 0f)
    private var vCenterBefore = PointF()
    private var vDistStart = 0f
    private var originDegrees = 0f

    private val quickScaleThreshold: Float
    private var quickScaleLastDistance = 0f
    private var quickScaleMoved = false
    private var quickScaleVLastPoint: PointF? = null
    private var quickScaleSCenter: PointF? = null
    private var quickScaleVStart: PointF? = null

    protected var anim: Anim? = null
        private set
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

    private val density = resources.displayMetrics.density

    init {
        setMinimumDpi(160)
        setDoubleTapZoomDpi(160)
        setMinimumTileDpi(320)
        setGestureDetector(context)
        quickScaleThreshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, context.resources.displayMetrics)
        createPaints()
    }

    private fun getIsBaseLayerReady(): Boolean {
        if (bitmap != null && bitmap!!.width == sWidth) {
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

    fun getSVCenter(): PointF? {
        return viewToSourceCoord(vCenterX, vCenterY)
    }

    /*fun setPreviewImage(bitmap: Bitmap, orientationDegrees: Int) {
        debug("setPreviewImage")
        if (sWidth == 0) {
            this.orientationDegrees = orientationDegrees
            sWidth = bitmap.width
            sHeight = bitmap.height
        }
        if (!isImageLoaded) {
            this.bitmap = bitmap
            invalidate()
        } else {
            debug("Preview Image Loaded after normal image")
        }
    }*/

    fun loadImage(path: String) {
        reset(true)

        var newPath = path
        if (!path.contains("://")) {
            if (path.startsWith("/")) {
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

        execute(InitTask(this, context, regionDecoderFactory, uri!!))
    }

    private fun reset(newImage: Boolean) {
        scale = 0f
        scaleStart = 0f
        rotationDegrees = 0f
        vTranslate.set(0f, 0f)
        pendingScale = null
        sPendingCenter = null
        twoFingerZooming = false
        isPanning = false
        isQuickScaling = false
        maxTouchCount = 0
        fullImageSampleSize = 0
        vDistStart = 0f
        originDegrees = 0f
        quickScaleLastDistance = 0f
        quickScaleMoved = false
        quickScaleSCenter = null
        quickScaleVLastPoint = null
        quickScaleVStart = null
        anim = null

        if (newImage) {
            uri = null
            regionDecoder = null
            bitmap = null

            prevDegreesInt = 0
            sWidth = 0
            sHeight = 0
            isReady = false
            isImageLoaded = false
            sin = 0f
            cos = 1f
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
                    val vX = (velocityX * cos - velocityY * -sin)
                    val vY = (velocityX * -sin + velocityY * cos)

                    val vTranslateEnd = PointF(vTranslate.x + vX * 0.25f, vTranslate.y + vY * 0.25f)
                    val targetSFocus = vTranslateEnd.apply {
                        x = (vCenterX - x) / scale
                        y = (vCenterY - y) / scale
                    }
                    AnimationBuilder(targetSFocus, getClosestRightAngleDegrees(rotationDegrees)).apply {
                        interruptible = true
                        interpolator = easeEndInterpolator
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
                    quickScaleSCenter = viewToSourceCoord(vCenterStart)
                    quickScaleVStart = PointF(event.x, event.y)
                    quickScaleVLastPoint = PointF(quickScaleSCenter!!.x, quickScaleSCenter!!.y)
                    quickScaleMoved = false
                    return false
                }
                return super.onDoubleTapEvent(event)
            }
        })
    }

    private fun updateVCenter() {
        vCenterX = paddingLeft + getInnerWidth() / 2f
        vCenterY = paddingTop + getInnerHeight() / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val sCenter = getSVCenter()
        if (isReady && sCenter != null) {
            stopAnimation()
            pendingScale = scale
            sPendingCenter = sCenter
        }
        updateVCenter()
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, top, right, bottom)
        //debug("setPadding left $left, top $top, right $right, bottom $bottom")
        updateVCenter()
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
                width = sWidth
                height = sHeight
            } else if (resizeHeight) {
                height = (sHeight.toDouble() / sWidth.toDouble() * width).toInt()
            } else if (resizeWidth) {
                width = (sWidth.toDouble() / sHeight.toDouble() * height).toInt()
            }
        }
        width = Math.max(width, suggestedMinimumWidth)
        height = Math.max(height, suggestedMinimumHeight)
        setMeasuredDimension(width, height)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector?.onTouchEvent(event)
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
                    vTranslateStart.set(vTranslate.x, vTranslate.y)
                    vCenterStart.set(event.x, event.y)
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
                vTranslateStart.set(vTranslate)
                vCenterStart.set((event.getX(0) + event.getX(1)) / 2f, (event.getY(0) + event.getY(1)) / 2f)
                viewToSourceCoord(vCenterStart, sCenterStart)

                if (rotationEnabled) {
                    originDegrees = Math.toDegrees(Math.atan2((event.getY(0) - event.getY(1)).toDouble(), (event.getX(0) - event.getX(1)).toDouble())).toFloat()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                var consumed = false
                if (maxTouchCount > 0) {
                    if (touchCount >= 2) {
                        if (rotationEnabled) {
                            val degrees = Math.toDegrees(
                                    Math.atan2((event.getY(0) - event.getY(1)).toDouble(), (event.getX(0) - event.getX(1)).toDouble())
                            ).toFloat()
                            if (triggeredRotation) {
                                setRotationDegrees(degrees - originDegrees)
                                consumed = true
                            } else {
                                if (diffDegrees(degrees, originDegrees) > ROTATION_THRESHOLD_DEGREES) {
                                    triggeredRotation = true
                                    originDegrees = degrees - rotationDegrees
                                }
                            }
                        }

                        val vDistEnd = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                        val vCenterEndX = (event.getX(0) + event.getX(1)) / 2f
                        val vCenterEndY = (event.getY(0) + event.getY(1)) / 2f
                        if (isPanning || distance(vCenterStart.x, vCenterEndX, vCenterStart.y, vCenterEndY) > 5 || Math.abs(vDistEnd - vDistStart) > 5) {
                            did2FingerZoomIn = true
                            twoFingerZooming = true
                            isPanning = true
                            consumed = true

                            val previousScale = scale.toDouble()
                            scale = vDistEnd / vDistStart * scaleStart

                            sourceToViewCoord(sCenterStart, vCenterStartNow)

                            val dx = vCenterEndX - vCenterStartNow.x
                            val dy = vCenterEndY - vCenterStartNow.y

                            val dxR = (dx * cos - dy * -sin)
                            val dyR = (dx * -sin + dy * cos)

                            vTranslate.x += dxR
                            vTranslate.y += dyR

                            if (previousScale * sHeight < height && scale * sHeight >= height || previousScale * sWidth < width && scale * sWidth >= width) {
                                vCenterStart.set(vCenterEndX, vCenterEndY)
                                vTranslateStart.set(vTranslate)
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

                            val vLeftStart = vCenterStart.x - vTranslateStart.x
                            val vTopStart = vCenterStart.y - vTranslateStart.y
                            val vLeftNow = vLeftStart * (scale / scaleStart)
                            val vTopNow = vTopStart * (scale / scaleStart)
                            vTranslate.x = vCenterStart.x - vLeftNow
                            vTranslate.y = vCenterStart.y - vTopNow
                            if (previousScale * sHeight < height && scale * sHeight >= height || previousScale * sWidth < width && scale * sWidth >= width) {
                                vCenterStart.set(sourceToViewCoord(quickScaleSCenter!!))
                                vTranslateStart.set(vTranslate)
                                scaleStart = scale
                                dist = 0f
                            }
                        }

                        quickScaleLastDistance = dist

                        refreshRequiredTiles(eagerLoadingEnabled)
                        consumed = true
                    } else if (!twoFingerZooming) {
                        val dx = event.x - vCenterStart.x
                        val dy = event.y - vCenterStart.y
                        val dxA = Math.abs(dx)
                        val dyA = Math.abs(dy)

                        val offset = density * 5
                        if (isPanning || dxA > offset || dyA > offset) {
                            consumed = true
                            val dxR = (dx * cos + dy * sin)
                            val dyR = (dx * -sin + dy * cos)

                            val lastX = vTranslate.x
                            val lastY = vTranslate.y

                            val newX = vTranslateStart.x + dxR
                            val newY = vTranslateStart.y + dyR

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
                                val fullScale = getFitCenterScale()
                                var edgeXSwipe: Boolean = dxA > dyA
                                var edgeYSwipe: Boolean = !edgeXSwipe
                                if (anim == null) {
                                    if (scale > fullScale) {
                                        fitToBounds(false)
                                        val degrees = rotationDegrees
                                        val rightAngle = getClosestRightAngleDegreesNormalized(degrees)
                                        val atXEdge = if (rightAngle == 90 || rightAngle == 270) newY != satTemp.vTranslate.y else newX != satTemp.vTranslate.x
                                        val atYEdge = if (rightAngle == 90 || rightAngle == 270) newX != satTemp.vTranslate.x else newY != satTemp.vTranslate.y
                                        edgeXSwipe = edgeXSwipe && atXEdge
                                        edgeYSwipe = edgeYSwipe && atYEdge
                                    }
                                } else {
                                    val finishEdges = anim!!.finishEdges.toInt()
                                    if (edgeXSwipe) {
                                        val swipeLeft = dx < 0 && finishEdges.and(ANIM_FINISH_EDGE_RIGHT.toInt()) != 0
                                        val swipeRight = dx > 0 && finishEdges.and(ANIM_FINISH_EDGE_LEFT.toInt()) != 0
                                        //debug("swipeLeft $swipeLeft, swipeRight $swipeRight")
                                        edgeXSwipe = (swipeLeft || swipeRight)
                                    } else {
                                        val swipeUp = dy < 0 && finishEdges.and(ANIM_FINISH_EDGE_BOTTOM.toInt()) != 0
                                        val swipeDown = dy > 0 && finishEdges.and(ANIM_FINISH_EDGE_TOP.toInt()) != 0
                                        //debug("swipeUp $swipeUp, swipeDown $swipeDown")
                                        edgeYSwipe = (swipeUp || swipeDown)
                                    }
                                }
                                edgeXSwipe = edgeXSwipe && retainXSwipe
                                edgeYSwipe = edgeYSwipe && retainYSwipe

                                vTranslate.x = lastX
                                vTranslate.y = lastY
                                if (edgeXSwipe || edgeYSwipe) {
                                    animateToBounds()
                                    maxTouchCount = 0
                                    parent?.requestDisallowInterceptTouchEvent(false)
                                } else {
                                    vCenterStart.set(event.x, event.y)
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
                        vCenterStart.set(event.getX(i), event.getY(i))
                        vTranslateStart.set(vTranslate.x, vTranslate.y)
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

    /* return an equivalent degrees for Math.abs(degrees1 - degrees2) but in range of [0°, 180°] */
    fun diffDegrees(degrees1: Float, degrees2: Float): Float {
        var diff = (degrees1 - degrees2) % 360
        if (diff < -180) diff += 360
        else if (diff > 180) diff -= 360
        return Math.abs(diff)
    }

    // @return {0, 90, 180, 270}
    @JvmOverloads
    fun getClosestRightAngleDegreesNormalized(degrees: Float = rotationDegrees): Int {
        var d = getClosestRightAngleDegrees(degrees) % 360
        if (d < 0) d += 360
        return d
    }

    @JvmOverloads
    fun getClosestRightAngleDegrees(degrees: Float = rotationDegrees) = Math.round(degrees / 90f) * 90

    private fun doubleTapZoom(sCenter: PointF?) {
        val initScale = getInitialScale()
        var doubleTapZoomScale = limitedScale(doubleTapZoomScale)
        if (minimumScaleType == SCALE_TYPE_UNSPECIFIED && doubleTapZoomScale <= initScale) doubleTapZoomScale = 1f
        val isToInitScale = (anim == null && scale == initScale) || anim?.scaleEnd == initScale
        val targetScale =
                if (isOneToOneZoomEnabled) {
                    if (isToInitScale) {
                        doubleTapZoomScale
                    } else {
                        val isToDoubleTapScale = scale == doubleTapZoomScale || anim?.scaleEnd == doubleTapZoomScale
                        if (isToDoubleTapScale) {
                            1f
                        } else {
                            initScale
                        }
                    }
                } else {
                    if (isToInitScale) doubleTapZoomScale else initScale
                }
        AnimationBuilder(sCenter!!, targetScale, getClosestRightAngleDegrees(rotationDegrees)).start()
        invalidate()
    }

    private val rectF = RectF()
    private val tileVRect = RectF()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sWidth == 0 || sHeight == 0)
            return
        if (tileMap == null && regionDecoder != null)
            initialiseBaseLayer(getMaxBitmapDimensions(canvas))
        if (!checkReadyToDraw())
            return

        if (anim != null) {
            val timeElapsed = System.currentTimeMillis() - anim!!.startTime
            val elapsed = Math.min(timeElapsed.toFloat() / anim!!.duration, 1f)
            anim!!.update(elapsed)
            if (elapsed == 1f) {
                stopAnimation()
                if (isPanning) {
                    vTranslateStart.set(vTranslate)
                    vCenterStart.set(vCenterBefore)
                }
                val degreesInt = Math.round(rotationDegrees)
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
        }

        canvas.rotate(rotationDegrees, vCenterX, vCenterY)
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

            fun drawTiles(tiles: List<Tile>) {
                for (tile in tiles) {
                    if (tile.visible) {
                        sourceToViewRectPreRotate(tile.sRectF, tileVRect)
                        if (tile.bitmap != null) {
                            objectMatrix.reset()
                            val tileBitmap = tile.bitmap!!
                            rectF.set(0f, 0f, tileBitmap.width.toFloat(), tileBitmap.height.toFloat())
                            objectMatrix.setRectToRect(rectF, tileVRect, Matrix.ScaleToFit.FILL)
                            canvas.drawBitmap(tileBitmap, objectMatrix, bitmapPaint)
                        }
                        if (debug) {
                            canvas.drawRect(tileVRect, debugLinePaint!!)
                            canvas.drawText("ISS ${tile.sampleSize} RECT ${tile.sRectF.top}, ${tile.sRectF.left}, ${tile.sRectF.bottom}, ${tile.sRectF.right}", (tileVRect.left + px(5)), (tileVRect.top + px(15)), debugTextPaint!!)
                            if (tile.loading) {
                                canvas.drawText("LOADING", tileVRect.left + px(5), tileVRect.top + px(35), debugTextPaint!!)
                            }
                        }
                    }
                }
            }

            if (drawFullImage) drawTiles(tileMap[fullImageSampleSize]!!)
            drawTiles(tileMap[sampleSize]!!)
        } else if (bitmap != null) {
            val scale = scale * sWidth / bitmap!!.width
            objectMatrix.apply {
                reset()
                postScale(scale, scale)
                postTranslate(vTranslate.x, vTranslate.y)
            }
            canvas.drawBitmap(bitmap!!, objectMatrix, bitmapPaint)
        }
        onDrawPreRotate(canvas)
        canvas.rotate(-rotationDegrees, vCenterX, vCenterY)
        if (debug) {
            canvas.drawText(String.format(Locale.ENGLISH, "Scale (%.2f - %.2f): %.2f", limitedScale(0f), maxScale, scale), px(5).toFloat(), px(15).toFloat(), debugTextPaint!!)
            canvas.drawText(String.format(Locale.ENGLISH, "Translate: %.0f, %.0f", vTranslate.x, vTranslate.y), px(5).toFloat(), px(30).toFloat(), debugTextPaint!!)
            canvas.drawText(String.format(Locale.ENGLISH, "Orientation: %d°, Total Rotation: %.0f°", orientationDegrees, rotationDegrees), px(5).toFloat(), px(45).toFloat(), debugTextPaint!!)
            val center = getSVCenter()
            canvas.drawText(String.format(Locale.ENGLISH, "Source Center: %.0f, %.0f", center!!.x, center.y), px(5).toFloat(), px(60).toFloat(), debugTextPaint!!)
            if (bitmap != null && bitmap!!.width != sWidth)
                canvas.drawText("Preview", px(5).toFloat(), px(75).toFloat(), debugTextPaint!!)
            if (anim != null) {
                val vFocus = sourceToViewCoord(anim!!.sFocus!!)

                debugLinePaint!!.color = Color.CYAN
                canvas.drawCircle(anim!!.vFocusEnd!!.x, anim!!.vFocusEnd!!.y, px(24).toFloat(), debugLinePaint!!)

                debugLinePaint!!.color = Color.RED
                canvas.drawCircle(vFocus.x, vFocus.y, px(24).toFloat(), debugLinePaint!!)
            }

            debugLinePaint!!.color = Color.LTGRAY
            canvas.drawCircle(vCenterStart.x, vCenterStart.y, px(24).toFloat(), debugLinePaint!!)

            if (quickScaleSCenter != null) {
                debugLinePaint!!.color = Color.BLUE
                val a = sourceToViewCoord(quickScaleSCenter!!)
                canvas.drawCircle(a.x, a.y, px(35).toFloat(), debugLinePaint!!)
            }

            if (quickScaleVStart != null && isQuickScaling) {
                debugLinePaint!!.color = Color.CYAN
                canvas.drawCircle(quickScaleVStart!!.x, quickScaleVStart!!.y, px(30).toFloat(), debugLinePaint!!)
            }

            debugLinePaint!!.color = Color.MAGENTA
        }
    }

    protected open fun onDrawPreRotate(canvas: Canvas) {
    }

    private fun checkReadyToDraw(): Boolean {
        if (isReady) {
            return true
        }

        isReady = width > 0 && height > 0 && sWidth > 0 && sHeight > 0 && (bitmap != null || getIsBaseLayerReady())
        if (isReady) {
            preDraw()
            onReady()
            onImageEventListener?.onReadyToDraw()
        }
        return isReady
    }

    fun setRotationDegrees(degrees: Float) {
        rotationDegrees = degrees % 360
        if (rotationDegrees < 0) rotationDegrees += 360

        when (rotationDegrees) {
            0f -> {
                sin = 0f
                cos = 1f
            }
            90f -> {
                sin = 1f
                cos = 0f
            }
            180f -> {
                sin = 0f
                cos = -1f
            }
            270f -> {
                sin = -1f
                cos = 0f
            }
            else -> {
                val rad = Math.toRadians(degrees.toDouble())
                cos = Math.cos(rad).toFloat()
                sin = Math.sin(rad).toFloat()
            }
        }
    }

    private fun checkBaseLayerReady(): Boolean {
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

    fun setFilterBitmapEnabled(enable: Boolean) {
        createBitmapPaint()
        bitmapPaint!!.isFilterBitmap = enable
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
        //debug("initialiseBaseLayer maxTileDimensions=${maxTileDimensions.x}x${maxTileDimensions.y}")
        scale = getInitialScale()
        fitToBounds()
        fullImageSampleSize = calculateInSampleSize(scale)
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2
        }

        if (fullImageSampleSize == 1 && sWidth < maxTileDimensions.x && sHeight < maxTileDimensions.y
                && decoderFactory != null) {
            regionDecoder = null
            execute(BitmapLoadTask(this, decoderFactory!!, uri!!, false))
        } else {
            initialiseTileMap(maxTileDimensions)
            for (tile in tileMap!![fullImageSampleSize]!!) {
                execute(TileLoadTask(this, regionDecoder!!, tile))
            }
            refreshRequiredTiles(true)
        }
    }

    private fun refreshRequiredTiles(load: Boolean) {
        if (regionDecoder == null || tileMap == null) {
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
                                val task = TileLoadTask(this, regionDecoder!!, tile)
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

    private val corners = Array(4) { PointF() }

    private fun tileVisible(tile: Tile): Boolean {
        if (this.rotationDegrees == 0f) {
            val sVisLeft = viewToSourceX(0f)
            val sVisRight = viewToSourceX(width.toFloat())
            val sVisTop = viewToSourceY(0f)
            val sVisBottom = viewToSourceY(height.toFloat())
            return !(sVisLeft > tile.sRectF.right || tile.sRectF.left > sVisRight || sVisTop > tile.sRectF.bottom || tile.sRectF.top > sVisBottom)
        }

        sourceToViewCoord(tile.sRectF.left, tile.sRectF.top, corners[0])
        sourceToViewCoord(tile.sRectF.right, tile.sRectF.top, corners[1])
        sourceToViewCoord(tile.sRectF.right, tile.sRectF.bottom, corners[2])
        sourceToViewCoord(tile.sRectF.left, tile.sRectF.bottom, corners[3])

        var rotation = this.rotationDegrees % 360
        if (rotation < 0) rotation += 360
        return when {
            rotation < 90 ->
                corners[0].y < height && corners[1].x > 0 && corners[2].y > 0 && corners[3].x < width
            rotation < 180 ->
                corners[3].y < height && corners[0].x > 0 && corners[1].y > 0 && corners[2].x < width
            rotation < 270 ->
                corners[2].y < height && corners[3].x > 0 && corners[0].y > 0 && corners[1].x < width
            else ->
                corners[1].y < height && corners[2].x > 0 && corners[3].y > 0 && corners[0].x < width
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

        val reqWidth = (sWidth * newScale).toInt()
        val reqHeight = (sHeight * newScale).toInt()

        var inSampleSize = 1
        if (reqWidth == 0 || reqHeight == 0) {
            return 32
        }

        if (sHeight > reqHeight || sWidth > reqWidth) {
            val heightRatio = Math.round(sHeight.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(sWidth.toFloat() / reqWidth.toFloat())
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }

        var power = 1
        while (power * 2 < inSampleSize) {
            power *= 2
        }

        if ((sWidth > 3000 || sHeight > 3000) && power == 2 && minimumTileDpi == 280 && scale == getFitCenterScale()) {
            power = 4
        }

        return power
    }

    private fun fitToBounds(sat: ScaleTranslateRotate) {
        val vTranslate = sat.vTranslate
        val scale = limitedScale(sat.scale)
        val vsWidth = scale * sWidth
        val vsHeight = scale * sHeight
        val degrees = sat.rotationDegrees
        val rightAngle = getClosestRightAngleDegreesNormalized(degrees)

        val width: Float
        val height: Float
        val wh: Float
        val paddingLeft: Int
        val paddingTop: Int
        val paddingRight: Int
        val paddingBottom: Int
        if (rightAngle == 90 || rightAngle == 270) {
            // Convert all variables to visual coordinates
            width = this.height.toFloat()
            height = this.width.toFloat()
            wh = (width - height) / 2f
            vTranslate.x += wh
            vTranslate.y -= wh
            if (rightAngle == 90) {
                paddingLeft = this.paddingTop
                paddingTop = this.paddingRight
                paddingRight = this.paddingBottom
                paddingBottom = this.paddingLeft
            } else {
                paddingLeft = this.paddingBottom
                paddingTop = this.paddingLeft
                paddingRight = this.paddingTop
                paddingBottom = this.paddingRight
            }
        } else {
            if (rightAngle == 180) {
                paddingLeft = this.paddingRight
                paddingTop = this.paddingBottom
                paddingRight = this.paddingLeft
                paddingBottom = this.paddingTop
            } else {
                paddingLeft = this.paddingLeft
                paddingTop = this.paddingTop
                paddingRight = this.paddingRight
                paddingBottom = this.paddingBottom
            }
            wh = 0f
            width = this.width.toFloat()
            height = this.height.toFloat()
        }

        val remainWidth = width - vsWidth
        val remainHeight = height - vsHeight

        // right, bottom
        vTranslate.x = Math.max(vTranslate.x, remainWidth - paddingRight)
        vTranslate.y = Math.max(vTranslate.y, remainHeight - paddingBottom)

        // left, top
        val maxTx = Math.max(paddingLeft.toFloat(), paddingLeft + (remainWidth - paddingRight - paddingLeft) / 2f)
        val maxTy = Math.max(paddingTop.toFloat(), paddingTop + (remainHeight - paddingBottom - paddingTop) / 2f)
        vTranslate.x = Math.min(vTranslate.x, maxTx)
        vTranslate.y = Math.min(vTranslate.y, maxTy)

        if (rightAngle == 90 || rightAngle == 270) {
            vTranslate.x -= wh
            vTranslate.y += wh
        }

        sat.scale = scale
        sat.rotationDegrees = rightAngle.toFloat()
    }

    private fun fitToBounds(apply: Boolean = true) {
        satTemp.scale = scale
        satTemp.vTranslate.set(vTranslate)
        satTemp.rotationDegrees = rotationDegrees
        fitToBounds(satTemp)
        if (apply) {
            scale = satTemp.scale
            vTranslate.set(satTemp.vTranslate)
            setRotationDegrees(satTemp.rotationDegrees)
        }
    }

    private fun animateToBounds() {
        val degrees = rotationDegrees
        val rightAngle = getClosestRightAngleDegrees(degrees)

        if (anim != null) {
            if (anim!!.duration != FLING_DURATION)
                AnimationBuilder(anim!!).start()
        } else {
            val center = viewToSourceCoord(vCenterX, vCenterY)
            AnimationBuilder(center, rightAngle).start()
        }
    }

    fun getInitialScale(): Float {
        val initScale = when (initialScaleType) {
            SCALE_TYPE_CENTER_CROP -> getCenterCropScale()
            else -> getFitCenterScale()
        }
        return limitedScale(initScale)
    }

    @JvmOverloads
    fun getFitCenterScale(sWidth: Int = this.sWidth, sHeight: Int = this.sHeight, normalizedRightAngleDegrees: Int = getClosestRightAngleDegreesNormalized()): Float {
        val innerWidth = getInnerWidth()
        val innerHeight = getInnerHeight()
        return if (normalizedRightAngleDegrees == 0 || normalizedRightAngleDegrees == 180) {
            Math.min(innerWidth / sWidth.toFloat(), innerHeight / sHeight.toFloat())
        } else {
            Math.min(innerWidth / sHeight.toFloat(), innerHeight / sWidth.toFloat())
        }
    }

    fun getInnerWidth() = width - paddingLeft - paddingRight
    fun getInnerHeight() = height - paddingTop - paddingBottom

    private fun getCenterCropScale(): Float {
        val rightAngle = getClosestRightAngleDegreesNormalized()
        return if (rightAngle == 0 || rightAngle == 180) {
            Math.max(width / sWidth.toFloat(), height / sHeight.toFloat())
        } else {
            Math.max(width / sHeight.toFloat(), height / sWidth.toFloat())
        }
    }

    private fun initialiseTileMap(maxTileDimensions: Point) {
        //debug("initialiseTileMap maxTileDimensions=${maxTileDimensions.x}x${maxTileDimensions.y}")
        tileMap = LinkedHashMap()
        var sampleSize = fullImageSampleSize
        var xTiles = 1
        var yTiles = 1

        while (true) {
            var sTileWidth = sWidth / xTiles
            var sTileHeight = sHeight / yTiles
            var subTileWidth = sTileWidth / sampleSize
            var subTileHeight = sTileHeight / sampleSize
            while (subTileWidth + xTiles + 1 > maxTileDimensions.x || subTileWidth > width * 1.25 && sampleSize < fullImageSampleSize) {
                xTiles += 1
                sTileWidth = sWidth / xTiles
                subTileWidth = sTileWidth / sampleSize
            }

            while (subTileHeight + yTiles + 1 > maxTileDimensions.y || subTileHeight > height * 1.25 && sampleSize < fullImageSampleSize) {
                yTiles += 1
                sTileHeight = sHeight / yTiles
                subTileHeight = sTileHeight / sampleSize
            }

            val tileGrid = ArrayList<Tile>(xTiles * yTiles)
            for (x in 0 until xTiles) {
                for (y in 0 until yTiles) {
                    val tile = Tile()
                    tile.sampleSize = sampleSize
                    tile.visible = sampleSize == fullImageSampleSize
                    tile.sRect.set(
                            x * sTileWidth,
                            y * sTileHeight,
                            if (x == xTiles - 1) sWidth else (x + 1) * sTileWidth,
                            if (y == yTiles - 1) sHeight else (y + 1) * sTileHeight)
                    tile.sRectF.set(
                            tile.sRect.left.toFloat(),
                            tile.sRect.top.toFloat(),
                            tile.sRect.right.toFloat(),
                            tile.sRect.bottom.toFloat())
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

    private class InitTask internal constructor(view: SubsamplingScaleImageView, context: Context, decoderFactory: DecoderFactory<out ImageRegionDecoder>, private val source: Uri) : AsyncTask<Void, Void, IntArray>() {
        private val viewRef = WeakReference(view)
        private val contextRef = WeakReference(context)
        private val decoderFactoryRef = WeakReference(decoderFactory)
        private var decoder: ImageRegionDecoder? = null
        private var exception: Exception? = null

        override fun doInBackground(vararg params: Void): IntArray? {
            val context = contextRef.get()
            val decoderFactory = decoderFactoryRef.get()
            val view = viewRef.get()
            try {
                if (context != null && decoderFactory != null && view != null) {
                    //view.debug("TilesInitTask.doInBackground")
                    decoder = decoderFactory.make()
                    val dimensions = decoder!!.init(context, source)
                    val sWidth = dimensions.x
                    val sHeight = dimensions.y
                    return intArrayOf(sWidth, sHeight, view.getExifOrientation())
                }
            } catch (e: Exception) {
                if (debug) {
                    Log.e(TAG, e.toString())
                }
                exception = e
            }

            return null
        }

        override fun onPostExecute(info: IntArray?) {
            val view = viewRef.get()
            if (view != null) {
                if (decoder != null && info?.size == 3) {
                    view.onInit(decoder!!, info[0], info[1], info[2])
                } else {
                    view.onImageEventListener?.onImageLoadError(exception)
                }
            }
        }
    }

    @Synchronized
    private fun onInit(regionDecoder: ImageRegionDecoder, sWidth: Int, sHeight: Int, sOrientation: Int) {
        //debug("onInit sWidth=$sWidth, sHeight=$sHeight, sOrientation=$sOrientation")
        this.regionDecoder = regionDecoder
        this.sWidth = sWidth
        this.sHeight = sHeight
        this.orientationDegrees = sOrientation

        if (previewDecoderFactory != null)
            execute(BitmapLoadTask(this, previewDecoderFactory!!, uri!!, true))

        invalidate()
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
                    //view.debug("TileLoadTask.doInBackground, tile.sRect=${tile.sRect}, tile.sampleSize=${tile.sampleSize}")
                    view.decoderLock.readLock().lock()
                    try {
                        if (decoder.isReady()) {
                            return decoder.decodeRegion(tile.sRect, tile.sampleSize)
                        } else {
                            tile.loading = false
                        }
                    } finally {
                        view.decoderLock.readLock().unlock()
                    }
                } else {
                    tile?.loading = false
                }
            } catch (e: OutOfMemoryError) {
                if (debug)
                    Log.e(TAG, "Failed to decode tile - OutOfMemoryError $e")
                exception = RuntimeException(e)
            } catch (e: Exception) {
                if (debug)
                    Log.e(TAG, "Failed to decode tile $e")
                exception = e
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
                    //view.debug("exception?.cause is OutOfMemoryError")
                    if (!view.recycleOtherSampleSize) {
                        view.recycleOtherSampleSize = true
                        view.refreshRequiredTiles(true)
                    } else if (!view.recycleOtherTiles) {
                        view.recycleOtherTiles = true
                        view.refreshRequiredTiles(true)
                    } else {
                        view.onImageEventListener?.onImageLoadError(exception)
                    }
                } else view.onImageEventListener?.onImageLoadError(exception)
            }
        }
    }

    @Synchronized
    private fun onTileLoaded() {
        //debug("onTileLoaded")
        checkReadyToDraw()
        checkBaseLayerReady()
        if (getIsBaseLayerReady()) {
            bitmap?.apply {
                //recycle()
                bitmap = null
            }
        }
        invalidate()
    }

    //todo cancel unneeded tasks
    private class BitmapLoadTask internal
    constructor(view: SubsamplingScaleImageView, decoderFactory: DecoderFactory<out ImageDecoder>,
                uri: Uri, val isPreview: Boolean) : AsyncTask<Void, Void, Unit>() {
        private val viewRef = WeakReference(view)
        private val uriRef = WeakReference(uri)
        private val decoderFactoryRef = WeakReference(decoderFactory)
        private var bitmap: Bitmap? = null
        private var exception: Exception? = null

        override fun doInBackground(vararg params: Void?) {
            try {
                val view = viewRef.get()
                val decoderFactory = decoderFactoryRef.get()
                val uri = uriRef.get()

                if (uri != null && decoderFactory != null && view != null) {
                    //view.debug("BitmapLoadTask.doInBackground")
                    bitmap = decoderFactory.make().decode(view.context, uri, view.orientationDegrees)
                    view.debug("BitmapLoadTask done ${uri.lastPathSegment}. isPreview: $isPreview")
                }
            } catch (e: Exception) {
                //Log.e(TAG, "Failed to load bitmap", e)
                exception = e
            } catch (e: OutOfMemoryError) {
                //Log.e(TAG, "Failed to load bitmap - OutOfMemoryError $e")
                exception = RuntimeException(e)
            }
        }

        override fun onPostExecute(result: Unit?) {
            val view = viewRef.get()
            if (view != null) {
                //view.debug("onPostExecute bitmap ${if (bitmap == null) '=' else '!'}= null")
                //view.debug("onPostExecute exception ${if (exception == null) '=' else '!'}= null")
                if (isPreview) {
                    if (view.bitmap == null)
                        if (bitmap != null) {
                            view.debug("Preview Loaded")
                            view.bitmap = bitmap
                            view.invalidate()
                        }
                } else {
                    if (bitmap == null)
                        view.onImageEventListener?.onImageLoadError(exception)
                    else {
                        view.bitmap = bitmap
                        view.invalidate()
                    }
                }
            }
        }
    }

    /*@Synchronized
    private fun onSingleBitmapLoaded(bitmap: Bitmap) {
        //debug("onSingleBitmapLoaded")
        changeBitmap(bitmap)
        val ready = checkReady()
        val imageLoaded = checkImageLoaded()
        if (ready || imageLoaded) {
            invalidate()
            requestLayout()
        }
    }*/

    private fun execute(asyncTask: AsyncTask<Void, Void, *>) {
        asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    fun setScaleAndCenter(scale: Float, sCenter: PointF) {
        this.scale = scale
        val vFocus = sourceToViewCoord(sCenter)
        val dX = vFocus.x - vCenterX
        val dY = vFocus.y - vCenterY
        vTranslate.x -= (dX * cos + dY * sin)
        vTranslate.y -= (dX * -sin + dY * cos)
        stopAnimation()
        invalidate()
    }

    fun getState(): ImageViewState? {
        return if (isReady)
            ImageViewState(scale, getSVCenter()!!, orientationDegrees)
        else null
    }

    /*fun setMaxTileSize(maxPixels: Int) {
        maxTileWidth = maxPixels
        maxTileHeight = maxPixels
    }

    fun setMaxTileSize(maxPixelsX: Int, maxPixelsY: Int) {
        maxTileWidth = maxPixelsX
        maxTileHeight = maxPixelsY
    }*/

    private fun getMaxBitmapDimensions(canvas: Canvas) = Point(Math.min(canvas.maximumBitmapWidth, maxTileWidth), Math.min(canvas.maximumBitmapHeight, maxTileHeight))

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

        if (rotationDegrees == 0f) {
            sTarget.set(sXPreRotate, sYPreRotate)
        } else {
            val sourceVCenterX = viewToSourceX(vCenterX)
            val sourceVCenterY = viewToSourceY(vCenterY)
            sXPreRotate -= sourceVCenterX
            sYPreRotate -= sourceVCenterY
            sTarget.x = (sXPreRotate * cos + sYPreRotate * sin) + sourceVCenterX
            sTarget.y = (-sXPreRotate * sin + sYPreRotate * cos) + sourceVCenterY
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

        if (rotationDegrees == 0f) {
            vTarget.set(xPreRotate, yPreRotate)
        } else {
            val vCenterX = vCenterX
            val vCenterY = vCenterY
            xPreRotate -= vCenterX
            yPreRotate -= vCenterY
            vTarget.x = (xPreRotate * cos - yPreRotate * sin) + vCenterX
            vTarget.y = (xPreRotate * sin + yPreRotate * cos) + vCenterY
        }

        return vTarget
    }

    protected fun sourceToViewRectPreRotate(sRect: RectF, result: RectF) {
        result.set(
                sourceToViewX(sRect.left),
                sourceToViewY(sRect.top),
                sourceToViewX(sRect.right),
                sourceToViewY(sRect.bottom)
        )
    }

    private fun vTranslateForSVCenter(svCenter: PointF, scale: Float): PointF {
        satTemp.scale = scale
        satTemp.vTranslate.set(vCenterX - svCenter.x * scale, vCenterY - svCenter.y * scale)
        satTemp.rotationDegrees = rotationDegrees
        fitToBounds(satTemp)
        return satTemp.vTranslate
    }

    private fun limitSVCenter(scale: Float, svCenter: PointF) {
        val vTranslate = vTranslateForSVCenter(svCenter, scale)
        val sx = (vCenterX - vTranslate.x) / scale
        val sy = (vCenterY - vTranslate.y) / scale
        svCenter.set(sx, sy)
    }

    private fun limitedScale(targetScale: Float): Float {
        var newTargetScale = targetScale
        val lowerBound =
                when (minimumScaleType) {
                    SCALE_TYPE_CENTER_CROP -> getCenterCropScale()
                    else -> Math.min(getFitCenterScale(), 1f)
                }
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

    protected open fun onReady() {}
    protected open fun onImageLoaded() {}

    fun setDoubleTapZoomDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2f
        doubleTapZoomScale = averageDpi / dpi
    }

    inner class AnimationBuilder {
        private val targetScale: Float
        private var sFocus: PointF
        private var targetRotationDegrees = rotationDegrees
        var duration: Long = (ANIMATION_DURATION * Settings.Global.getFloat(context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)).toLong()
        var interpolator: Interpolator = SubsamplingScaleImageView.interpolator
        var interruptible = false

        constructor(sFocus: PointF, scale: Float) {
            targetScale = scale
            this.sFocus = sFocus
        }

        constructor(sFocus: PointF, degrees: Int) {
            targetScale = limitedScale(scale)
            this.sFocus = sFocus
            targetRotationDegrees = degrees.toFloat()
        }

        constructor(sFocus: PointF, scale: Float, degrees: Int) {
            targetScale = scale
            this.sFocus = sFocus
            targetRotationDegrees = degrees.toFloat()
        }

        constructor(sFocus: PointF, scale: Float, degrees: Float) {
            targetScale = scale
            this.sFocus = sFocus
            targetRotationDegrees = degrees
        }

        constructor(anim: Anim) {
            targetRotationDegrees = anim.rotationDegreesEnd
            interruptible = anim.interruptible
            interpolator = anim.interpolator
            duration = anim.duration
            targetScale = anim.scaleEnd
            sFocus = getSVCenter()!!
        }

        @JvmOverloads
        fun start(skipCenterLimiting: Boolean = false) {
            val sFocusRequested = PointF(sFocus.x, sFocus.y)
            if (!skipCenterLimiting) {
                limitSVCenter(targetScale, sFocus)
            }
            if (scale == targetScale && rotationDegrees == targetRotationDegrees && getSVCenter() == sFocus)
                return

            anim = Anim().apply {
                scaleStart = scale
                scaleEnd = targetScale
                rotationDegreesStart = rotationDegrees
                rotationDegreesEnd = targetRotationDegrees
                sFocus = this@AnimationBuilder.sFocus
                vFocusStart = sourceToViewCoord(this@AnimationBuilder.sFocus)
                vFocusEnd = PointF(vCenterX, vCenterY)
                duration = this@AnimationBuilder.duration
                interruptible = this@AnimationBuilder.interruptible
                interpolator = this@AnimationBuilder.interpolator
                initFinishEdges(sFocusRequested)
            }

            val tx = vTranslate.x
            val ty = vTranslate.y
            anim!!.update(1f)
            refreshRequiredTiles(true)

            scale = anim!!.scaleStart
            setRotationDegrees(anim!!.rotationDegreesStart)
            vTranslate.set(tx, ty)

            invalidate()
        }
    }

    inner class Anim {
        var scaleStart = 0f
        var scaleEnd = 0f

        var rotationDegreesStart = 0f
        var rotationDegreesEnd = 0f

        var sFocus: PointF? = null
        var vFocusStart: PointF? = null
        var vFocusEnd: PointF? = null

        var duration: Long = 0
        var startTime = System.currentTimeMillis()

        var interruptible = false
        lateinit var interpolator: Interpolator

        var finishEdges: Byte = ANIM_FINISH_EDGE_NONE

        fun initFinishEdges(sFocusRequested: PointF) {
            if (scaleEnd <= getFitCenterScale()) {
                finishEdges = ANIM_FINISH_EDGE_ALL
            } else {
                val degrees = rotationDegreesEnd
                val rightAngle = getClosestRightAngleDegreesNormalized(degrees)
                val sWidth: Int
                val sHeight: Int
                if (rightAngle == 90 || rightAngle == 270) {
                    sWidth = this@SubsamplingScaleImageView.sHeight
                    sHeight = this@SubsamplingScaleImageView.sWidth
                } else {
                    sWidth = this@SubsamplingScaleImageView.sWidth
                    sHeight = this@SubsamplingScaleImageView.sHeight
                }
                val scaledSWidth = scaleEnd * sWidth
                val scaledSHeight = scaleEnd * sHeight
                ////debug("scaledSWidth $scaledSWidth, scaledSHeight $scaledSHeight")

                var finishEdges2 = 0
                if (scaledSWidth < width) {
                    finishEdges2 = ANIM_FINISH_EDGE_RIGHT.toInt() or ANIM_FINISH_EDGE_LEFT.toInt()
                }
                if (scaledSHeight < height) {
                    finishEdges2 = finishEdges2 or ANIM_FINISH_EDGE_TOP.toInt() or ANIM_FINISH_EDGE_BOTTOM.toInt()
                }

                var finishEdges3 = 0
                if (sFocusRequested.x < sFocus!!.x) finishEdges3 = ANIM_FINISH_EDGE_LEFT.toInt()
                else if (sFocusRequested.x > sFocus!!.x) finishEdges3 = ANIM_FINISH_EDGE_RIGHT.toInt()
                if (sFocusRequested.y < sFocus!!.y) finishEdges3 = finishEdges3 or ANIM_FINISH_EDGE_TOP.toInt()
                else if (sFocusRequested.y > sFocus!!.y) finishEdges3 = finishEdges3 or ANIM_FINISH_EDGE_BOTTOM.toInt()
                val rotateRight =
                        when (rightAngle) {
                            90 -> 1
                            180 -> 2
                            270 -> 3
                            else -> 0
                        }
                finishEdges3 = (finishEdges3.ushr(rotateRight) or finishEdges3.shl(4 - rotateRight)) and ANIM_FINISH_EDGE_ALL.toInt()

                this@Anim.finishEdges = (finishEdges2 or finishEdges3).toByte()
            }
            /*if (debug) {
                val finishEdges = finishEdges.toInt()
                val str = StringBuilder("finishEdges:")
                if (finishEdges and ANIM_FINISH_EDGE_LEFT.toInt() != 0)
                    str.append(" Left")
                if (finishEdges and ANIM_FINISH_EDGE_TOP.toInt() != 0)
                    str.append(" Top")
                if (finishEdges and ANIM_FINISH_EDGE_RIGHT.toInt() != 0)
                    str.append(" Right")
                if (finishEdges and ANIM_FINISH_EDGE_BOTTOM.toInt() != 0)
                    str.append(" Bottom")
                //debug(str.toString())
            }*/
        }

        fun update(@FloatRange(from = 0.0, to = 1.0) elapsed: Float) {
            val interpolation = anim!!.interpolator.getInterpolation(elapsed)
            scale = ease(interpolation, anim!!.scaleStart, anim!!.scaleEnd - anim!!.scaleStart, anim!!.scaleEnd)
            val newVFocusX = ease(interpolation, anim!!.vFocusStart!!.x, anim!!.vFocusEnd!!.x - anim!!.vFocusStart!!.x, anim!!.vFocusEnd!!.x)
            val newVFocusY = ease(interpolation, anim!!.vFocusStart!!.y, anim!!.vFocusEnd!!.y - anim!!.vFocusStart!!.y, anim!!.vFocusEnd!!.y)
            val rotation = ease(interpolation, anim!!.rotationDegreesStart, anim!!.rotationDegreesEnd - anim!!.rotationDegreesStart, anim!!.rotationDegreesEnd)
            setRotationDegrees(rotation)
            // Find out where the focal point is at this scale/rotation then adjust its position to follow the animation path
            val vFocus = sourceToViewCoord(anim!!.sFocus!!)
            var dX = vFocus.x - newVFocusX
            var dY = vFocus.y - newVFocusY
            if (isPanning) {
                dX -= diffMove.x
                dY -= diffMove.y
            }
            vTranslate.x -= (dX * cos + dY * sin)
            vTranslate.y -= (dX * -sin + dY * cos)
        }
    }

    data class ScaleTranslateRotate(var scale: Float, var vTranslate: PointF, var rotationDegrees: Float)

    class Tile {
        var sampleSize = 0
        var sRect = Rect()
        var sRectF = RectF()
        var bitmap: Bitmap? = null
        var loading = false
        var visible = false
    }

    @AnyThread
    private fun getExifOrientation(): Int {
        return ExifInterface(context.contentResolver.openInputStream(uri!!)!!).rotationDegrees
    }

    fun stopAnimation() {
        anim = null
        refreshRequiredTiles(true)
    }

    class SigmoidInterpolator @JvmOverloads
    constructor(easeEnd: Double = 6.0, easeStart: Double = 1.0) : Interpolator {
        private val xStart = -easeStart
        private val xEnd = easeEnd
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
            return if (x <= 0) Math.exp(x)
            else 2 - Math.exp(-x)
        }
    }

    interface OnImageEventListener {
        fun onReadyToDraw()
        fun onImageLoaded()
        fun onImageLoadError(e: Exception?)
        fun onImageRotation(degrees: Int)
    }

    open class DefaultOnImageEventListener : OnImageEventListener {
        override fun onReadyToDraw() {}
        override fun onImageLoaded() {}
        override fun onImageLoadError(e: Exception?) {}
        override fun onImageRotation(degrees: Int) {}
    }
}
