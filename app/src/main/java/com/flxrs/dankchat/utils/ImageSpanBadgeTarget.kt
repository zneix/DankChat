package com.flxrs.dankchat.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class ImageSpanBadgeTarget(private val context: Context, private val callback: (Drawable) -> Unit) : CustomTarget<Bitmap>() {

	override fun onLoadStarted(placeholder: Drawable?) {
		if (placeholder != null) {
			callback(placeholder)
		}
	}

	override fun onLoadFailed(errorDrawable: Drawable?) {
		if (errorDrawable != null) {
			callback(errorDrawable)
		}
	}

	override fun onLoadCleared(placeholder: Drawable?) = Unit

	override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
		val drawable = BitmapDrawable(context.resources, resource)
		callback(drawable)
	}
}