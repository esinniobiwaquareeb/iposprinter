import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter/services.dart';
import 'package:iposprinter/iposprinter.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  Widget build(BuildContext context) {
    return const MaterialApp(home: HomeWidget());
  }
}

class HomeWidget extends StatefulWidget {
  const HomeWidget({Key? key}) : super(key: key);

  @override
  _HomeWidgetState createState() => _HomeWidgetState();
}

class _HomeWidgetState extends State<HomeWidget> {
  late IPOSPrinter posPrinter;
  bool isConnected = false;
  bool canPrint = false;
  late Uint8List imageData;

  @override
  void initState() {
    super.initState();
    posPrinter = IPOSPrinter.instance;
    initPlatformState();
  }

  Future<Uint8List> getImageFileFromAssets() async {
    ByteData bytes = await rootBundle.load("assets/logo.png");
    return await bytes.buffer
        .asUint8List(bytes.offsetInBytes, bytes.lengthInBytes);
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    bool state = await posPrinter.initPrinter();
    print("Init Status: $state");
    if (state) {
      posPrinter.onRead().listen((event) {
        print("Reed Event: $event");
      });
      posPrinter.onStateChanged().listen((event) {
        switch (event) {
          case PrinterStatus.PRINTER_BLUETOOTH_ON:
            print("bluetooth device state: on");
            setState(() {
              isConnected = true;
            });
            break;
          case PrinterStatus.PRINTER_BLUETOOTH_CONNECTED:
            print("bluetooth device state: connected");
            setState(() {
              isConnected = true;
            });
            break;
          case PrinterStatus.PRINTER_BLUETOOTH_DISCONNECTED:
            print("bluetooth device state: disconnected");
            setState(() {
              isConnected = false;
            });
            break;
          case PrinterStatus.PRINTER_BLUETOOTH_DISCONNECTING:
            print("bluetooth device state: disconnecting");
            setState(() {
              isConnected = false;
            });
            break;
          case PrinterStatus.PRINTER_BLUETOOTH_OFF:
            print("bluetooth device state: off");
            setState(() {
              isConnected = false;
            });
            break;
          case PrinterStatus.PRINTER_BLUETOOTH_TURNING_OFF:
            print("bluetooth device state: turning off");
            setState(() {
              isConnected = false;
            });
            break;
          case PrinterStatus.PRINTER_PAPER_EXISTS:
            print("Paper present in the POS Printer");
            setState(() {
              isConnected = true;
            });
            break;
          case PrinterStatus.PRINTER_PAPER_LESS:
            print("No paper in the POS Printer");
            setState(() {
              isConnected = false;
            });
            break;
          case PrinterStatus.PRINTER_IS_BUSY:
            print("POS Printer is busy");
            setState(() {
              isConnected = false;
            });
            break;
          case PrinterStatus.PRINTER_NORMAL:
            print("POS Printer is normal");
            setState(() {
              isConnected = true;
            });
            break;
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('POS Printer Demo'),
      ),
      body: Center(
        child: TextButton(
            onPressed: (isConnected)
                ? () async {
                    imageData = await getImageFileFromAssets();
                    try {
                      var status = await posPrinter.printerStatus();
                      print("Printer Status: $status");
                      await posPrinter.printStyledFormatText(
                          "\u20A6 Welcome to Printer Test",
                          fontSize: 16,
                          align: 0);
                      await posPrinter.printStyledFormatText(
                          "\u20A6 Welcome to Printer Test",
                          fontSize: 24,
                          align: 1);
                      await posPrinter.printStyledFormatText(
                          "\u20A6 Welcome to Printer Test",
                          fontSize: 32,
                          align: 2);
                      await posPrinter.printStyledFormatText(
                          "\u20A6 Welcome to Printer Test",
                          fontSize: 48,
                          align: 0);
                      await posPrinter.printBitmap(imageData);
                      await posPrinter.printQRCode("Printer Test");
                      await posPrinter.printLineDemarcation();
                      // }
                    } catch (error) {
                      print(error.toString());
                      if (error is PlatformException) {
                        showModalBottomSheet(
                            context: context,
                            backgroundColor: Colors.grey[900],
                            builder: (context) {
                              return Wrap(
                                children: [
                                  Padding(
                                      padding: const EdgeInsets.symmetric(
                                          horizontal: 16, vertical: 8),
                                      child: Row(
                                        children: [
                                          Expanded(
                                              child: Text(
                                            error.message ??
                                                "Error from printer",
                                            style: Theme.of(context)
                                                .textTheme
                                                .bodyText2!
                                                .copyWith(color: Colors.white),
                                          )),
                                          TextButton(
                                              onPressed: () {
                                                Navigator.pop(context);
                                                posPrinter.initPrinter();
                                              },
                                              child: const Text("Retry"))
                                        ],
                                      ))
                                ],
                              );
                            });
                      } else {
                        print("Error: ${error.toString()}");
                        showModalBottomSheet(
                            context: context,
                            backgroundColor: Colors.grey[900],
                            builder: (context) {
                              return Wrap(
                                children: [
                                  Padding(
                                      padding: const EdgeInsets.symmetric(
                                          horizontal: 16, vertical: 8),
                                      child: Row(
                                        children: [
                                          Expanded(
                                              child: Text(
                                            "Error Printing Data",
                                            style: Theme.of(context)
                                                .textTheme
                                                .bodyText2!
                                                .copyWith(color: Colors.white),
                                          )),
                                          TextButton(
                                              onPressed: () {
                                                Navigator.pop(context);
                                              },
                                              child: const Text("Retry"))
                                        ],
                                      ))
                                ],
                              );
                            });
                      }
                    }
                  }
                : null,
            child: const OutlinedButton(onPressed: null, child: Text("Test Print"))
        ),
      ),
    );
  }
}
