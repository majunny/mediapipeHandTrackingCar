package com.example.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.car.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastProcessingTime = 0L
    private val processingInterval = 100
    private var lastGesture: String = "stop"
    private var lastGestureTime: Long = 0

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread
    private var cameraId: String? = null

    private var handLandmarker: HandLandmarker? = null
    private var imageReader: ImageReader? = null
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0

    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var writableCharacteristic: BluetoothGattCharacteristic? = null

    private lateinit var connectionStatusText: TextView
    private var isBleReady: Boolean = false
    private var isWritingBleCommand: Boolean = false

    companion object {
        const val EXTRA_DEVICE = "extra_device"
        // These UUIDs are examples and should be replaced with the actual UUIDs of the Pico W service
        val PICO_W_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E") // UART Service
        val PICO_W_CHARACTERISTIC_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // UART RX Characteristic (for writing to Pico W)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "카메라 권한이 승인되었습니다.", Toast.LENGTH_SHORT).show()
                startCamera()
            } else {
                Toast.makeText(this, "카메라 권한이 거부되었습니다. 앱을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (width == 0 || height == 0) {
                    Handler(Looper.getMainLooper()).postDelayed({ startCamera() }, 300)
                } else {
                    startCamera()
                }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeCamera()
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        connectionStatusText = findViewById(R.id.connection_status_text)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        binding.viewFinder.surfaceTextureListener = surfaceTextureListener

        startBackgroundThread()

        bluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_DEVICE)
        }

        if (bluetoothDevice == null) {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        connectToDevice()

        setupMediaPipeHandLandmarker()

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        binding.viewFinder.surfaceTextureListener = surfaceTextureListener

        startBackgroundThread()
    }


    override fun onResume() {
        super.onResume()
        if (binding.viewFinder.isAvailable) {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (handLandmarker != null) {
                    startCamera()
                } else {
                    Log.w("MainActivity", "onResume에서 handLandmarker가 아직 초기화되지 않음 → 카메라 시작 생략")
                }
            }
        } else {
            binding.viewFinder.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (handLandmarker != null) {
                            startCamera()
                        } else {
                            Log.w("MainActivity", "onResume에서 handLandmarker가 아직 초기화되지 않음 → 카메라 시작 생략")
                        }
                    }
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    closeCamera(); return true
                }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onPause() {
        closeCamera()
        bluetoothGatt?.disconnect()
        super.onPause()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        handLandmarker?.close()
        handLandmarker = null
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            Log.e("MainActivity", "Error stopping background thread", e)
        }
    }

    private fun startCamera() {
        try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id
                    break
                }
            }

            if (cameraId == null) {
                Toast.makeText(this, "사용 가능한 카메라를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
                return
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e("MainActivity", "Camera permission not granted when trying to start camera.")
                Toast.makeText(this, "카메라 권한이 없습니다.", Toast.LENGTH_LONG).show()
                return
            }

            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)

            frameWidth = 640
            frameHeight = 480

            imageReader = ImageReader.newInstance(
                frameWidth,
                frameHeight,
                ImageFormat.YUV_420_888,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        processImageWithMediaPipe(image)
                        image.close()
                    }
                }, backgroundHandler)
            }

            cameraManager.openCamera(cameraId!!, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    Log.w("MainActivity", "Camera disconnected.")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e("MainActivity", "CameraDevice.StateCallback onError: $error")
                    Toast.makeText(this@MainActivity, "카메라 오류 발생", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e("MainActivity", "Camera Access Exception", e)
            Toast.makeText(this, "카메라를 열 수 없습니다: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Security Exception: Camera permission denied.", e)
            Toast.makeText(this, "카메라 권한이 없어 접근할 수 없습니다.", Toast.LENGTH_LONG).show()
        }
        Log.e("Debug", "startCamera() called")
    }


    private fun closeCamera() {
        if (::cameraCaptureSession.isInitialized) {
            cameraCaptureSession.close()
        }
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    private fun createCameraPreviewSession() {
        if (cameraDevice == null || !binding.viewFinder.isAvailable) {
            Log.w("MainActivity", "CameraDevice or TextureView not ready for preview session.")
            return
        }
        try {
            val texture = binding.viewFinder.surfaceTexture!!
            texture.setDefaultBufferSize(frameWidth, frameHeight)
            val surface = Surface(texture)

            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)
            previewRequestBuilder.addTarget(imageReader!!.surface)

            cameraDevice!!.createCaptureSession(
                listOf(surface, imageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        try {
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            cameraCaptureSession.setRepeatingRequest(
                                previewRequestBuilder.build(),
                                null,
                                backgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            Log.e("MainActivity", "Camera Capture Session Failed", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(this@MainActivity, "카메라 설정 실패", Toast.LENGTH_SHORT).show()
                        Log.e("MainActivity", "CameraCaptureSession onConfigureFailed")
                    }
                }, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e("MainActivity", "createCameraPreviewSession Exception", e)
        }
        Log.d("Debug", "createCameraPreviewSession 시작")
    }

    private fun setupMediaPipeHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.6f)
                .setMinTrackingConfidence(0.6f)
                .setResultListener { resultBundle, inputImage ->

                    val landmarkList: List<List<NormalizedLandmark>> = resultBundle.landmarks()
                    val handednessList: List<List<Category>> = resultBundle.handedness()

                    var leftIndexOpen = false
                    var rightIndexOpen = false

                    for (i in landmarkList.indices) {
                        val landmarks: List<NormalizedLandmark> = landmarkList[i]
                        val handedness: String = handednessList[i][0].categoryName()
                        val isIndexFingerOnlyOpen = isIndexFingerOpen(landmarks)
                        if (handedness == "Left") {
                            leftIndexOpen = isIndexFingerOnlyOpen
                        } else if (handedness == "Right") {
                            rightIndexOpen = isIndexFingerOnlyOpen
                        }
                    }

                    val currentGesture = when {
                        leftIndexOpen && rightIndexOpen -> "go"
                        !leftIndexOpen && !rightIndexOpen -> "back"
                        leftIndexOpen && !rightIndexOpen -> "right"
                        !leftIndexOpen && rightIndexOpen -> "left"
                        else -> "0"
                    }

                    val now = System.currentTimeMillis()
                    if (landmarkList.isNotEmpty()) {
                        lastGesture = currentGesture
                        lastGestureTime = now
                    }

                    val displayGesture = if (now - lastGestureTime < 500) lastGesture else "stop"

                    runOnUiThread {
                        Log.d("HandGesture", displayGesture)
                        binding.gestureText.text = displayGesture
                        sendBleCommand(displayGesture)

                        binding.overlay.setResults(
                            landmarkList,
                            inputImage.width,
                            inputImage.height,
                            0
                        )
                        binding.overlay.invalidate()
                    }
                }
                .setErrorListener { error ->
                    runOnUiThread {
                        Toast.makeText(this, "MediaPipe 오류: ${error.message}", Toast.LENGTH_SHORT).show()
                        Log.e("MainActivity", "MediaPipe 오류: ${error.message}", error)
                    }
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(this, options)
            if (handLandmarker != null) {
                Log.d("MainActivity", "✅ HandLandmarker initialized successfully.")
                runOnUiThread {
                    startCamera()
                }
            } else {
                Log.e("MainActivity", "❌ HandLandmarker 초기화 실패: null 반환")
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "HandLandmarker setup failed: ${e.message}", e)
            Toast.makeText(this, "HandLandmarker 설정 실패", Toast.LENGTH_LONG).show()
        }
    }


    private fun processImageWithMediaPipe(image: Image) {
        if (handLandmarker == null) {
            Log.w("MainActivity", "❌ HandLandmarker is not initialized. 이미지 처리 생략")
            image.close()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessingTime < processingInterval) {
            image.close()
            return
        }
        lastProcessingTime = currentTime

        try {
            val originalRotation = getRotationDegrees(this)
            val correctedRotation = (originalRotation + 90) % 360

            val bitmap = ImageUtils.imageToBitmap(image, this)
            val rotatedBitmap = rotateBitmap(bitmap, correctedRotation, flipX = true)
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()

            handLandmarker!!.detectAsync(mpImage, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e("MainActivity", "📛 이미지 처리 중 오류: ${e.message}", e)
        } finally {
            image.close()
        }
    }


    fun rotateBitmap(bitmap: Bitmap, degrees: Int, flipX: Boolean): Bitmap {
        val matrix = Matrix()
        if (flipX) matrix.postScale(-1f, 1f)
        if (degrees != 0) matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun getRotationDegrees(context: Context): Int {
        val rotation =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        return when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }
    fun isIndexFingerOpen(landmarks: List<NormalizedLandmark>): Boolean {
        val indexTip = landmarks[8]
        val indexPIP = landmarks[6]

        return indexTip.y() < indexPIP.y()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        connectionStatusText.text = "Connecting to ${bluetoothDevice?.name ?: "Unknown Device"}..."
        Log.d("MainActivity", "Attempting to connect to device: ${bluetoothDevice?.address}")
        bluetoothGatt = bluetoothDevice?.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread { // UI 업데이트는 UI 스레드에서
                Log.i("MainActivity", "onConnectionStateChange: status=$status, newState=$newState")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("MainActivity", "Connected to GATT server. Discovering services...")
                    connectionStatusText.text = "Connected to ${bluetoothDevice?.name ?: "Unknown Device"}"
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("MainActivity", "Disconnected from GATT server. Status: $status")
                    connectionStatusText.text = "Disconnected"
                    Toast.makeText(this@MainActivity, "Disconnected from device (Status: $status)", Toast.LENGTH_SHORT).show()
                    isBleReady = false
                } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                    connectionStatusText.text = "Connecting..."
                } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                    connectionStatusText.text = "Disconnecting..."
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            runOnUiThread { // UI 업데이트는 UI 스레드에서
                Log.i("MainActivity", "onServicesDiscovered: status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(PICO_W_SERVICE_UUID)
                    if (service != null) {
                        writableCharacteristic = service.getCharacteristic(PICO_W_CHARACTERISTIC_UUID)
                        if (writableCharacteristic != null) {
                            Log.i("MainActivity", "Found writable characteristic.")
                            connectionStatusText.text = "Connected and ready"
                            Toast.makeText(this@MainActivity, "Connected and ready to send commands", Toast.LENGTH_SHORT).show()
                            isBleReady = true
                        } else {
                            Log.w("MainActivity", "Writable characteristic not found. Check UUID.")
                            connectionStatusText.text = "Connected, but characteristic not found"
                            Toast.makeText(this@MainActivity, "Characteristic not found. Check UUID.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.w("MainActivity", "Service not found. Check UUID.")
                        connectionStatusText.text = "Connected, but service not found"
                        Toast.makeText(this@MainActivity, "Service not found. Check UUID.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.w("MainActivity", "onServicesDiscovered received: $status")
                    connectionStatusText.text = "Service discovery failed: $status"
                    Toast.makeText(this@MainActivity, "Service discovery failed: $status", Toast.LENGTH_LONG).show()
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            runOnUiThread { // UI 업데이트는 UI 스레드에서
                isWritingBleCommand = false // Reset the flag after write attempt
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("MainActivity", "Successfully wrote to characteristic: ${characteristic.uuid}")
                } else {
                    Log.w("MainActivity", "Failed to write to characteristic: ${characteristic.uuid} with status $status")
                    Toast.makeText(this@MainActivity, "Failed to send command: $status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendBleCommand(command: String) {
        if (!isBleReady || isWritingBleCommand) {
            Log.w("MainActivity", "BLE not ready or command already in progress.")
            return
        }

        isWritingBleCommand = true

        if (writableCharacteristic != null && bluetoothGatt != null) {
            val commandBytes = (command + "\r\n").toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(
                    writableCharacteristic!!,
                    commandBytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                writableCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                writableCharacteristic?.value = commandBytes
                bluetoothGatt?.writeCharacteristic(writableCharacteristic)
            }
        } else {
            Log.w("MainActivity", "Cannot send command: Characteristic or GATT not ready.")
        }
    }
}
