package co.eivo.brother_printer;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Map;

import com.brother.ptouch.sdk.Printer;
import com.brother.ptouch.sdk.PrinterInfo;


import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;

/** BrotherPrinterPlugin */
public class BrotherPrinterPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;
  private Context context;
  private Activity activity;
  private UsbManager usbManager;
  private UsbDevice usbDevice;
  private static final String ACTION_USB_PERMISSION= "co.eivo.brother_printer.USB_PERMISSION";
  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "brother_printer");
    channel.setMethodCallHandler(this);
    context = flutterPluginBinding.getApplicationContext();
    usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    checkAndRequestPermission();
  }

  @Override
  public void onDetachedFromActivity() {
    
  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
    if(activity==null){
      activity = binding.getActivity();
    }
    Log.d("Activity=>>>", "onReattachedToActivityForConfigChanges");
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activity = binding.getActivity();
    Log.d("Activity=>>>", "onAttachedToActivity");

  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    activity = null;
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("getUsbDevice")) {
      
      String usbDevice = getUsbDevice();
      if (usbDevice != null) {
          result.success(usbDevice);
      } else {
          result.error("UNAVAILABLE", "USB device not found", null);
      }
    }else if (call.method.equals("searchDevices")) {
      
      searchDevices(call, result);
    }
    else if (call.method.equals("printPDF")) {
      
      printPDF(call, result);
    }
    else {
      result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }
  public String getUsbDevice() {
        // Initialize the Printer object
        Printer printer = new Printer();

        // Get the connected USB device using Brother SDK
        UsbDevice usbDevice = printer.getUsbDevice(usbManager);

        if (usbDevice != null) {
            // USB device found
            return usbDevice.getDeviceName();
        } else {
            // No USB device connected
            return null;
        }
    }
  public void searchDevices(@NonNull MethodCall call, @NonNull final Result result) {
    int delay = call.argument("delay");
    ArrayList<String> printerNames = call.argument("printerNames");

    PrinterDiscovery.getInstance().start(delay, printerNames, new BRPrinterDiscoveryCompletion(){
      public void completion(final ArrayList<Map<String, String>> devices, final Exception exception) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
          @Override
          public void run() {
            if (exception != null) {
              if (exception instanceof PrinterErrorException) {
                PrinterErrorException castException = (PrinterErrorException)exception;
                result.error(castException.code.toString(), castException.getMessage(), null);
              }
              else {
                result.error("unknown", exception.getMessage(), null);
              }
            } else {
              result.success(devices);
            }
          }
        });
      }
    });
  }

  public void printPDF(@NonNull MethodCall call, @NonNull final Result result) {
    String path = call.argument("path");
    int copies = call.argument("copies");
    int modelCode = call.argument("modelCode");
    String ipAddress = call.argument("ipAddress");
    String macAddress = call.argument("macAddress");
    String bleAdvertiseLocalName = call.argument("bleAdvertiseLocalName");
    String paperSettingsPath = call.argument("paperSettingsPath");
    String labelSize = call.argument("labelSize");

    PrinterSession session = new PrinterSession();
    session.print(activity, context, modelCode, path, copies, ipAddress, macAddress, bleAdvertiseLocalName, paperSettingsPath, labelSize, new BRPrinterSessionCompletion(){
      public void completion(final Exception exception) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
          @Override
          public void run() {
            if (exception != null) {
              if (exception instanceof PrinterErrorException) {
                PrinterErrorException castException = (PrinterErrorException)exception;
                result.error(castException.code.toString(), castException.getMessage(), null);
              }
              else {
                result.error(exception.getMessage(), exception.getMessage(), null);
              }
            } else {
              result.success(null);
            }
          }
        });
      }
    });
  }

  private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // Permission granted, proceed with USB setup
                            connectToUsbDevice(device);
                        }
                    } else {
                        Log.d("BrotherPrinterPlugin", "USB permission denied");
                    }
                }
            }
        }
    };

    public void checkAndRequestPermission() {
        usbDevice = getUSBDevice();
        if (usbDevice == null) {
            Log.d("BrotherPrinterPlugin", "No USB device found");
            return;
        }

        if (usbManager.hasPermission(usbDevice)) {
            // Permission already granted, proceed with USB setup
            connectToUsbDevice(usbDevice);
        } else {
            // Request permission
            PendingIntent permissionIntent = PendingIntent.getBroadcast(activity, 0,
                    new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
            activity.registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
            usbManager.requestPermission(usbDevice, permissionIntent);
        }
    }

    private UsbDevice getUSBDevice() {
        // Use Brother SDK or other method to retrieve the connected USB device
        // Assuming `printer.getUsbDevice(usbManager)` retrieves the device
        Printer printer = new Printer();
        return printer.getUsbDevice(usbManager);
    }

    private void connectToUsbDevice(UsbDevice device) {
        // Add your code to connect and start using the USB device
        Log.d("BrotherPrinterPlugin", "Connected to USB device: " + device.getDeviceName());
    }
}
