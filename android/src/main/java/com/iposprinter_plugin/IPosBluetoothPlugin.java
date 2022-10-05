package com.iposprinter_plugin;

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.iposprinter_plugin.iposprinterservice.*;
import com.iposprinter_plugin.iposprinterservice.Utils.BitMapUtil;
import com.iposprinter_plugin.iposprinterservice.Utils.BluetoothUtil;
import com.iposprinter_plugin.iposprinterservice.Utils.ESCUtil;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Random;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;


public class IPosBluetoothPlugin implements FlutterPlugin,
        MethodChannel.MethodCallHandler, ActivityAware {
    private static final String TAG = "IPOSPrinterPlugin";
    private static final String NAMESPACE = "iposprinter_plugin";


    private BluetoothManager mBluetoothManager = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothDevice mBluetoothPrinterDevice = null;
    private BluetoothSocket socket = null;

    // Define Messages
    private final int MSG_IS_NORMAL = 0;
    private final int MSG_IS_BUSY = 1;
    private final int MSG_PAPER_LESS = 2;
    private final int MSG_PAPER_EXISTS = 3;
    private final int MSG_THP_HIGH_TEMP = 4;
    private final int MSG_THP_TEMP_NORMAL = 5;
    private final int MSG_MOTOR_HIGH_TEMP = 6;
    private final int MSG_MOTOR_HIGH_TEMP_INIT_PRINTER = 7;
    private final int MSG_CURRENT_TASK_PRINT_COMPLETE = 8;
    private final int PRINTER_ERROR_UNKNOWN = 9;
    private final int PRINTER_BLUETOOTH_TURNING_ON = 10;
    private final int PRINTER_BLUETOOTH_TURNING_OFF = 11;
    private final int PRINTER_BLUETOOTH_ON = 12;
    private final int PRINTER_BLUETOOTH_OFF = 13;
    private final int PRINTER_BLUETOOTH_CONNECTING = 14;
    private final int PRINTER_BLUETOOTH_DISCONNECTING= 15;
    private final int PRINTER_BLUETOOTH_CONNECTED = 16;
    private final int PRINTER_BLUETOOTH_DISCONNECTED = 17;
    private final int PRINTER_BLUETOOTH_ERROR = 18;


    // Printer current status
    private int printerStatus = PRINTER_ERROR_UNKNOWN;

    // Define state broadcast
    private final String PRINTER_NORMAL_ACTION =
            "com.iposprinter.iposprinterservice.NORMAL_ACTION";
    private final String PRINTER_PAPERLESS_ACTION =
            "com.iposprinter.iposprinterservice.PAPERLESS_ACTION";
    private final String PRINTER_PAPEREXISTS_ACTION =
            "com.iposprinter.iposprinterservice.PAPEREXISTS_ACTION";
    private final String PRINTER_THP_HIGHTEMP_ACTION =
            "com.iposprinter.iposprinterservice.THP_HIGHTEMP_ACTION";
    private final String PRINTER_THP_NORMALTEMP_ACTION =
            "com.iposprinter.iposprinterservice.THP_NORMALTEMP_ACTION";
    private final String PRINTER_MOTOR_HIGHTEMP_ACTION =
            "com.iposprinter.iposprinterservice.MOTOR_HIGHTEMP_ACTION";
    private final String PRINTER_BUSY_ACTION =
            "com.iposprinter.iposprinterservice.BUSY_ACTION";
    private final String PRINTER_CURRENT_TASK_PRINT_COMPLETE_ACTION =
            "com.iposprinter.iposprinterservice.CURRENT_TASK_PRINT_COMPLETE_ACTION";
    private final String GET_CUST_PRINTAPP_PACKAGENAME_ACTION =
            "android.print.action.CUST_PRINTAPP_PACKAGENAME";



    private final static int REQUEST_ENABLE_BT = 1;

    // Loop print type
    private final int  MULTI_THREAD_LOOP_PRINT  = 1;
    private final int  DEFAULT_LOOP_PRINT       = 0;

    //loop print flags
    private int  loopPrintFlag = DEFAULT_LOOP_PRINT;
    private boolean isBluetoothOpen = false;
    private boolean isBluetoothEnabled = false;
    private Random random = new Random();

    private Context context;
    private Object initializationLock = new Object();

    private FlutterPlugin.FlutterPluginBinding pluginBinding;
    private ActivityPluginBinding activityBinding;

    private Application application;
    private Activity activity;

    private MethodChannel channel;
    private EventChannel stateChannel;
    private EventChannel readChannel;

    private EventSink readSink;
    private EventSink statusSink;



    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void registerWith(PluginRegistry.Registrar registrar) {
        final IPosBluetoothPlugin instance = new IPosBluetoothPlugin();
        //registrar.addRequestPermissionsResultListener(instance);
        Activity activity = registrar.activity();
        Application application = null;
        instance.setup(registrar.messenger(), application, activity, registrar, null);
    }

    public IPosBluetoothPlugin() {
    }

    // Setup the plugin
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void setup(final BinaryMessenger messenger,
                       final Application application,
                       final Activity activity,
                       final PluginRegistry.Registrar registrar,
                       final ActivityPluginBinding activityBinding) {
        synchronized (initializationLock) {
            io.flutter.Log.i(TAG, "setup");
            this.activity = activity;
            this.application = application;
            this.context = application;
            channel = new MethodChannel(messenger, NAMESPACE + "/methods");
            channel.setMethodCallHandler(this);
            stateChannel = new EventChannel(messenger, NAMESPACE + "/state");
            stateChannel.setStreamHandler(stateStreamHandler);
            readChannel = new EventChannel(messenger, NAMESPACE + "/read");
            readChannel.setStreamHandler(readResultsHandler);
//            initialiseBluetoothPrinter();
            mBluetoothManager = (BluetoothManager) application.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = BluetoothUtil.getBluetoothAdapter();
        }
    }

    // Stream Handler
    private final StreamHandler stateStreamHandler = new StreamHandler() {
        private final BroadcastReceiver IPosPrinterStatusListener = new BroadcastReceiver() {
          @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
          @Override
          public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
              Log.i(TAG, "IPosPrinterStatusListener onReceive action = null");
              return;
            }
            Log.i(TAG, "IPosPrinterStatusListener action = " + action);
            if (action.equals(PRINTER_NORMAL_ACTION)) {
              statusSink.success(MSG_IS_NORMAL);
            } else if (action.equals(PRINTER_PAPERLESS_ACTION)) {
              statusSink.success(MSG_PAPER_LESS);
            } else if (action.equals(PRINTER_BUSY_ACTION)) {
              statusSink.success(MSG_IS_BUSY);
            } else if (action.equals(PRINTER_PAPEREXISTS_ACTION)) {
              statusSink.success(MSG_PAPER_EXISTS);
            } else if (action.equals(PRINTER_THP_HIGHTEMP_ACTION)) {
              statusSink.success(MSG_THP_HIGH_TEMP);
            } else if (action.equals(PRINTER_THP_NORMALTEMP_ACTION)) {
              statusSink.success(MSG_THP_TEMP_NORMAL);
            } else if (action.equals(PRINTER_MOTOR_HIGHTEMP_ACTION)) {
              //At this time, the current task will continue to print.
              // After completing the current task, please wait for more than 2 minutes to continue the next printing task.
              statusSink.success(MSG_MOTOR_HIGH_TEMP);
            } else if (action.equals(PRINTER_CURRENT_TASK_PRINT_COMPLETE_ACTION)) {
              statusSink.success(MSG_CURRENT_TASK_PRINT_COMPLETE);
            } else if (action.equals(GET_CUST_PRINTAPP_PACKAGENAME_ACTION)) {
              String mPackageName = null;
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.DONUT) {
                mPackageName = intent.getPackage();
              }
              Log.i(TAG, "*******GET_CUST_PRINTAPP_PACKAGENAME_ACTION：" + action + "*****mPackageName:" + mPackageName);

            } else if(action.equals(BluetoothAdapter.ACTION_STATE_CHANGED))
            {
                int state= intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.i(TAG, "STATE_OFF bluetooth off");
                        isBluetoothOpen = false;
                        statusSink.success(PRINTER_BLUETOOTH_OFF);
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.i(TAG, "STATE_TURNING_OFF bluetooth is turning off");
                        isBluetoothOpen = false;
                        if(mBluetoothAdapter != null)
                            mBluetoothAdapter = null;
                        if(mBluetoothPrinterDevice != null)
                            mBluetoothPrinterDevice = null;
                        try {
                            if (socket != null && (socket.isConnected())) {
                                socket.close();
                                socket = null;
                            }
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                            statusSink.error("ERROR", e.getMessage(), e.toString());
                            break;
                        }
                        statusSink.success(PRINTER_BLUETOOTH_TURNING_OFF);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.i(TAG, "STATE_ON bluetooth on");
                        loopPrintFlag = DEFAULT_LOOP_PRINT;
                        isBluetoothOpen = true;
                        initialiseBluetoothPrinter();
                        statusSink.success(PRINTER_BLUETOOTH_ON);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        isBluetoothOpen = true;
                        Log.i(TAG, "STATE_TURNING_ON bluetooth is on");
                        statusSink.success(PRINTER_BLUETOOTH_TURNING_ON);
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        isBluetoothOpen = true;
                        Log.i(TAG, "STATE_CONNECTED bluetooth is connected");
                        statusSink.success(PRINTER_BLUETOOTH_CONNECTED);
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        isBluetoothOpen = true;
                        Log.i(TAG, "STATE_CONNECTING bluetooth is connecting");
                        statusSink.success(PRINTER_BLUETOOTH_CONNECTING);
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTED:
                        isBluetoothOpen = false;
                        Log.i(TAG, "STATE_DISCONNECTED bluetooth is disconnected");
                        statusSink.success(PRINTER_BLUETOOTH_DISCONNECTED);
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTING:
                        isBluetoothOpen = true;
                        Log.i(TAG, "STATE_DISCONNECTING bluetooth is disconnecting");
                        statusSink.success(PRINTER_BLUETOOTH_DISCONNECTING);
                        break;
                    case BluetoothAdapter.ERROR:
                        isBluetoothOpen = true;
                        Log.i(TAG, "STATE_ERROR Error with Bluetooth");
                        statusSink.success(PRINTER_BLUETOOTH_ERROR);
                        break;


                }
            }
            else {
              statusSink.success(PRINTER_ERROR_UNKNOWN);
            }
          }
        };

        @Override
        public void onListen(Object o, EventSink eventSink) {
          statusSink = eventSink;
          IntentFilter printerStatusFilter = new IntentFilter();
          printerStatusFilter.addAction(PRINTER_NORMAL_ACTION);
          printerStatusFilter.addAction(PRINTER_PAPERLESS_ACTION);
          printerStatusFilter.addAction(PRINTER_PAPEREXISTS_ACTION);
          printerStatusFilter.addAction(PRINTER_THP_HIGHTEMP_ACTION);
          printerStatusFilter.addAction(PRINTER_THP_NORMALTEMP_ACTION);
          printerStatusFilter.addAction(PRINTER_MOTOR_HIGHTEMP_ACTION);
          printerStatusFilter.addAction(PRINTER_BUSY_ACTION);
          printerStatusFilter.addAction(PRINTER_CURRENT_TASK_PRINT_COMPLETE_ACTION);
          printerStatusFilter.addAction(GET_CUST_PRINTAPP_PACKAGENAME_ACTION);
          context.registerReceiver(IPosPrinterStatusListener, printerStatusFilter);
          context.registerReceiver(IPosPrinterStatusListener, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        }

        @Override
        public void onCancel(Object o) {
          statusSink = null;
          context.unregisterReceiver(IPosPrinterStatusListener);
        }
    };

    private final StreamHandler readResultsHandler = new StreamHandler() {
        @Override
        public void onListen(Object o, EventSink eventSink) {
            readSink = eventSink;
        }

        @Override
        public void onCancel(Object o) {
            readSink = null;
        }
    };


    byte convertFontSizes(int size) {
        switch (size) {
            case 16:
                return (byte) 0;
            case 24:
                return (byte) 17;
            case 32:
                return (byte) 34;
            case 48:
                return (byte) 47;
            default:
                return (byte) 0;
        }
    }

    public boolean isPackageExisted(String targetPackage){
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(targetPackage,PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void sendBluetoothCommand(byte[] data, Result result) {
        if (!mBluetoothAdapter.isEnabled() || mBluetoothPrinterDevice == null) {
            initialiseBluetoothPrinter();
        }
        try {
            if((socket == null) || (!socket.isConnected()))
            {
                socket = BluetoothUtil.getSocket(mBluetoothPrinterDevice);
            }
            //Log.d(TAG,"=====printerInit======");
            OutputStream out = socket.getOutputStream();
            out.write(data,0,data.length);
            out.close();
            socket.close();
            result.success(true);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            result.error("ERROR", e.getMessage(), e.toString());
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public boolean initialiseBluetoothPrinter()
    {
        // 1: Get BluetoothAdapter
        mBluetoothAdapter = BluetoothUtil.getBluetoothAdapter();
        Log.i(TAG, mBluetoothAdapter.toString());
        if (mBluetoothAdapter == null) {
            Log.i(TAG, "Bluetooth is Off. Attempting to restart");
            mBluetoothManager = (BluetoothManager) application.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = BluetoothUtil.getBluetoothAdapter();
        }
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.enable();
            isBluetoothOpen = true;
        } else {
            isBluetoothOpen = false;
        }

        //2: Get bluetoothPrinter Devices
        mBluetoothPrinterDevice = BluetoothUtil.getIposPrinterDevice(mBluetoothAdapter);
        if(mBluetoothPrinterDevice == null)
        {
            Log.e(TAG, "Failed to get POS Bluetooth device");
            return false;
        }

        //3: Get Socket Connection to IPOSPrinter Bluetooth
        try {
            socket = BluetoothUtil.getSocket(mBluetoothPrinterDevice);
        }
        catch (IOException e)
        {
//            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    private void getState(Result result) {
        try {
            switch (mBluetoothAdapter.getState()) {
                case BluetoothAdapter.STATE_OFF:
                    result.success(BluetoothAdapter.STATE_OFF);
                    break;
                case BluetoothAdapter.STATE_ON:
                    result.success(BluetoothAdapter.STATE_ON);
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    result.success(BluetoothAdapter.STATE_TURNING_OFF);
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    result.success(BluetoothAdapter.STATE_TURNING_ON);
                    break;
                default:
                    result.success(0);
                    break;
            }
        } catch (SecurityException e) {
            result.error("invalid_argument", "Argument 'address' not found", null);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public int getPrinterStatus(Result result)
    {
        byte[] statusData = new byte[3];
        if(!isBluetoothOpen)
        {
            printerStatus = PRINTER_ERROR_UNKNOWN;
            return printerStatus;
        }
        if (mBluetoothPrinterDevice == null) {
            initialiseBluetoothPrinter();
        }
        if((socket == null) || (!socket.isConnected()))
        {
            try {
                socket = BluetoothUtil.getSocket(mBluetoothPrinterDevice);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                return printerStatus;
            }
        }
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            byte[] data = ESCUtil.getPrinterStatus();
            out.write(data,0,data.length);
            int readsize = in.read(statusData);
            Log.d(TAG,"~~~ readsize:"+readsize+" statusData:"+statusData[0]+" "+statusData[1]+" "+statusData[2]);
            if((readsize > 0) &&(statusData[0] == ESCUtil.ACK && statusData[1] == 0x11)) {
                printerStatus = statusData[2];
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            result.error("ERROR", e.getMessage(), e.toString());
        }

        return printerStatus;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean printerInit(Result result) {
        if (mBluetoothPrinterDevice == null) {
            initialiseBluetoothPrinter();
        }
        try {
            if ((socket == null) || (!socket.isConnected())) {
                socket = BluetoothUtil.getSocket(mBluetoothPrinterDevice);
            }
            //Log.d(TAG,"=====printerInit======");
            OutputStream out = socket.getOutputStream();
            byte[] data = ESCUtil.byteMerger(new byte[][] {
                    ESCUtil.init_printer(),
                    ESCUtil.selectChineseMode(),
            });
            out.write(data, 0, data.length);
            out.close();
            socket.close();
            result.success(true);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void printData(List<Map<String, Object>> data, Result result) {
        try {
            byte[][] cmdData = new byte[data.size()][];
            for (int i = 0; i < data.size(); i++) {
                Map<String, Object> line = data.get(i);
                String type = (String) line.get("type");
                android.util.Log.i(TAG, type);
                if (type.equals("image")) {
                    android.util.Log.i(TAG, "Printing Image");
                    // AssetManager assetManager = getAssets();
                    Bitmap bt;
                    // InputStream imageFile = assetManager.open("logo2.jpg");
                    byte[] imageBytes = (byte[]) line.get("image");
                    bt = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    cmdData[i] = ESCUtil.byteMerger(new byte[][] {
                            ESCUtil.alignMode((byte) 1),
                            BitMapUtil.getBitmapPrintData(bt, (int) line.get("size"), (int) line.get("bmpMode")),
                    });
                } else {
                    cmdData[i] = ESCUtil.byteMerger(new byte[][] {
                            ESCUtil.fontSizeSet((byte) convertFontSizes((int) line.get("size"))),
                            ESCUtil.alignMode((byte) 0),
                            line.get("text").toString().getBytes("cp1258")
                    });
                }
            }
            sendBluetoothCommand(ESCUtil.byteMerger(cmdData), result);
        } catch (Exception e) {
            e.printStackTrace();
            result.error("ERROR", e.getMessage(), e.toString());
        }
    }

    // MethodChannel.Result wrapper that responds on the platform thread.
    private static class MethodResultWrapper implements Result {
        private Result methodResult;
        private Handler handler;

        MethodResultWrapper(Result result) {
            methodResult = result;
            handler = new Handler(Looper.getMainLooper());
        }

        @Override
        public void success(final Object result) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    methodResult.success(result);
                }
            });
        }

        @Override
        public void error(final String errorCode, final String errorMessage, final Object errorDetails) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    methodResult.error(errorCode, errorMessage, errorDetails);
                }
            });
        }

        @Override
        public void notImplemented() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    methodResult.notImplemented();
                }
            });
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result rawResult) {
        Result result = new MethodResultWrapper(rawResult);
        if (isPackageExisted("com.iposprinter.iposprinterservice") == false) {
            result.error("ERROR", "POS Printer Service is not available", "Please " +
                    "ensure you are using a compatible POS device");
            return;
        }

        if (!isBluetoothOpen) {
            initialiseBluetoothPrinter();
        }

        final Map<String, Object> arguments = call.arguments();

        io.flutter.Log.i(TAG, "Attempting to run task: "+call.method);
        ThreadPoolManager.getInstance().executeTask(new Runnable() {

            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void run() {
                try {
                    // byte[] buffer = new byte[1024];
                    // readSink.success(new String(buffer, 0, bytes));

                    switch (call.method) {
                        case "state":
                            getState(result);
                            break;

                        case "isAvailable":
                            result.success(mBluetoothAdapter != null);
                            break;

                        case "isOn":
                            try {
                                assert mBluetoothAdapter != null;
                                result.success(mBluetoothAdapter.isEnabled());
                            } catch (Exception ex) {
                                result.error("Error", ex.getMessage(), ex.toString());
                            }
                            break;

                        case "isConnected":
                            result.success(socket != null);
                            break;

                        case "printerStatus":
                            io.flutter.Log.i(TAG, "Get Printer Status");
                            result.success(getPrinterStatus(result));
                            break;

                        // Printer initialization The printer is powered on and initialized with default settings.
                        // Please check the printer status when using it.
                        // Please wait for PRINTER_IS_BUSY.
                        case "printerInit":
                            io.flutter.Log.i(TAG, "Initialise Printer");
                            printerInit(result);
                            break;

                        // Setting the font size will have an impact on subsequent printing unless initialized.
                        // Note: the font size is a printing method that exceeds the standard international
                        // instructions.
                        // Adjusting the font size will affect the character width, and the number of characters in
                        // each line will also change accordingly.
                        // The typesetting may be messy, you need to adjust it yourself
                        case "setPrinterPrintFontSize":
                            sendBluetoothCommand(ESCUtil.fontSizeSet(convertFontSizes((int) arguments.get("fontSize"))), result);
                            break;

                        // Set the alignment, which will affect subsequent printing unless initialized
                        // alignment 0--left, 1--center, 2--right, center by default
                        case "setPrinterPrintAlignment":
                            sendBluetoothCommand(ESCUtil.alignMode((byte) ((int) arguments.get("alignment")))
                            , result);
                            break;

                        // The printer is feeding paper (forced line feed, after finishing the previous printing
                        // content,
                        // the paper feeds lines, the motor is idling to feed the paper, and no data is sent to the
                        // printer)
                        //case "printerFeedLines":
                        //break

                        // Print a blank line (force a newline, print a blank line after the end of the previous print
                        // content,
                        // and the data sent to the printer at this time is all 0x00)
                        case "printBlankLines":
                            Log.i(TAG, "Print Blank Lines with arguments: "+arguments.toString());
                            sendBluetoothCommand(ESCUtil.nextLines((int) arguments.get("lines")), result);
                            break;

                        case "printFeedLines":
//                            Log.i(TAG, "Print Feed Blank Lines with arguments: "+arguments.toString());
                            sendBluetoothCommand(ESCUtil.performPrintFeedPaperLines((byte) ((int) arguments.get("lines"))), result);
                            break;

                        case "printLineDemarcation":
//                            Log.i(TAG, "Print Feed Blank Lines with arguments: "+arguments.toString());
                            sendBluetoothCommand(ESCUtil.performPrintAndFeedPaper((byte) ((int) arguments.get("lines"))), result);
                            break;

                        // Print Text
                        // Print text Automatic line wrapping when the text width is full
                        case "printText":
                            sendBluetoothCommand(arguments.get("text").toString().getBytes("cp1258"), result);
                            break;

                        // Print Type-faced Text
                        // Print the specified font type and size text. The font setting is only valid for this time.
                        // The text width is automatically wrapped and typed
                        case "printSpecifiedTypeText":
                            sendBluetoothCommand(ESCUtil.byteMerger(new byte[][] {
                                    ESCUtil.fontSizeSet(convertFontSizes((int) arguments.get("fontSize"))),
                                    arguments.get("text").toString().getBytes("cp1258"),
                                    ESCUtil.nextLines(1),
                            }), result);
                            break;

                        // Print Formatted Text
                        // Print the specified font type and size text. The font setting is only valid for this time.
                        // The text width is automatically wrapped and typed
                        case "printSpecFormatText":
                            sendBluetoothCommand(ESCUtil.byteMerger(new byte[][] {
                                    ESCUtil.alignMode((byte) ((int) arguments.get("alignment"))),
                                    ESCUtil.fontSizeSet(convertFontSizes((int) arguments.get("fontSize"))),
                                    arguments.get("text").toString().getBytes("cp1258"),
                                    ESCUtil.nextLines(1),
                            }), result);
                            break;

                        // Print Column Text
                        // Print a row of the table, you can specify the column width and alignment
                        // Print a row of the table, you can specify the column width and alignment
                        // Params:
                        // colsTextArr – an array of text strings for each column
                        // colsWidthArr – the width array of each column. The total width cannot be greater than
                        // ((384 / fontsize) << 1)-(number of columns + 1) (calculated in English characters, each Chinese character occupies two English characters, and each width is greater than 0),
                        // colsAlign – Alignment of each column (0 is left, 1 is center, 2 is right)
                        // isContinuousPrint – whether to continue printing the table
                        // 1: continue printing
                        // 0: do not continue printing
                        // Note: The array length of the three parameters should be the same, if the width of colsTextArr[i] is greater than colsWidthArr[i], the text wraps


                        // Print a Bitmap Image
                        // Alignment 0--left, 1--center, 2--right, center by default
                        // Bitmap size, the incoming size range is 1~16, and the default selection is 10 if it exceeds the range. Unit: 24bit
                        case "printBitmap":
                            byte[] bytes = (byte[]) arguments.get("imageBytesArray");
                            Bitmap bt = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            sendBluetoothCommand(
                                BitMapUtil.getBitmapPrintData(
                                        bt,
                                        (int) arguments.get("imageSize"),
                                        (int) arguments.get("bmpMode")
                                ), result);
                            break;

                        // Print Bar Code
                        // Barcode Type 0 -- UPC-A, 1 -- UPC-E, 2 -- JAN13(EAN13), 3 -- JAN8(EAN8), 4 -- CODE39, 5 -- ITF, 6 -- CODABAR, 7 -- CODE93, 8 -- CODE128
                        // Barcode height, the value ranges from 1 to 16, if it exceeds the range, the default value is 6, and each unit represents 24 pixels high.
                        // Barcode width, the value is 1 to 16, the default value is 12 outside the range, each unit represents the length of 24 pixels
                        // Text position 0--do not print text, 1--text above the barcode, 2--text below the barcode, 3--print both above and below the barcode
                        case "printBarCode":
                            sendBluetoothCommand(
                                ESCUtil.byteMerger(new byte[][] {
                                        ESCUtil.alignMode((byte) ((int) arguments.get("alignment"))),
                                        ESCUtil.setBarcodeHeight(((int) arguments.get("imageSize") / 2)),
                                        ESCUtil.setBarcodeWidth((int) arguments.get("imageSize")),
                                        ESCUtil.barcodePrint(),
                                        ESCUtil.barcodeData(0, (String) arguments.get("barcodeString")),
                                        ESCUtil.nextLines(1)
                                    }
                                ), result
                            );
                            break;

                        //Print QR Code
                        // QR code module size (unit: point, value from 1 to 16 ), the default value is 10 if it exceeds the setting range
                        // Two-dimensional error correction level (0:L 1:M 2:Q 3:H)
                        case "printQRCode":
                            sendBluetoothCommand(ESCUtil.byteMerger(new byte[][] {
                                    ESCUtil.alignMode((byte) ((int) arguments.get("alignment"))),
                                    ESCUtil.setQRsize((int) arguments.get("moduleSize")),
                                    ESCUtil.setQRCorrectionLevel((int) arguments.get("errorCorrectionLevel")),
                                    ESCUtil.cacheQRData(arguments.get("StringData").toString().getBytes("cp1258")),
                                    ESCUtil.nextLines(1)
                            }), result);
                            break;

                        // Print Raw Data
    //                    case "printRawData":
    //                        iPosPrinterService.printRawData(call.argument
    //                                ("byteData"), callback);
    //                        break;

                        // Printing with ESC/POS commands
                        case "sendUserCMDData":
                            sendBluetoothCommand(
                                (byte[])arguments.get("byteData"), result
                            );
                            break;

                        // Execute printing After executing each printing function method
                        // this method needs to be executed before the printer can execute printing
                        // before this method is executed, the printer status needs to be judged.
                        // When the printer is in PRINTER_NORMAL, this method is valid, otherwise it will not be executed.
                        case "printerPerformPrint":
                            sendBluetoothCommand(
                                ESCUtil.performPrintAndFeedPaper((byte) 160), result
                            );
                            break;

                        // Print Data Function
                        case "printData":
                            printData(call.argument("printData"), result);
                            break;

                            // Run Test Printer Functions
                        case "testPrint":
                            sendBluetoothCommand(ESCUtil.byteMerger(new byte[][] {
                                //Align Left
                                ESCUtil.alignMode((byte) 0),

                                // Set font to 16 and print text
                                ESCUtil.fontSizeSet((byte) 0x00),
                                "Hello People Font Size 16".getBytes("cp1258"),
                                ESCUtil.nextLines(1),

                                // Set font to 24 and print text
                                ESCUtil.fontSizeSet((byte) 0x11),
                                "Hello People Font size 24".getBytes("cp1258"),
                                ESCUtil.nextLines(1),

                                // Set font to 32 and print text
                                ESCUtil.fontSizeSet((byte) 0x22),
                                "Hello People Font Size 32".getBytes("cp1258"),
                                ESCUtil.nextLines(1),

                                // Set font to 48 and print text
                                ESCUtil.fontSizeSet((byte) 0x33),
                                "Hello People Font Size 48".getBytes("cp1258"),
                                ESCUtil.nextLines(1),

                                // Print Bitmap


                                // Print Bar Code
                                ESCUtil.barcodePrint(),
                                ESCUtil.barcodeData(0, "1234567890"),

                                //Print QR Code with smallest module size
                                ESCUtil.setQRsize(1),
                                ESCUtil.setQRCorrectionLevel(0),
                                ESCUtil.cacheQRData("IPOSPrinter Plugin".getBytes("cp1258")),

                                // Print QR Code with largest module size
                                ESCUtil.setQRsize(16),
                                ESCUtil.setQRCorrectionLevel(1),
                                ESCUtil.cacheQRData("IPOSPrinter Plugin".getBytes("cp1258"))
                            }), result);
                            break;

                        default:
                            result.notImplemented();
                    }
                } catch (Exception exception) {
                    Log.e(TAG, exception.getMessage());
                    result.error("ERROR", "Error performing print operation", exception.getMessage());
                }
            }
        });
    }

    public void close()
    {
        Log.i(TAG, "detach");
        context = null;
        activityBinding = null;
        channel.setMethodCallHandler(null);
        channel = null;
        stateChannel.setStreamHandler(null);
        stateChannel = null;
        mBluetoothManager = null;
        if(mBluetoothAdapter != null)
            mBluetoothAdapter = null;
        if(mBluetoothPrinterDevice != null)
            mBluetoothPrinterDevice = null;
        try {
            if (socket != null && (socket.isConnected())) {
                socket.close();
                socket = null;
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        application = null;
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        io.flutter.Log.i(TAG, "Attaching Plugin to Engine");
        pluginBinding = binding;
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        io.flutter.Log.i(TAG, "Detaching Plugin from Engine");
        pluginBinding = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        io.flutter.Log.i(TAG, "Attaching Plugin to Activity");
        activityBinding = binding;
        setup(
            pluginBinding.getBinaryMessenger(),
            (Application) pluginBinding.getApplicationContext(),
            activityBinding.getActivity(),
            null,
            activityBinding
        );
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        io.flutter.Log.i(TAG, "Detaching Plugin from Activity");
        close();
    }
}

