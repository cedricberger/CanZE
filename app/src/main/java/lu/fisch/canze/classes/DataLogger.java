package lu.fisch.canze.classes;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import lu.fisch.canze.activities.MainActivity;
import lu.fisch.canze.actors.Field;
import lu.fisch.canze.interfaces.FieldListener;

import static lu.fisch.canze.activities.MainActivity.debug;


/**
 * Created by Chris Mattheis on 03/11/15.
 * don't use yet - still work in progress
 */
public class DataLogger  implements FieldListener {

    /* ****************************
     * Singleton stuff
     * ****************************/

    private static DataLogger dataLogger = null;

    public static DataLogger getInstance() {
        if(dataLogger ==null) dataLogger =new DataLogger();
        return dataLogger;
    }

    /* ****************************
     * Datalogger stuff
     * ****************************/

    // -------- Data Definitions copied from Driving Activity -- start ---
    // for ISO-TP optimization to work, group all identical CAN ID's together when calling addListener
    // free data
    public static final String SID_Pedal                                = "186.40"; //EVC
    public static final String SID_MeanEffectiveTorque                  = "186.16"; //EVC
    public static final String SID_RealSpeed                            = "5d7.0";  //ESC-ABS
    public static final String SID_SoC                                  = "654.25"; //EVC
    public static final String SID_RangeEstimate                        = "654.42"; //EVC
    public static final String SID_DriverBrakeWheel_Torque_Request      = "130.44"; //UBP braking wheel torque the driver wants
    public static final String SID_ElecBrakeWheelsTorqueApplied         = "1f8.28"; //UBP 10ms

    // ISO-TP data
//  public static final String SID_EVC_SoC                              = "7ec.622002.24"; //  (EVC)
//  public static final String SID_EVC_RealSpeed                        = "7ec.622003.24"; //  (EVC)
    public static final String SID_EVC_Odometer                         = "7ec.622006.24"; //  (EVC)
    //  public static final String SID_EVC_Pedal                            = "7ec.62202e.24"; //  (EVC)
    public static final String SID_EVC_TractionBatteryVoltage           = "7ec.623203.24"; //  (EVC)
    public static final String SID_EVC_TractionBatteryCurrent           = "7ec.623204.24"; //  (EVC)

    private double dcVolt                           = 0; // holds the DC voltage, so we can calculate the power when the amps come in
    private int    odo                              = 0;
    private double realSpeed                        = 0;

    private ArrayList<Field> subscribedFields;
    // -------- Data Definitions copied from Driving Activity -- end ---

    private File logFile = null;
    private boolean activated = false;

    private long z = 2;

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    // Checks if external storage is available for read and write

    public DataLogger() {

        debug("DataLogger: constructor called");

    }

    public boolean isExternalStorageWritable() {
        String SDstate = Environment.getExternalStorageState();
        return ( Environment.MEDIA_MOUNTED.equals(SDstate));
        }

    public boolean isCreated()
    {
        return (logFile!=null);
    }

    public boolean activate ( boolean state ) {
        boolean result = state;
        debug ( "DataLogger: activate > request = " + state );

        if ( activated != state) {
            if (state) { // now need to activate, open file, start timer
                result = start();
                activated = result; // only true in case of no errors
                // debug("DataLogger: start");
            } else { // now need to de-activate, close file, stop timer
                result = stop();
                activated = false; // always false
                // debug("DataLogger: stop ");
            }
        }
        debug ( "DataLogger: activate > return " + result );
       return result;
    }


    public boolean createNewLog() {
        boolean result = false;

        // ensure that there is a CanZE Folder in SDcard
        if ( ! isExternalStorageWritable()) {
            debug ( "DataLogger: SDcard not writeable");
            return false;
        }
        else {
            String file_path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/CanZE/";
            File dir = new File(file_path);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            debug("DataLogger: file_path:" + file_path);

            // SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            String exportdataFileName = file_path + "data" + sdf.format(Calendar.getInstance().getTime()) + ".log";

            logFile = new File(exportdataFileName);
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                    debug("DataLogger: NewFile:" +  exportdataFileName );
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                //BufferedWriter for performance, true to set append to file flag
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(logFile, true));
                // set global static BufferedWriter dataexportStream later
                //if (true) {
                //    bufferedWriter.append("this is just a test if stream is writeable");
                //    bufferedWriter.newLine();
                //    bufferedWriter.close();
                //}
                bufferedWriter.close();
                result = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private long intervall = 5000;

    private Handler handler = new Handler();


    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            // write data to file
            String timestamp = "timestamp"; // sdf.format(sdf.format(Calendar.getInstance().getTime()));
            String data = "Zeile";
            // String dataWithNewLine= sdf.format(Calendar.getInstance()) + data + System.getProperty("line.separator");
            String dataWithNewLine=  timestamp + ";" + data + System.getProperty("line.separator");

            // if(!isCreated()) createNewLog();

            // try {
            //    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(logFile, true));
            //    bufferedWriter.append(data);
            //    bufferedWriter.close();
            //}
            //catch (IOException e) {
            //    e.printStackTrace();
            //}
            log ( dataWithNewLine );
            handler.postDelayed(this, intervall);
        }
    };

    /**
     * Appends a line of text to the log file
     * @param text  the text line. A CR will be added automatically
     */
    public void log(String text)
    {
        if(!isCreated()) createNewLog();
        debug("DataLogger - log: " + text);

        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(logFile, true));
            bufferedWriter.append(text+"\n");
            bufferedWriter.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean start() {
        boolean result = false;

        // open logfile
        // start timer
        debug("DataLogger: start");
        handler.postDelayed(runnable, 400);
        initListeners();
        return createNewLog();
    }

    public boolean stop() {
        boolean result = false;

        // flush and close logfile
        // stop timer
        debug("DataLogger: stop");
        logFile = null;
        handler.removeCallbacks(runnable);

        // free up the listeners again
        if (subscribedFields != null) {
            for (Field field : subscribedFields) {
                field.removeListener(this);
            }
            subscribedFields.clear();
        }
        debug("DataLogger: stop - and logFile = null");
        return result;
    }

    // Bob: Useless
    /*
    // @Override
    // protected void onCreate(Bundle savedInstanceState) {
    //    super.onCreate(savedInstanceState);
    public void create() {
        // start timer in 400ms
        boolean result = start();
        // handler.postDelayed(runnable, 400 );
    }
    */

    public void destroy() {
        handler.removeCallbacks(runnable);
        boolean result = stop();
    }

    private void addListener(String sid, int intervalMs) {
        Field field;
        field = MainActivity.fields.getBySID(sid);
        if (field != null) {
            field.addListener(this);
            // MainActivity.device.addActivityField(field, intervalMs);
            MainActivity.device.addApplicationField(field, intervalMs);
            subscribedFields.add(field);
        }
        else
        {
            MainActivity.toast("sid " + sid + " does not exist in class Fields");
        }
    }

    private void initListeners() {

        subscribedFields = new ArrayList<>();

        debug("DataLogger: initListeners");

        // Make sure to add ISO-TP listeners grouped by ID

        addListener(SID_Pedal, 2000);
        addListener(SID_MeanEffectiveTorque, 2000);
        addListener(SID_DriverBrakeWheel_Torque_Request, 2000);
        addListener(SID_ElecBrakeWheelsTorqueApplied, 2000);
        addListener(SID_RealSpeed, 2000);
        addListener(SID_SoC, 3600);
        addListener(SID_RangeEstimate, 3600);

        //addListener(SID_EVC_SoC);
        addListener(SID_EVC_Odometer, 6000);
        addListener(SID_EVC_TractionBatteryVoltage, 5000);
        addListener(SID_EVC_TractionBatteryCurrent, 2000);
        //addListener(SID_PEB_Torque);
    }


    // This is the event fired as soon as this the registered fields are
    // getting updated by the corresponding reader class.
    @Override
    public void onFieldUpdateEvent(final Field field) {
                String fieldId = field.getSID();
                double fieldValue;

        Long tsLong = System.currentTimeMillis()/1000;
        String timestamp = tsLong.toString();

        // String timestamp = "timestamp"; // sdf.format(sdf.format(Calendar.getInstance().getTime()));
        // System.getProperty("line.separator");

        log ( timestamp + ";" + fieldId + ";" + field.getPrintValue() );
                // get the text field
                switch (fieldId) {
                    case SID_SoC:
//                  case SID_EVC_SoC:
                        fieldValue = field.getValue();
                        log ( "...SID_SoC: " + fieldValue );
                        break;
                    case SID_Pedal:
//                  case SID_EVC_Pedal:
                        // pb.setProgress((int) field.getValue());
                        break;
                    case SID_MeanEffectiveTorque:
                        // pb.setProgress((int) field.getValue());
                        break;
                    case SID_EVC_Odometer:
                        odo = (int ) field.getValue();
                        //odo = (int) Utils.kmOrMiles(field.getValue());
                        break;
                    case SID_RealSpeed:
//                  case SID_EVC_RealSpeed:
                        //realSpeed = (Math.round(Utils.kmOrMiles(field.getValue()) * 10.0) / 10.0);
                        realSpeed = (Math.round(field.getValue() * 10.0) / 10.0);
                        break;
                    //case SID_PEB_Torque:
                    //    tv = (TextView) findViewById(R.id.textTorque);
                    //    break;
                    case SID_EVC_TractionBatteryVoltage: // DC volts
                        // save DC voltage for DC power purposes
                        dcVolt = field.getValue();
                        break;
                    case SID_EVC_TractionBatteryCurrent: // DC amps
                        // calculate DC power
                        double dcPwr = Math.round(dcVolt * field.getValue() / 100.0) / 10.0;
                        break;
                    case SID_RangeEstimate:
                        //int rangeInBat = (int) Utils.kmOrMiles(field.getValue());
                        int rangeInBat = (int) field.getValue();
                        break;
                    case SID_DriverBrakeWheel_Torque_Request:
                        // driverBrakeWheel_Torque_Request = field.getValue();
                        break;
                    case SID_ElecBrakeWheelsTorqueApplied:
                        // double frictionBrakeTorque = driverBrakeWheel_Torque_Request - field.getValue();
                        // a fair full red bar is estimated @ 1000 Nm
                        // pb = (ProgressBar) findViewById(R.id.FrictionBreaking);
                        // pb.setProgress((int) (frictionBrakeTorque * realSpeed));
                        break;
                }

    }

    // Bob: This is no activity ...
    /*
    public void onPause() {
        onDestroy();
    }
    */

    // Bob: This is no activity ...
    /*
    public void onResume() {
        onCreate();
    }
    */


    // only for test and trace purposes - delete later on
    public void add14() {
        z += 14;
        debug( "DataLogger: " + z );
    }
}

