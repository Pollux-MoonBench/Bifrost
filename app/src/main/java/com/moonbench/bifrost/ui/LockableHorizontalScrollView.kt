package com.moonbench.bifrost.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.HorizontalScrollView

class LockableHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    var scrollLocked: Boolean = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return if (scrollLocked) {
            true
        } else {
            super.onInterceptTouchEvent(ev)
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return if (scrollLocked) {
            true
        } else {
            super.onTouchEvent(ev)
        }
    }

    override fun fling(velocityX: Int) {
        if (!scrollLocked) {
            super.fling(velocityX)
        }
    }
}
