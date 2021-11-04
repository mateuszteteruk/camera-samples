package pl.mateuszteteruk.camerasamples

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pl.mateuszteteruk.camerasamples.databinding.FragmentCameraBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding: FragmentCameraBinding
        get() = requireNotNull(_binding)

    private lateinit var cameraExecutor: ExecutorService

    private val imageFlow: MutableSharedFlow<ImageProxy> = MutableSharedFlow()
    private val areaFlow: MutableSharedFlow<Area> = MutableSharedFlow()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
                .apply {
                    setSurfaceProvider(binding.previewFront.surfaceProvider)
                }

            val analyzer = ImageAnalysis.Builder().build()

            val bitmap = binding.previewFront
            val paint = Paint().apply {
                color = Color.MAGENTA
                style = Paint.Style.STROKE
                strokeWidth = 10f
            }

            observeCameraImageProxy()
            observeAreaChanges(bitmap, paint)

            analyzer.setAnalyzer(cameraExecutor) { image ->
                viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                    imageFlow.emit(image)
                }
                image.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, analyzer)
            } catch (exception: Exception) {

            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun observeCameraImageProxy() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            imageFlow
                .map {
                    calculateArea(it)
                }
                .collect {
                    areaFlow.emit(it)
                }
        }
    }

    private fun observeAreaChanges(bitmap: PreviewView, paint: Paint) {
        val overlay = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            areaFlow
                .distinctUntilChanged()
                .collect { area ->
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    canvas.drawRect(area.x, area.y, area.x + area.size, area.y + area.size, paint)
                    binding.overlay.setImageBitmap(overlay)
                    Log.d("TAG", "Area: $area")
                }
        }
    }

    private fun calculateArea(image: ImageProxy): Area {
        return Area(
            x = AreaGenerator.next(),
            y = 300F,
            size = 400F,
        )
    }

    private data class Area(
        val x: Float,
        val y: Float,
        val size: Float,
    )
}

private object AreaGenerator {

    private val max = 1024F - 400F
    private var currentOffsetX = 0F

    fun next(): Float {
        if (currentOffsetX > max) {
            currentOffsetX = 0F
        } else {
            currentOffsetX += 10F
        }
        return currentOffsetX
    }
}
