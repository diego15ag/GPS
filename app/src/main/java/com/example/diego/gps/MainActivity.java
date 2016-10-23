package com.example.diego.gps;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.Button;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Set;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.UUID;

public class MainActivity extends ActionBarActivity {

    private BluetoothAdapter BTAdapter;
    private ArrayList<BluetoothDevice> pairedDevices;
    ListView lv;


    BluetoothSocket mmSocket = null;
    BluetoothDevice mmDevice = null;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;
    TextView TBLat, TBDirLat, TBLong, TBDirLong, TBAlt;

    ArrayList<BluetoothAdapter> mArrayAdapter;
    private ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();

    public static int REQUEST_BLUETOOTH = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BTAdapter = BluetoothAdapter.getDefaultAdapter();
        // Phone does not support Bluetooth so let the user know and exit.
        if (BTAdapter == null) {
            System.exit(0);
        }

        if (!BTAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, REQUEST_BLUETOOTH);
        }

        lv = (ListView)findViewById(R.id.listview);
        TBLat = (TextView)findViewById(R.id.TBLat);
        TBDirLat = (TextView)findViewById(R.id.TBDirLat);
        TBLong  = (TextView)findViewById(R.id.TBLong);
        TBDirLong = (TextView)findViewById(R.id.TBDirLong);
        TBAlt = (TextView)findViewById(R.id.TBAlt);

        Button openButton = (Button)findViewById(R.id.BOpen);
        Button closeButton = (Button)findViewById(R.id.BClose);

        openButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                    openBT();
            }
        });
        closeButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                    closeBT();
            }
        });

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener(){
                @Override
                public void onItemClick(AdapterView<?> parent , View v, int position, long id) {
                    mmDevice  = pairedDevices.get(position);
                }
        });
        list();



        return;
    }

    public void list(){
        Set<BluetoothDevice> lista = BTAdapter.getBondedDevices();
        pairedDevices = new ArrayList<BluetoothDevice>();
        ArrayList list = new ArrayList();

        for(BluetoothDevice bt : lista) {
            list.add(bt.getName());
            pairedDevices.add(bt);
        }

        final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, list);
        lv.setAdapter(adapter);
    }

    /*public void list(){
        Set<BluetoothDevice> pairedDevices = BTAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                devices.add(device);
            }
        }

        /*if(BluetoothDevice.ACTION_FOUND.equals(getIntent().getAction())){
            BluetoothDevice device = getIntent().getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            devices.add(device);
        }*/

     /*   discoverDevices();

        final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, devices);
        lv.setAdapter(adapter);

    }*/

    public void discoverDevices(){
        // Create a BroadcastReceiver for ACTION_FOUND
         final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // Add the name and address to an array adapter to show in a ListView
                    //mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    devices.add(device);
                }
            }
        };
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is presents
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public void openBT()
    {
        try {
            if(pairedDevices.size()==0) {
                Toast.makeText(getApplicationContext(), "El vector de bluetooths esta vacio", Toast.LENGTH_LONG).show();
                return;
            }

            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();

            beginListenForData();

            Toast.makeText(getApplicationContext(),"Conexión establecida",Toast.LENGTH_LONG).show();
        }catch (IOException e){
            Toast.makeText(getApplicationContext(),"Algo falla",Toast.LENGTH_LONG).show();
        }
    }

    public void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = '\n'; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            String temp = data;
                                            if(temp.contains("Latitud: ") && temp.contains("Grados")){
                                                TBLat.setText(data);
                                            }
                                            else if(temp.contains("Latitud ") && temp.contains("Dir")){
                                                if(temp.contains("N"))
                                                    TBDirLat.setText("Latitud Dir.: Norte");
                                                else
                                                    TBDirLat.setText("Latitud Dir.: Sur");
                                                //TBDirLat.setText(data);
                                            }
                                            else if(temp.contains("Longitud: ") && temp.contains("Grados")){
                                                TBLong.setText(data);
                                            }
                                            else if(temp.contains("Longitud ") && temp.contains("Dir")){
                                                if(temp.contains("W"))
                                                    TBDirLong.setText("Longitud Dir.: Oeste");
                                                else
                                                    TBDirLong.setText("Longitud Dir.: Este");
                                                //TBDirLong.setText(data);
                                            }
                                            else if(temp.contains("Altitud: ")){
                                                TBAlt.setText(data);
                                            }
                                            else if(temp.contains("No")){
                                                Toast.makeText(getApplicationContext(),"Cargando datos de satélite, espere por favor",Toast.LENGTH_LONG).show();
                                            }
                                           // TBLong.setText(data);
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    public void closeBT()
    {
        try{
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
            Toast.makeText(getApplicationContext(),"Conexión cerrada",Toast.LENGTH_LONG).show();
            //myLabel.setText("Bluetooth Closed");
        }catch (IOException e){
            Toast.makeText(getApplicationContext(),"Algo falla",Toast.LENGTH_LONG).show();
        }
    }

}
