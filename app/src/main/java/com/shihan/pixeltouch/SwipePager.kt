package com.shihan.pixeltouch

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ViewFlipper
import kotlin.math.abs

/** A compact pager that lets the overlay grid switch pages with a finger swipe. */
class SwipePager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewFlipper(context, attrs) {

    var onPageChanged: ((Int) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var isHorizontalSwipe = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isHorizontalSwipe = false
            }
            MotionEvent.ACTION_MOVE -> {
                val distanceX = event.x - downX
                val distanceY = event.y - downY
                if (abs(distanceX) > touchSlop && abs(distanceX) > abs(distanceY)) {
                    isHorizontalSwipe = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val distanceX = event.x - downX
                if (isHorizontalSwipe && abs(distanceX) > touchSlop * 3) {
                    if (distanceX < 0 && displayedChild < childCount - 1) {
                        showNext()
                    } else if (distanceX > 0 && displayedChild > 0) {
                        showPrevious()
                    }
                    onPageChanged?.invoke(displayedChild)
                }
                isHorizontalSwipe = false
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isHorizontalSwipe = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
