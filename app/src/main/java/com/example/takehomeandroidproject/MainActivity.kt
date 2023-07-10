package com.example.takehomeandroidproject

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

    private lateinit var sensorService: SensorService
    private var bound = false

    // Creating a ServiceConnection object which allows interaction with the service
    private val connection = object : ServiceConnection {
        // Invoked when the service is connected
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SensorService.LocalBinder
            sensorService = binder.service
            bound = true

            // Setting up the UI
            setContent {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                    // Use a state variable to control when the actual app is displayed
                    var isDelayFinished by remember { mutableStateOf(false) }

                    // Create a coroutine that delays for 5 seconds before changing isDelayFinished
                    LaunchedEffect(key1 = true) {
                        delay(5000) // 5 seconds delay
                        isDelayFinished = true
                    }

                    // Display a loading screen until the delay finishes, then display the actual app
                    if (isDelayFinished) {
                        SensorsApp(sensorService)
                    } else {
                        Text(text = "Loading...")
                    }
                }
            }
        }

        // Invoked when the service gets disconnected
        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to the service when the activity starts
        Intent(this, SensorService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from the service when the activity stops
        unbindService(connection)
        bound = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start the foreground service when the activity gets created
        Intent(this, SensorService::class.java).also { intent ->
            startForegroundService(intent)
        }
        setTheme(R.style.AppTheme)
    }
}

@Composable
fun SensorsApp(sensorService: SensorService) {
    val pitch by sensorService.pitch.observeAsState(initial = 0.0)
    val tilt by sensorService.tilt.observeAsState(initial = 0.0)
    val azimuth by sensorService.azimuth.observeAsState(initial = 0.0)
    val pitchValues by sensorService.pitchValues.observeAsState(emptyList())
    val tiltValues by sensorService.tiltValues.observeAsState(emptyList())
    val azimuthValues by sensorService.azimuthValues.observeAsState(emptyList())
    val pitchColor by sensorService.pitchColor.observeAsState(initial = Triple(255, 0, 0))
    val tiltColor by sensorService.tiltColor.observeAsState(initial = Triple(255, 255, 0))
    val azimuthColor by sensorService.azimuthColor.observeAsState(initial = Triple(0, 0, 255))
    val deviceHeight = LocalConfiguration.current.screenHeightDp
    val deviceWidth = LocalConfiguration.current.screenWidthDp
    val circleDiameters  = listOf(pitch, tilt, azimuth).map { normalize(it, -180.0, 180.0) * 100  }
    val colors = listOf(pitchColor,tiltColor,azimuthColor)

    // Normalize the lists, assuming each is the same size
    val normalizedPitchValues = pitchValues.map { normalize(it, -180.0, 180.0) }
    val normalizedTiltValues = tiltValues.map { normalize(it, -180.0, 180.0) }
    val normalizedAzimuthValues = azimuthValues.map { normalize(it, -180.0, 180.0) }

    // Merge normalized lists into one single list for chart
    val normalizedAngles = normalizedPitchValues + normalizedTiltValues + normalizedAzimuthValues
    Surface(
        color = Color.DarkGray,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Text("Sensor Values Over Time", fontSize = 24.sp, color = Color.White)

            Row {
                VerticalBar()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                        .height((deviceHeight * 0.3f).dp)) {
                    PointChart(normalizedAngles)
                }
            }
            Text("Pitch : $pitch", fontSize = 12.sp, color = Color.Red)
            Text("Tilt : $tilt", fontSize = 12.sp, color = Color.Yellow)
            Text("Azimuth : $azimuth", fontSize = 12.sp, color = Color.Blue)
            Text("Pitch, Tilt, Azimuth Circles", fontSize = 24.sp, color = Color.White)
            Row(
                modifier = Modifier
                    .height((deviceHeight * 0.3f).dp)
                    .width(deviceWidth.dp)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                circleDiameters.forEachIndexed { index, diameter ->
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .padding(horizontal = 8.dp)

                    ) {
                        VariableCircle(size = diameter.toDouble(), color = colors[index])
                    }
                }
            }
        }
    }
}

@Composable
fun VariableCircle(size: Double, color: Triple<Int,Int,Int>) {
    val colorSize = Color(color.first, color.second, color.third)
    val floatSize = size.toFloat() * 2
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .requiredHeight(100.dp)) {
        drawCircle(color = colorSize, radius = floatSize, center = Offset(0f + 150,0f + 300))
    }
}

fun normalize(value: Double, min: Double, max: Double): Float {
    return ((value - min) / (max - min)).toFloat()
}

@Composable
fun PointChart(values: List<Float>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val colors = listOf(Color.Red, Color.Blue, Color.Yellow)
        val step = size.width / 100 // adjust it according to your needs

        // Iterate over the 'values' list containing sensor values
        values.forEachIndexed { index, value ->
            // Draw Circle
            drawCircle(
                color = colors[index / 100 % colors.size],
                radius = 4f,
                center = Offset(index % 100 * step, value * size.height)
            )
        }
    }
}

@Composable
fun VerticalBar() {
    Column(
        modifier = Modifier
            .width(50.dp)
            .padding(top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        for (i in 0..4) {
            Text(
                text = "${180 - i * 90}",
                textAlign = TextAlign.Right,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

