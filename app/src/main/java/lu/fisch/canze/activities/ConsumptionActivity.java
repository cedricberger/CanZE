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

import android.os.Bundle;
import android.view.Menu;
import android.widget.ProgressBar;
import android.widget.TextView;

import lu.fisch.canze.R;
import lu.fisch.canze.actors.Field;

public class ConsumptionActivity extends CanzeActivity {

    public static final String SID_MeanEffectiveTorque                  = "186.16"; //EVC
    public static final String SID_TotalPotentialResistiveWheelsTorque  = "1f8.16"; //UBP 10ms
    public static final String SID_DriverBrakeWheel_Torque_Request      = "130.44"; //UBP braking wheel torque the driver wants
    public static final String SID_Coasting_Torque                      = "18a.27"; //10ms Friction torque means EMULATED friction, what we'd call coasting
    public static final String SID_Instant_Consumption                  = "800.6100.24";


    private double coasting_Torque                  = 0;
    private double driverBrakeWheel_Torque_Request  = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consumption);

        addField(SID_MeanEffectiveTorque, 0);
        addField(SID_DriverBrakeWheel_Torque_Request, 0);
        addField(SID_Coasting_Torque, 0);
        addField(SID_TotalPotentialResistiveWheelsTorque, 7200);
        addField(SID_Instant_Consumption, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_empty, menu);
        return true;
    }

    /********************************/

    @Override
    public void onFieldUpdateEvent(final Field field) {
        // the update has to be done in a separate thread
        // otherwise the UI will not be repainted
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String fieldId = field.getSID();
                ProgressBar pb;

                switch (fieldId) {
                    case SID_MeanEffectiveTorque:
                        pb = (ProgressBar) findViewById(R.id.MeanEffectiveAccTorque);
                        pb.setProgress((int) (field.getValue() * 9.3)); // --> translate from motor torque to wheel torque
                        break;
                    case SID_Coasting_Torque:
                        coasting_Torque = field.getValue() * 9.3; // it seems this torque is given in motor torque, not in wheel torque. Maybe another adjustment by a factor 05 is needed (two wheels)
                        break;
                    case SID_TotalPotentialResistiveWheelsTorque:
                        int tprwt = -((int) field.getValue());
                        pb = (ProgressBar) findViewById(R.id.MaxBreakTorque);
                        if (pb != null) pb.setProgress(tprwt < 2047 ? tprwt : 10);
                        break;
                    case SID_DriverBrakeWheel_Torque_Request:
                        driverBrakeWheel_Torque_Request = field.getValue() + coasting_Torque;
                        pb = (ProgressBar) findViewById(R.id.pb_driver_torque_request);
                        if (pb != null) pb.setProgress((int) driverBrakeWheel_Torque_Request);
                        break;
                    case SID_Instant_Consumption:
                        ((ProgressBar) findViewById(R.id.pb_instant_consumption_negative)).setProgress(Math.abs(Math.min(0, (int) field.getValue())));
                        ((ProgressBar) findViewById(R.id.pb_instant_consumption_positive)).setProgress(Math.max(0, (int) field.getValue()));
                        break;
                }/**/
            }
        });

    }


}
