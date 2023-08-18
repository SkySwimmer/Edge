package org.asf.razorwhip.sentinel.launcher.software.projectedge;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.function.Consumer;

import org.asf.razorwhip.sentinel.launcher.LauncherUtils;
import org.asf.razorwhip.sentinel.launcher.api.IEmulationSoftwareProvider;
import org.asf.razorwhip.sentinel.launcher.descriptors.data.LauncherController;
import org.asf.razorwhip.sentinel.launcher.descriptors.data.ServerEndpoints;
import org.asf.razorwhip.sentinel.launcher.software.projectedge.windows.LaunchOptionMenu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class EdgeEmulationSoftware implements IEmulationSoftwareProvider {

	private JsonObject launchSettings;
	private Process serverProc;
	private boolean serverExited;

	@Override
	public void init() {
		try {
			File launchSettingsF = new File("edgelauncher.json");
			if (!launchSettingsF.exists()) {
				// Create
				launchSettings = new JsonObject();
				launchSettings.addProperty("launchMode", "normal");
				JsonObject remoteEndpoints = new JsonObject();
				remoteEndpoints.addProperty("gameplay", "http://localhost:5320/");
				remoteEndpoints.addProperty("common", "http://localhost:5321/");
				remoteEndpoints.addProperty("social", "http://localhost:5322/");
				remoteEndpoints.addProperty("smartfoxHost", "localhost");
				remoteEndpoints.addProperty("smartfoxPort", 5323);
				launchSettings.add("remoteEndpoints", remoteEndpoints);
				Files.writeString(launchSettingsF.toPath(), launchSettings.toString());
			} else {
				// Load
				launchSettings = JsonParser.parseString(Files.readString(launchSettingsF.toPath())).getAsJsonObject();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void showOptionWindow() {
		// Show option menu
		LaunchOptionMenu window = new LaunchOptionMenu();
		window.setVisible(true);

		// TODO:
		// Main menu:
		// - More options:
		// - - Open version manager
		// - - Open payload manager
		// - - Open server configuration
		// - - Manual game descriptor update
		// - - Manual emulation software update

		// Reload
		init();
	}

	@Override
	public void prepareLaunchWithStreamingAssets(String assetArchiveURL, File assetModifications, JsonObject archiveDef,
			JsonObject descriptorDef, String clientVersion, File clientDir, Runnable successCallback,
			Consumer<String> errorCallback) {
		prepareLaunch(successCallback, errorCallback);
	}

	@Override
	public void prepareLaunchWithLocalAssets(File assetArchive, File assetModifications, JsonObject archiveDef,
			JsonObject descriptorDef, String clientVersion, File clientDir, Runnable successCallback,
			Consumer<String> errorCallback) {
		prepareLaunch(successCallback, errorCallback);
	}

	private void prepareLaunch(Runnable successCallback, Consumer<String> errorCallback) {
		// Check start mode
		LauncherUtils.log("Loading configuration...");

		// Load remote endpoints
		JsonObject remoteEndpoints = launchSettings.get("remoteEndpoints").getAsJsonObject();
		ServerEndpoints endpointsRemote = new ServerEndpoints();
		endpointsRemote.achievementServiceEndpoint = remoteEndpoints.get("gameplay").getAsString();
		endpointsRemote.commonServiceEndpoint = remoteEndpoints.get("common").getAsString();
		endpointsRemote.contentserverServiceEndpoint = remoteEndpoints.get("gameplay").getAsString();
		endpointsRemote.groupsServiceEndpoint = remoteEndpoints.get("social").getAsString();
		endpointsRemote.itemstoremissionServiceEndpoint = remoteEndpoints.get("gameplay").getAsString();
		endpointsRemote.messagingServiceEndpoint = remoteEndpoints.get("social").getAsString();
		endpointsRemote.userServiceEndpoint = remoteEndpoints.get("common").getAsString();
		endpointsRemote.smartFoxHost = remoteEndpoints.get("smartfoxHost").getAsString();
		endpointsRemote.smartFoxPort = remoteEndpoints.get("smartfoxPort").getAsInt();

		// Load local endpoints
		ServerEndpoints endpointsLocal = new ServerEndpoints();

		// Load edge config
		File edgeConfig = new File("server/server.json");
		if (edgeConfig.exists()) {
			try {
				JsonObject configData = JsonParser.parseString(Files.readString(edgeConfig.toPath())).getAsJsonObject();

				// Load common server configuration
				JsonObject cApiJson = configData.get("commonApiServer").getAsJsonObject();
				String commonURL = (cApiJson.get("https").getAsBoolean() ? "https://" : "http://");
				String ip = cApiJson.get("listenAddress").getAsString();
				if (ip.equals("0.0.0.0"))
					ip = "localhost";
				if (ip.contains(":"))
					commonURL += "[";
				commonURL += ip;
				if (ip.contains(":"))
					commonURL += "]";
				commonURL += ":";
				commonURL += cApiJson.get("listenPort").getAsInt();

				// Load gameplay server configuration
				JsonObject gpApiJson = configData.get("gameplayApiServer").getAsJsonObject();
				String gpURL = (gpApiJson.get("https").getAsBoolean() ? "https://" : "http://");
				ip = gpApiJson.get("listenAddress").getAsString();
				if (ip.equals("0.0.0.0"))
					ip = "localhost";
				if (ip.contains(":"))
					gpURL += "[";
				gpURL += ip;
				if (ip.contains(":"))
					gpURL += "]";
				gpURL += ":";
				gpURL += gpApiJson.get("listenPort").getAsInt();

				// Load social server configuration
				if (configData.has("socialApiServer")) {
					JsonObject sApiJson = configData.get("socialApiServer").getAsJsonObject();
					String sURL = (sApiJson.get("https").getAsBoolean() ? "https://" : "http://");
					ip = sApiJson.get("listenAddress").getAsString();
					if (ip.equals("0.0.0.0"))
						ip = "localhost";
					if (ip.contains(":"))
						sURL += "[";
					sURL += ip;
					if (ip.contains(":"))
						sURL += "]";
					sURL += ":";
					sURL += sApiJson.get("listenPort").getAsInt();

					// Apply
					endpointsLocal.achievementServiceEndpoint = sURL;
					endpointsLocal.itemstoremissionServiceEndpoint = sURL;
				}

				// Apply
				endpointsLocal.commonServiceEndpoint = commonURL;
				endpointsLocal.groupsServiceEndpoint = commonURL;
				endpointsLocal.achievementServiceEndpoint = gpURL;
				endpointsLocal.messagingServiceEndpoint = gpURL;

				// Load smartfox server config
				JsonObject mmoSrvJson = configData.get("mmoServer").getAsJsonObject();
				ip = mmoSrvJson.get("listenAddress").getAsString();
				if (ip.equals("0.0.0.0"))
					ip = "localhost";
				endpointsLocal.smartFoxHost = ip;
				endpointsLocal.smartFoxPort = mmoSrvJson.get("listenPort").getAsInt();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		// Log
		LauncherUtils.log("Determining launch mode...");
		String launchMode = launchSettings.get("launchMode").getAsString();

		// Prepare client startup
		if (launchMode.equals("server")) {
			LauncherUtils.addTag("no_launch_client");
		} else {
			// Select endpoints
			if (launchMode.equals("remote-client")) {
				LauncherUtils.addTag("server_endpoints").setValue(ServerEndpoints.class, endpointsRemote);
			} else {
				LauncherUtils.addTag("server_endpoints").setValue(ServerEndpoints.class, endpointsLocal);
			}
		}

		// Check connection
		if (launchMode.equals("remote-client")) {
			try {
				// Open URL connection
				HttpURLConnection conn = (HttpURLConnection) new URL(endpointsRemote.commonServiceEndpoint
						+ (endpointsRemote.commonServiceEndpoint.endsWith("/") ? "" : "/") + "testconnection")
						.openConnection();
				int code = conn.getResponseCode();
				if (code != 200 && code != 404)
					throw new IOException(); // Down
			} catch (Exception e) {
				// Error
				errorCallback.accept("Remote server is not online.");
				return;
			}
		} else if (launchMode.equals("local-client")) {
			try {
				// Open URL connection
				HttpURLConnection conn = (HttpURLConnection) new URL(endpointsLocal.commonServiceEndpoint
						+ (endpointsLocal.commonServiceEndpoint.endsWith("/") ? "" : "/") + "testconnection")
						.openConnection();
				int code = conn.getResponseCode();
				if (code != 200 && code != 404)
					throw new IOException(); // Down
			} catch (Exception e) {
				// Error
				errorCallback.accept("Local server is not online.");
				return;
			}
		}

		// Handle launch mode
		boolean serverLog = launchMode.equals("server");
		if (!launchMode.equals("local-client") && !launchMode.equals("remote-client")) {
			// Check if already active
			boolean alreadyActive = false;
			if (!launchMode.equals("server")) {
				try {
					// Open URL connection
					HttpURLConnection conn = (HttpURLConnection) new URL(endpointsLocal.commonServiceEndpoint
							+ (endpointsLocal.commonServiceEndpoint.endsWith("/") ? "" : "/") + "testconnection")
							.openConnection();
					int code = conn.getResponseCode();
					if (code != 200 && code != 404)
						throw new IOException(); // Down

					// Success
					alreadyActive = true;
				} catch (Exception e) {
				}
			}

			// Check
			if (!alreadyActive) {
				// Start server
				LauncherUtils.log("Starting server...", true);

				// Create process
				String jvm = ProcessHandle.current().info().command().get();

				// Scan libs
				String libs = "globalserver.jar";
				for (File lib : new File("server", "libs").listFiles()) {
					libs += File.pathSeparator + "libs/" + lib.getName();
				}

				// Create builder
				ProcessBuilder builder;
				if (!serverLog)
					builder = new ProcessBuilder(jvm, "-cp", libs, "org.asf.edge.globalserver.EdgeGlobalServerMain");
				else
					builder = new ProcessBuilder(jvm, "-cp", libs, "-DopenGuiLog=true",
							"org.asf.edge.globalserver.EdgeGlobalServerMain");
				builder.directory(new File("server"));
				builder.inheritIO();

				// Start
				try {
					startServer(builder, launchMode);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				// Wait for server to come online
				while (true) {
					// Check server
					if (serverExited) {
						// Error
						errorCallback.accept("Server exited before the launch was completed!");
						return;
					}

					// Test connection
					try {
						// Open URL connection
						HttpURLConnection conn = (HttpURLConnection) new URL(endpointsLocal.commonServiceEndpoint
								+ (endpointsLocal.commonServiceEndpoint.endsWith("/") ? "" : "/") + "testconnection")
								.openConnection();
						int code = conn.getResponseCode();
						if (code != 200 && code != 404)
							throw new IOException(); // Down

						// Success
						break;
					} catch (Exception e) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e1) {
							break;
						}
					}
				}
			}
		}

		// Call success
		successCallback.run();
	}

	private void startServer(ProcessBuilder builder, String launchMode) throws IOException {
		// Start server
		Process proc = builder.start();
		serverProc = proc;
		proc.onExit().thenAccept(t -> {
			int code = proc.exitValue();
			if (code == 237) {
				// Restart server
				try {
					startServer(builder, launchMode);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else {
				serverExited = true;
				if (launchMode.equals("server")) {
					// Done
					if (LauncherUtils.hasTag("no_launch_client")) {
						LauncherController cont = LauncherUtils.getTag("no_launch_client")
								.getValue(LauncherController.class);
						if (cont != null)
							cont.exitCallback.run();
					}
				}
			}
		});
	}

	@Override
	public void onGameLaunchSuccess(String version, File clientDir) {
		// Check exit
		if (serverExited) {
			// Exit if needed
			if (LauncherUtils.hasTag("no_launch_client")) {
				LauncherController cont = LauncherUtils.getTag("no_launch_client").getValue(LauncherController.class);
				if (cont != null)
					cont.exitCallback.run();
			}
		}
	}

	@Override
	public void onGameExit(String version, File clientDir) {
		// Check exit
		if (!serverExited && serverProc != null) {
			// Exit
			serverProc.destroy();
		}
	}

}