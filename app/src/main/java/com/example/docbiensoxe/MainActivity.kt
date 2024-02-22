package com.example.docbiensoxe

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.util.Linkify
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var recognizer: TextRecognizer

    //private val SAVED_IMAGE_BITMAP = "SavedImage"
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    lateinit var camera: Camera
    var savedBitmap: Bitmap? = null

    private val TAG = "Testing"
    private val SAVED_TEXT_TAG = "SavedText"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
    }

    private fun init() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        shareLayout.visibility = View.GONE
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // lay ket qua
        extractTextButton.setOnClickListener {
            when {
                previewImage.visibility == View.VISIBLE -> {
                    savedBitmap = previewImage.drawable.toBitmap()
                    runTextRecognition(savedBitmap!!)
                }
                viewFinder.bitmap != null -> {
                    previewImage.visibility = View.VISIBLE
                    savedBitmap = viewFinder.bitmap
                    previewImage.setImageBitmap(viewFinder.bitmap!!)
                    runTextRecognition(savedBitmap!!)
                }
                else -> {
                    showToast(getString(R.string.camera_error_default_msg))
                }
            }
        }

        // copy tam ket qua
//        copyToClipboard.setOnClickListener {
//            val textToCopy = textInImage.text
//            if (isTextValid(textToCopy.toString())) {
//                copyToClipboard(textToCopy)
//            } else {
//                showToast(getString(R.string.no_text_found))
//            }
//        }

        shareLayout.setOnClickListener {
            val textToCopy = textInImage.text.toString()
            if (isTextValid(textToCopy)) {
                shareText(textToCopy)
            } else {
                showToast(getString(R.string.no_text_found))
            }
        }

//        close.setOnClickListener {
//            textInImageLayout.visibility = View.GONE
//            if(!allPermissionsGranted()){
//                requestPermissions()
//            } else {
//                previewImage.visibility = View.GONE
//                savedBitmap = null
//            }
//        }

        reloadCam.setOnClickListener {
            textInImageLayout.visibility = View.GONE
            shareLayout.visibility = View.GONE
            if (!allPermissionsGranted()) {
                requestPermissions()
            } else {
                previewImage.visibility = View.GONE
                savedBitmap = null
            }
        }

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                showToast(
                    getString(R.string.permission_denied_msg)
                )
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun isTextValid(text: String?): Boolean {
        if (text == null)
            return false

        return text.isNotEmpty() and !text.equals(getString(R.string.no_text_found))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                //  Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )


                camera.apply {
                    if (cameraInfo.hasFlashUnit()) {
                        torchButton.setOnClickListener {
                            cameraControl.enableTorch(cameraInfo.torchState.value == TorchState.OFF)
                        }
                    } else {
                        torchButton.setOnClickListener {
                            showToast(getString(R.string.torch_not_available_msg))
                        }
                    }

                    cameraInfo.torchState.observe(this@MainActivity) { torchState ->
                        if (torchState == TorchState.OFF) {
                            torchImage.setImageResource(R.drawable.ic_flashlight_on)
                        } else {
                            torchImage.setImageResource(R.drawable.ic_flashlight_off)
                        }
                    }
                }

            } catch (exc: Exception) {
                showToast(getString(R.string.error_default_msg))
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun runTextRecognition(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        recognizer
            .process(inputImage)
            .addOnSuccessListener { text ->
                textInImageLayout.visibility = View.VISIBLE
                shareLayout.visibility = View.VISIBLE
                processTextRecognitionResult(text)
            }.addOnFailureListener { e ->
                e.printStackTrace()
                showToast(e.localizedMessage ?: getString(R.string.error_default_msg))
            }
    }

    private fun processTextRecognitionResult(result: Text) {
        var finalText = ""
        for (block in result.textBlocks) {
            for (line in block.lines) {
                finalText += line.text + " \n"
            }
            finalText += "\n"
        }

        Log.d(TAG, finalText)
        Log.d(TAG, result.text)

        textInImage.text = if (finalText.isNotEmpty()) {
            finalText
        } else {
            getString(R.string.no_text_found)
        }

        Linkify.addLinks(textInImage, Linkify.ALL)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> {
                //Image Uri will not be null for RESULT_OK
                val uri: Uri = data?.data!!

                // Use Uri object instead of File to avoid storage permissions
                previewImage.apply {
                    visibility = View.VISIBLE
                    setImageURI(uri)
                }
                //runTextRecognition(binding.previewImage.drawable.toBitmap())
            }
//            ImagePicker.RESULT_ERROR -> {
//                showToast(ImagePicker.getError(data))
//            }
            else -> {
                showToast("No Image Selected")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun shareText(text: String) {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_text_title)))
    }

    private fun copyToClipboard(text: CharSequence) {
        val clipboard =
            ContextCompat.getSystemService(applicationContext, ClipboardManager::class.java)
        val clip = ClipData.newPlainText("label", text)
        clipboard?.setPrimaryClip(clip)
        showToast(getString(R.string.clipboard_text))
    }

}