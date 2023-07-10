# README.md

## MainActivity.kt:

- Implements sensor readings by connecting to and interacting with a background service through a ServiceConnection object.
- Binds to the background service as the user starts the activity and unbinds from it when activity stops.
- The activity's life cycle callbacks are used to start/stop the service and bind/unbind our SensorService.
- OnServiceConnected runs a setup of the Android UI using Jetpack Composables and includes a coroutine that delays the loading of the sensor reading screen.
- After the sensor readings are initialized and stabilized, shows three circles representing the orientation of the device along three axes (Pitch, Tilt, Azimuth) and a real-time chart of these readings.
- Each sensor reading is represented by a circle, with a diameter proportional to the normalized reading and color varying by the degree of the angle detected.
- Animation used with Jetpack Compose to smoothly animate sensor readings.

## SensorService.kt:

- Handles data processing in the background separate from the main thread of the application.
- Provides live sensor data for our MainActivity using Android Service.
- Maintains several states observed: pitch, tilt, azimuth, and their respective color values and sensor reading histories.
- Uses services for sensor readings to allow processes to run in the background, even if the user isn't interacting with the app.
- LiveData values updated by sensor updates, and then update Composables in main activity as part of their normal recomposition cycle.
- Limits the amount of data stored and displayed on the graph to 100 data points at any given time for optimal performance.
- Asynchronous design enhances overall app performance and prevents potential UI freezes or crashes.

The combination of both files allows for a seamless experience of viewing real-time device orientation status. With Jetpack Compose and services running the sensor readings, we have efficiently balanced the load between UI and background tasks, ensuring high performance and smooth operation, even under rapid sensor updates.
