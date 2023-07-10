package com.example.takehomeandroidproject

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SensorService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "my_channel_id"
        const val NOTIFICATION_ID = 1
        const val MAX_VALUES_SIZE = 100 // Limit for size of pitch, tilt, and azimuth values
        private val PITCH_COLOR = Triple(255, 0, 0)
        private val TILT_COLOR = Triple(255, 255, 0)
        private val AZIMUTH_COLOR = Triple(0, 0, 255)
    }

    // Binder instance for bounded service.
    private val binder = LocalBinder()

    // Sensor Manager and the Rotation Vector Sensor.
    private lateinit var sensorManager: SensorManager
    private var rotationVector: Sensor? = null

    // Variables for storing sensor data.
    private var mPitch = 0.0
    private var mTilt = 0.0
    private var mAzimuth = 0.0

    private val pitchValuesList = ArrayDeque<Double>(MAX_VALUES_SIZE)
    private val tiltValuesList = ArrayDeque<Double>(MAX_VALUES_SIZE)
    private val azimuthValuesList = ArrayDeque<Double>(MAX_VALUES_SIZE)

    private val _pitch = MutableLiveData<Double>(0.00)
    private val _tilt = MutableLiveData<Double>(0.00)
    private val _azimuth = MutableLiveData<Double>(0.00)
    private val _pitchColor = MutableLiveData<Triple<Int, Int, Int>>()
    private val _tiltColor = MutableLiveData<Triple<Int, Int, Int>>()
    private val _azimuthColor = MutableLiveData<Triple<Int, Int, Int>>()
    private val _pitchValues = MutableLiveData<List<Double>>()
    private val _tiltValues = MutableLiveData<List<Double>>()
    private val _azimuthValues = MutableLiveData<List<Double>>()

    // These are the public facing, read-only versions of the LiveData
    val pitch: LiveData<Double> get() = _pitch
    val tilt: LiveData<Double> get() = _tilt
    val azimuth: LiveData<Double> get() = _azimuth
    val pitchColor: LiveData<Triple<Int, Int, Int>> get() = _pitchColor
    val tiltColor: LiveData<Triple<Int, Int, Int>> get() = _tiltColor
    val azimuthColor: LiveData<Triple<Int, Int, Int>> get() = _azimuthColor
    val pitchValues: LiveData<List<Double>> get() = _pitchValues
    val tiltValues: LiveData<List<Double>> get() = _tiltValues
    val azimuthValues: LiveData<List<Double>> get() = _azimuthValues

    // Binder class to bind service with activity/component
    inner class LocalBinder : Binder() {
        val service: SensorService
            get() = this@SensorService
    }
    // Job for cancelling the coroutine when the service is destroyed
    private val job = Job()
    // CoroutineScope that is tied to the lifecycle of the service
    private val coroutineScope = CoroutineScope(Dispatchers.Default + job)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("My Awesome App")
            .setContentText("Now running in the foreground...")
            .setSmallIcon(androidx.core.R.drawable.notification_bg)
            .build()

        this.startForeground(NOTIFICATION_ID, notification)

        // Initialise sensor objects and register sensor listener
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }

        // Launch the coroutine worker that sends sensor data updates every 100 ms on Default Dispatcher
        coroutineScope.launch {
            while (isActive) {
                // Post sensor values
                _pitch.postValue(mPitch)
                _tilt.postValue(mTilt)
                _azimuth.postValue(mAzimuth)

                // Post buffer lists
                _pitchValues.postValue(ArrayList(pitchValuesList))
                _tiltValues.postValue(ArrayList(tiltValuesList))
                _azimuthValues.postValue(ArrayList(azimuthValuesList))

                // Post colors
                _pitchColor.postValue(tripleColor(mPitch, PITCH_COLOR))
                _tiltColor.postValue(tripleColor(mTilt, TILT_COLOR))
                _azimuthColor.postValue(tripleColor(mAzimuth, AZIMUTH_COLOR))
                // Updating every 100ms
                delay(100)
            }
        }
    }

    // Create the notification channel
    private fun createNotificationChannel() {
        val serviceName = getString(R.string.app_name)
        val channel = NotificationChannel(CHANNEL_ID, serviceName, NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    // Function to calculate color variable from angle
    private fun tripleColor(mValue: Double, color: Triple<Int, Int, Int>): Triple<Int, Int, Int> {
        // Normalize value from [-180, 180] to [0, 1]
        val normalizedValue = ((mValue + 180) / 360.0).coerceIn(0.0, 1.0)
        var r = 255
        var g = 255
        var b = 255
        if (color.first != 255) {
            r = (color.first.toDouble() * normalizedValue).toInt()
        }
        if (color.second != 255) {
            g = (color.second.toDouble() * normalizedValue).toInt()
        }
        if (color.third != 255) {
            b = (color.third.toDouble() * normalizedValue).toInt()
        }

        return Triple(r, g, b)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // convert sensor values to angles
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientationVals = FloatArray(3)
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, rotationMatrix)
        SensorManager.getOrientation(rotationMatrix, orientationVals)

        // convert radians to degrees
        mPitch = orientationVals[0]?.toDouble()?.let { Math.toDegrees(it) } ?: 0.0
        mTilt = orientationVals[1]?.toDouble()?.let { Math.toDegrees(it) } ?: 0.0
        mAzimuth = orientationVals[2]?.toDouble()?.let { Math.toDegrees(it) } ?: 0.0

        // Use helper function to manage adding new values to the lists and keeping their size <= 100
        manageSensorValues(pitchValuesList, mPitch)
        manageSensorValues(tiltValuesList, mTilt)
        manageSensorValues(azimuthValuesList, mAzimuth)
    }
    private fun manageSensorValues(list: ArrayDeque<Double>, value: Double) {
        list.addLast(value)
        if (list.size > 100) list.removeFirst()
    }

    // Triggered when the accuracy of the registered sensor has changed
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // In case you need to handle changes in accuracy, add your code here
    }

    // System calls this to receive the IBinder when a component binds to the service
    override fun onBind(intent: Intent): IBinder {
        // Return the binder instance for establishing a connection with the service
        return binder
    }

    // System calls this when the service is no longer used and is being destroyed
    override fun onDestroy() {
        super.onDestroy()
        // Unregister the sensor listener when the service is destroyed to prevent memory leaks
        sensorManager.unregisterListener(this)
    }
}
