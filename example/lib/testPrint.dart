

import 'package:iposprinter/iposprinter.dart';

class TestPrint {
  IPOSPrinter posPrinter = IPOSPrinter.instance;

  sample() async {
    //SIZE
    // 0- normal size text
    // 1- only bold text
    // 2- bold with medium text
    // 3- bold with large text
    //ALIGN
    // 0- ESC_ALIGN_LEFT
    // 1- ESC_ALIGN_CENTER
    // 2- ESC_ALIGN_RIGHT

//     var response = await http.get("IMAGE_URL");
//     Uint8List bytes = response.bodyBytes;
    posPrinter.initPrinter().then((statusValue) {
      if (statusValue == 1) {
        posPrinter.printBlankLine();
        posPrinter.printStyledText("HEADER");
        posPrinter.printStyledFormatText("FORMATTED HEADER", fontSize: 32, align: 0);
        // posPrinter.printBitmap(pathImage); //path of your image/logo
        posPrinter.printBlankLines(5); //Five Blank Lines
//      bluetooth.printImageBytes(bytes.buffer.asUint8List(bytes.offsetInBytes, bytes.lengthInBytes));
        posPrinter.printColumnsText(["LEFT", "RIGHT"], [1,1], [0,2]);
        posPrinter.printColumnsText(["LEFT", "MIDDLE", "RIGHT"], [1, 1, 1], [0, 1, 2]);
        posPrinter.printBlankLine();
        posPrinter.printBlankLines(2);
      }
    });
  }
}