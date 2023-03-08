package com.example.clinometer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.GeomagneticField

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import com.example.improvedorientation.R
import kotlin.math.asin
import kotlin.math.atan

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    // https://www.cbcity.de/tutorial-rotationsmatrix-und-quaternion-einfach-erklaert-in-din70000-zyx-konvention

    //Gimbal Lock: https://de.wikipedia.org/wiki/Gimbal_Lock

    private val quaternion = FloatArray(4)
    private lateinit var tvazimuth: TextView
    private lateinit var tvpitch : TextView
    private lateinit var tvroll: TextView
    private lateinit var tvbar : TextView
    private lateinit var tvaltitude: TextView
    private lateinit var tvdirection: TextView
    private lateinit var locationManager: LocationManager
    private val locationRequestCode = 2




    //Magnetic declination
    private var latitude = 0f
    private var longitude = 0f
    private var altitude = 0f

    private var declination_x = 0f
    private var declination_y = 0f
    private var declination_z = 0f


    private var imageCompass: ImageView? = null
    private var imageRoll: ImageView? = null
    private var imagePitch: ImageView? = null


    private lateinit var sensorManager: SensorManager
    private var sensorRotate: Sensor? = null
    private var sensorGravity: Sensor? = null
    private var sensorBar: Sensor? = null

    private var currentAzimuth = 0f
    private var currentpitch = 0f
    private var currentroll = 0f


    private var azimuthRadians = 0.0
    private var pitchRadians = 0.0
    private var rollRadians = 0.0


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        tvazimuth = findViewById(R.id.azimuth)
        tvpitch = findViewById(R.id.pitch)
        tvroll = findViewById(R.id.roll)
        tvbar = findViewById(R.id.pressure)
        tvaltitude = findViewById(R.id.altitude)
        tvdirection = findViewById(R.id.direction)
        imageCompass = findViewById(R.id.compass)
        imagePitch = findViewById(R.id.pitch_image)
        imageRoll = findViewById(R.id.roll_image)
    }

    override fun onResume() {
        super.onResume()
        sensorManager = getSystemService(Service.SENSOR_SERVICE) as SensorManager

        if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null) {
            sensorRotate = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        }
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
            sensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        }
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null) {
            sensorBar = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        }
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) == null) {
            tvbar.text = getString(R.string.Not_detected)
            Log.d("MainActivity", "Here was I once with no pressure!")
        }
        // register Sensor
        registerListener()
        getActualLocation()


    }

    private fun getActualLocation() {
        if (checkPermissions()) {
            if (isLocationValid()) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermission()
                    return
                }

                locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 1f, this)

            }
            else {
                Toast.makeText(this, "Turn on", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)

            }
        }
        else {
            requestPermission()
        }
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            locationRequestCode)
    }

    private fun isLocationValid(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        return false
    }

    override fun onLocationChanged(location: Location) {

        if (location.accuracy <= 3f) {
            tvaltitude.text = "Altitude: ${"%.2f".format(location.altitude)} m"

            latitude = location.latitude.toFloat()
            longitude = location.longitude.toFloat()
            altitude = location.altitude.toFloat()

            var geofield = GeomagneticField(latitude, longitude, altitude,1677628805000)

            declination_x = geofield.x
            declination_y = geofield.y
            declination_z = geofield.z

            // Output: mT

        }
    }


    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
    }

    private fun registerListener() {
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null) {
            sensorManager.registerListener(this, sensorRotate, SensorManager.SENSOR_DELAY_FASTEST)
        }
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
           sensorManager.registerListener(this, sensorGravity, SensorManager.SENSOR_DELAY_FASTEST)
        }
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null) {
            sensorManager.registerListener(this, sensorBar, SensorManager.SENSOR_DELAY_FASTEST)
        }



    }



    override fun onSensorChanged(event: SensorEvent?) {

        if (event!!.sensor.type == Sensor.TYPE_PRESSURE) {
            tvbar.text = "Pressure: ${"%.2f".format(event.values[0])} hPa"
        }

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getQuaternionFromVector(quaternion, event.values)
            // a = quaternion[0], b = quaternion[1], c = quaternion[2], d = quaternion[3]
            // SensorManager.getRotationMatrixFromVector(rotation, event.values)
            // SensorManager.getOrientation(rotation, orientation)
            val or = resources.configuration.orientation

            // here the mobile phone is horizontal, like the normal infotainment system implemented in cars

            if (or == Configuration.ORIENTATION_LANDSCAPE) {
                // TODO Convert to Euler Angles is not the right solution!!!

                //     (x, y, z, w) = (q[0], q[1], q[2], q[3])
                //     (w, x, y, z) = (q[0], q[1], q[2], q[3])

                // 0 -> 1,  1 -> 2,  2 -> 3  3 -> 0
                //convert from android mode to standard mode
                // Yaw, Pitch, roll = Heading, Attitude, Bank
                /*
                How should you represent a rotation in three dimensions? You can try using Euler angles to represent it using three rotation angles, but there's something fishy about this.
                That naturally parametrizes a three-dimensional torus, but the rotation group is not a torus (rather, it's a projective space).
                It doesn't even have a torus as a covering space, but rather a 3-sphere.
                So the problem is that the naive coordinates just don't give the right topology, and therefore something must go wrong in degenerate cases to fix the topology.
                Gimbal lock is essentially a name for what goes wrong.
                When people say quaternions avoid gimbal lock, they mean the unit quaternions naturally form a 3-sphere, so there are no topology issues and they give a beautiful double cover of the rotation group (via a very simple map).
                Keeping track of a unit quaternion is fundamentally a more natural way to describe a rotation than keeping track of three Euler angles.
                On the other hand, if you describe your quaternion via Euler angles, then gimbal lock shows up again, not in the quaternions themselves but in your coordinate system for them.
                Some explanations of gimbal lock don't distinguish clearly between the underlying geometry/topology and the choice of coordinates,  since that's essential for understanding what's going on mathematically.
                */
                /*
                    // roll (x-axis rotation)
                    double sinr_cosp = 2 * (q.w * q.x + q.y * q.z);
                    double cosr_cosp = 1 - 2 * (q.x * q.x + q.y * q.y);
                    angles.roll = std::atan2(sinr_cosp, cosr_cosp);

                    // pitch (y-axis rotation)
                    double sinp = std::sqrt(1 + 2 * (q.w * q.y - q.x * q.z));
                    double cosp = std::sqrt(1 - 2 * (q.w * q.y - q.x * q.z));
                    angles.pitch = 2 * std::atan2(sinp, cosp) - M_PI / 2;

                    // yaw (z-axis rotation)
                    double siny_cosp = 2 * (q.w * q.z + q.x * q.y);
                    double cosy_cosp = 1 - 2 * (q.y * q.y + q.z * q.z);
                    angles.yaw = std::atan2(siny_cosp, cosy_cosp);
                */

                var t0 = 2.0f * (quaternion[0] * quaternion[1] + quaternion[2] * quaternion[3]).toDouble()
                var t1 = 1.0f - 2.0f * (quaternion[1] * quaternion[1] + quaternion[2] * quaternion[2]).toDouble()

                rollRadians = Math.atan2(t0,t1)

                var t2 = 2.0f * (quaternion[0] * quaternion[2] - quaternion[3] * quaternion[1]).toDouble()

                pitchRadians = asin(t2)

                var t3 = 2.0f * (quaternion[0] * quaternion[3] + quaternion[1] * quaternion[2]).toDouble()
                var t4 = 1.0f - 2.0f * (quaternion[2] * quaternion[2] + quaternion[3] * quaternion[3]).toDouble()

                azimuthRadians = Math.atan2(t3,t4)

                var azimuth = -Math.toDegrees(azimuthRadians).toFloat()
                val roll = -Math.toDegrees(rollRadians).toFloat()
                val pitch = Math.toDegrees(pitchRadians).toFloat()
                azimuth = (azimuth + 360) % 360
                if(roll > 90 || roll < -90) {
                    tvroll.text = ""
                    return
                }

                else {
                    tvpitch.text = "Pitch: ${"%.2f".format(pitch)}" + "°"
                    tvroll.text = "Roll:  ${"%.2f".format(roll)}" + "°"
                    tvazimuth.text = "Azimuth: ${"%.2f".format(azimuth)} °"

                    if (azimuth <= 11.25 || azimuth >= 348.75) {
                        tvdirection.text = "N"
                    }
                    else if (azimuth in 11.25..33.75) {
                        tvdirection.text = "NNE"
                    }
                    else if (azimuth in 33.75..56.25) {
                        tvdirection.text = "NE"
                    }
                    else if (azimuth in 56.25..78.75) {
                        tvdirection.text = "ENE"
                    }
                    else if (azimuth in 78.75..101.25) {
                        tvdirection.text = "E"
                    }
                    else if (azimuth in 101.25..123.75) {
                        tvdirection.text = "ESE"
                    }
                    else if (azimuth in 123.75..146.25) {
                        tvdirection.text = "SE"
                    }
                    else if (azimuth in 146.25..168.75) {
                        tvdirection.text = "SSE"
                    }
                    else if (azimuth in 168.75..191.25) {
                        tvdirection.text = "S"
                    }
                    else if (azimuth in 191.25..213.75) {
                        tvdirection.text = "SSW"
                    }
                    else if (azimuth in 213.75..236.25) {
                        tvdirection.text = "SW"
                    }
                    else if (azimuth in 236.25..258.75) {
                        tvdirection.text = "WSW"
                    }
                    else if (azimuth in 258.75..281.25) {
                        tvdirection.text = "W"
                     }
                    else if (azimuth in 281.25..303.75) {
                        tvdirection.text = "WNW"
                    }
                    else if (azimuth in 303.75..326.25) {
                        tvdirection.text = "NW"
                    }
                    else if (azimuth in 326.25..348.75) {
                        tvdirection.text = "NNW"
                    }

                    val animationCompass: Animation = RotateAnimation(-currentAzimuth,-azimuth,
                        Animation.RELATIVE_TO_SELF,0.5f, Animation.RELATIVE_TO_SELF,0.5f
                    )

                    //currentAzimuth saves n value of azimuth, then for the next iteration it will rotate from current n value to n + 1 value of azimuth
                    currentAzimuth = azimuth
                    animationCompass.duration = 500
                    animationCompass.repeatCount = 0
                    animationCompass.fillAfter = true
                    imageCompass!!.startAnimation(animationCompass)

                    val animationPitch: Animation = RotateAnimation(-currentpitch, -pitch, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)

                    currentpitch = pitch
                    animationPitch.duration = 500
                    animationPitch.repeatCount = 0
                    animationPitch.fillAfter = true
                    imagePitch!!.startAnimation(animationPitch)

                    val animationRoll: Animation = RotateAnimation(-currentroll, -roll, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)

                    currentroll = roll
                    animationRoll.duration = 500
                    animationRoll.repeatCount = 0
                    animationRoll.fillAfter = true
                    imageRoll!!.startAnimation(animationRoll)

                }
            }

            // here if the mobile phone is vertical

            else {

                val rollRadians = atan(2.0f * (quaternion[1] * quaternion[2] + quaternion[0] * quaternion[3]) / (quaternion[0] * quaternion[0] + quaternion[1] * quaternion[1]
                        - quaternion[2] * quaternion[2] - quaternion[3] * quaternion[3]))
                val pitchRadians = asin(2.0f * (quaternion[0] * quaternion[2] - quaternion[1] * quaternion[3]))
                val azimuthRadians = atan(2.0f * (quaternion[2] * quaternion[3] + quaternion[0] * quaternion[1]) / (- quaternion[0] * quaternion[0] + quaternion[1] * quaternion[1]
                        + quaternion[2] * quaternion[2] - quaternion[3] * quaternion[3]))

                var azimuth = Math.toDegrees(azimuthRadians.toDouble()).toFloat()
                val roll = Math.toDegrees(rollRadians.toDouble()).toFloat()
                val pitch = Math.toDegrees(pitchRadians.toDouble()).toFloat()
                azimuth = (azimuth + 360) % 360
                if(roll > 90 || roll < -90) {
                    tvroll.text = "Car is gonna crash"

                }
                else {
                    var mpitch = (-1) * pitch
                    tvpitch.text = "Pitch: ${"%.2f".format(mpitch)}" + "°"
                    tvroll.text = "Roll:  ${"%.2f".format(roll)}" + "°"
                    tvazimuth.text = "Azimuth: ${"%.2f".format(azimuth)} °"

                    if (azimuth <= 11.25 || azimuth >= 348.75) {
                        tvdirection.text = "N"
                    }
                    else if (azimuth in 11.25..33.75) {
                        tvdirection.text = "NNE"
                    }
                    else if (azimuth in 33.75..56.25) {
                        tvdirection.text = "NE"
                    }
                    else if (azimuth in 56.25..78.75) {
                        tvdirection.text = "ENE"
                    }
                    else if (azimuth in 78.75..101.25) {
                        tvdirection.text = "E"
                    }
                    else if (azimuth in 101.25..123.75) {
                        tvdirection.text = "ESE"
                    }
                    else if (azimuth in 123.75..146.25) {
                        tvdirection.text = "SE"
                    }
                    else if (azimuth in 146.25..168.75) {
                        tvdirection.text = "SSE"
                    }
                    else if (azimuth in 168.75..191.25) {
                        tvdirection.text = "S"
                    }
                    else if (azimuth in 191.25..213.75) {
                        tvdirection.text = "SSW"
                    }
                    else if (azimuth in 213.75..236.25) {
                        tvdirection.text = "SW"
                    }
                    else if (azimuth in 236.25..258.75) {
                        tvdirection.text = "WSW"
                    }
                    else if (azimuth in 258.75..281.25) {
                        tvdirection.text = "W"
                    }
                    else if (azimuth in 281.25..303.75) {
                        tvdirection.text = "WNW"
                    }
                    else if (azimuth in 303.75..326.25) {
                        tvdirection.text = "NW"
                    }
                    else if (azimuth in 326.25..348.75) {
                        tvdirection.text = "NNW"
                    }

                    val animationCompass: Animation = RotateAnimation(-currentAzimuth,-azimuth,
                        Animation.RELATIVE_TO_SELF,0.5f, Animation.RELATIVE_TO_SELF,0.5f
                    )
                    //currentAzimuth saves n value of azimuth, then for the next iteration it will rotate from current n value to n + 1 value of azimuth

                    currentAzimuth = azimuth
                    animationCompass.duration = 500
                    animationCompass.repeatCount = 0
                    animationCompass.fillAfter = true
                    imageCompass!!.startAnimation(animationCompass)

                    val animationPitch: Animation = RotateAnimation(-currentpitch, -pitch, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)

                    currentpitch = pitch
                    animationPitch.duration = 500
                    animationPitch.repeatCount = 0
                    animationPitch.fillAfter = true
                    imagePitch!!.startAnimation(animationPitch)

                    val animationRoll: Animation = RotateAnimation(-currentroll, -roll, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)

                    currentroll = roll
                    animationRoll.duration = 500
                    animationRoll.repeatCount = 0
                    animationRoll.fillAfter = true
                    imageRoll!!.startAnimation(animationRoll)


                }

            }

        }


    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }
}