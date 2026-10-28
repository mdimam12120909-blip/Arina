package com.arina.assistant.device

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * One photo from the front camera without any preview on screen. The camera streams for a moment so
 * exposure settles, then the newest frame is kept, turned upright and shrunk. Nothing is saved to disk.
 * Blocking: call from a background thread.
 */
object FrontCamera {

    private const val WARMUP_MS = 900L
    private const val MAX_SIDE = 960

    @SuppressLint("MissingPermission")
    fun capture(ctx: Context): ByteArray {
        val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = mgr.cameraIdList.firstOrNull {
            mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: throw IOException("this phone has no front camera")
        val chars = mgr.getCameraCharacteristics(id)
        val rotation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
        val sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888)
            ?: throw IOException("the front camera gives no usable size")
        val size = sizes.filter { maxOf(it.width, it.height) <= 1280 }.maxByOrNull { it.width * it.height } ?: sizes.minBy { it.width * it.height }

        val thread = HandlerThread("front-camera").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 3)
        val latest = AtomicReference<ByteArray?>()
        val firstFrameAt = AtomicReference(0L)
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            img.use {
                val now = System.currentTimeMillis()
                firstFrameAt.compareAndSet(0L, now)
                // Keep frames only after the warm-up, when exposure and focus have settled.
                if (now - firstFrameAt.get() >= WARMUP_MS) latest.set(toNv21(it))
            }
        }, handler)

        val opened = CountDownLatch(1)
        val camera = AtomicReference<CameraDevice?>()
        val failure = AtomicReference<String?>()
        try {
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(c: CameraDevice) { camera.set(c); opened.countDown() }
                override fun onDisconnected(c: CameraDevice) { c.close(); failure.set("the camera was taken by another app"); opened.countDown() }
                override fun onError(c: CameraDevice, error: Int) { c.close(); failure.set("camera error $error"); opened.countDown() }
            }, handler)
        } catch (e: CameraAccessException) {
            thread.quitSafely(); reader.close()
            throw IOException(e.message ?: "camera access refused")
        }
        if (!opened.await(5, TimeUnit.SECONDS)) { thread.quitSafely(); reader.close(); throw IOException("the camera did not open in time") }
        val cam = camera.get() ?: run { thread.quitSafely(); reader.close(); throw IOException(failure.get() ?: "the camera did not open") }

        try {
            val ready = CountDownLatch(1)
            val session = AtomicReference<CameraCaptureSession?>()
            @Suppress("DEPRECATION")
            cam.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) { session.set(s); ready.countDown() }
                override fun onConfigureFailed(s: CameraCaptureSession) { failure.set("could not start the camera stream"); ready.countDown() }
            }, handler)
            if (!ready.await(5, TimeUnit.SECONDS)) throw IOException("the camera stream did not start in time")
            val s = session.get() ?: throw IOException(failure.get() ?: "could not start the camera stream")
            val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            s.setRepeatingRequest(req.build(), null, handler)

            val until = System.currentTimeMillis() + WARMUP_MS + 3500
            while (latest.get() == null && System.currentTimeMillis() < until) Thread.sleep(50)
            runCatching { s.stopRepeating() }
            val nv21 = latest.get() ?: throw IOException("no picture came from the camera")
            return finish(nv21, size.width, size.height, rotation)
        } finally {
            runCatching { cam.close() }
            runCatching { reader.close() }
            thread.quitSafely()
        }
    }

    /** YUV_420_888 to NV21, honouring row and pixel strides. */
    private fun toNv21(img: Image): ByteArray {
        val w = img.width
        val h = img.height
        val out = ByteArray(w * h * 3 / 2)
        val y = img.planes[0]
        var pos = 0
        for (row in 0 until h) {
            val base = row * y.rowStride
            for (col in 0 until w) out[pos++] = y.buffer.get(base + col * y.pixelStride)
        }
        val u = img.planes[1]
        val v = img.planes[2]
        for (row in 0 until h / 2) {
            for (col in 0 until w / 2) {
                out[pos++] = v.buffer.get(row * v.rowStride + col * v.pixelStride)
                out[pos++] = u.buffer.get(row * u.rowStride + col * u.pixelStride)
            }
        }
        return out
    }

    private fun finish(nv21: ByteArray, w: Int, h: Int, rotation: Int): ByteArray {
        val raw = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 90, raw)
        val bmp = BitmapFactory.decodeByteArray(raw.toByteArray(), 0, raw.size())
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        val turned = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val scale = MAX_SIDE.toFloat() / maxOf(turned.width, turned.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(turned, (turned.width * scale).toInt(), (turned.height * scale).toInt(), true) else turned
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }
}
