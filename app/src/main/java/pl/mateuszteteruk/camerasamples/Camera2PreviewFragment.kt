package pl.mateuszteteruk.camerasamples

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import java.lang.RuntimeException
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Comparator
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class Camera2PreviewFragment : Fragment() {

    val TAG = "Camera2PreviewFragment"
    private val MAX_PREVIEW_WIDTH = 1920
    private val MAX_PREVIEW_HEIGHT = 1080

    private var cameraId: String = ""

    private var textureView: AutoFitTextureView? = null

    private var captureSession: CameraCaptureSession? = null

    private var cameraDevice: CameraDevice? = null

    private var previewSize: Size? = null

    private var backgroundThread: HandlerThread? = null

    private var backgroundHandler: Handler? = null

    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var previewRequest: CaptureRequest? = null

    private var cameraOpenCloseLock = Semaphore(1)

    private var sensorOrientation = 0

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.

            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseLock.release()
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
            val activity: Activity? = activity
            activity?.finish()
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture: SurfaceTexture = textureView!!.getSurfaceTexture()!!

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize!!.getWidth(), previewSize!!.getHeight())

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder!!.addTarget(surface)

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice!!.createCaptureSession(Arrays.asList(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (null == cameraDevice) {
                            return
                        }

                        // When the session is ready, we start displaying the preview.
                        captureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview.
                            previewRequestBuilder!!.set<Int>(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                            // Finally, we start displaying the camera preview.
                            previewRequest = previewRequestBuilder!!.build()
                            captureSession!!.setRepeatingRequest(previewRequest!!,
                                null, backgroundHandler)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(
                        cameraCaptureSession: CameraCaptureSession,
                    ) {
                        Log.e(TAG, "Failed")
//                        showToast("Failed")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera2_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textureView = view.findViewById(R.id.textureView) as AutoFitTextureView
    }

    override fun onResume() {
        super.onResume()
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread!!.looper)

        if (textureView?.isAvailable == true) {
            openCamera(textureView!!.width, textureView!!.height)
        } else {
            textureView?.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
        stopBackgroundThread()
    }

    private fun openCamera(width: Int, height: Int) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        configureCamera(width, height)
        configureTransform(width, height)
        val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw IllegalStateException("Oops")
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }

    private fun configureCamera(width: Int, height: Int) {
        val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (camera in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(camera)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }

                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

                // For still image captures, we use the largest available size.

                // For still image captures, we use the largest available size.
                val largest = Collections.max(
                    Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                    CompareSizesByArea())

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation = requireActivity().windowManager.defaultDisplay.rotation
                //noinspection ConstantConditions
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                var swappedDimensions = false
                when (displayRotation) {
                    Surface.ROTATION_0, Surface.ROTATION_180 -> if (sensorOrientation == 90 || sensorOrientation == 270) {
                        swappedDimensions = true
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270 -> if (sensorOrientation == 0 || sensorOrientation == 180) {
                        swappedDimensions = true
                    }
                    else -> Log.e(TAG, "Display rotation is invalid: $displayRotation")
                }

                val displaySize = Point()
                requireActivity().windowManager.defaultDisplay.getSize(displaySize)
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth = displaySize.x
                var maxPreviewHeight = displaySize.y

                if (swappedDimensions) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySize.y
                    maxPreviewHeight = displaySize.x
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, largest)

                // We fit the aspect ratio of TextureView to the size of preview we picked.

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView!!.setAspectRatio(
                        previewSize!!.getWidth(), previewSize!!.getHeight())
                } else {
                    textureView!!.setAspectRatio(
                        previewSize!!.getHeight(), previewSize!!.getWidth())
                }

                cameraId = camera
                return

            }
        } catch (exc: Exception) {
            exc.printStackTrace()
        }
    }

    internal class CompareSizesByArea : Comparator<Size> {

        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(lhs.width.toLong() * lhs.height -
                rhs.width.toLong() * rhs.height)
        }
    }

    private fun chooseOptimalSize(
        choices: Array<Size>, textureViewWidth: Int,
        textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size,
    ): Size? {

        // Collect the supported resolutions that are at least as big as the preview Surface
        val bigEnough: MutableList<Size> = ArrayList()
        // Collect the supported resolutions that are smaller than the preview Surface
        val notBigEnough: MutableList<Size> = ArrayList()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w) {
                if (option.width >= textureViewWidth &&
                    option.height >= textureViewHeight
                ) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        return if (bigEnough.size > 0) {
            Collections.min(bigEnough, CompareSizesByArea())
        } else if (notBigEnough.size > 0) {
            Collections.max(notBigEnough, CompareSizesByArea())
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size")
            choices[0]
        }
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity: Activity? = activity
        if (null == textureView || null == previewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0F, 0F, previewSize!!.getHeight().toFloat(), previewSize!!.getWidth().toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale: Float = Math.max(
                viewHeight.toFloat() / previewSize!!.getHeight(),
                viewWidth.toFloat() / previewSize!!.getWidth())
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView!!.setTransform(matrix)
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            if (null != captureSession) {
                captureSession!!.close()
                captureSession = null
            }
            if (null != cameraDevice) {
                cameraDevice!!.close()
                cameraDevice = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
