package com.techindika.liveconnect.ui

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.techindika.liveconnect.R

/**
 * Full-screen preview shown when a chat image attachment is tapped.
 *
 * Supports pinch-to-zoom, drag-to-pan while zoomed, and double-tap to reset.
 * Tapping the image (a plain, non-dragging tap) or the close button dismisses it.
 */
class ImagePreviewDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_LiveConnect_ImagePreview)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_image_preview, container, false)

    override fun onStart() {
        super.onStart()
        // Let the black backdrop fill the entire screen, edge to edge.
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageView = view.findViewById<ImageView>(R.id.imagePreview)
        val closeButton = view.findViewById<ImageButton>(R.id.closeButton)
        val progressBar = view.findViewById<ProgressBar>(R.id.previewProgress)

        closeButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.lc_white))
        closeButton.setOnClickListener { dismissAllowingStateLoss() }

        val url = arguments?.getString(ARG_IMAGE_URL).orEmpty()
        progressBar.visibility = View.VISIBLE
        Glide.with(this)
            .load(url)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }
            })
            .into(imageView)

        setupZoomableTouch(imageView)
    }

    /**
     * Minimal pinch-zoom/pan/double-tap handler. Starts from the ImageView's own
     * FIT_CENTER matrix (captured lazily on first touch) so the image is centred and
     * fully visible before any gesture, then switches to MATRIX scaling to apply
     * pinch/pan transforms on top of it.
     */
    private fun setupZoomableTouch(imageView: ImageView) {
        val matrix = Matrix()
        val savedMatrix = Matrix()
        val initialMatrix = Matrix()
        val start = PointF()
        var mode = NONE
        var initialized = false
        var scale = 1f

        fun ensureInitialized() {
            if (!initialized) {
                matrix.set(imageView.imageMatrix)
                initialMatrix.set(matrix)
                imageView.scaleType = ImageView.ScaleType.MATRIX
                imageView.imageMatrix = matrix
                initialized = true
            }
        }

        val scaleDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    ensureInitialized()
                    val newScale = (scale * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                    val appliedFactor = newScale / scale
                    scale = newScale
                    matrix.postScale(appliedFactor, appliedFactor, detector.focusX, detector.focusY)
                    imageView.imageMatrix = matrix
                    return true
                }
            }
        )

        val gestureDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    ensureInitialized()
                    matrix.set(initialMatrix)
                    scale = 1f
                    imageView.imageMatrix = matrix
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    dismissAllowingStateLoss()
                    return true
                }
            }
        )

        imageView.setOnTouchListener { _, event ->
            ensureInitialized()
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    start.set(event.x, event.y)
                    mode = DRAG
                }
                MotionEvent.ACTION_POINTER_DOWN -> mode = ZOOM
                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG && scale > 1f) {
                        matrix.set(savedMatrix)
                        matrix.postTranslate(event.x - start.x, event.y - start.y)
                        imageView.imageMatrix = matrix
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> mode = NONE
            }
            true
        }
    }

    companion object {
        private const val ARG_IMAGE_URL = "arg_image_url"
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 4f

        /** Tag to use with [androidx.fragment.app.FragmentManager.findFragmentByTag]. */
        const val TAG = "image_preview"

        fun newInstance(imageUrl: String): ImagePreviewDialogFragment {
            return ImagePreviewDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_IMAGE_URL, imageUrl) }
            }
        }
    }
}
