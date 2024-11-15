package co.eivo.brother_printer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

public class UsbBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            Toast.makeText(context, "USB Device Attached: " + device.getDeviceName(), Toast.LENGTH_SHORT).show();
            // Handle device attached
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            Toast.makeText(context, "USB Device Detached", Toast.LENGTH_SHORT).show();
            // Handle device detached
        }
    }
}