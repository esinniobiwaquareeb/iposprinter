package com.iposprinter_plugin;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;

import androidx.annotation.NonNull;

import com.iposprinter_plugin.iposprinterservice.IPosPrinterCallback;
import com.iposprinter_plugin.iposprinterservice.IPosPrinterService;
import com.iposprinter_plugin.iposprinterservice.ThreadPoolManager;

import java.util.List;
import java.util.Map;

import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.plugins.service.ServiceAware;
import io.flutter.embedding.engine.plugins.service.ServicePluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * PrintingPlugin
 */
public class IPosPrinterPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, ServiceAware {
  private static final String TAG = "IPOSPrinterPlugin";
  private static final String NAMESPACE = "iposprinter_plugin";

  private Context context;
  private Object initializationLock = new Object();

  private FlutterPluginBinding pluginBinding;
  private ActivityPluginBinding activityBinding;

  private Application application;
  private Activity activity;

  private MethodChannel channel;
  private EventChannel stateChannel;
  private EventChannel.EventSink statusSink;

  private ServiceConnection printerService;
  private com.iposprinter_plugin.iposprinterservice.IPosPrinterService iPosPrinterService;


  /*Define state broadcast*/
  private final String PRINTER_NORMAL_ACTION = "com.iposprinter.iposprinterservice.NORMAL_ACTION";
  private final String PRINTER_PAPERLESS_ACTION = "com.iposprinter.iposprinterservice.PAPERLESS_ACTION";
  private final String PRINTER_PAPEREXISTS_ACTION = "com.iposprinter.iposprinterservice.PAPEREXISTS_ACTION";
  private final String PRINTER_THP_HIGHTEMP_ACTION = "com.iposprinter.iposprinterservice.THP_HIGHTEMP_ACTION";
  private final String PRINTER_THP_NORMALTEMP_ACTION = "com.iposprinter.iposprinterservice.THP_NORMALTEMP_ACTION";
  private final String PRINTER_MOTOR_HIGHTEMP_ACTION = "com.iposprinter.iposprinterservice.MOTOR_HIGHTEMP_ACTION";
  private final String PRINTER_BUSY_ACTION = "com.iposprinter.iposprinterservice.BUSY_ACTION";
  private final String PRINTER_CURRENT_TASK_PRINT_COMPLETE_ACTION = "com.iposprinter.iposprinterservice.CURRENT_TASK_PRINT_COMPLETE_ACTION";
  private final String GET_CUST_PRINTAPP_PACKAGENAME_ACTION = "android.print.action.CUST_PRINTAPP_PACKAGENAME";

  /*Define Messages*/
  private final int MSG_TEST = 1;
  private final int MSG_IS_NORMAL = 2;
  private final int MSG_IS_BUSY = 3;
  private final int MSG_PAPER_LESS = 4;
  private final int MSG_PAPER_EXISTS = 5;
  private final int MSG_THP_HIGH_TEMP = 6;
  private final int MSG_THP_TEMP_NORMAL = 7;
  private final int MSG_MOTOR_HIGH_TEMP = 8;
  private final int MSG_MOTOR_HIGH_TEMP_INIT_PRINTER = 9;
  private final int MSG_CURRENT_TASK_PRINT_COMPLETE = 10;


  public static void registerWith(Registrar registrar) {
    final IPosPrinterPlugin instance = new IPosPrinterPlugin();
    //registrar.addRequestPermissionsResultListener(instance);
    Activity activity = registrar.activity();
    Application application = null;
    instance.setup(registrar.messenger(), application, activity, registrar, null);
  }

  public IPosPrinterPlugin() {
  }

  private void setup(final BinaryMessenger messenger,
                     final Application application,
                     final Activity activity,
                     final PluginRegistry.Registrar registrar,
                     final ActivityPluginBinding activityBinding) {
    synchronized (initializationLock) {
      Log.i(TAG, "setup");
      this.activity = activity;
      this.application = application;
      this.context = application;
      channel = new MethodChannel(messenger, NAMESPACE + "/methods");
      channel.setMethodCallHandler(this);
//      stateChannel = new EventChannel(messenger, NAMESPACE + "/state");
//      stateChannel.setStreamHandler(stateStreamHandler);
      initializeService();
    }
  }

//  private final StreamHandler stateStreamHandler = new StreamHandler() {
//    private final BroadcastReceiver IPosPrinterStatusListener = new BroadcastReceiver() {
//      @Override
//      public void onReceive(Context context, Intent intent) {
//        String action = intent.getAction();
//        if (action == null) {
//          Log.d(TAG, "IPosPrinterStatusListener onReceive action = null");
//          return;
//        }
//        Log.d(TAG, "IPosPrinterStatusListener action = " + action);
//        if (action.equals(PRINTER_NORMAL_ACTION)) {
//          statusSink.success(MSG_IS_NORMAL);
//        } else if (action.equals(PRINTER_PAPERLESS_ACTION)) {
//          statusSink.error("ERROR", "MSG_PAPER_LESS", null);
//        } else if (action.equals(PRINTER_BUSY_ACTION)) {
//          statusSink.error("ERROR", "MSG_IS_BUSY", null);
//        } else if (action.equals(PRINTER_PAPEREXISTS_ACTION)) {
//          statusSink.success(MSG_PAPER_EXISTS);
//        } else if (action.equals(PRINTER_THP_HIGHTEMP_ACTION)) {
//          statusSink.error("ERROR", "MSG_THP_HIGH_TEMP", null);
//        } else if (action.equals(PRINTER_THP_NORMALTEMP_ACTION)) {
//          statusSink.success(MSG_THP_TEMP_NORMAL);
//        } else if (action.equals(PRINTER_MOTOR_HIGHTEMP_ACTION)) {
//          //At this time, the current task will continue to print.
//          // After completing the current task, please wait for more than 2 minutes to continue the next printing task.
//          statusSink.error("ERROR", "MSG_MOTOR_HIGH_TEMP", null);
//        } else if (action.equals(PRINTER_CURRENT_TASK_PRINT_COMPLETE_ACTION)) {
//          statusSink.success(true);
//        } else if (action.equals(GET_CUST_PRINTAPP_PACKAGENAME_ACTION)) {
//          String mPackageName = null;
//          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.DONUT) {
//            mPackageName = intent.getPackage();
//          }
//          Log.d(TAG, "*******GET_CUST_PRINTAPP_PACKAGENAME_ACTION：" + action + "*****mPackageName:" + mPackageName);
//
//        } else {
//          statusSink.success("MSG_TEST");
//        }
//      }
//    };
//
//    @Override
//    public void onListen(Object o, EventChannel.EventSink eventSink) {
//      statusSink = eventSink;
//      IntentFilter printerStatusFilter = new IntentFilter();
//      printerStatusFilter.addAction(PRINTER_NORMAL_ACTION);
//      printerStatusFilter.addAction(PRINTER_PAPERLESS_ACTION);
//      printerStatusFilter.addAction(PRINTER_PAPEREXISTS_ACTION);
//      printerStatusFilter.addAction(PRINTER_THP_HIGHTEMP_ACTION);
//      printerStatusFilter.addAction(PRINTER_THP_NORMALTEMP_ACTION);
//      printerStatusFilter.addAction(PRINTER_MOTOR_HIGHTEMP_ACTION);
//      printerStatusFilter.addAction(PRINTER_BUSY_ACTION);
//      printerStatusFilter.addAction(GET_CUST_PRINTAPP_PACKAGENAME_ACTION);
//      context.registerReceiver(IPosPrinterStatusListener, printerStatusFilter);
//
//    }
//
//    @Override
//    public void onCancel(Object o) {
//      statusSink = null;
//      context.unregisterReceiver(IPosPrinterStatusListener);
//    }
//  };

  /**
   * Binding service instance
   */
  private ServiceConnection createPrinterServiceConnection() {
    Log.i(TAG, "Creating Service Connection" + "\n");
    return new ServiceConnection() {

      @Override
      public void onServiceConnected(ComponentName componentName, IBinder service) {
        Log.i(TAG, "Printer Service Started" + "\n");
        iPosPrinterService = IPosPrinterService.Stub.asInterface(service);
      }

      @Override
      public void onServiceDisconnected(ComponentName componentName) {
        Log.i(TAG, "Printer Service Stopped" + "\n");
        iPosPrinterService = null;
      }
    };
  }

  private void initializeService() {
    Log.i(TAG, "Initializing service" + "\n");
    printerService = createPrinterServiceConnection();
    try {
      Intent intent = new Intent();
      intent.setPackage("com.iposprinter.iposprinterservice");
      intent.setAction("com.iposprinter.iposprinterservice.IPosPrintService");
      boolean isBound = context.bindService(intent, printerService, Context.BIND_ADJUST_WITH_ACTIVITY | Context.BIND_AUTO_CREATE);
//    context.startService(intent);
      Log.i(TAG, "Initialised Service Intent: "+isBound);
    } catch (Exception exception) {
      Log.e(TAG, "Error during Initialisation: "+exception.getMessage());
    }
  }

  private void detach() {
    Log.i(TAG, "detach iposprinter plugin");
    context.unbindService(printerService);
    channel.setMethodCallHandler(null);
    channel = null;
    stateChannel.setStreamHandler(null);
    context = null;
    activityBinding = null;
    stateChannel = null;
    application = null;
  }

  @Override
  public void onMethodCall(MethodCall call, MethodChannel.Result result) {

    if (iPosPrinterService == null) {
      result.error("ERROR","POS Printer Service is not available", "Please " +
              "ensure you are using a compatible POS device");
      return;
    }

    Log.i(TAG, "Attempting to run task: "+call.method);
    ThreadPoolManager.getInstance().executeTask(new Runnable() {
      @Override
      public void run() {

        try {
          IPosPrinterCallback callback = new IPosPrinterCallback.Stub() {
            @Override
            public void onRunResult(final boolean isSuccess) {
              Log.i(TAG, "Running result from printAction");
//               if (!result.) {
                 if (isSuccess) {
                   result.success(true);
                 } else {
                   result.error("ERROR", null, null);
                 }
//               }
            }

            @Override
            public void onReturnString(final String value) {
              Log.i(TAG, "Returning result string from printAction: "+value);
              result.success(value);
            }
          };

          switch (call.method) {
            case "printerStatus":
              Log.i(TAG, "Get Printer Status");
              result.success(iPosPrinterService.getPrinterStatus());
              break;

            // Printer initialization The printer is powered on and initialized with default settings.
            // Please check the printer status when using it.
            // Please wait for PRINTER_IS_BUSY.
            case "printerInit":
              Log.i(TAG, "Initialise Printer");
              iPosPrinterService.printerInit(callback);
              break;

            // Set the print density of the printer,
            // which will affect subsequent printing unless initialized
            // Concentration level, range 1-10, out of range this function will not be executed Default level 6
            case "setPrinterPrintDepth":
              iPosPrinterService.setPrinterPrintDepth(call.arguments(), callback);
              break;

            // Set the print font type, which will affect subsequent printing unless initialized
            // (only one font ST is currently supported, and more fonts will be supported in the future)
            case "setPrinterPrintFontType":
              iPosPrinterService.setPrinterPrintFontType(call.arguments(), callback);
              break;

            // Setting the font size will have an impact on subsequent printing unless initialized.
            // Note: the font size is a printing method that exceeds the standard international
            // instructions.
            // Adjusting the font size will affect the character width, and the number of characters in
            // each line will also change accordingly.
            // The typesetting may be messy, you need to adjust it yourself
            case "setPrinterPrintFontSize":
              iPosPrinterService.setPrinterPrintFontSize(call.arguments(), callback);
              break;

            // Set the alignment, which will affect subsequent printing unless initialized
            // alignment 0--left, 1--center, 2--right, center by default
            case "setPrinterPrintAlignment":
              iPosPrinterService.setPrinterPrintAlignment(call.arguments(), callback);
              break;

            // The printer is feeding paper (forced line feed, after finishing the previous printing
            // content,
            // the paper feeds lines, the motor is idling to feed the paper, and no data is sent to the
            // printer)
            case "printerFeedLines":
              iPosPrinterService.printerFeedLines(call.arguments(), callback);
              break;

            // Print a blank line (force a newline, print a blank line after the end of the previous print
            // content,
            // and the data sent to the printer at this time is all 0x00)
            case "printBlankLines":
              Log.i(TAG, "Print Blank Lines with arguments: "+call.arguments());
              iPosPrinterService.printBlankLines(call.argument("lines"), call.argument
                      ("lineHeight"), callback);
              break;

            // Print Text
            // Print text Automatic line wrapping when the text width is full
            case "printText":
              iPosPrinterService.printText(call.arguments(), callback);
              break;

            // Print Type-faced Text
            // Print the specified font type and size text. The font setting is only valid for this time.
            // The text width is automatically wrapped and typed
            case "printSpecifiedTypeText":
              iPosPrinterService.printSpecifiedTypeText(call.argument("text"), call.argument
                      ("font"), call.argument("fontSize"), callback);
              break;

            // Print Formatted Text
            // Print the specified font type and size text. The font setting is only valid for this time.
            // The text width is automatically wrapped and typed
            case "printSpecFormatText":
              iPosPrinterService.PrintSpecFormatText(call.argument("text"), call.argument
                              ("font"), call.argument("fontSize"), call.argument("alignment"),
                      callback);
              break;

            // Print Column Text
            // Print a row of the table, you can specify the column width and alignment
            case "printColumnsText":
              iPosPrinterService.printColumnsText(call.argument
                      ("colsTextArray"), call.argument
                      ("colsWidthArray"), call.argument
                      ("colsAlignmentArray"), call.argument("isContinuousPrint"), callback);
              break;

            // Print a Bitmap Image
            // Alignment 0--left, 1--center, 2--right, center by default
            // Bitmap size, the incoming size range is 1~16, and the default selection is 10 if it exceeds the range. Unit: 24bit
            case "printBitmap":
              byte[] bytes = call.argument("imageBytesArray");
              iPosPrinterService.printBitmap(call.argument("alignment"), call.argument
                              ("imageSize"), BitmapFactory.decodeByteArray(bytes, 0, bytes.length),
                      callback);
              break;

            // Print Bar Code
            // Barcode Type 0 -- UPC-A, 1 -- UPC-E, 2 -- JAN13(EAN13), 3 -- JAN8(EAN8), 4 -- CODE39, 5 -- ITF, 6 -- CODABAR, 7 -- CODE93, 8 -- CODE128
            // Barcode height, the value ranges from 1 to 16, if it exceeds the range, the default value is 6, and each unit represents 24 pixels high.
            // Barcode width, the value is 1 to 16, the default value is 12 outside the range, each unit represents the length of 24 pixels
            // Text position 0--do not print text, 1--text above the barcode, 2--text below the barcode, 3--print both above and below the barcode
            case "printBarCode":
              iPosPrinterService.printBarCode(call.argument("StringData"), call.argument
                              ("symbology"), call.argument("height"), call.argument("width"),
                      call.argument("textPosition"), callback);
              break;

            //Print QR Code
            // QR code module size (unit: point, value from 1 to 16 ), the default value is 10 if it exceeds the setting range
            // Two-dimensional error correction level (0:L 1:M 2:Q 3:H)
            case "printQRCode":
              iPosPrinterService.printQRCode(call.argument("StringData"), call.argument
                      ("moduleSize"), call.argument("errorCorrectionLevel"), callback);
              break;

            // Print Raw Data
            case "printRawData":
              iPosPrinterService.printRawData(call.argument
                      ("byteData"), callback);
              break;

            // Printing with ESC/POS commands
            case "sendUserCMDData":
              iPosPrinterService.sendUserCMDData(call.argument
                      ("byteData"), callback);
              break;

            // Execute printing After executing each printing function method
            // this method needs to be executed before the printer can execute printing
            // before this method is executed, the printer status needs to be judged.
            // When the printer is in PRINTER_NORMAL, this method is valid, otherwise it will not be executed.
            case "printerPerformPrint":
              iPosPrinterService.printerPerformPrint(call.arguments(), callback);
              break;

            // Print Data Function
            case "printData":
              printData(call.argument("printData"), callback);

            // Run Test Printer Functions
            case "testPrint":
              iPosPrinterService.setPrinterPrintAlignment(0, callback);
              iPosPrinterService.printQRCode("http://www.baidu.com\n", 2, 1, callback);
              iPosPrinterService.printBlankLines(1, 15, callback);
              iPosPrinterService.setPrinterPrintAlignment(1, callback);
              iPosPrinterService.printQRCode("http://www.baidu.com\n", 3, 0, callback);


            default:
              result.notImplemented();
          }
        } catch (Exception exception) {
          Log.e(TAG, exception.getMessage());
          result.error("ERROR", exception.getMessage(), null);
        }
      }
    });
  }

//  private byte[] ArrayToByteArray(Array data) throws JSONException {
//    byte[] bytes = new byte[data.length()];
//    for (int i = 0; i < data.length(); i++) {
//      bytes[i] = (byte) (((int) data.get(i)) & 0xFF);
//    }
//    return bytes;
//  }
//
//  private String[] ArrayToStringArray(JSONArray data) throws JSONException {
//    String[] strings = new String[data.length()];
//    for(int i = 0; i < data.length(); i++) {
//      strings[i] = data.getString(i);
//    }
//    return strings;
//  }

//  private int[] ArrayToIntArray(JSONArray data) throws JSONException {
//    int[] numbers = new int[data.length()];
//    for(int i = 0; i < data.length(); i++) {
//      numbers[i] = data.getInt(i);
//    }
//    return numbers;
//  }

  public void printData(List<Map<String, Object>> data, IPosPrinterCallback callback) {
    try {
      for (Map<String, Object> line : data) {
        String type = (String) line.get("type");
        android.util.Log.i(TAG, type);
        if (type.equals("image")) {
          android.util.Log.i(TAG, "Printing Image");
//                            AssetManager assetManager = getAssets();
          Bitmap bt;
//                            InputStream imageFile = assetManager.open("logo2.jpg");
          byte [] imageBytes = (byte[]) line.get("image");
          bt = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
          iPosPrinterService.printBitmap(1, 12, bt, callback);
        } else {
          iPosPrinterService.printSpecifiedTypeText((String) line.get("text"), "ST", (Integer) line.get("size"), callback);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    Log.i(TAG, "Attaching Plugin to Engine");
    pluginBinding = binding;
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    Log.i(TAG, "Detaching Plugin from Engine");
    pluginBinding = null;
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    Log.i(TAG, "Attaching Plugin to Activity");
    activityBinding = binding;
    setup(
            pluginBinding.getBinaryMessenger(),
            (Application) pluginBinding.getApplicationContext(),
            activityBinding.getActivity(),
            null,
            activityBinding);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    Log.i(TAG, "Detaching Plugin from Activity");
    detach();
  }

  @Override
  public void onAttachedToService(@NonNull ServicePluginBinding binding) {
    Log.i(TAG, "Currently attached to Service: " + binding.getService().getPackageName() + "\n");
  }

  @Override
  public void onDetachedFromService() {
    Log.i(TAG, "Detached from Service" + "\n");
    iPosPrinterService = null;
  }
}