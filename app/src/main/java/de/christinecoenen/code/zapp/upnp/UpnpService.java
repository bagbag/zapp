package de.christinecoenen.code.zapp.upnp;

import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.android.FixedAndroidLogHandler;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;
import org.fourthline.cling.support.model.TransportState;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.christinecoenen.code.zapp.model.ChannelModel;

public class UpnpService extends AndroidUpnpServiceImpl implements RegistryListener {

	private static final String TAG = UpnpService.class.getSimpleName();

	private final Binder binder = new Binder();
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final WeakHashMap<Listener, Void> listeners = new WeakHashMap<>();
	private final List<RendererDevice> devices = new ArrayList<>();

	@Override
	public void onCreate() {
		super.onCreate();

		// Fix the logging integration between java.util.logging and Android internal logging
		org.seamless.util.logging.LoggingUtil.resetRootHandler(
			new FixedAndroidLogHandler()
		);
		// Now you can enable logging as needed for various categories of Cling:
		Logger.getLogger("org.fourthline.cling").setLevel(Level.INFO);

		upnpService.getRegistry().addListener(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		listeners.clear();
		devices.clear();
		upnpService.getRegistry().removeListener(this);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
		Log.d(TAG, "discovery started");
	}

	@Override
	public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
		Log.d(TAG, "discovery failed: " + ex.getMessage());
	}

	@Override
	public void remoteDeviceAdded(Registry registry, final RemoteDevice device) {
		addDevice(device);
	}

	@Override
	public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
	}

	@Override
	public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
		removeDevice(device);
	}

	@Override
	public void localDeviceAdded(Registry registry, LocalDevice device) {
	}

	@Override
	public void localDeviceRemoved(Registry registry, LocalDevice device) {
	}

	@Override
	public void beforeShutdown(Registry registry) {
	}

	@Override
	public void afterShutdown() {
	}


	private void addDevice(final Device device) {
		final Service avTransportService = device.findService(new UDAServiceType("AVTransport"));
		if (avTransportService != null) {
			Log.d(TAG, "added renderer: " + device.getDetails().getFriendlyName());
			final RendererDevice rendererDevice = new RendererDevice(device);
			devices.add(rendererDevice);

			handler.post(new Runnable() {
				@Override
				public void run() {
					for (Listener listener : listeners.keySet()) {
						listener.onDeviceAdded(rendererDevice);
					}
				}
			});
		}
	}

	private void removeDevice(final Device device) {
		Log.d(TAG, "removed device: " + device.getDetails().getFriendlyName());
		final RendererDevice rendererDevice = new RendererDevice(device);
		devices.remove(rendererDevice);

		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners.keySet()) {
					listener.onDeviceRemoved(rendererDevice);
				}
			}
		});
	}


	public class Binder extends android.os.Binder {

		public void addListener(Listener listener) {
			listeners.put(listener, null);
		}

		public void removeListener(Listener listener) {
			listeners.remove(listener);
		}

		public void search() {
			Log.d(TAG, "start search for devices");

			// Now add all devices to the list we already know about
			for (Device device : upnpService.getRegistry().getDevices()) {
				UpnpService.this.addDevice(device);
			}

			// Search asynchronously for all devices, they will respond soon
			upnpService.getControlPoint().search();
		}

		public List<RendererDevice> getDevices() {
			return devices;
		}

		void sendToDevice(final RendererDevice device, ChannelModel channel, final IUpnpCommand.Listener listener) {
			IUpnpCommand command = new SendVideoCommand(upnpService, device, channel.getStreamUrl(), channel.getName());
			command.setListener(new IUpnpCommand.Listener() {
				@Override
				public void onCommandSuccess() {
					listener.onCommandSuccess();
					// device should be playing right now - ask for transport info
					getTransportInfo(device);
				}

				@Override
				public void onCommandFailure(String reason) {
					listener.onCommandFailure(reason);
				}
			});
			command.execute();
		}

		private void getTransportInfo(final RendererDevice device) {
			final GetTransportInfoCommand command = new GetTransportInfoCommand(upnpService, device);
			command.setListener(new IUpnpCommand.Listener() {
				@Override
				public void onCommandSuccess() {
					handler.post(new Runnable() {
						@Override
						public void run() {
							for (Listener listener : listeners.keySet()) {
								listener.onDevicePlayStateChanged(device, command.getTransportState());
							}
						}
					});
				}

				@Override
				public void onCommandFailure(String reason) {
				}
			});
			command.execute();
		}
	}


	public interface Listener {

		@SuppressWarnings("UnusedParameters")
		void onDeviceAdded(RendererDevice device);

		@SuppressWarnings("UnusedParameters")
		void onDeviceRemoved(RendererDevice device);

		void onDevicePlayStateChanged(RendererDevice device, TransportState transportState);
	}
}
