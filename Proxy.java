package ProxyServer;
import java.io.*;
import java.net.*;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.*;


/*This class is the implementation of runnable and works as the engine of my implementation i.e will be waiting for the requests 
 *and connection listening o n a port(5001) it will call the Driverthread class which implements threads and creates multiple 
 *Driver threads to handle multiple requests separately. This class manages the user inputs as well as blocked and cached Web sites
 *When closed it keeps track of the blocked and cached web sites by saving the files on a local hard drive which will then be extracted if required 
 
 * */
public class Proxy implements Runnable {

	public static void main(String[] args) {
		// Create an instance of Proxy and starts listening for requests form the browser
		Proxy server = new Proxy(50000);
		server.listen();	
	}
	/*  DataStructures and variable required to run the programme*/
	private static HashMap<String, File> cache;
	private static HashMap<String, String> block;
	private static ArrayList<Thread> threads;
	private ServerSocket Listen;
	private volatile boolean running = true;
	private Thread managementConsole;
/*
 * Methods to return values contained in the datastructures
 * 
 * */
	
	public static File getCachedPage(String url) {
		return cache.get(url);
	}
	public static void addCachedPage(String urlString, File fileToCache) {
		cache.put(urlString, fileToCache);
	}
	public static void  removeBlocked(String urlString) {
		block .remove(urlString);
		 {
		        try {
		            Files.deleteIfExists(
		                Paths.get("D:\\study\\eclipse Project\\PRoxyServer blockedSites.txt"));
		        }
		        catch (NoSuchFileException e) {
		            System.out.println(
		                "No such file/directory exists");
		        }
		        catch (DirectoryNotEmptyException e) {
		            System.out.println("Directory is not empty.");
		        }
		        catch (IOException e) {
		            System.out.println("Invalid permissions.");
		        }
		 
		        System.out.println("Deletion successful.");
		    }
		}
	
	public static boolean isBlocked(String url) {
		for (String key : block.keySet()) {
			if (url.contains(key)) {
				return true;
			}
		}
		return false;
	}
	/*
	 * Constructor for the Proxy.
	 */
	public Proxy(int port) {
		// initialise the data structures and client port number
		cache = new HashMap<>();
		block = new HashMap<>();
		threads = new ArrayList<>();
		
		// Spin off a seperate thread to handle the management console
		managementConsole = new Thread(this);
		managementConsole.start();
		// Initalize the maps of cached site and blocked sites
		initializeCachedSites();
		initializeBlockedSites();
		// Start listening to the browser
		try {
			Listen = new ServerSocket(port);
			System.out.println("Waiting for client on port " + Listen.getLocalPort());
			running = true;
		} catch (SocketException e) {
			System.out.println("Socket Exception when connecting to client");
		} catch (SocketTimeoutException e) {
			System.out.println("Timeout occured while connecting to client");
		} catch (IOException e) {
			System.out.println("IO exception when connecting to client");
		}
	}

	
	// start a new instance of thread for every new connection that the browser requests
	public void listen() {
		while (running) {
			try {
				Socket socket = Listen.accept();
				Thread thread = new Thread(new DriverThread(socket));
				threads.add(thread);
				thread.start();
			} catch (SocketException e) {
				System.out.println("Server closed");
			} catch (IOException e) {
				System.out.println("Error creating new Thread from ServerSocket.");
			}
		}
	}

	// Initalize the data structure for the cached sites by reading from the cache
	// file. If a cache file does not exist, create one
	@SuppressWarnings("unchecked")
	private void initializeCachedSites() {
		try {
			File cachedSites = new File("cachedSites.txt");
			if (!cachedSites.exists()) {
				System.out.println("Creating new cache file");
				cachedSites.createNewFile();
			} else {
				FileInputStream cachedFileStream = new FileInputStream(cachedSites);
				ObjectInputStream cachedObjectStream = new ObjectInputStream(cachedFileStream);
				cache = (HashMap<String, File>) cachedObjectStream.readObject();
				cachedFileStream.close();
				cachedObjectStream.close();
			}
		} catch (IOException e) {
			System.out.println("Error loading previously cached sites file");
		} catch (ClassNotFoundException e) {
			System.out.println("Class not found loading in preivously cached sites file");
		}
	}

	// Initalize the data structure for the blocked sites by reading from the
	// blocked file. If a blocked file does not exist, create one
	private void initializeBlockedSites() {
		try {
			File blockedSitesTxtFile = new File("blockedSites.txt");
			if (!blockedSitesTxtFile.exists()) {
				System.out.println("No blocked files");
				blockedSitesTxtFile.createNewFile();
			} else {
				FileInputStream blockedFileStream = new FileInputStream(blockedSitesTxtFile);
				ObjectInputStream blockedObjectStream = new ObjectInputStream(blockedFileStream);
				block = (HashMap<String, String>) blockedObjectStream.readObject();
				blockedFileStream.close();
				blockedObjectStream.close();
			}
		} catch (IOException e) {
			System.out.println("Error loading previously Blocked sites file");
		} catch (ClassNotFoundException e) {
			System.out.println("Class not found loading in preivously Blocked sites file");
		}
	}

	// Close the server. Write back to the cached and blocked files. Join the
	// threads.
	private void closeServer() {
		System.out.println("Closing server");
		running = false;
		try {
			FileOutputStream cachedFileStream = new FileOutputStream("cachedSites.txt");
			ObjectOutputStream cachedObjectStream = new ObjectOutputStream(cachedFileStream);

			cachedObjectStream.writeObject(cache);
			cachedObjectStream.close();
			cachedFileStream.close();
			System.out.println("Cached sites written");

			FileOutputStream blockedFileStream = new FileOutputStream("blockedSites.txt");
			ObjectOutputStream blockedObjectStream = new ObjectOutputStream(blockedFileStream);
			blockedObjectStream.writeObject(block);
			blockedObjectStream.close();
			blockedFileStream.close();
			System.out.println("Blocked site list saved");
			try {
				for (Thread thread : threads) {
					if (thread.isAlive()) {
						thread.join();
					}
				}
			} catch (InterruptedException e) {
				System.out.println("Interrupted exception when closing server.");
			}

		} catch (IOException e) {
			System.out.println("Error saving cache/blocked sites");
		}
		try {
			System.out.println("Terminating connection");
			Listen.close();
		} catch (Exception e) {
			System.out.println("Exception closing proxy's server socket");
			e.printStackTrace();
		}
	}

	// The functionality of the management console. Watch System.in and look out for
	// commands. If a defined command is not entered, assume that the input is a URL
	// to be blocked and block it.
	@Override
	public void run() {
		Scanner terminalScanner = new Scanner(System.in);
		String userInput;
		while (running) {
			System.out.println("Please enter a command. Enter 9 to see the list of commands.");
			userInput = terminalScanner.next();

			switch (userInput) {
				case "1":
					System.out.println("\nCurrently Blocked Sites");
					for (String key : block.keySet()) {
						System.out.println(key);
					}
					System.out.println();
					break;
				case "2":
					System.out.println("\nCurrently Cached Sites");
					for (String key : cache.keySet()) {
						System.out.println(key);
					}
					System.out.println();
					break;
				case "3":
					running = false;
					closeServer();
					break;
				
				case "4":
						 System.out.println("Enter the site to be REMOVED from BLOCKED ");
						 String r= terminalScanner.next();
						 removeBlocked(r);
						 break;	
				case "9":
					System.out.println("Enter 1 to view the list of blocked URLs.");
					System.out.println("Enter 2 to view the list of caches webpages");
					System.out.println("Enter 3 to close the proxy server.");
					System.out.println("Enter 4 to UNBLOCK a website");
					System.out.println("Enter any url to BLOCK" );
					System.out.println("Enter 9 to see the list of possible commands");
				
					break;
				default:
				
	    
	    	        
			
	    	        
					block.put(userInput.toLowerCase(), userInput.toLowerCase());
					System.out.println("\n" + userInput + " blocked successfully \n");
				    

				    break;
				
					
			}
		}
		terminalScanner.close();
	}
}