package co.eivo.brother_printer;

import static androidx.core.content.ContextCompat.getSystemService;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.brother.ptouch.sdk.Printer;
import com.brother.ptouch.sdk.BLEPrinter;
import com.brother.ptouch.sdk.NetPrinter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

interface BRPrinterDiscoveryCompletion {
    public void completion(ArrayList<Map<String, String>> devices, Exception exception);
}

class PrinterDiscovery {

    private BRPrinterDiscoveryCompletion _completionHandler;
    private boolean _isSearchingWifi = false;
    private boolean _isSearchingUsb = false;
    private boolean _isSearchingBLE = false;
    private ArrayList<Map<String, String>> _results = new ArrayList<Map<String, String>>();

    private PrinterDiscovery()
    {}

    private static PrinterDiscovery instance = null;

    public static PrinterDiscovery getInstance()
    {
        if (instance == null)
        {
            instance = new PrinterDiscovery();
        }
        return instance;
    }

    public void start(int delay, ArrayList<String> printerNames, BRPrinterDiscoveryCompletion completion) {
        if (_completionHandler != null) {
            completion.completion(null, new Exception("Already in progress"));
            return;
        }

        _completionHandler = completion;
        _results.clear();

        // TODO require permission for new version
        if (android.os.Build.VERSION.SDK_INT <= 30) {
            searchBLEPrinter(delay);
        }

        searchWiFiPrinter(printerNames);
        // searchUsbPrinter(context);
    }


    void searchWiFiPrinter(final ArrayList<String> printerNames) {
        _isSearchingWifi = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                Printer service = new Printer();

                NetPrinter[] printerList = service.getNetPrinters(printerNames.toArray(new String[0]));
                for (final NetPrinter printer: printerList) {
                    _results.add(new HashMap<String, String>() {{
                                     put("source", "network");
                                     put("ipAddress", printer.ipAddress);
                                     put("location", printer.location);
                                     put("modelName", printer.modelName);
                                     put("printerName", null);
                                     put("serialNumber", null);
                                     put("nodeName", printer.nodeName);
                                     put("macAddress", printer.macAddress);
                                     put("bleAdvertiseLocalName", null);
                                 }}
                    );
                }

                _isSearchingWifi = false;
                if (!_isSearchingBLE) {
                    _completionHandler.completion(_results, null);
                    _completionHandler = null;
                }
            }
        }).start();
    }

    // void searchUsbPrinter(final Context context) {
    //     new Thread(new Runnable() {
    //         @Override
    //         public void run() {
    //             UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

    //             // Get the list of connected USB devices
    //             Map<String, UsbDevice> usbDevices = usbManager.getDeviceList();

    //             for (UsbDevice device : usbDevices.values()) {
    //                 // Populate only the requested fields
    //                 _results.add(new HashMap<String, String>() {{
    //                     put("source", "usb");
    //                     put("ipAddress", null);
    //                     put("location", null);
    //                     put("modelName", null);
    //                     put("printerName", null);
    //                     put("serialNumber", null);
    //                     put("nodeName", null);
    //                     put("macAddress", null);
    //                     put("bleAdvertiseLocalName", null);
    //                 }});
    //             }

    //             // Call completion handler if needed
    //             if (_completionHandler != null) {
    //                 _completionHandler.completion(_results, null);
    //             }
    //         }
    //     }).start();
    // }
    void searchBLEPrinter(final int delay) {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            _isSearchingBLE = false;
            return;
        }

        _isSearchingBLE = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                Printer service = new Printer();
                List<BLEPrinter> printerList = service.getBLEPrinters(bluetoothAdapter, delay);
                for (final BLEPrinter printer: printerList) {
                    _results.add(new HashMap<String, String>() {{
                                     put("source", "ble");
                                     put("ipAddress", null);
                                     put("location", null);
                                     put("modelName", null);
                                     put("printerName", null);
                                     put("serialNumber", null);
                                     put("nodeName", null);
                                     put("macAddress", null);
                                     put("bleAdvertiseLocalName", printer.localName);
                                 }}
                    );
                }

                // Add paired devices but don't know if it's a Brother printer
                // Will be filtered in Dart code when trying to find the model
                {
                    BluetoothDevice[] devices = bluetoothAdapter.getBondedDevices().toArray(new BluetoothDevice[0]);
                    for (final BluetoothDevice device: devices) {
                        _results.add(new HashMap<String, String>() {{
                            put("source", "bluetooth");
                            put("ipAddress", null);
                            put("location", null);
                            put("modelName", null);
                            put("printerName", device.getName());
                            put("serialNumber", null);
                            put("nodeName", null);
                            put("macAddress", device.getAddress());
                            put("bleAdvertiseLocalName", null);
                        }});
                    }
                }

                _isSearchingBLE = false;
                if (!_isSearchingWifi) {
                    _completionHandler.completion(_results, null);
                    _completionHandler = null;
                }
            }
        }).start();
    }
}
