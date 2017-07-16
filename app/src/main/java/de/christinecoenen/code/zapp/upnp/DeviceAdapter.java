package de.christinecoenen.code.zapp.upnp;

import android.content.Context;
import android.widget.ArrayAdapter;

import org.fourthline.cling.support.model.TransportState;


class DeviceAdapter extends ArrayAdapter<RendererDevice> implements UpnpService.Listener {

	DeviceAdapter(Context context, int resource, UpnpService.Binder upnpRendererService) {
		super(context, resource, upnpRendererService.getDevices());
		upnpRendererService.addListener(this);
	}

	@Override
	public void onDeviceAdded(RendererDevice device) {
		notifyDataSetChanged();
	}

	@Override
	public void onDeviceRemoved(RendererDevice device) {
		notifyDataSetChanged();
	}

	@Override
	public void onDevicePlayStateChanged(RendererDevice device, TransportState transportState) {

	}
}
