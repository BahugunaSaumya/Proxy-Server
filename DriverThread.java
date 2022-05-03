package ProxyServer;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.util.Random;

public class DriverThread implements Runnable {

	/*
	 * Variables and constants  that are used in the code
	 */
	private Socket browserSocket;
	private BufferedReader clientReader;
	private BufferedWriter clientWriter;
	private Thread clientToServerThread;
    public Random a = new Random();
	/*
	 * Constants for this class. To identify connection types and file types.
	 */
	private static final String CONNECT = "CONNECT";
	private static final String JPG = ".jpg";
	private static final String JPEG = ".jpeg";
	private static final String PNG = ".png";
	private static final String GIF = ".gif";

	/*
	 * Constructor for this thread. Initialize the local variables.
	 */
	public DriverThread(Socket browserSocket) {
		this.browserSocket = browserSocket;
		try {
			this.browserSocket.setSoTimeout(2000);
			clientReader = new BufferedReader(new InputStreamReader(browserSocket.getInputStream()));
			clientWriter = new BufferedWriter(new OutputStreamWriter(browserSocket.getOutputStream()));
		} catch (IOException e) {
			System.out.println("Error initializing new thread.");
		}
	}

	/*
	This Function ch3eck if the url that was requested by the client is blocked or not if it is do no t continue otherwise segregate the request based on http and https
	 */
	@Override
	public void run() {
		String requestString, requestType, requestUrl;
		try {
			requestString = clientReader.readLine();
			String[] splitRequest = splitRequest(requestString);
			requestType = splitRequest[0];
			requestUrl = splitRequest[1];
		} catch (IOException e) {
			System.out.println("Error reading request from client.");
			return;
		}

		if (Proxy.isBlocked(requestUrl)) {
			System.out.println("Blocked site " + requestUrl + " requested.");
			handleblocked();
			return;
		}

		switch (requestType) {
			case CONNECT:
				System.out.println("HTTPS request for : " + requestUrl);
				handleHTTPSRequest(requestUrl);
				break;
			default:
				File file = Proxy.getCachedPage(requestUrl);
				if (file == null) {
					System.out.println("HTTP request for : " + requestUrl + ". No storedcache page found.");
					Cachenotstored(requestUrl);
				} else {
					System.out.println("HTTP request for : " + requestUrl + ". storedcache page found.");
					storedcache(file);
				}
				break;
		}
	}

	// Parse a CONNECT or GET request and return an array containing the URL and
	// port number
	private String[] splitRequest(String requestString) {
		String requestType, requestUrl;
		int requestSeparatorIndex;
		requestSeparatorIndex = requestString.indexOf(' ');
		requestType = requestString.substring(0, requestSeparatorIndex);
		requestUrl = requestString.substring(requestSeparatorIndex + 1);
		requestUrl = requestUrl.substring(0, requestUrl.indexOf(' '));
		if (!requestUrl.substring(0, 4).equals("http")) {
			requestUrl = "http://" + requestUrl;
		}
		return new String[] { requestType, requestUrl };
	}

	// Fetch a page from the cache for the client
	private void storedcache(File storedcacheFile) {
		try {
			String fileExtension = storedcacheFile.getName().substring(storedcacheFile.getName().lastIndexOf('.'));
			if (checkifImage(fileExtension)) {
				BufferedImage image = ImageIO.read(storedcacheFile);
				if (image == null) {
					System.out.println("Image " + storedcacheFile.getName() + " was null");
					String response = getResponse(404, false);
					clientWriter.write(response);
					clientWriter.flush();
				} else {
					String response = getResponse(200, false);
					clientWriter.write(response);
					clientWriter.flush();
					ImageIO.write(image, fileExtension.substring(1), browserSocket.getOutputStream());
				}
			} else {
				BufferedReader storedcacheFileBufferedReader = new BufferedReader(
						new InputStreamReader(new FileInputStream(storedcacheFile)));
				String response = getResponse(200, false);
				clientWriter.write(response);
				clientWriter.flush();
				String line;
				while ((line = storedcacheFileBufferedReader.readLine()) != null) {
					clientWriter.write(line);
				}
				clientWriter.flush();

				if (storedcacheFileBufferedReader != null) {
					storedcacheFileBufferedReader.close();
				}
			}
			if (clientWriter != null) {
				clientWriter.close();
			}
		} catch (IOException e) {
			System.out.println("Error sending storedcache file to client");
		}
	}

	// handles a not cached HTTP url and adds the value and the path to the HashMap and saves the files on the hard drive in the folder made for cached files.
	private void Cachenotstored(String requestUrl) {
		try {
			
			int fileExtensionIndex = requestUrl.lastIndexOf(".");
			String fileExtension;
			fileExtension = requestUrl.substring(fileExtensionIndex, requestUrl.length());
			String fileName = requestUrl.substring(0, fileExtensionIndex);
			if(fileName.indexOf('.')!=fileName.lastIndexOf('.')&&fileName.indexOf('.')!=-1 &&fileName.lastIndexOf('.')!=-1 ){
				fileName=fileName.substring(fileName.indexOf('.'), fileName.lastIndexOf('.'));
			}
			else{
				fileName = fileName.substring(7,fileName.indexOf("."));
			}
			while(fileName.contains("/")||fileName.contains(".")){
			fileName = fileName.replace("/", "__");
			fileName = fileName.replace(".", "_");
			}

			if (fileExtension.contains("/")) {
				fileExtension = fileExtension.replace("/", "__");
				fileExtension = fileExtension.replace(".", "_");
				fileExtension += ".html";
			}
		
			boolean caching = true;
			File fileToCache = null;
			BufferedWriter cacheWriter = null;
			fileName = fileName + fileExtension;
			try {
				fileToCache = new File("cache/" + fileName);
				if (!fileToCache.exists()) {
					fileToCache.createNewFile();
				}
				
				cacheWriter = new BufferedWriter(new FileWriter(fileToCache));
			} catch (IOException e) {
				System.out.println("Error trying to cache " + fileName);
				caching = false;
				e.printStackTrace();
			} catch (NullPointerException e) {
				System.out.println("Null pointer opening file " + fileName);
			}
			if (checkifImage(fileExtension)) {
				URL remoteURL = new URL(requestUrl);
				BufferedImage image = ImageIO.read(remoteURL);

				if (image != null) {
					ImageIO.write(image, fileExtension.substring(1), fileToCache);
					String line = getResponse(200, false);
					clientWriter.write(line);
					clientWriter.flush();
					ImageIO.write(image, fileExtension.substring(1), browserSocket.getOutputStream());

				} else {
					String error = getResponse(404, false);
					clientWriter.write(error);
					clientWriter.flush();
					return;
				}
			} else {
				URL remoteURL = new URL(requestUrl);
				HttpURLConnection ServerCon = (HttpURLConnection) remoteURL.openConnection();
				ServerCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				ServerCon.setRequestProperty("Content-Language", "en-US");
				ServerCon.setUseCaches(false);
				ServerCon.setDoOutput(true);
				BufferedReader toServer = new BufferedReader(
						new InputStreamReader(ServerCon.getInputStream()));
				String line = getResponse(200, false);
				clientWriter.write(line);
				while ((line = toServer.readLine()) != null) {
					clientWriter.write(line);
					if (caching) {
						cacheWriter.write(line);
					}
				}
				clientWriter.flush();
				if (toServer != null) {
					toServer.close();
				}
			}

			if (caching) {
				cacheWriter.flush();
				Proxy.addCachedPage(requestUrl, fileToCache);
			}
			if (cacheWriter != null) {
				cacheWriter.close();
			}
			if (clientWriter != null) {
				clientWriter.close();
			}
		} catch (Exception e) {
			System.out.println("Error sending non stored cache page to client");
		}
	}

	// Handle a HTTPS CONNECT request
	private void handleHTTPSRequest(String requestUrl) {
		String url = requestUrl.substring(7);
		String pieces[] = url.split(":");
		url = pieces[0];
		int serverPort = Integer.valueOf(pieces[1]);

		try {

			for (int i = 0; i < 5; i++) {
				clientReader.readLine();
			}
	
			InetAddress serverAddress = InetAddress.getByName(url);
			Socket serverSocket = new Socket(serverAddress, serverPort);
			serverSocket.setSoTimeout(5000);

			String line = getResponse(200, true);
			clientWriter.write(line);
			clientWriter.flush();
			//Create a Buffered Writer between proxy and remote
		
			BufferedWriter serverWriter = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
			// Create Buffered Reader from proxy and remote
			BufferedReader serverReader = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));

			// Create a new thread to listen to client and transmit to server
			Clienttoservertunnel clientToServer = new Clienttoservertunnel(
					browserSocket.getInputStream(), serverSocket.getOutputStream());
			clientToServerThread = new Thread(clientToServer);
			clientToServerThread.start();

			// Listen to remote server and relay to client
			try {
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = serverSocket.getInputStream().read(buffer);
					if (read > 0) {
						browserSocket.getOutputStream().write(buffer, 0, read);
						if (serverSocket.getInputStream().available() < 1) {
							browserSocket.getOutputStream().flush();
						}
					}
				} while (read >= 0);
			} catch (SocketTimeoutException e) {
				System.out.println("Socket timeout during HTTPs connection");
			} catch (IOException e) {
				System.out.println("Error handling HTTPs connection");
			}

			// Close the resources
			closeResources(serverSocket, serverReader, serverWriter, clientWriter);

		} catch (SocketTimeoutException e) {
			String line = getResponse(504, false);
			try {
				clientWriter.write(line);
				clientWriter.flush();
			} catch (IOException x) {
			}
		} catch (Exception e) {
			System.out.println("Error on HTTPS " + requestUrl);
		}
	}

	// Respond to the browser with a 403 Error Code
	private void handleblocked() {
		try {
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(browserSocket.getOutputStream()));
			String line = getResponse(403, false);
			bufferedWriter.write(line);
			bufferedWriter.flush();
		} catch (IOException e) {
			System.out.println("Error writing to client when requested a blocked site");
		}
	}

	// Check whether the file extension is that of an image
	private boolean checkifImage(String fileExtension) {
		return fileExtension.contains(PNG) || fileExtension.contains(JPG) || fileExtension.contains(JPEG)
				|| fileExtension.contains(GIF);
	}

/*
 * 
 * it gets the response string based on the error that was received
 * 
 * */
	private String getResponse(int code, boolean connectionEstablished) {
		String response = "";
		switch (code) {
			case 200:
				if (connectionEstablished) {
					response = "HTTP/1.0 200 Connection established\r\nProxy-Agent: ProxyServer/1.0\r\n\r\n";
				} else {
					response = "HTTP/1.0 200 OK\nProxy-agent: ProxyServer/1.0\n\r\n";
				}
				break;
			case 403:
				response = "HTTP/1.0 403 Access Forbidden \nUser-Agent: ProxyServer/1.0\n\r\n";
				break;
			case 404:
				response = "HTTP/1.0 404 NOT FOUND \nProxy-agent: ProxyServer/1.0\n\r\n";
				break;
			case 504:
				response = "HTTP/1.0 504 Timeout Occured after 10s\nUser-Agent: ProxyServer/1.0\n\r\n";
				break;
			default:
				break;
		}
		return response;
	}

	// Close the passed resources
	private void closeResources(Socket serverSocket, BufferedReader serverReader, BufferedWriter serverWriter,
			BufferedWriter clientWriter) throws IOException {
		if (serverSocket != null) {
			serverSocket.close();
		}
		if (serverReader != null) {
			serverReader.close();
		}
		if (serverWriter != null) {
			serverWriter.close();
		}
		if (clientWriter != null) {
			clientWriter.close();
		}
	}
/*  this class handles the tunneling between  client and the server and is started as thread to run parallel to server to client tunneling 
 * 
 * 
 * 
 * 
 * */
	class Clienttoservertunnel implements Runnable {

		InputStream client;
		OutputStream server;

		public Clienttoservertunnel(InputStream ClientInput, OutputStream serverout) {
			this.client = ClientInput;
			this.server = serverout;
		}

		@Override
		public void run() {
			try {
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = client.read(buffer);
					if (read > 0) {
						server.write(buffer, 0, read);
						if (client.available() < 1) {
							server.flush();
						}
					}
				} while (read >= 0);
			} catch (SocketTimeoutException e) {
			} catch (IOException e) {
				System.out.println("Proxy to client HTTPS read timed out");
			}
		}
	}
}