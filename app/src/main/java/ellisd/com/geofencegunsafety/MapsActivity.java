 package ellisd.com.geofencegunsafety;

import android.*;
import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
//import android.location.LocationListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import android.view.View;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import android.os.Handler;

import java.util.Map;
import java.util.Random;

 public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener,LocationListener {

    private GoogleMap mMap;

     private final String DEVICE_ADDRESS = "98:D3:37:90:F3:75"; //MAC Address of Bluetooth Module
     private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

     private BluetoothDevice device;
     private BluetoothSocket socket;

     private OutputStream outputStream;
     private InputStream inputStream;
     byte buffer[];
     boolean stopThread;
     boolean connected = false;
     String command;

     Button bluetooth_connect_btn;
    //Play services Location
    private static final int MY_PERMISSION_REQUEST_CODE = 32495;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST =  40892;

    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;

    private static int UPDATE_INTERVAL = 3000;
    private static int FASTEST_INTERVAL = 3000;
    private static int DISPLACEMENT = 10;


    DatabaseReference ref;
    GeoFire geoFire;

    GeoFire test;

    Marker mCurrent;



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        switch (requestCode)
        {
            case MY_PERMISSION_REQUEST_CODE:
                if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    if (checkPlayServices())
                    {
                        buildGoogleApiClient();
                        createLocationRequest();
                        displayLocation();
                    }
                }
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        ref = FirebaseDatabase.getInstance().getReference("My Location");
        geoFire = new GeoFire(ref);

        bluetooth_connect_btn = (Button) findViewById(R.id.bluetooth_connect_btn);

        //geoFire.setLocation("firebase-hq", new GeoLocation(41.995117, -73.875685));

        setUpLocation();

        bluetooth_connect_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){

                if(BTinit())
                {
                    msg("Error");
                    BTconnect();
                    beginListenForData();
                    //msg("Error");

                    // The code below sends the number 3 to the Arduino asking it to send the current state of the door lock so the lock state icon can be updated accordingly

                    command = "1";

                    try
                    {
                        outputStream.write("0".toString().getBytes());
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }

                }
            }
        });

    }

    private void setUpLocation(){
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new  String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, MY_PERMISSION_REQUEST_CODE);
        }
        else
        {
            if(checkPlayServices()){

                buildGoogleApiClient();
                createLocationRequest();
                displayLocation();
            }
        }

    }

    private void displayLocation(){

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation !=null){
            final double latitude = mLastLocation.getLatitude();
            final double longitude = mLastLocation.getLongitude();

            geoFire.setLocation("You", new GeoLocation(latitude,longitude),
                                new GeoFire.CompletionListener() {
                                    @Override
                                    public void onComplete(String key, DatabaseError error) {
                                        if (mCurrent != null)
                                            mCurrent.remove();
                                        mCurrent = mMap.addMarker(new MarkerOptions()
                                                                    .position(new LatLng(latitude,longitude))
                                                                    .title("You"));
                                        //Move Camera to this position
                                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude,longitude), 12.0f));
                                    }
                                });


            //Add Marker

            if(mCurrent != null)
                mCurrent.remove();




            Log.d("Hellloooooo", String.format("Your location was changed: %f / %f ", latitude, longitude));
        }

        else Log.d("Helllooooo", "Can not get your location");

    }

    private void createLocationRequest(){
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    private void buildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    private boolean checkPlayServices(){
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if(resultCode != ConnectionResult.SUCCESS){
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode))
                GooglePlayServicesUtil.getErrorDialog(resultCode,this,PLAY_SERVICES_RESOLUTION_REQUEST).show();
            else{
                Toast.makeText(this,"This device is not supported", Toast.LENGTH_SHORT).show();
                finish();
            }
            return false;
        }
        return true;


    }

     //Arduino hackable?
     //Equivalent of a requirements document
     /**
      * How the app should work in the idealized case
      * Test success vs the requirements how often it succeeds vs fails
      * Reguirements document to start writing
      * Security matter in app think of how it should be implemented whether it gets implement or not is a different story
      * Fully fledged app in my area (Who gets to control the geofences)
      *
      */


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;



        //Create Dangerous area 41.997803, -73.875834
        LatLng dangerous_area = new LatLng(41.997803, -73.875834);
        mMap.addCircle(new CircleOptions()
                    .center(dangerous_area)
                    .radius(10)
                    .strokeColor(Color.RED)
                    .fillColor(0x220000FF)
                    .strokeWidth(5.0f)
        );

        LatLng RKC = new LatLng(42.020104, -73.907820);
        mMap.addCircle(new CircleOptions()
                .center(RKC)
                .radius(100)
                .strokeColor(Color.RED)
                .fillColor(0x220000FF)
                .strokeWidth(5.0f)
        );

        LatLng Bizzer = new LatLng(41.994758, -73.869014);
        mMap.addCircle(new CircleOptions()
                .center(Bizzer)
                .radius(100)
                .strokeColor(Color.RED)
                .fillColor(0x220000FF)
                .strokeWidth(5.0f)
        );

        LatLng Stewarts = new LatLng(41.996871, -73.874070);
        mMap.addCircle(new CircleOptions()
                .center(Stewarts)
                .radius(100)
                .strokeColor(Color.RED)
                .fillColor(0x220000FF)
                .strokeWidth(5.0f)
        );

        LatLng House = new LatLng(42.250781, -71.053740);
        mMap.addCircle(new CircleOptions()
                .center(House)
                .radius(100)
                .strokeColor(Color.RED)
                .fillColor(0x220000FF)
                .strokeWidth(5.0f)
        );



        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(RKC.latitude, RKC.longitude), 0.025f);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                sendNotification("Geofence Gun Safety", String.format("%s are now able to unlock your gun ", key));
            }

            @Override
            public void onKeyExited(String key) {
                sendNotification("Geofence Gun Safety", String.format("%s left the range, your gun must be put in safe", key));

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.d("MOVE", String.format("%s moved within the dangerous area [%f/%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                    Log.e("ERROR", ""+ error);

            }
        });

        GeoQuery geoQuery1 = geoFire.queryAtLocation(new GeoLocation(dangerous_area.latitude, dangerous_area.longitude), 0.01f);
        geoQuery1.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered (String key, GeoLocation location) {
                sendNotification("Geofence Gun Safety", String.format("%s 5 Moul Gun Range", key));


            }

            @Override
            public void onKeyExited(String key) {
                sendNotification("Geofence Gun Safety", String.format("%s left the range, your gun must be put in safe", key));
                try
                {
                    msg("YOOOO");
                    outputStream.write("1".toString().getBytes()); // Sends the number 1 to the Arduino. For a detailed look at how the resulting command is handled, please see the Arduino Source Code
                }
                catch (IOException e)
                {
                    msg("Error");
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.d("MOVE", String.format("%s moved within the dangerous area [%f/%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.e("ERROR", ""+ error);

            }
        });

        GeoQuery geoQuery4 = geoFire.queryAtLocation(new GeoLocation(House.latitude, House.longitude), 0.1f);
        geoQuery4.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                sendNotification("Geofence Gun Safety", String.format("%s 5 Moul Gun Range", key));
            }

            @Override
            public void onKeyExited(String key) {
                sendNotification("Geofence Gun Safety", String.format("%s left the range, your gun must be put in safe", key));
                try
                {
                    msg("YOOOO");
                    outputStream.write("1".toString().getBytes()); // Sends the number 1 to the Arduino. For a detailed look at how the resulting command is handled, please see the Arduino Source Code
                }
                catch (IOException e)
                {
                    msg("Error");
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.d("MOVE", String.format("%s moved within the dangerous area [%f/%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.e("ERROR", ""+ error);

            }
        });

        GeoQuery geoQuery2 = geoFire.queryAtLocation(new GeoLocation(Bizzer.latitude, Bizzer.longitude), 0.01f);
        geoQuery2.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                sendNotification("Geofence Gun Safety", String.format("%s are now able to unlock your gun ", key));
            }

            @Override
            public void onKeyExited(String key) {
                sendNotification("Geofence Gun Safety", String.format("%s left the range, your gun must be put in safe", key));

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.d("MOVE", String.format("%s moved within the dangerous area [%f/%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.e("ERROR", ""+ error);

            }
        });

        GeoQuery geoQuery3 = geoFire.queryAtLocation(new GeoLocation(Stewarts.latitude, Stewarts.longitude), 0.01f);
        geoQuery3.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                sendNotification("Geofence Gun Safety", String.format("%s are now able to unlock your gun ", key));
            }

            @Override
            public void onKeyExited(String key) {
                sendNotification("Geofence Gun Safety", String.format("%s left the range, your gun must be put in safe", key));

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.d("MOVE", String.format("%s moved within the dangerous area [%f/%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.e("ERROR", ""+ error);

            }
        });


    }

     private void sendNotification(String title, String content){
         Notification.Builder builder = new Notification.Builder(this)
                 .setSmallIcon(R.mipmap.ic_launcher)
                 .setContentTitle(title)
                 .setContentText(content);
         NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
         Intent intent = new Intent(this, lockUnlock.class);
         PendingIntent contentIntent = PendingIntent.getActivity(this, 0,intent, PendingIntent.FLAG_IMMUTABLE);
         builder.setContentIntent(contentIntent);
         Notification notification = builder.build();
         notification.flags |= Notification.FLAG_AUTO_CANCEL;
         notification.defaults |= Notification.DEFAULT_SOUND;

         manager.notify(new Random().nextInt(), notification);
     }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        displayLocation();
    }


    public void onStatusChanged(String s, int i, Bundle bundle) {

    }


    public void onProviderEnabled(String s) {

    }


    public void onProviderDisabled(String s) {

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        displayLocation();
        startLocationUpdates();



    }

    private void startLocationUpdates()
    {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

     private void msg(String s)
     {
         Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
     }

     //Initializes bluetooth module
     public boolean BTinit()
     {
         boolean found = false;

         BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

         if(bluetoothAdapter == null) //Checks if the device supports bluetooth
         {
             Toast.makeText(getApplicationContext(), "Device doesn't support bluetooth", Toast.LENGTH_SHORT).show();
         }

         if(!bluetoothAdapter.isEnabled()) //Checks if bluetooth is enabled. If not, the program will ask permission from the user to enable it
         {
             Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
             startActivityForResult(enableAdapter,0);

             try
             {
                 Thread.sleep(1000);
             }
             catch(InterruptedException e)
             {
                 e.printStackTrace();
             }
         }

         Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

         if(bondedDevices.isEmpty()) //Checks for paired bluetooth devices
         {
             Toast.makeText(getApplicationContext(), "Please pair the device first", Toast.LENGTH_SHORT).show();
         }
         else
         {
             for(BluetoothDevice iterator : bondedDevices)
             {
                 if(iterator.getAddress().equals(DEVICE_ADDRESS))
                 {
                     device = iterator;
                     found = true;
                     break;
                 }
             }
         }

         return found;
     }

     public boolean BTconnect()
     {

         try
         {
             socket = device.createRfcommSocketToServiceRecord(PORT_UUID); //Creates a socket to handle the outgoing connection
             socket.connect();

             Toast.makeText(getApplicationContext(),
                     "Connection to bluetooth device successful", Toast.LENGTH_LONG).show();
             connected = true;
         }
         catch(IOException e)
         {
             e.printStackTrace();
             connected = false;
         }

         if(connected)
         {
             try
             {
                 outputStream = socket.getOutputStream(); //gets the output stream of the socket
             }
             catch(IOException e)
             {
                 e.printStackTrace();
             }

             try
             {
                 inputStream = socket.getInputStream(); //gets the input stream of the socket
             }
             catch (IOException e)
             {
                 e.printStackTrace();
             }
         }

         return connected;
     }

     void beginListenForData() // begins listening for any incoming data from the Arduino
     {

         stopThread = false;
         buffer = new byte[1024];

         Thread thread = new Thread(new Runnable()
         {
             public void run()
             {
                 while(!Thread.currentThread().isInterrupted() && !stopThread)
                 {
                     try
                     {
                         int byteCount = inputStream.available();


                     }
                     catch (IOException ex)
                     {
                         stopThread = true;
                     }
                 }
             }
         });

         thread.start();
     }

     @Override
     protected void onStart()
     {
         super.onStart();
     }
 }

