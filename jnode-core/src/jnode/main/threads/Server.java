package jnode.main.threads;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import jnode.logger.Logger;
import jnode.protocol.binkp.deprecated.BinkpConnector;
import jnode.protocol.binkp.deprecated.Connector;
import jnode.protocol.binkp.deprecated.ProtocolException;

/**
 * 
 * @author kreon
 * 
 */
@Deprecated
public class Server extends Thread {
	private static final Logger logger = Logger.getLogger(Server.class);

	private static class ServerClient implements Runnable {
		private final Socket socket;

		public ServerClient(Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			try {
				logger.l3(String.format("Incoming connection from %s:%d",
						socket.getInetAddress().getHostAddress(),
						socket.getPort()));
				Connector connector = new Connector(new BinkpConnector());
				connector.accept(socket);
			} catch (ProtocolException e) {
				logger.l2("Connector initialization failed");
			} finally {
				try {
					socket.close();
				} catch (IOException ignore) {
				}
			}

		}

	}

	private final String host;
	private final int port;
	private int errors = 0;

	public Server(String host, int port) {
		super();
		this.host = host;
		this.port = port;
	}

	@Override
	public void run() {
		logger.l4("Server listens on " + host + ":" + port);
		try {

			ServerSocket socket = new ServerSocket(port, 0,
					Inet4Address.getByName(host));
			socket.setSoTimeout(10000);
			while (!socket.isClosed() && socket.isBound()) {
				try {
					Socket clientSocket = socket.accept();
					clientSocket.setSoTimeout(10000);
					ThreadPool.execute(new ServerClient(clientSocket));
				} catch (SocketTimeoutException e) {
				}
			}
			socket.close();
		} catch (IOException e) {
			logger.l2("Server error: " + e.getMessage());
		}
		errors++;
		if (errors < 10) {
			logger.l3("Server crashed, restart");
			this.run();
		} else {
			logger.l2("Server crashed 10 times, leave");
			System.exit(-1);
		}
	}
}
