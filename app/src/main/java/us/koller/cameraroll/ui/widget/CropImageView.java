package us.koller.cameraroll.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.davemorrissey.labs.subscaleview.ImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import us.koller.cameraroll.R;
import us.koller.cameraroll.imageDecoder.CustomRegionDecoder;
import us.koller.cameraroll.imageDecoder.RAWImageRegionDecoder;
import us.koller.cameraroll.interpolator.MyInterpolator;
import us.koller.cameraroll.util.MediaType;
import us.koller.cameraroll.util.Util;

@SuppressWarnings("FieldCanBeLocal")
public class CropImageView extends SubsamplingScaleImageView implements View.OnTouchListener {
    static final String TAG = CropImageView.class.getSimpleName();

    private boolean showCroppingEdge = false;

    private static final int STROKE_WIDTH_DP = 2;
    private static final int STROKE_COLOR_RES = R.color.white_translucent1;

    private static final int CORNER_STROKE_WIDTH_DP = 3;
    private static final int CORNER_LENGTH_DP = 16;
    private static final int CORNER_COLOR_RES = R.color.white;
    private static final int CORNER_COLOR_RES_COLORED = R.color.colorAccent;

    private static final int GUIDELINE_STROKE_WIDTH_DP = 1;
    private static final int GUIDELINE_COLOR_RES = R.color.white;

    private static final int TOUCH_DELTA_DP = 40;

    private static final int NO_CORNER = -1;
    private static final int TOP_LEFT = 1;
    private static final int TOP_RIGHT = 2;
    private static final int BOTTOM_RIGHT = 3;
    private static final int BOTTOM_LEFT = 4;

    private Uri imageUri;

    private PointF downPos = new PointF();
    private PointF newTouchPos = new PointF();
    private Rect StartCropRect = new Rect();
    private Rect cropRect;
    private Paint cropRectPaint;
    private Paint cropRectCornerPaint;
    private Paint cropRectCornerPaintColored;
    private Paint guidelinePaint;
    private Paint backgroundPaint;
    // aspectRation < 0: free aspect ratio; otherwise: deltaY * aspectRation = deltaX
    private double aspectRatio = -1.0;

    private int touchedCorner = NO_CORNER;
    private boolean touching = false;

    private int strokeWidth;
    private int cornerStrokeWidth;
    private int cornerLength;
    private int touchDelta;

    public interface OnResultListener {
        void onResult(Result result);
    }

    /**
     * Result object for the imageView, containing the cropped bitmap.
     **/
    public static class Result {

        private Uri uri;
        private Bitmap bitmap;

        Result(Uri uri, Bitmap bitmap) {
            this.uri = uri;
            this.bitmap = bitmap;
        }

        public Uri getImageUri() {
            return uri;
        }

        public Bitmap getCroppedBitmap() {
            return bitmap;
        }
    }

    /**
     * Store the current state of the ImageView to preserve it across configuration changes.
     **/
    public static class State extends ImageViewState {

        private int[] cropRect;
        private double aspectRatio;

        State(float scale, PointF center, int orientation, Rect cropRect, double aspectRatio) {
            super(scale, center, orientation);
            this.cropRect = new int[]{
                    cropRect.left, cropRect.top,
                    cropRect.right, cropRect.bottom};
            this.aspectRatio = aspectRatio;
        }

        Rect getCropRect() {
            return new Rect(
                    cropRect[0], cropRect[1],
                    cropRect[2], cropRect[3]);
        }

        double getAspectRatio() {
            return aspectRatio;
        }
    }

    public CropImageView(Context context, AttributeSet attr) {
        super(context, attr);
        init();
    }

    public CropImageView(Context context) {
        this(context, null);
    }

    private void init() {
        //setZoomEnabled(false);
        //setPanEnabled(false);
        //setPanLimit(PAN_LIMIT_CENTER);
        //setOrientationDegrees(0);
        //setMinScale(0.01f);
        //setMinimumScaleType(SCALE_TYPE_CUSTOM);
        //setRotationEnabled(false);
        setRetainXSwipe(false);

        setOnTouchListener(this);

        strokeWidth = Util.dpToPx(getContext(), STROKE_WIDTH_DP);
        cornerStrokeWidth = Util.dpToPx(getContext(), CORNER_STROKE_WIDTH_DP);
        cornerLength = Util.dpToPx(getContext(), CORNER_LENGTH_DP);
        touchDelta = Util.dpToPx(getContext(), TOUCH_DELTA_DP);

        cropRectPaint = new Paint();
        cropRectPaint.setColor(ContextCompat.getColor(getContext(), STROKE_COLOR_RES));
        cropRectPaint.setStrokeWidth(strokeWidth);
        cropRectPaint.setStyle(Paint.Style.STROKE);

        cropRectCornerPaint = new Paint();
        cropRectCornerPaint.setColor(ContextCompat.getColor(getContext(), CORNER_COLOR_RES));
        cropRectCornerPaint.setStrokeWidth(Util.dpToPx(getContext(), CORNER_STROKE_WIDTH_DP));
        cropRectCornerPaint.setStyle(Paint.Style.STROKE);

        cropRectCornerPaintColored = new Paint();
        cropRectCornerPaintColored
                .setColor(ContextCompat.getColor(getContext(), CORNER_COLOR_RES_COLORED));
        cropRectCornerPaintColored
                .setStrokeWidth(Util.dpToPx(getContext(), CORNER_STROKE_WIDTH_DP));
        cropRectCornerPaintColored.setStyle(Paint.Style.STROKE);

        guidelinePaint = new Paint();
        guidelinePaint.setColor(ContextCompat.getColor(getContext(), GUIDELINE_COLOR_RES));
        guidelinePaint.setStrokeWidth(Util.dpToPx(getContext(), GUIDELINE_STROKE_WIDTH_DP));
        guidelinePaint.setStyle(Paint.Style.STROKE);
        guidelinePaint.setAlpha(100);

        backgroundPaint = new Paint();
        backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.black));
        backgroundPaint.setAlpha(100);
    }

    /**
     * Load an image into the imageView.
     *
     * @param uri   for the image to be loaded
     * @param state the state for the imageView (might be null)
     **/
    public void loadImage(Uri uri, State state) {
        setProgressBarVisibility(VISIBLE);

        imageUri = uri;

        String mimeType = MediaType.getMimeType(getContext(), imageUri);
        if (MediaType.checkRAWMimeType(mimeType)) {
            setRegionDecoderFactory(RAWImageRegionDecoder::new);
        } else {
            setRegionDecoderFactory(CustomRegionDecoder::new);
        }

        if (state != null) {
            cropRect = state.getCropRect();
            setAspectRatio(state.getAspectRatio());
        }
        loadImage(uri.toString());
    }

    public Uri getImageUri() {
        return imageUri;
    }

    @Override
    protected void onImageLoaded() {
        super.onImageLoaded();
        if (cropRect == null) {
            cropRect = getMaxCenteredCropRect();
            //Log.d("CropImageView", "onImageLoaded: " + cropRect);
        }
        autoZoom(false);

        setProgressBarVisibility(GONE);
    }

    /**
     * Rotate the image by 90°.
     **/
    public void rotate90Degree() {
        aspectRatio = 1 / aspectRatio;
        float oldRotationDegrees;
        if (getAnim() != null) oldRotationDegrees = getAnim().getRotationDegreesEnd();
        else oldRotationDegrees = getRotationDegrees();
        float rotationDegrees = oldRotationDegrees - 90;
        new AnimationBuilder(getCenterOfCropRect(), getFullScale(cropRect.width(), cropRect.height(), getClosestRightAngleDegreesNormalized(rotationDegrees)), rotationDegrees).start(true);
    }

    public void setShowCroppingEdge(boolean show) {
        showCroppingEdge = show;
        invalidate();
    }

    /**
     * Restore the ImageView to initial state.
     **/
    public void restore() {
        setOrientationDegrees(0);
        setCropRect(getMaxCenteredCropRect(), false);
        autoZoom(true);
    }

    /**
     * Method to call when the cropped bitmap is needed.
     *
     * @param onResultListener listener for the resulting cropped bitmap
     **/
    public void getCroppedBitmap(final OnResultListener onResultListener) {
        AsyncTask.execute(() -> {
            try {
                Bitmap croppedBitmap;
                if (getBitmap() != null/*todo isPreview?*/) {
                    croppedBitmap = Bitmap.createBitmap(getBitmap(), cropRect.left, cropRect.top, cropRect.width(), cropRect.height());
                } else {
                    ImageRegionDecoder regionDecoder = getRegionDecoder();
                    if (regionDecoder == null) {
                        regionDecoder = getRegionDecoderFactory().make();
                        regionDecoder.init(getContext(), imageUri);
                    }

                    croppedBitmap = regionDecoder.decodeRegion(cropRect, 1);
                }
                //rotate image
                Matrix matrix = new Matrix();
                matrix.postRotate((float) getClosestRightAngleDegrees() + getRotation());
                Bitmap rotatedBitmap = Bitmap.createBitmap(croppedBitmap, 0, 0, croppedBitmap.getWidth(), croppedBitmap.getHeight(), matrix, false);
                croppedBitmap.recycle();

                //ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                //rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);

                final Result result = new Result(imageUri, rotatedBitmap);
                CropImageView.this.post(() -> {
                    onResultListener.onResult(result);
                });
            } catch (Exception e) {
                e.printStackTrace();
                CropImageView.this.post(() -> {
                    onResultListener.onResult(new Result(getImageUri(), null));
                });
            }
        });
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (touchedCorner != NO_CORNER) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_MOVE: {
                    Rect newCropRect;
                    if (touchedCorner != NO_CORNER) {
                        newCropRect = getNewRect(motionEvent.getX(), motionEvent.getY());
                    } else {
                        newCropRect = getMovedRect(motionEvent);
                    }

                    if (newCropRect != null) {
                        setCropRect(newCropRect);
                    }

                    /*PointF center = getCenterOfCropRect();
                    float scale = getScale();
                    float newScale = getNewScale();
                    setScaleAndCenter(newScale < scale ? newScale : scale, center);*/
                    invalidate();
                    break;
                }
                case MotionEvent.ACTION_UP:
                    if (cropRect != null) {
                        float widthRatio = cropRect.width() * getScale() / getWidth();
                        float heightRatio = cropRect.height() * getScale() / getHeight();
                        if (widthRatio < 1 && heightRatio < 1)
                            autoZoom(touchedCorner != NO_CORNER);
                        touching = false;
                        touchedCorner = NO_CORNER;
                    }
                    break;
                default:
                    break;
            }
            return true;
        }

        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            touchedCorner = getTouchedCorner(motionEvent);
            if (touchedCorner != NO_CORNER) {
                StartCropRect.set(cropRect);
                touching = true;
                return true;
            }
        }

        return super.onTouchEvent(motionEvent);
    }

    /**
     * Get the center of the current cropRect.
     *
     * @return the center of the current cropRect
     **/
    private PointF getCenterOfCropRect() {
        return new PointF(
                cropRect.exactCenterX(),
                cropRect.exactCenterY());
    }

    /**
     * Set if the ImageView should zoom in and out according to the current cropRect
     **/
    public void autoZoom(boolean animate) {
        //Log.i(TAG, "autoZoom");
        //auto-zoom
        float scale = getFullScale(cropRect.width(), cropRect.height());
        PointF center = getCenterOfCropRect();
        if (animate) {
            new AnimationBuilder(center, scale).start(true);
        } else {
            setScaleAndCenter(scale, center);
        }
    }

    /**
     * Returns the touched corner to the associated motionEvent.
     *
     * @param motionEvent the associated MotionEvent
     * @return one of: NO_CORNER, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT or BOTTOM_LEFT
     **/
    private int getTouchedCorner(MotionEvent motionEvent) {
        if (cropRect == null) return NO_CORNER;

        downPos.set(motionEvent.getX(), motionEvent.getY());
        newTouchPos.set(downPos);

        sourceToViewCoord(cropRect.left, cropRect.top, p);
        if (isPointFCloseEnough(newTouchPos, p))
            return TOP_LEFT;

        sourceToViewCoord(cropRect.right, cropRect.top, p);
        if (isPointFCloseEnough(newTouchPos, p))
            return TOP_RIGHT;

        sourceToViewCoord(cropRect.right, cropRect.bottom, p);
        if (isPointFCloseEnough(newTouchPos, p))
            return BOTTOM_RIGHT;

        sourceToViewCoord(cropRect.left, cropRect.bottom, p);
        if (isPointFCloseEnough(newTouchPos, p))
            return BOTTOM_LEFT;

        return NO_CORNER;
    }

    boolean isPointFCloseEnough(PointF p1, PointF p2) {
        return Math.max(Math.abs(p1.x - p2.x), Math.abs(p1.y - p2.y)) < touchDelta;
    }

    /**
     * Get the new cropRect after a motionEvent from the x and y coordinate.
     *
     * @param x x-coordinate of the associated MotionEvent
     * @param y y-coordinate of the associated MotionEvent
     * @return the new cropRect; might be null if cropRect didn't change; bounds need to be checked!
     **/
    private Rect getNewRect(float x, float y) {
        PointF currentTouchPos = viewToSourceCoord(x, y);

        boolean freeAspectRatio = aspectRatio < 0.0;

        if (freeAspectRatio) {
            if (touchedCorner == TOP_LEFT) {
                return checkRectBounds(new Rect((int) currentTouchPos.x, (int) currentTouchPos.y,
                        cropRect.right, cropRect.bottom), true);
            } else if (touchedCorner == TOP_RIGHT) {
                return checkRectBounds(new Rect(cropRect.left, (int) currentTouchPos.y,
                        (int) currentTouchPos.x, cropRect.bottom), true);
            } else if (touchedCorner == BOTTOM_RIGHT) {
                return checkRectBounds(new Rect(cropRect.left, cropRect.top,
                        (int) currentTouchPos.x, (int) currentTouchPos.y), true);
            } else if (touchedCorner == BOTTOM_LEFT) {
                return checkRectBounds(new Rect((int) currentTouchPos.x, cropRect.top,
                        cropRect.right, (int) currentTouchPos.y), true);
            }
        } else {
            // fixed aspectRatio
            if (touchedCorner == TOP_LEFT) {
                int delta = (int) Math.max(currentTouchPos.x - cropRect.left,
                        currentTouchPos.y - cropRect.top);
                return checkRectBounds(new Rect((int) Math.round(cropRect.left
                        + delta * aspectRatio), cropRect.top + delta,
                        cropRect.right, cropRect.bottom), true);
            } else if (touchedCorner == TOP_RIGHT) {
                int delta = (int) Math.max(cropRect.right - currentTouchPos.x,
                        currentTouchPos.y - cropRect.top);
                return checkRectBounds(new Rect(cropRect.left, cropRect.top + delta,
                        (int) Math.round(cropRect.right - delta * aspectRatio),
                        cropRect.bottom), true);
            } else if (touchedCorner == BOTTOM_RIGHT) {
                int delta = (int) Math.max(cropRect.right - currentTouchPos.x,
                        cropRect.bottom - currentTouchPos.y);
                return checkRectBounds(new Rect(cropRect.left, cropRect.top,
                        (int) Math.round(cropRect.right - delta * aspectRatio),
                        cropRect.bottom - delta), true);
            } else if (touchedCorner == BOTTOM_LEFT) {
                int delta = (int) Math.max(currentTouchPos.x - cropRect.left,
                        cropRect.bottom - currentTouchPos.y);
                return checkRectBounds(new Rect((int) Math.round(cropRect.left
                        + delta * aspectRatio), cropRect.top,
                        cropRect.right, cropRect.bottom - delta), true);
            }
        }

        return null;
    }

    /**
     * Get the moved cropRect from the motionEvent.
     *
     * @param motionEvent the associated MotionEvent
     * @return the moved cropRect; might be null if cropRect didn't change
     **/
    private Rect getMovedRect(MotionEvent motionEvent) {
        if (cropRect == null) {
            return null;
        }

        newTouchPos.set(motionEvent.getX(), motionEvent.getY());

        int dSX = Math.round((downPos.x - newTouchPos.x) / getScale());
        int dSY = Math.round((downPos.y - newTouchPos.y) / getScale());

        return checkRectBounds(new Rect(
                StartCropRect.left + dSX,
                StartCropRect.top + dSY,
                StartCropRect.right + dSX,
                StartCropRect.bottom + dSY), false);
    }

    /**
     * Checks the bounds and size of the current cropRect:
     * - checks if inside image
     * - checks that cropRect is bigger than min size
     *
     * @param cropRect the Rect that should be checked
     * @param resize   flag if the cropRect can be resized or only translated to be made valid
     * @return a valid cropRect; might be null if cropRect didn't change
     **/
    private Rect checkRectBounds(Rect cropRect, boolean resize) {
        Rect image = getImageRect();
        Rect newCropRect = cropRect;
        //check if inside image
        int width = newCropRect.width();
        int height = newCropRect.height();

        if (!image.contains(newCropRect)) {
            if (aspectRatio >= 0.0) {
                if (resize) {
                    // new cropRect to big => try and fix size
                    // check corners
                    if (touchedCorner == TOP_LEFT) {
                        if (image.left > newCropRect.left) {
                            int delta = (int) ((image.left - newCropRect.left) / aspectRatio);
                            newCropRect = new Rect(image.left, newCropRect.top + delta,
                                    newCropRect.right, newCropRect.bottom);
                        }
                        if (image.top > newCropRect.top) {
                            int delta = (int) ((image.top - newCropRect.top) * aspectRatio);
                            newCropRect = new Rect(newCropRect.left + delta, image.top,
                                    newCropRect.right, newCropRect.bottom);
                        }
                    } else if (touchedCorner == TOP_RIGHT) {
                        if (image.right < newCropRect.right) {
                            int delta = (int) ((newCropRect.right - image.right) / aspectRatio);
                            newCropRect = new Rect(newCropRect.left, newCropRect.top + delta,
                                    image.right, newCropRect.bottom);
                        }
                        if (image.top > newCropRect.top) {
                            int delta = (int) ((image.top - newCropRect.top) * aspectRatio);
                            newCropRect = new Rect(newCropRect.left, image.top,
                                    newCropRect.right - delta, newCropRect.bottom);
                        }
                    } else if (touchedCorner == BOTTOM_RIGHT) {
                        if (image.right < newCropRect.right) {
                            int delta = (int) ((newCropRect.right - image.right) / aspectRatio);
                            newCropRect = new Rect(newCropRect.left, newCropRect.top,
                                    image.right, newCropRect.bottom - delta);
                        }
                        if (image.bottom < newCropRect.bottom) {
                            int delta = (int) ((newCropRect.bottom - image.bottom) * aspectRatio);
                            newCropRect = new Rect(newCropRect.left, newCropRect.top,
                                    newCropRect.right - delta, image.bottom);
                        }
                    } else if (touchedCorner == BOTTOM_LEFT) {
                        if (image.left > newCropRect.left) {
                            int delta = (int) ((image.left - newCropRect.left) / aspectRatio);
                            newCropRect = new Rect(image.left, newCropRect.top,
                                    newCropRect.right, newCropRect.bottom - delta);
                        }
                        if (image.bottom < newCropRect.bottom) {
                            int delta = (int) ((newCropRect.bottom - image.bottom) * aspectRatio);
                            newCropRect = new Rect(newCropRect.left + delta, newCropRect.top,
                                    newCropRect.right, image.bottom);
                        }
                    }
                } else {
                    // check edges
                    // left edges
                    if (image.left > newCropRect.left) {
                        newCropRect = new Rect(image.left, newCropRect.top,
                                image.left + width, newCropRect.bottom);
                    }
                    // top edge
                    if (image.top > newCropRect.top) {
                        newCropRect = new Rect(newCropRect.left, image.top,
                                newCropRect.right, image.top + height);
                    }
                    // right edge
                    if (image.right < newCropRect.right) {
                        newCropRect = new Rect(image.right - width, newCropRect.top,
                                image.right, newCropRect.bottom);
                    }
                    // bottom edge
                    if (image.bottom < newCropRect.bottom) {
                        newCropRect = new Rect(newCropRect.left, image.bottom - height,
                                newCropRect.right, image.bottom);
                    }
                }
            } else {
                // cropRect not inside => try to fix it
                if (image.left > newCropRect.left) {
                    newCropRect = new Rect(image.left, newCropRect.top,
                            resize ? newCropRect.right : image.left + width,
                            newCropRect.bottom);
                }

                if (image.top > newCropRect.top) {
                    newCropRect = new Rect(newCropRect.left, image.top, newCropRect.right,
                            resize ? newCropRect.bottom : image.top + height);
                }

                if (image.right < newCropRect.right) {
                    newCropRect = new Rect(resize ? newCropRect.left : image.right - width,
                            newCropRect.top, image.right, newCropRect.bottom);
                }

                if (image.bottom < newCropRect.bottom) {
                    newCropRect = new Rect(newCropRect.left,
                            resize ? newCropRect.top : image.bottom - height,
                            newCropRect.right, image.bottom);
                }
            }
        }

        Rect minRect = getMinCropRect();
        //check min size
        width = newCropRect.width();
        if (width < minRect.width()) {
            if (touchedCorner == TOP_LEFT) {
                newCropRect = new Rect(newCropRect.right - minRect.width(),
                        newCropRect.top, newCropRect.right, newCropRect.bottom);
            } else if (touchedCorner == TOP_RIGHT) {
                newCropRect = new Rect(newCropRect.left, newCropRect.top,
                        newCropRect.left + minRect.width(),
                        newCropRect.bottom);
            } else if (touchedCorner == BOTTOM_RIGHT) {
                newCropRect = new Rect(newCropRect.left, newCropRect.top,
                        newCropRect.left + minRect.width(),
                        newCropRect.bottom);
            } else if (touchedCorner == BOTTOM_LEFT) {
                newCropRect = new Rect(newCropRect.right - minRect.width(),
                        newCropRect.top, newCropRect.right, newCropRect.bottom);
            }
        }

        height = newCropRect.height();
        if (height < minRect.height()) {
            if (touchedCorner == TOP_LEFT) {
                newCropRect = new Rect(newCropRect.left,
                        newCropRect.bottom - minRect.height(),
                        newCropRect.right, newCropRect.bottom);
            } else if (touchedCorner == TOP_RIGHT) {
                newCropRect = new Rect(newCropRect.left,
                        newCropRect.bottom - minRect.height(),
                        newCropRect.right, newCropRect.bottom);
            } else if (touchedCorner == BOTTOM_RIGHT) {
                newCropRect = new Rect(newCropRect.left, newCropRect.top,
                        newCropRect.right,
                        newCropRect.top + minRect.height());
            } else if (touchedCorner == BOTTOM_LEFT) {
                newCropRect = new Rect(newCropRect.left,
                        newCropRect.top, newCropRect.right,
                        newCropRect.top + minRect.height());
            }
        }

        return newCropRect;
    }

    private void getImageRect(Rect result) {
        result.set(0, 0, getSWidth(), getSHeight());
    }

    private Rect getImageRect() {
        Rect rect = new Rect();
        getImageRect(rect);
        return rect;
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
    }

    RectF rectF = new RectF();
    RectF vCropRectF = new RectF();

    @Override
    protected void onDrawPreRotate(@NonNull Canvas canvas) {
        if (cropRect == null) {
            return;
        }
        vCropRectF.set(cropRect);
        sourceToViewRectPreRotate(vCropRectF, vCropRectF);
        drawBackground(canvas);
        if (showCroppingEdge)
            drawRect(canvas);
        drawCorners(canvas);
        if (touching) {
            drawGuidelines(canvas);
        }
    }

    private void drawRect(Canvas canvas) {
        canvas.drawRect(
                vCropRectF.left + strokeWidth / 2f,
                vCropRectF.top + strokeWidth / 2f,
                vCropRectF.right - strokeWidth / 2f,
                vCropRectF.bottom - strokeWidth / 2f,
                cropRectPaint);
    }

    PointF p = new PointF();

    private void drawCorners(Canvas canvas) {
        drawCorner(vCropRectF.left, vCropRectF.top, canvas, 0);
        drawCorner(vCropRectF.right, vCropRectF.top, canvas, 90);
        drawCorner(vCropRectF.right, vCropRectF.bottom, canvas, 180);
        drawCorner(vCropRectF.left, vCropRectF.bottom, canvas, 270);
    }

    Matrix m = new Matrix();

    private void drawCorner(float x, float y, Canvas canvas, float degrees) {
        Path tlCorner = getTopLeftCornerPath();
        m.reset();
        m.postRotate(degrees);
        m.postTranslate(x, y);
        tlCorner.transform(m);
        canvas.drawPath(tlCorner, cropRectCornerPaint);
    }

    Path path = new Path();

    @SuppressWarnings("SuspiciousNameCombination")
    private Path getTopLeftCornerPath() {
        float halfStrokeWidth = cornerStrokeWidth / 2f;
        float endPoint = cornerLength - halfStrokeWidth;
        path.reset();
        path.moveTo(halfStrokeWidth, endPoint);
        path.lineTo(halfStrokeWidth, halfStrokeWidth);
        path.lineTo(endPoint, halfStrokeWidth);
        return path;
    }

    Rect imageRect = new Rect();

    private void drawBackground(Canvas canvas) {
        getImageRect(imageRect);
        rectF.set(imageRect);
        sourceToViewRectPreRotate(rectF, rectF);

        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(vCropRectF.left, vCropRectF.top);
        path.lineTo(vCropRectF.right, vCropRectF.top);
        path.lineTo(vCropRectF.right, vCropRectF.bottom);
        path.lineTo(vCropRectF.left, vCropRectF.bottom);
        path.close();

        path.moveTo(rectF.left, rectF.top);
        path.lineTo(rectF.right, rectF.top);
        path.lineTo(rectF.right, rectF.bottom);
        path.lineTo(rectF.left, rectF.bottom);
        path.close();

        backgroundPaint.setAlpha(touching ? 100 : 200);

        canvas.drawPath(this.path, backgroundPaint);
    }

    private void drawGuidelines(Canvas canvas) {
        float width = vCropRectF.right - vCropRectF.left;
        float height = vCropRectF.bottom - vCropRectF.top;
        float thirdWidth = width / 3;
        float thirdHeight = height / 3;
        float strokeWidth = this.strokeWidth * 1.5f;
        path.reset();
        for (int i = 1; i <= 2; i++) {
            path.moveTo(vCropRectF.left + thirdWidth * i, vCropRectF.top + strokeWidth);
            path.lineTo(vCropRectF.left + thirdWidth * i, vCropRectF.bottom - strokeWidth);
            path.moveTo(vCropRectF.left + strokeWidth, vCropRectF.top + thirdHeight * i);
            path.lineTo(vCropRectF.right - strokeWidth, vCropRectF.top + thirdHeight * i);
        }
        canvas.drawPath(path, guidelinePaint);
    }

    public State getCropImageViewState() {
        ImageViewState state = getState();
        if (state != null) {
            return new State(state.getScale(), state.getCenter(), state.getOrientation(),
                    cropRect, aspectRatio);
        }
        return null;
    }

    private ProgressBar getProgressBar() {
        ViewGroup parent = (ViewGroup) getParent();
        return parent.findViewById(R.id.progress_bar);
    }

    private void setProgressBarVisibility(int visibility) {
        ProgressBar progressBar = getProgressBar();
        if (progressBar != null) {
            progressBar.setVisibility(visibility);
        }
    }

    @SuppressWarnings("unused")
    public double getAspectRatio() {
        return aspectRatio;
    }

    /**
     * Set the an aspectRatio for the cropRect.
     *
     * @param aspectRatio smaller 0: free aspectRatio
     *                    greater 0: a = aspectRation * b
     *                    where a and b are the sides of the cropping rect
     **/
    public void setAspectRatio(double aspectRatio) {
        if (this.aspectRatio == aspectRatio) {
            return;
        }
        this.aspectRatio = aspectRatio;
        // update the cropRect
        if (isImageLoaded() && aspectRatio > 0.0) {
            setCropRect(getMaxCenteredCropRect(), true);
            //autoZoom(true);
        }
    }

    /**
     * Sets the aspect ratio to free.
     **/
    public void setFreeAspectRatio() {
        setAspectRatio(-1.0);
    }

    /**
     * Set the original aspect ratio of the image as fixed aspect ratio.
     **/
    public void setOriginalAspectRatioFixed() {
        if (!isImageLoaded()) {
            // if no image was loaded set the aspect ratio to free
            setAspectRatio(-1.0);
            return;
        }
        Rect imageRect = getImageRect();
        setAspectRatio((double) (imageRect.width()) / (double) (imageRect.height()));
    }

    /**
     * Returns the max possible cropRect that is inside the image.
     *
     * @return max centered cropRect; might be null if no image was loaded
     **/
    private Rect getMaxCenteredCropRect() {
        if (!isReady()) {
            return null;
        }
        if (aspectRatio < 0.0) {
            return getImageRect();
        } else {
            Rect imageRect = getImageRect();
            int imageHeight = imageRect.bottom - imageRect.top,
                    imageWidth = imageRect.right - imageRect.left;
            if (imageHeight * aspectRatio <= imageWidth) {
                int padding = (int) ((imageWidth - (imageHeight * aspectRatio)) / 2);
                return new Rect(padding,
                        0,
                        (int) ((imageHeight * aspectRatio) + padding),
                        imageHeight);
            } else {
                int padding = (int) ((imageHeight - (imageWidth / aspectRatio)) / 2);
                return new Rect(0,
                        padding,
                        imageWidth,
                        (int) ((imageWidth / aspectRatio) + padding));
            }
        }
    }

    /**
     * Returns the minimal valid cropRect.
     *
     * @return minimal valid cropRect.
     **/
    private Rect getMinCropRect() {
        return new Rect(0, 0, 1, 1);
    }

    /**
     * Set a new cropRect. When animate is set to true the view is automatically autoZoomed.
     *
     * @param cropRect new cropRect
     * @param animate  between the old and new cropRect
     **/
    public void setCropRect(final Rect cropRect, boolean animate) {
        if (!animate || this.cropRect == null || cropRect == null) {
            setCropRect(cropRect);
            return;
        }

        final Rect oldCropRect = this.cropRect;

        final Rect delta = new Rect(
                cropRect.left - oldCropRect.left,
                cropRect.top - oldCropRect.top,
                cropRect.right - oldCropRect.right,
                cropRect.bottom - oldCropRect.bottom);

        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(300);
        animator.setInterpolator(MyInterpolator.accelerateDecelerateInterpolator);
        animator.addUpdateListener(valueAnimator -> {
            float animatedValue = valueAnimator.getAnimatedFraction();
            setCropRect(new Rect(
                    (int) (oldCropRect.left + delta.left * animatedValue),
                    (int) (oldCropRect.top + delta.top * animatedValue),
                    (int) (oldCropRect.right + delta.right * animatedValue),
                    (int) (oldCropRect.bottom + delta.bottom * animatedValue)));
            autoZoom(false);
        });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        setCropRect(cropRect);
                        autoZoom(false);
                    }
                });
        animator.start();
    }

    /**
     * Set a new cropRect.
     *
     * @param cropRect new cropRect
     **/
    public void setCropRect(Rect cropRect) {
        this.cropRect = cropRect;
    }
}