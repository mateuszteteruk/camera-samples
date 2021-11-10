package pl.mateuszteteruk.camerasamples

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Rational
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
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
//                    binding.previewFront.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    setSurfaceProvider(binding.previewFront.surfaceProvider)
                }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            observeCameraImageProxy()
            observeAreaChanges()

            analyzer.setAnalyzer(cameraExecutor) { image: ImageProxy ->
                viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                    imageFlow.emit(image)
                }
                image.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val viewPort = ViewPort.Builder(Rational(4, 3), Surface.ROTATION_0).build()
            val usecase = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(analyzer)
//                .setViewPort(viewPort)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera: Camera = cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, usecase)
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

    private fun observeAreaChanges() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            areaFlow
                .distinctUntilChanged()
                .collect { area ->
//                    binding.overlay.update(area.x, area.y)
//                    binding.previewFront.translationX = area.x
//                    binding.previewFront.translationY = area.y
                }
        }
    }

    private fun calculateArea(image: ImageProxy): Area {
        return Area(
            x = AreaGenerator.next(),
            y = 500F,
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

    private val max = 600
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
