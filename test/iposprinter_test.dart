import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:iposprinter/iposprinter.dart';

void main() {
  const MethodChannel channel = MethodChannel('iposprinter');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPrinterStatus', () async {
    expect(await IPOSPrinter.instance.printerStatus(), 1);
  });
}
