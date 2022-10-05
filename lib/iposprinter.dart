import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/services.dart';

class PrinterStatus {
  static const int PRINTER_NORMAL = 0;
  static const int PRINTER_IS_BUSY = 1;
  static const int PRINTER_PAPER_LESS = 2;
  static const int PRINTER_PAPER_EXISTS = 3;
  static const int PRINTER_THP_HIGH_TEMPERATURE = 4;
  static const int PRINTER_THP_TEMPERATURE_NORMAL = 5;
  static const int PRINTER_MOTOR_HIGH_TEMPERATURE = 6;
  static const int PRINTER_MOTOR_TEMPERATURE_NORMAL = 7;
  static const int PRINTER_CURRENT_TASK_PRINT_COMPLETE = 8;
  static const int PRINTER_ERROR_UNKNOWN = 9;
  static const int PRINTER_BLUETOOTH_TURNING_ON = 10;
  static const int PRINTER_BLUETOOTH_TURNING_OFF = 11;
  static const int PRINTER_BLUETOOTH_ON = 12;
  static const int PRINTER_BLUETOOTH_OFF = 13;
  static const int PRINTER_BLUETOOTH_CONNECTING = 14;
  static const int PRINTER_BLUETOOTH_DISCONNECTING = 15;
  static const int PRINTER_BLUETOOTH_CONNECTED = 16;
  static const int PRINTER_BLUETOOTH_DISCONNECTED = 17;
  static const int PRINTER_BLUETOOTH_ERROR = 18;
}

// * Printer status query
// * 0: PRINTER_NORMAL
//      You can start a new print at this time
// * 1: PRINTER_IS_BUSY
//      The printer is printing at this time
// * 2: PRINTER_PAPERLESS
//      Stop printing at this time, if the current printing is not completed,
//      you need to reprint after adding paper
// * 3: PRINTER_PAPER_EXISTS
//      There is Paper so continue printing at this time.
// * 4: PRINTER_THP_HIGH_TEMPERATURE
//      Pause printing at this time, if the current printing is not completed,
//      it will continue to print after cooling down, no need to reprint
// * 5: PRINTER_THP_TEMPERATURE_NORMAL
//      Continue to print after cooling down, no need to reprint
// * 6: PRINTER_MOTOR_HIGH_TEMPERATURE
//      Printing is not executed at this time. After cooling down,
//      the printer needs to be initialized and the printing task is re-initiated
// * 7: PRINTER_MOTOR_TEMPERATURE_NORMAL
//      Allow to cool down, after which
//      the printer needs to be initialized and the printing task is re-initiated
// * 8: PRINTER_CURRENT_TASK_PRINT_COMPLETE
//      Indicate the current print job is complete.
// * 9: PRINTER_ERROR_UNKNOWN
//      Printer abnormal
// * 10:PRINTER_BLUETOOTH_OFF,
//      POS Device Bluetooth off
// * 11:PRINTER_BLUETOOTH_TURNING_OFF,
//      Turning Off POS Device Bluetooth
// * 11:PRINTER_BLUETOOTH_TURNING_ON,
//      Turning On device Bluetooth
// * 12:PRINTER_BLUETOOTH_ON,
//      POS Device Bluetooth On

class IPOSPrinter {
  static const String namespace = "iposprinter_plugin";

  static const MethodChannel _channel =
      const MethodChannel('$namespace/methods');

  static const EventChannel _readChannel =
      const EventChannel('$namespace/read');

  static const EventChannel _stateChannel =
      const EventChannel('$namespace/state');

  final StreamController<MethodCall> _methodStreamController =
      StreamController.broadcast();

  Stream<MethodCall> get _methodStream => _methodStreamController.stream;

  IPOSPrinter._() {
    _channel.setMethodCallHandler((MethodCall call) async {
      _methodStreamController.add(call);
    });
  }

  static final IPOSPrinter _instance = IPOSPrinter._();

  static IPOSPrinter get instance => _instance;

  ///onStateChanged()
  Stream<int?> onStateChanged() async* {
    yield await _channel.invokeMethod('state').then((buffer) {
      print("State Val: $buffer");
      return buffer;
    });

    yield* _stateChannel.receiveBroadcastStream().map((buffer) {
      print("State Val: $buffer");
      return buffer;
    });
  }

  ///onRead()
  Stream<String> onRead() =>
      _readChannel.receiveBroadcastStream().map((buffer) => buffer.toString());

  Future<dynamic> initPrinter() async {
    var status = await _channel.invokeMethod('printerInit');
    return status;
  }

  Future<dynamic> printerStatus() async {
    int statusCode = await _channel.invokeMethod('printerStatus');
    print("Status Code: $statusCode");
    switch (statusCode) {
      case 0:
        return PrinterStatus.PRINTER_NORMAL;
      case 1:
        return PrinterStatus.PRINTER_IS_BUSY;
      case 2:
        return PrinterStatus.PRINTER_PAPER_LESS;
      case 3:
        return PrinterStatus.PRINTER_PAPER_EXISTS;
      case 4:
        return PrinterStatus.PRINTER_THP_HIGH_TEMPERATURE;
      case 5:
        return PrinterStatus.PRINTER_THP_TEMPERATURE_NORMAL;
      case 6:
        return PrinterStatus.PRINTER_MOTOR_HIGH_TEMPERATURE;
      case 7:
        return PrinterStatus.PRINTER_MOTOR_TEMPERATURE_NORMAL;
      case 8:
        return PrinterStatus.PRINTER_CURRENT_TASK_PRINT_COMPLETE;
      case 9:
        return PrinterStatus.PRINTER_ERROR_UNKNOWN;
      case 10:
        return PrinterStatus.PRINTER_BLUETOOTH_TURNING_ON;
      case 11:
        return PrinterStatus.PRINTER_BLUETOOTH_TURNING_OFF;
      case 12:
        return PrinterStatus.PRINTER_BLUETOOTH_ON;
      case 13:
        return PrinterStatus.PRINTER_BLUETOOTH_OFF;
      case 14:
        return PrinterStatus.PRINTER_BLUETOOTH_CONNECTING;
      case 15:
        return PrinterStatus.PRINTER_BLUETOOTH_DISCONNECTING;
      case 16:
        return PrinterStatus.PRINTER_BLUETOOTH_CONNECTED;
      case 17:
        return PrinterStatus.PRINTER_BLUETOOTH_DISCONNECTED;
      case 18:
        return PrinterStatus.PRINTER_BLUETOOTH_ERROR;
      default:
        return PrinterStatus.PRINTER_ERROR_UNKNOWN;
    }
  }

  Future<dynamic> printBlankLine({int lineHeight: 1}) async {
    await _channel.invokeMethod('printBlankLines', {
      'lines': 1,
      'lineHeight': lineHeight,
    });
  }

  Future<dynamic> printBlankLines(int linesNumber, {int lineHeight: 1}) async {
    await _channel.invokeMethod('printBlankLines', {
      'lines': linesNumber,
      'lineHeight': lineHeight,
    });
  }

  Future<dynamic> printFeedLines(int linesNumber) async {
    await _channel.invokeMethod('printFeedLines', {
      'lines': linesNumber,
    });
  }

  Future<dynamic> printLineDemarcation({int linesNumber: 32}) async {
    await _channel.invokeMethod('printLineDemarcation', {
      'lines': linesNumber,
    });
  }

  Future<dynamic> printText(String text) async => await _channel.invokeMethod(
        'printText',
        text,
      );

  Future<dynamic> printStyledText(String text,
          {String font: 'ST', int fontSize: 16}) async =>
      await _channel.invokeMethod('printSpecifiedTypeText', {
        'text': text,
        'font': font,
        'fontSize': fontSize,
      });

  Future<dynamic> printStyledFormatText(String text,
          {String font: 'ST', int fontSize: 16, int align: 0}) async =>
      await _channel.invokeMethod('printSpecFormatText', {
        'text': text,
        'font': font,
        'fontSize': fontSize,
        'alignment': align,
      });

  Future<dynamic> printColumnsText(List<String> textArray, List<int> widthArray,
          List<int> alignmentArray,
          {bool isContinuousPrint: false}) async =>
      await _channel.invokeMethod('printColumnsText', {
        'colsTextArray': textArray,
        'colsWidthArray': widthArray,
        'colsAlignmentArray': alignmentArray,
        'isContinuousPrint': (isContinuousPrint) ? 1 : 0
      });

  // bmpMode is between 0, 1, 32, 33
  // alignment is between 0, 1, 2
  Future<dynamic> printBitmap(Uint8List imageData,
          {int imageSize: 32, int alignment: 1, int bmpMode: 0}) async =>
      await _channel.invokeMethod('printBitmap', {
        'imageBytesArray': imageData,
        'imageSize': imageSize,
        'alignment': alignment,
        'bmpMode': bmpMode
      });

  // Size is between 1 - 16
  Future<dynamic> printBarcode(String barcodeString,
          {int imageSize: 12, int alignment: 1}) async =>
      await _channel.invokeMethod('printBarCode', {
        'barcodeString': barcodeString,
        'imageSize': (imageSize > 16)
            ? 16
            : (imageSize < 1)
                ? 1
                : imageSize,
        'alignment': alignment
      });

  // Image Size is a value between 1 - 16 each represents 24px
  Future<dynamic> printQRCode(String qrCodeString,
          {int imageSize: 10,
          int errorCorrectionLevel: 1,
          int alignment: 1}) async =>
      await _channel.invokeMethod('printQRCode', {
        'StringData': qrCodeString,
        'moduleSize': (imageSize > 16)
            ? 16
            : (imageSize < 1)
                ? 1
                : imageSize,
        "errorCorrectionLevel": errorCorrectionLevel,
        'alignment': alignment,
      });

  Future<dynamic> testPrint() async => await _channel.invokeMethod('testPrint');

  Future<dynamic> printData(List<Map<String, dynamic>> data) async =>
      await _channel.invokeMethod('printData', {"printData": data});
}

class IPOSPrintJob {
  late IPOSPrinter printer;
  bool isConnected = false;
  bool canPrint = false;

  String? bluetoothError;
  String? printerError;

  IPOSPrintJob() {
    printer = IPOSPrinter.instance;
  }

  Future<bool> initialisePrinter() async {
    bool status = await printer.initPrinter();
    print("Init Status: $status");
    printer.onStateChanged().listen((status) {
      switch (status) {
        case PrinterStatus.PRINTER_BLUETOOTH_ON:
          print("bluetooth device state: on");
          isConnected = true;
          break;
        case PrinterStatus.PRINTER_BLUETOOTH_CONNECTED:
          print("bluetooth device state: connected");
          isConnected = true;
          break;
        case PrinterStatus.PRINTER_BLUETOOTH_DISCONNECTED:
          print("bluetooth device state: disconnected");
          isConnected = false;
          bluetoothError = "Printer Bluetooth device is disconnected";
          break;
        case PrinterStatus.PRINTER_BLUETOOTH_DISCONNECTING:
          print("bluetooth device state: disconnecting");
          isConnected = false;
          bluetoothError = "Printer Bluetooth device is disconnected";
          break;
        case PrinterStatus.PRINTER_BLUETOOTH_OFF:
          print("bluetooth device state: off");
          isConnected = false;
          bluetoothError =
              "Device Bluetooth is switched off. Please turn it on";
          break;
        case PrinterStatus.PRINTER_BLUETOOTH_TURNING_OFF:
          print("bluetooth device state: turning off");
          isConnected = false;
          bluetoothError =
              "Device Bluetooth is switched off. Please turn it on";
          break;
        case PrinterStatus.PRINTER_PAPER_EXISTS:
          print("Paper present in the POS Printer");
          canPrint = true;
          break;
        case PrinterStatus.PRINTER_PAPER_LESS:
          print("No paper in the POS Printer");
          canPrint = false;
          printerError = "POS Printer is out of paper";
          break;
        case PrinterStatus.PRINTER_IS_BUSY:
          print("POS Printer is busy");
          canPrint = false;
          printerError = "POS Printer is currently printing a job";
          break;
        case PrinterStatus.PRINTER_NORMAL:
          print("POS Printer is normal");
          canPrint = true;
          break;
        case PrinterStatus.PRINTER_ERROR_UNKNOWN:
          print("POS Printer is normal");
          canPrint = false;
          printerError = "POS Printer is inactive. Try again";
          break;
        case PrinterStatus.PRINTER_THP_HIGH_TEMPERATURE:
          print("POS Printer has high temperature");
          canPrint = false;
          printerError =
              "POS Printer has temperature. Please allow to cool off.";
          break;
        case PrinterStatus.PRINTER_MOTOR_HIGH_TEMPERATURE:
          print(
              "POS Printer temperature is high. Please wait for 1 minute to cool off");
          canPrint = false;
          printerError =
              "POS Printer has temperature. Please allow to cool off.";
          break;
        case PrinterStatus.PRINTER_CURRENT_TASK_PRINT_COMPLETE:
          print("POS Printer has finished priting the job");
          canPrint = true;
          break;
      }
    });
    return status;
  }
}
