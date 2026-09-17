package com.shihan.pixeltouch

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.DragEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lays its pages side by side and moves them under the finger, snapping to the
 * nearest page on release. Taps still reach the tiles; only horizontal drags
 * are taken over by the pager.
 */
class SwipePager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    var onPageChanged: ((Int) -> Unit)? = null

    var currentPage = 0
        private set

    private val config = ViewConfiguration.get(context)
    private val touchSlop = config.scaledTouchSlop
    private val minFlingVelocity = config.scaledMinimumFlingVelocity * 4
    private var velocityTracker: VelocityTracker? = null
    private var snapAnimator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var isDragging = false
    private val edgeFlipZone = (28 * resources.displayMetrics.density).toInt()
    private var pendingEdgeFlip = 0
    private val edgeFlip = Runnable {
        setPage(currentPage + pendingEdgeFlip)
        pendingEdgeFlip = 0
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Overlay windows measure loosely, so wrap the widest page unless told an exact width.
        val exactWidth = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
        val childWidthSpec = if (exactWidth) widthMeasureSpec else MeasureSpec.makeMeasureSpec(
            MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST
        )
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var width = 0
        var height = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(childWidthSpec, childHeightSpec)
            width = maxOf(width, child.measuredWidth)
            height = maxOf(height, child.measuredHeight)
        }
        if (exactWidth) width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val pageWidth = r - l
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val left = i * pageWidth + (pageWidth - child.measuredWidth) / 2
            child.layout(left, 0, left + child.measuredWidth, child.measuredHeight)
        }
        if (snapAnimator?.isRunning != true && !isDragging) scrollTo(currentPage * pageWidth, 0)
    }

    fun setPage(page: Int, animate: Boolean = true) {
        val target = page.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        val changed = target != currentPage
        currentPage = target
        if (animate && width > 0) snapTo(target * width) else scrollTo(target * width, 0)
        if (changed) onPageChanged?.invoke(target)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                isDragging = snapAnimator?.isRunning == true
                if (isDragging) snapAnimator?.cancel()
                trackVelocity(event, reset = true)
            }
            MotionEvent.ACTION_MOVE -> {
                trackVelocity(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    isDragging = true
                    // Start moving from the slop edge so the page tracks the whole drag.
                    lastX = downX + if (dx > 0) touchSlop else -touchSlop
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) recycleVelocity()
            }
        }
        return isDragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        trackVelocity(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                lastX = event.x
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging && abs(event.x - downX) > touchSlop) isDragging = true
                if (isDragging) {
                    val maxScroll = (childCount - 1).coerceAtLeast(0) * width
                    val delta = lastX - event.x
                    val next = scrollX + delta
                    // Resist past the first and last page so the edge feels elastic.
                    val resisted = if (next < 0 || next > maxScroll) scrollX + delta / 3f else next
                    scrollTo(resisted.roundToInt(), 0)
                    lastX = event.x
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) settle() else performClick()
                isDragging = false
                recycleVelocity()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) setPage(currentPage)
                isDragging = false
                recycleVelocity()
            }
        }
        return true
    }

    /**
     * A tile being dragged to rearrange blocks normal swipes, so holding it near
     * the left or right edge turns the page instead.
     */
    override fun dispatchDragEvent(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_LOCATION -> {
                val direction = when {
                    event.x < edgeFlipZone -> -1
                    event.x > width - edgeFlipZone -> 1
                    else -> 0
                }
                val canFlip = currentPage + direction in 0 until childCount
                if (direction == 0 || !canFlip) {
                    cancelEdgeFlip()
                } else if (direction != pendingEdgeFlip) {
                    cancelEdgeFlip()
                    pendingEdgeFlip = direction
                    postDelayed(edgeFlip, EDGE_FLIP_DELAY_MS)
                }
            }
            DragEvent.ACTION_DRAG_EXITED,
            DragEvent.ACTION_DROP,
            DragEvent.ACTION_DRAG_ENDED -> cancelEdgeFlip()
        }
        return super.dispatchDragEvent(event)
    }

    private fun cancelEdgeFlip() {
        removeCallbacks(edgeFlip)
        pendingEdgeFlip = 0
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun settle() {
        if (width == 0) return
        val velocity = velocityTracker?.run {
            computeCurrentVelocity(1000)
            xVelocity
        } ?: 0f
        val offset = scrollX - currentPage * width
        val target = when {
            velocity < -minFlingVelocity -> currentPage + 1
            velocity > minFlingVelocity -> currentPage - 1
            offset > width / 3 -> currentPage + 1
            offset < -width / 3 -> currentPage - 1
            else -> currentPage
        }
        setPage(target)
    }

    private fun snapTo(targetX: Int) {
        snapAnimator?.cancel()
        val startX = scrollX
        if (startX == targetX) return
        snapAnimator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
            start()
        }
    }

    private fun trackVelocity(event: MotionEvent, reset: Boolean = false) {
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        if (reset) tracker.clear()
        tracker.addMovement(event)
    }

    private fun recycleVelocity() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onDetachedFromWindow() {
        snapAnimator?.cancel()
        cancelEdgeFlip()
        recycleVelocity()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val EDGE_FLIP_DELAY_MS = 450L
    }
}
