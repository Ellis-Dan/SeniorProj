package ellisd.com.geofencegunsafety;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import android.os.Handler;


public class lockUnlock extends AppCompatActivity {

    private final String DEVICE_ADDRESS = "98:D3:37:90:F3:75"; //MAC Address of Bluetooth Module
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private BluetoothDevice device;
    private BluetoothSocket socket;

    private OutputStream outputStream;
    private InputStream inputStream;

    Thread thread;
    byte buffer[];

    boolean stopThread;
    boolean connected = false;
    String command;

    Button lock_state_btn, bluetooth_connect_btn, unlock_state_btn;

    TextView lock_state_text;

    ImageView lock_state_img;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lockunlock);

        lock_state_btn = (Button) findViewById(R.id.lock_state_btn);

        unlock_state_btn = (Button) findViewById(R.id.unlock_state_btn);

        bluetooth_connect_btn = (Button) findViewById(R.id.bluetooth_connect_btn);

        lock_state_text = (TextView) findViewById(R.id.lock_state_text);

        lock_state_img = (ImageView) findViewById(R.id.lock_state_img);

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


        lock_state_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){

                if(connected == false)
                {
                    Toast.makeText(getApplicationContext(), "Please establish a connection with the bluetooth servo door lock first", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    command = "0";

                    try
                    {
                        outputStream.write("1".toString().getBytes());; // Sends the number 1 to the Arduino. For a detailed look at how the resulting command is handled, please see the Arduino Source Code
                    }
                    catch (IOException e)
                    {
                        msg("Error");
                    }
                }
            }
        });


        unlock_state_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){

                if(connected == false)
                {
                    Toast.makeText(getApplicationContext(), "Please establish a connection with the bluetooth servo door lock first", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    command = "3";

                    try
                    {
                        outputStream.write("1".toString().getBytes()); // Sends the number 1 to the Arduino. For a detailed look at how the resulting command is handled, please see the Arduino Source Code
                    }
                    catch (IOException e)
                    {
                        msg("Error");
                    }
                }
            }
        });
    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    void beginListenForData() // begins listening for any incoming data from the Arduino
    {
        final Handler handler = new Handler();
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

                        if(byteCount > 0)
                        {
                            byte[] rawBytes = new byte[byteCount];
                            int ignore = inputStream.read(rawBytes);
                            final String string = new String(rawBytes, "UTF-8");

                            handler.post(new Runnable()
                            {
                                public void run()
                                {
                                    if(string.equals("3"))
                                    {
                                        lock_state_text.setText("Lock State: LOCKED"); // Changes the lock state text
                                        lock_state_img.setImageResource(R.drawable.locked_icon); //Changes the lock state icon
                                    }
                                    else if(string.equals("4"))
                                    {
                                        lock_state_text.setText("Lock State: UNLOCKED");
                                        lock_state_img.setImageResource(R.drawable.unlocked_icon);
                                    }
                                }
                            });
                        }
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

    @Override
    protected void onStart()
    {
        super.onStart();
    }
}