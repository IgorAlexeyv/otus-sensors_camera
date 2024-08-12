package com.example.myapplication

import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.BoringLayout
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.camera.core.*
import androidx.camera.video.*
import androidx.camera.video.VideoCapture

private const val REQUEST_PERMISSION = 1

class MainActivity : AppCompatActivity(), SensorEventListener {

    lateinit var sensorManager: SensorManager
    lateinit var photo: ActivityResultLauncher<*>

    private lateinit var imageCapture: ImageCapture

    private lateinit var videoCapture: VideoCapture<Recorder>
    private var currentRecording: Recording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        photo = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
//            findViewById<ImageView>(R.id.image).setImageBitmap(bitmap)
//        }

        findViewById<ImageView>(R.id.image).visibility = View.GONE
        doWithCameraStream()
        findViewById<Button>(R.id.action_mode_close_button).setOnClickListener {
//            takePhoto()
            if (currentRecording == null)
                startRecording()
            else stopRecording()
        }
//        showCamera()
//        doWithCameraStream()
//        showLightSensor()
    }

    private fun doWithCameraStream() {
        val cameraProvider = ProcessCameraProvider.getInstance(this)

        cameraProvider.addListener({
            val camera = cameraProvider.get()
            val preview = Preview.Builder()
                .build()

            imageCapture = ImageCapture.Builder()
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            preview.setSurfaceProvider(findViewById<PreviewView>(R.id.image_preview).surfaceProvider)

//            camera.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            camera.bindToLifecycle(this, cameraSelector, preview, videoCapture)


        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        if (!isPermissionGranted()) {
            requestPermission()
        }
    }

    private fun showCamera() {
        findViewById<Button>(R.id.action_mode_close_button).setOnClickListener {
            doHavePermission()
        }
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf("android.permission.CAMERA"), REQUEST_PERMISSION)
    }

    private fun doHavePermission() {
        photo.launch(null)
    }

    private fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, "android.permission.CAMERA") == PERMISSION_GRANTED
    }

    private fun showLightSensor() {
        sensorManager = getSystemService(SensorManager::class.java)

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun showDeviceSensors() {
        val sensorManager = getSystemService(SensorManager::class.java)
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)

        Log.d("SENSORS", "List:  $sensors ")
    }

    override fun onSensorChanged(event: SensorEvent) {
        Log.d("SENSORS", "onSensorChanged: ${event.values[0]}")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("SENSORS", "onAccuracyChanged:  $accuracy")
    }

    private fun takePhoto() {
        val outputDirectory = getOutputDirectory()
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Обработка сохраненного снимка
                    // Здесь вы можете выполнить любые действия после сохранения изображения
                    // Например, можно отобразить сообщение пользователю или открыть снимок в другом экране
                    val savedUri = outputFileResults.savedUri
                    // Пример: запуск новой активности для отображения сохраненного изображения
                    // val intent = Intent(this@MainActivity, DisplayImageActivity::class.java).apply {
                    //     putExtra("image_uri", savedUri.toString())
                    // }
                    // startActivity(intent)
                    findViewById<ImageView>(R.id.image).apply {
                        visibility = View.VISIBLE
                        setImageURI(savedUri)
                    }

                    findViewById<View>(R.id.image_preview).visibility = View.GONE
                }

                override fun onError(exception: ImageCaptureException) {
                    // Обработка ошибок
                }
            }
        )
    }

    private fun startRecording() {
        val outputDirectory = getOutputDirectory()
        val videoFile = File(
            outputDirectory,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
                .format(System.currentTimeMillis()) + ".mp4"
        )

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        currentRecording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        // Начало записи видео
                        Log.d("CameraX", "Recording started")
                    }
                    is VideoRecordEvent.Finalize -> {
                        // Завершение записи видео
                        if (!recordEvent.hasError()) {
                            Log.d("CameraX", "Video saved successfully: ${recordEvent.outputResults.outputUri}")
                        } else {
                            Log.e("CameraX", "Video recording error: ${recordEvent.error}")
                        }
                        currentRecording?.close()
                        currentRecording = null
                    }
                }
            }
    }


    private fun stopRecording() {
        currentRecording?.stop()
        currentRecording = null
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

}