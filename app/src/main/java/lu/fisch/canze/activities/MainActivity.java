/*
    CanZE
    Take a closer look at your ZE car

    Copyright (C) 2015 - The CanZE Team
    http://canze.fisch.lu

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or any
    later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package lu.fisch.canze.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;

import lu.fisch.canze.R;
import lu.fisch.canze.actors.Field;
import lu.fisch.canze.actors.Fields;
import lu.fisch.canze.actors.Frames;
import lu.fisch.canze.bluetooth.BluetoothManager;
import lu.fisch.canze.classes.DataLogger;
import lu.fisch.canze.classes.DebugLogger;
import lu.fisch.canze.database.CanzeDataSource;
import lu.fisch.canze.devices.BobDue;
import lu.fisch.canze.devices.Device;
import lu.fisch.canze.devices.ELM327;
import lu.fisch.canze.fragments.ExperimentalFragment;
import lu.fisch.canze.fragments.MainFragment;
import lu.fisch.canze.fragments.TechnicalFragment;
import lu.fisch.canze.interfaces.BluetoothEvent;
import lu.fisch.canze.interfaces.FieldListener;
import lu.fisch.canze.ui.AppSectionsPagerAdapter;
import lu.fisch.canze.widgets.WidgetView;

public class MainActivity extends AppCompatActivity implements FieldListener /*, android.support.v7.app.ActionBar.TabListener */{
    public static final String TAG = "  CanZE";

    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public final static String PREFERENCES_FILE = "lu.fisch.canze.settings";
    public final static String DATA_FILE = "lu.fisch.canze.data";

    // MAC-address of Bluetooth module (you must edit this line)
    private static String bluetoothDeviceAddress = null;
    private static String bluetoothDeviceName = null;
    private static String dataFormat = "bob";
    private static String deviceName = "Arduino";

    public final static int RECEIVE_MESSAGE   = 1;
    public final static int REQUEST_ENABLE_BT = 3;
    public final static int SETTINGS_ACTIVITY = 7;
    public final static int LEAVE_BLUETOOTH_ON= 11;

    public static final int CAR_ANY             = 0;
    public static final int CAR_FLUENCE         = 1;
    public static final int CAR_ZOE             = 2;
    public static final int CAR_KANGOO          = 3;
    public static final int CAR_TWIZY           = 4;    // you'll never know ;-)
    public static final int CAR_X10             = 5;

    private StringBuilder sb = new StringBuilder();
    private String buffer = "";

    private int count;
    private long start;

    private boolean visible = true;
    public boolean leaveBluetoothOn = false;
    private boolean returnFromWidget = false;

    public static Fields fields = Fields.getInstance();

    public static Device device = null;

    private static MainActivity instance = null;

    public static boolean safeDrivingMode = true;
    public static boolean bluetoothBackgroundMode = false;
    public static boolean debugLogMode = false;
    public static boolean dataExportMode = false;
    public static DataLogger  dataLogger = null; // rather use singleton in onCreate
    public static int car = CAR_ANY;

    private static boolean isDriving = false;

    public static boolean milesMode = false;
    public static int toastLevel = 1;

    private Fragment actualFragment;


    // bluetooth stuff
    private MenuItem bluetoothMenutItem = null;
    public final static int BLUETOOTH_DISCONNECTED = 21;
    public final static int BLUETOOTH_SEARCH       = 22;
    public final static int BLUETOOTH_CONNECTED    = 23;



    //The BroadcastReceiver that listens for bluetooth broadcasts
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                //Device has disconnected

                // only resume if this activity is also visible
                if(visible)
                {
                    // stop reading
                    if (device!=null)
                    {
                        device.stopAndJoin();
                    }

                    // inform user
                    setTitle(TAG + " - disconnected");
                    setBluetoothState(BLUETOOTH_DISCONNECTED);
                    Toast.makeText(MainActivity.this.getBaseContext(),"Bluetooth connection lost!",Toast.LENGTH_LONG).show();

                    // try to reconnect
                    onResume();
                }
            }
        }
    };

    public static MainActivity getInstance()
    {
        return instance;
    }

    public static void debug(String text)
    {
        Log.d(TAG, text);
        if(debugLogMode) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            DebugLogger.getInstance().log(sdf.format(Calendar.getInstance().getTime()) + ": " + text);
        }
    }

    public static void toast(final String message)
    {
        instance.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(instance, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void loadSettings()
    {
        debug("MainActivity: loadSettings");

        SharedPreferences settings = getSharedPreferences(PREFERENCES_FILE, 0);
        bluetoothDeviceAddress =settings.getString("deviceAddress", null);
        bluetoothDeviceName =settings.getString("deviceName", null);
        dataFormat = settings.getString("dataFormat", "crdt");
        deviceName = settings.getString("device", "Arduino");
        safeDrivingMode = settings.getBoolean("optSafe", true);
        bluetoothBackgroundMode = settings.getBoolean("optBTBackground", false);
        milesMode = settings.getBoolean("optMiles", false);
        dataExportMode = settings.getBoolean("optDataExport", false);
        debugLogMode = settings.getBoolean("optDebugLog", false);
        toastLevel = settings.getInt("optToast", 1);

        String carStr = settings.getString("car", "Any");
        switch (carStr) {
            case "Any":
                // Fields.getInstance().setCar(Fields.CAR_ANY);
                car = CAR_ANY;
                break;
            case "Zoé":
                // Fields.getInstance().setCar(Fields.CAR_ZOE);
                car = CAR_ZOE;
                break;
            case "Fluence":
                // Fields.getInstance().setCar(Fields.CAR_FLUENCE);
                car = CAR_FLUENCE;
                break;
            case "Kangoo":
                // Fields.getInstance().setCar(Fields.CAR_KANGOO);
                car = CAR_KANGOO;
                break;
            case "X10":
                // Fields.getInstance().setCar(Fields.CAR_X10);
                car = CAR_X10;
                break;
        }

        // as the settings may have changed, we need to reload different things

        // create a new device
        switch (deviceName) {
            case "Bob Due":
                device = new BobDue();
                break;
            case "ELM327":
                device = new ELM327();
                break;
            default:
                device = null;
                break;
        }

        // since the car type may have changed, reload the grame timings
        Frames.getInstance().reloadTiming();

        if(device!=null) {
            // initialise the connection
            device.initConnection();

            // register application wide fields
            registerApplicationFields();
        }
    }

    private void registerApplicationFields() {
        if (safeDrivingMode) {
            // speed
            Field field = fields.getBySID("5d7.0");
            field.addListener(MainActivity.getInstance());
            if(device!=null)
                device.addApplicationField(field,1000); // query every second
        } else {
            Field field = fields.getBySID("5d7.0");
            field.removeListener(MainActivity.getInstance());
            if(device!=null)
                device.removeApplicationField(field);
        }
    }

    private ArrayList<WidgetView> getWidgetViewArrayList(ViewGroup viewGroup)
    {
        ArrayList<WidgetView> result = new ArrayList<WidgetView>();

        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View v = viewGroup.getChildAt(i);
            if (v instanceof ViewGroup) {
                result.addAll(getWidgetViewArrayList((ViewGroup) v));
            }
            else if (v instanceof WidgetView)
            {
                result.add((WidgetView)v);
            }
        }

        return result;
    }

    protected void updateActionBar()
    {
        switch (viewPager.getCurrentItem())
        {
            case 0:
                actionBar.setIcon(R.mipmap.ic_launcher);
                break;
            case 1:
                actionBar.setIcon(R.mipmap.fragement_technical);
                break;
            case 2:
                actionBar.setIcon(R.mipmap.fragement_experimental);
                break;
            default:
                break;
        }
    }

    protected void loadFragement(Fragment newFragment)
    {
        if(actualFragment==null || !actualFragment.getClass().equals(newFragment.getClass())) {

            ActionBar actionBar = getSupportActionBar();
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
            if(newFragment instanceof  MainFragment)
                actionBar.setIcon(R.mipmap.ic_launcher);
            else if(newFragment instanceof  ExperimentalFragment)
                actionBar.setIcon(R.mipmap.fragement_experimental);
            else if(newFragment instanceof  TechnicalFragment)
                actionBar.setIcon(R.mipmap.fragement_technical);

            actualFragment=newFragment;
            // Create fragment and give it an argument specifying the article it should show
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            // Replace whatever is in the fragment_container view with this fragment,
            // and add the transaction to the back stack so the user can navigate back
            transaction.replace(R.id.main, newFragment);
            //transaction.addToBackStack(null);
            // Commit the transaction
            transaction.commit();
        }
    }

    private ViewPager viewPager;
    private AppSectionsPagerAdapter appSectionsPagerAdapter;
    private ActionBar actionBar ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DebugLogger.getInstance().createNewLog();

        // always create an instance
        // dataLogger = DataLogger.getInstance();
        dataLogger = new DataLogger();

        debug("MainActivity: onCreate");

        instance = this;

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // navigation bar
        appSectionsPagerAdapter = new AppSectionsPagerAdapter(getSupportFragmentManager());
        actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        //actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        viewPager = (ViewPager) findViewById(R.id.main);
        viewPager.setAdapter(appSectionsPagerAdapter);
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                //actionBar.setSelectedNavigationItem(position);
                updateActionBar();
            }
        });
        updateActionBar();

        /*
        for (int i = 0; i < appSectionsPagerAdapter.getCount(); i++) {
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(appSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(MainActivity.this));
        }
        */


        // load the initial "main" fragment
        //loadFragement(new MainFragment());

        setTitle(TAG + " - not connected");
        setBluetoothState(BLUETOOTH_DISCONNECTED);


        // tabs
        //final ActionBar actionBar = getSupportActionBar();
        // Specify that tabs should be displayed in the action bar.

        // open the database
        CanzeDataSource.getInstance(getBaseContext()).open();
        // cleanup
        CanzeDataSource.getInstance().cleanUp();

        // setup cleaning (once every hour)
        Runnable cleanUpRunnable = new Runnable() {
            @Override
            public void run() {
                CanzeDataSource.getInstance().cleanUp();
            }
        };
        Handler handler = new Handler();
        handler.postDelayed(cleanUpRunnable, 60*1000);


        // register for bluetooth changes
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(broadcastReceiver, intentFilter);

        // configure Bluetooth manager
        BluetoothManager.getInstance().setBluetoothEvent(new BluetoothEvent() {
            @Override
            public void onBeforeConnect() {
                setBluetoothState(BLUETOOTH_SEARCH);
            }

            @Override
            public void onAfterConnect(BluetoothSocket bluetoothSocket) {
                device.init(visible);
                device.registerFilters();

                // set title
                debug("MainActivity: onAfterConnect > set title");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setTitle(TAG + " - connected to <" + bluetoothDeviceName + "@" + bluetoothDeviceAddress + ">");
                        setBluetoothState(BLUETOOTH_CONNECTED);
                    }
                });
            }

            @Override
            public void onBeforeDisconnect(BluetoothSocket bluetoothSocket) {
            }

            @Override
            public void onAfterDisconnect() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setTitle(TAG + " - disconnected");
                    }
                });
            }
        });
        // detect hardware status
        int BT_STATE = BluetoothManager.getInstance().getHardwareState();
        if(BT_STATE==BluetoothManager.STATE_BLUETOOTH_NOT_AVAILABLE)
            Toast.makeText(this.getBaseContext(),"Sorry, but your device doesn't seem to have Bluetooth support!",Toast.LENGTH_LONG).show();
        else if (BT_STATE==BluetoothManager.STATE_BLUETOOTH_NOT_ACTIVE)
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }


        // load settings
        // - includes the reader
        // - includes the decoder
        //loadSettings(); --> done in onResume

        // load fields from static code
        debug("Loaded fields: " + fields.size());


        // load fields
        //final SharedPreferences settings = getSharedPreferences(PREFERENCES_FILE, 0);
        (new Thread(new Runnable() {
            @Override
            public void run() {
                debug("Loading fields last field values from database");
                for(int i=0; i<fields.size(); i++)
                {
                    Field field = fields.get(i);
                    field.setCalculatedValue(CanzeDataSource.getInstance().getLast(field.getSID()));
                    //debug("MainActivity: Setting "+field.getSID()+" = "+field.getValue());
                    //f.setValue(settings.getFloat(f.getUniqueID(), 0));
                }
                debug("Loading fields last field values from database (done)");
            }
        })).start();
    }


    @Override
    public void onResume() {
        debug("MainActivity: onResume");

        visible=true;
        super.onResume();

        // if returning from a single widget activity, we have to leave here!
        if(returnFromWidget) {
            returnFromWidget=false;
            return;
        }

        if(!leaveBluetoothOn) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setBluetoothState(BLUETOOTH_DISCONNECTED);
                }
            });
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    reloadBluetooth();
                }
            })).start();
        }

        final SharedPreferences settings = getSharedPreferences(PREFERENCES_FILE, 0);
        if(!settings.getBoolean("disclaimer",false)) {

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

            // set title
            alertDialogBuilder.setTitle("Formal Disclaimer");

            // set dialog message
            String yes = "Yes, I got it!";
            String no  = "No, I didn't understand a word ...";

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            float width = size.x;
            int height = size.y;
            width = width / getResources().getDisplayMetrics().scaledDensity;
            if(width<=480)
            {
                yes="Yes";
                no ="No";
            }

            alertDialogBuilder
                    .setMessage(Html.fromHtml("<html>CanZE (“the software”) is provided as is. Use the software at your own risk. " +
                            "The authors make no warranties as to performance or fitness for a particular purpose, " +
                            "or any other warranties whether expressed or implied. No oral or written communication " +
                            "from or information provided by the authors shall create a warranty. Under no circumstances " +
                            "shall the authors be liable for direct, indirect, special, incidental, or consequential " +
                            "damages resulting from the use, misuse, or inability to use the software, even if the author " +
                            "has been advised of the possibility of such damages. These exclusions and limitations may not " +
                            "apply in all jurisdictions. You may have additional rights and some of these limitations may not " +
                            "apply to you. This software is only intended for scientific usage." +
                            "<br>" +
                            "<br>" +
                            "<b>By using this software you are interfering with your car and doing that with hardware and " +
                            "software beyond your control, created by a loose team of interested amateurs in this field. Any " +
                            "car is a possibly lethal piece of machinery and you might hurt or kill yourself or others using " +
                            "it, or even paying attention to the displays instead of watching the road.</b></html>"))
                    .setCancelable(true)
                    .setPositiveButton(yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // if this button is clicked, close
                            SharedPreferences.Editor editor = settings.edit();
                            editor.putBoolean("disclaimer", true);
                            editor.commit();
                            // current activity
                            dialog.cancel();
                        }
                    })
                    .setNegativeButton(no,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // if this button is clicked, just close
                                    // the dialog box and do nothing
                                    dialog.cancel();
                                    //MainActivity.this.finishAffinity(); requires API16
                                    MainActivity.this.finish();
                                    android.os.Process.killProcess(android.os.Process.myPid());
                                    System.exit(0);
                                }
                            });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();
        }

        // after loading PREFERENCES we may have new values for "dataExportMode"
        dataExportMode = dataLogger.activate ( dataExportMode );
    }

    public void reloadBluetooth() {
        reloadBluetooth(true);
    }

    public void reloadBluetooth(boolean reloadSettings)
    {
        // re-load the settings if asked to
        if(reloadSettings)
            loadSettings();

        // try to get a new BT thread
        BluetoothManager.getInstance().connect(bluetoothDeviceAddress, true, BluetoothManager.RETRIES_INFINITE);
    }

    @Override
    public void onPause() {
        debug("MainActivity: onPause");
        debug("MainActivity: onPause > leaveBluetoothOn = "+leaveBluetoothOn);
        visible=false;

        // stop here if BT should stay on!
        if(bluetoothBackgroundMode)
        {
            super.onPause();
            return;
        }

        if(!leaveBluetoothOn)
        {
            if(device!=null)
                device.clearFields();
            debug("MainActivity: stopping BT");
            stopBluetooth();
        }

        super.onPause();
    }

    public void stopBluetooth() {
        stopBluetooth(true);
    }

    public void stopBluetooth(boolean reset)
    {
        if(device!=null) {
            // stop the device
            debug("MainActivity: stopBluetooth > stopAndJoin");
            device.stopAndJoin();
            // remove reference
            if(reset) {
                device.clearFields();
                device.registerFilters();
            }
        }
        // disconnect BT
        debug("MainActivity: stopBluetooth > BT disconnect");
        BluetoothManager.getInstance().disconnect();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        MainActivity.debug("MainActivity: onActivityResult");
        MainActivity.debug("MainActivity: onActivityResult > requestCode = " + requestCode);
        MainActivity.debug("MainActivity: onActivityResult > resultCode = " + resultCode);

        // this must be set in any case
        leaveBluetoothOn=false;

        if(requestCode==SETTINGS_ACTIVITY)
        {
            // load settings
            loadSettings();
        }
        else if(requestCode==LEAVE_BLUETOOTH_ON)
        {
            MainActivity.debug("MainActivity: onActivityResult > "+LEAVE_BLUETOOTH_ON);
            returnFromWidget=true;
            // register fields this activity needs
            /*
            registerFields();
             */
        }
        else super.onActivityResult(requestCode, resultCode, data);
    }

        /*
    public void saveFields()
    {
        // safe fields
        SharedPreferences settings = getSharedPreferences(PREFERENCES_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        for(int i=0; i<fields.size(); i++)
        {
            Field f = fields.get(i);
            editor.putFloat(f.getUniqueID(),(float) f.getRawValue());
            //debug("Setting "+f.getUniqueID()+" = "+f.getRawValue());
        }
        editor.commit();
    }
        */

    @Override
    protected void onDestroy() {
        debug("MainActivity: onDestroy");

        dataLogger.destroy(); // clean up

        if(device!=null) {
            // stop the device nicely
            device.stopAndJoin();
            device.clearFields();
            device.registerFilters();
        }
        // disconnect the bluetooth
        BluetoothManager.getInstance().disconnect();

        // un-register for bluetooth changes
        this.unregisterReceiver(broadcastReceiver);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // get a reference to the bluetooth action button
        bluetoothMenutItem = menu.findItem(R.id.action_bluetooth);
        // and put the right view on it
        bluetoothMenutItem.setActionView(R.layout.animated_menu_item);
        // set the correct initial state
        setBluetoothState(BLUETOOTH_DISCONNECTED);
        // get access to the image view
        ImageView imageView = (ImageView) bluetoothMenutItem.getActionView().findViewById(R.id.animated_menu_item_action);
        // define an action
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        toast("Reconnecting Bluetooth ...");
                        stopBluetooth();
                        reloadBluetooth();
                    }
                })).start();
            }
        });

        return true;
    }


    private void setBluetoothState(int btState)
    {
        if(bluetoothMenutItem!=null) {
            final ImageView imageView = (ImageView) bluetoothMenutItem.getActionView().findViewById(R.id.animated_menu_item_action);

            // stop the animation if there is one running
            AnimationDrawable frameAnimation;
            if(imageView.getBackground() instanceof AnimationDrawable) {
                frameAnimation = (AnimationDrawable) imageView.getBackground();
                if (frameAnimation.isRunning())
                    frameAnimation.stop();
            }

            switch (btState) {
                case BLUETOOTH_DISCONNECTED:
                    imageView.setBackgroundResource(R.mipmap.bluetooth_none);
                    break;
                case BLUETOOTH_CONNECTED:
                    imageView.setBackgroundResource(R.mipmap.bluetooth_3);
                    break;
                case BLUETOOTH_SEARCH:
                    runOnUiThread(new Runnable() {
                        @SuppressLint("NewApi")
                        @Override
                        public void run() {
                            AnimationDrawable drawable = (AnimationDrawable) ContextCompat.getDrawable(getApplicationContext(), R.anim.animation_bluetooth);
                            // Use setBackgroundDrawable() for API 14 and 15 and setBackground() for API 16+:
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                imageView.setBackground(drawable);
                            }
                            else
                            {
                                imageView.setBackgroundDrawable(drawable);
                            }
                            AnimationDrawable frameAnimation = (AnimationDrawable) imageView.getBackground();
                            frameAnimation.start();
                        }
                    });
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // start the settings activity
        if (id == R.id.action_settings) {

            if(isSafe())
            {
                // run a toast
                Toast.makeText(MainActivity.this, "Stopping Bluetooth. Settings are being loaded. Please wait ....", Toast.LENGTH_SHORT).show();

                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // give the toast a moment to appear
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        if (device != null) {
                            // stop the BT device
                            device.stopAndJoin();
                            device.clearFields();
                            device.registerFilters();
                            BluetoothManager.getInstance().disconnect();
                        }

                        // load the activity
                        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                        startActivityForResult(intent, SETTINGS_ACTIVITY);
                    }
                })).start();
                return true;
            }
        }
        // see AppSectionsPagerAdapter for the right sequence
        else if (id == R.id.action_main) {
            //loadFragement(new MainFragment());
            viewPager.setCurrentItem(0,true);
            updateActionBar();

        }
        else if (id == R.id.action_technical) {
            //loadFragement(new TechnicalFragment());
            viewPager.setCurrentItem(1,true);
            updateActionBar();

        }
        else if (id == R.id.action_experimental) {
            //loadFragement(new ExperimentalFragment());
            viewPager.setCurrentItem(2,true);
            updateActionBar();

        }
        //else if (id == R.id.action_bluetooth) {
        //}


        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFieldUpdateEvent(Field field) {
        if(field.getSID().equals("5d7.0"))
        {
            //debug("Speed "+field.getValue());
            isDriving = (field.getValue()>10);
        }
    }

    public static boolean isSafe()
    {
        boolean safe = !isDriving || !safeDrivingMode;
        if(!safe)
        {
            Toast.makeText(MainActivity.instance,"Not possible while driving ...",Toast.LENGTH_LONG).show();
        }
        return safe;
    }
}
