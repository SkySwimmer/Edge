package org.asf.edge.mmoserver.networking.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import org.asf.connective.tasks.AsyncTaskManager;
import org.asf.edge.mmoserver.networking.SmartfoxClient;
import org.asf.edge.mmoserver.networking.SmartfoxServer;
import org.asf.edge.mmoserver.networking.bitswarm.BitswarmClient;

/**
 * 
 * Bitswarm-based Smartfox Server
 * 
 * @author Sky Swimmer
 *
 */
public class BitswarmSmartfoxServer extends SmartfoxServer {

	private String address;
	private int port;

	private ServerSocket sockTcp;
//	private DatagramSocket sockUdp;
	private boolean connected = false;

	private ArrayList<BitswarmClientContainer> clients = new ArrayList<BitswarmClientContainer>();

	public BitswarmSmartfoxServer(String address, int port) {
		this.address = address;
		this.port = port;
	}

	class BitswarmClientContainer {
		public Socket socket;
		public BitswarmClient bitswarmClient;
		public BitswarmSmartfoxClient sfsClient;
	}

	@Override
	public boolean isRunning() {
		return connected;
	}

	@Override
	public void start() throws IOException {
		if (sockTcp != null) // || sockUdp != null)
			throw new IOException("Server is already started");

		// Create socket
		sockTcp = new ServerSocket(port, 0, InetAddress.getByName(address));
//		try {
//			sockUdp = new DatagramSocket(port, InetAddress.getByName(address));
//		} catch (Exception e) {
//			sockTcp.close();
//			sockTcp = null;
//		}
		connected = true;

		// Accept connections
		AsyncTaskManager.runAsync(() -> {
			while (isRunning()) {
				// Accept client
				Socket sock;
				try {
					sock = this.sockTcp.accept();
				} catch (IOException e) {
					continue;
				}

				// Log
				getLogger().debug("Client connected: " + sock.getRemoteSocketAddress());

				// Handle data
				AsyncTaskManager.runAsync(() -> {
					try {
						// Create client objects
						BitswarmClient bsCl = new BitswarmClient(sock.getInputStream(), sock.getOutputStream());
						BitswarmSmartfoxClient sfsCl = new BitswarmSmartfoxClient(bsCl, sock, this);

						// Add
						if (!isRunning())
							throw new IOException("Closed");
						BitswarmClientContainer client = new BitswarmClientContainer();
						client.socket = sock;
						client.bitswarmClient = bsCl;
						client.sfsClient = sfsCl;
						sfsCl.container = client;
						synchronized (clients) {
							clients.add(client);
						}

						// Accepted
						onClientAccepted(sfsCl);
					} catch (IOException e) {
						// Failed
						try {
							sock.close();
						} catch (IOException e2) {
						}
					}
				});
			}
		});
	}

	@Override
	public void stopForced() throws IOException {
		// Check state
		if (!connected)
			return;

		// Disconnect
		getLogger().debug("Stopping server...");
		connected = false;
		try {
			sockTcp.close();
		} catch (IOException e) {
		}
//		sockUdp.close();

		// Disconnect clients
		getLogger().debug("Disconnecting clients...");
		BitswarmClientContainer[] clientLst;
		synchronized (clients) {
			clientLst = clients.toArray(t -> new BitswarmClientContainer[t]);
		}
		for (BitswarmClientContainer client : clientLst) {
			try {
				client.sfsClient.callDisconnectEventsInternal();
			} catch (Exception e) {
			}
			try {
				client.socket.close();
			} catch (Exception e) {
			}
		}
		clients.clear();

		// Unset server
		sockTcp = null;
//		sockUdp = null;
		getLogger().debug("SFS server closed!");
	}

	@Override
	public void stop() throws IOException {
		// Check state
		if (!connected)
			return;

		// Close server
		getLogger().debug("Stopping server...");
		connected = false;
		try {
			sockTcp.close();
		} catch (IOException e) {
		}

		// Disconnect clients
		getLogger().debug("Disconnecting clients...");
		BitswarmClientContainer[] clientLst;
		synchronized (clients) {
			clientLst = clients.toArray(t -> new BitswarmClientContainer[t]);
		}
		for (BitswarmClientContainer client : clientLst) {
			try {
				client.sfsClient.disconnect();
			} catch (Exception e) {
			}
		}
		clients.clear();

		// Unset server
		sockTcp = null;
//		sockUdp.close();
//		sockUdp = null;
		getLogger().debug("SFS server closed!");
	}

	@Override
	public SmartfoxClient[] getClients() {
		synchronized (clients) {
			return clients.stream().map(t -> t.sfsClient).toArray(t -> new SmartfoxClient[t]);
		}
	}

	void onClientDisconnect(BitswarmSmartfoxClient client) {
		// Disconnect
		synchronized (clients) {
			clients.remove(client.container);
		}
		getLogger().debug("Client disconnected: " + client.getRemoteAddress());
		try {
			client.socket.close();
		} catch (IOException e2) {
		}
	}

}
