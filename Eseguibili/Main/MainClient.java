package Eseguibili.Main;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;

import Eseguibili.Client.Printer;
import Eseguibili.Client.ReceiverClient;
import Eseguibili.Client.UDP;
import Gson.GsonCredentials;
import Gson.GsonLimitStopOrder;
import Gson.GsonMessage;
import Gson.GsonUser;
import Gson.Values;

// Classe principale del client
public class MainClient {
    // Configurazione del client
    public static final String configFile = "client.properties";
    public static String hostname;
    public static int TCPport;

    // Socket e stream
    private static Socket TCPsocket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static Thread UDPreceiver = null;
    private static Thread receiver = null;
    private static Scanner scanner = new Scanner(System.in);
    private static Gson gson = new Gson();
    private static GsonMessage<Values> mes;

    // Flags
    private static boolean udpMessage = false;

    // Comandi validi
    private static final String[] validCommands = {
        "register\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*\\S(?:.*\\S)?\\s*\\)",
        "updateCredentials\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*\\S(?:.*\\S)?\\s*,\\s*\\S(?:.*\\S)?\\s*\\)",
        "login\\s*\\(\\s*[a-zA-Z0-9]+\\s*,\\s*\\S(?:.*\\S)?\\s*\\)",
        "logout\\s*\\(\\s*\\)",
        "insertMarketOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*\\)",
        "insertLimitOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*,\\s*\\d+(\\.\\d+)?\\s*\\)",
        "insertStopOrder\\s*\\(\\s*[a-zA-Z]+\\s*,\\s*\\d+\\s*,\\s*\\d+(\\.\\d+)?\\s*\\)",
        "cancelOrder\\s*\\(\\s*\\d+\\s*\\)",
        "getPriceHistory\\s*\\(\\s*\\d+\\s*\\)",
        "showOrderBook\\s*\\(\\s*\\)",
        "showStopOrders\\s*\\(\\s*\\)",
        "help"
    };

    // Messaggio di aiuto
    private static String helpMessage = "- register(username, password)\n" +
            "- updateCredentials(username, currentPassword, newPassword)\n" +
            "- login(username, password)\n" +
            "- logout()\n" +
            "- insertLimitOrder(type, size, limitPrice)\n" +
            "- insertMarketOrder(type, size)\n" +
            "- insertStopOrder(type, size, stopPrice)\n" +
            "- cancelOrder(orderID)\n" +
            "- getPriceHistory(month)\n" +
            "- showOrderBook()\n" +
            "- showStopOrders()\n" +
            "- help - Show this help message";

    // Dati utente
    private static String userName;
    private static String password;
    private static String message;

    // Classe per dati condivisi
    public static class SharedData {
        public AtomicBoolean isLogged = new AtomicBoolean(false);
        public AtomicBoolean isClosed = new AtomicBoolean(false);
        public AtomicBoolean loginError = new AtomicBoolean(false);
        public AtomicBoolean isShuttingDown = new AtomicBoolean(false);
        public volatile int UDPport = 0;
    }

    public static void main(String[] args) throws Exception {
        SharedData sharedData = new SharedData();

        Printer printer = new Printer();
        try (DatagramSocket UDPsocket = new DatagramSocket()) {
            TCPsocket = new Socket(hostname, TCPport);
            in = new BufferedReader(new InputStreamReader(TCPsocket.getInputStream()));
            out = new PrintWriter(TCPsocket.getOutputStream(), true);

            receiver = new Thread(new ReceiverClient(TCPsocket, in, printer, sharedData)); 
            receiver.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (sharedData.isShuttingDown.compareAndSet(false, true)) {
                    mes = new GsonMessage<>("Logout", new Values());
                    message = gson.toJson(mes);
                    out.println(message);
                    closeConnection();
                }
            }));

            System.out.println("Welcome, here's what you can do\n" + helpMessage);

            while (!sharedData.isShuttingDown.get()) {
                try {
                    if (System.in.available() <= 0 && !scanner.hasNextLine()) {
                        Thread.sleep(100);
                        continue;
                    }
                    String input = scanner.nextLine().trim();
                    if (input == null || input.isEmpty()) continue;

                    printer.inputReceived();

                    if (isValidCommand(input)) {
                        String[] tokens = input.split("\\s*,\\s*|\\s+");
                        String command = tokens[0].trim().toLowerCase();

                        switch (command) {
                            case "help":
                                System.out.println(helpMessage);
                                break;

                            case "register":
                                if (tokens.length == 3) {
                                    userName = tokens[1].trim();
                                    password = tokens[2].trim();
                                    mes = new GsonMessage<>("register", new GsonUser(userName, password));
                                    message = gson.toJson(mes);
                                    out.println(message);
                                } else {
                                    System.out.println("Invalid format. Use: register username password");
                                }
                                break;

                            case "updatecredentials":
                                if (tokens.length == 4) {
                                    userName = tokens[1].trim();
                                    String oldPassword = tokens[2].trim();
                                    String newPassword = tokens[3].trim();
                                    mes = new GsonMessage<>("updateCredentials", new GsonCredentials(userName, oldPassword, newPassword));
                                    message = gson.toJson(mes);
                                    out.println(message);
                                } else {
                                    System.out.println("Invalid format. Use: updateCredentials username oldPassword newPassword");
                                }
                                break;

                            case "login":
                                if (tokens.length == 3) {
                                    userName = tokens[1].trim();
                                    password = tokens[2].trim();
                                    mes = new GsonMessage<>("login", new GsonUser(userName, password));
                                    message = gson.toJson(mes);
                                    out.println(message);

                                    while (!udpMessage) {
                                        if (sharedData.isLogged.get()) {
                                            sendUDPmessage(UDPsocket, printer, sharedData);
                                            udpMessage = true;
                                        }
                                        if (sharedData.loginError.get()) break;
                                    }
                                } else {
                                    System.out.println("Invalid format. Use: login username password");
                                }
                                break;

                            case "logout":
                                if (sharedData.isShuttingDown.compareAndSet(false, true)) {
                                    mes = new GsonMessage<>("logout", new Values());
                                    message = gson.toJson(mes);
                                    out.println(message);
                                    while (!sharedData.isClosed.get()) {}
                                    closeConnection();
                                    return;
                                }
                                break;

                            case "insertlimitorder":
                                if (tokens.length == 4) {
                                    try {
                                        String type = tokens[1].toLowerCase();
                                        int size = Integer.parseInt(tokens[2]);
                                        int limitPrice = Integer.parseInt(tokens[3]);
                                        if (type.equals("ask") || type.equals("bid")) {
                                            if (!sharedData.isLogged.get()) {
                                                printer.print("You're not logged");
                                                printer.promptUser();
                                            } else {
                                                mes = new GsonMessage<>("insertLimitOrder", new GsonLimitStopOrder(type, size, limitPrice));
                                                message = gson.toJson(mes);
                                                out.println(message);
                                            }
                                        } else {
                                            System.out.println("Unknown order type: use 'ask' or 'bid'");
                                        }
                                    } catch (NumberFormatException e) {
                                        System.out.println("Invalid size or limitPrice: must be numbers");
                                    }
                                } else {
                                    System.out.println("Invalid format. Use: insertLimitOrder ask|bid size limitPrice");
                                }
                                break;

                            // Gli altri case restano simili...

                            default:
                                System.out.println("Invalid command: try again.");
                        }
                    } else {
                        System.out.println("Invalid Command: try again.");
                        printer.promptUser();
                    }

                } catch (NoSuchElementException e) {
                    if (sharedData.isShuttingDown.get()) break;
                    if (!sharedData.isClosed.get()) {
                        System.err.println("[MAINCLIENT] Input interrotto: " + e.getMessage());
                        printer.promptUser();
                    }
                } catch (SocketException e) {
                    if (!sharedData.isShuttingDown.get() && !sharedData.isClosed.get()) {
                        System.err.println("[MAINCLIENT] Socket error: " + e.getMessage());
                    }
                    printer.promptUser();
                } catch (Exception e) {
                    if (!sharedData.isShuttingDown.get() && !sharedData.isClosed.get()) {
                        System.out.println("[MAINCLIENT] Error: " + e.getMessage() + " Cause: " + e.getCause());
                    }
                    printer.promptUser();
                }
            }

            if (!sharedData.isShuttingDown.get()) {
                System.out.println("Closing Connection");
                sharedData.isShuttingDown.set(true);
                closeConnection();
            }
        }
    }

    public static void closeConnection() {
        try {
            if (receiver != null && receiver.isAlive()) {
                receiver.interrupt();
                try { receiver.join(500); } catch (InterruptedException ignored) {}
            }
            if (UDPreceiver != null && UDPreceiver.isAlive()) {
                UDPreceiver.interrupt();
                try { UDPreceiver.join(500); } catch (InterruptedException ignored) {}
            }
            if (TCPsocket != null && !TCPsocket.isClosed()) TCPsocket.close();
            if (in != null) in.close();
            if (out != null) {
                out.flush();
                out.close();
            }
        } catch (Exception e) {
            System.err.println("[MAINCLIENT] Error while closing connection: " + e.getMessage());
        }
    }

    public static void sendUDPmessage(DatagramSocket UDPsocket, Printer printer, SharedData sharedData) {
        try {
            InetAddress address = InetAddress.getByName(hostname);
            DatagramPacket packet = new DatagramPacket(new byte[1], 1, address, sharedData.UDPport);
            UDPsocket.send(packet);
            UDPreceiver = new Thread(new UDP(UDPsocket, in, printer));
            UDPreceiver.start();
        } catch (Exception e) {
            System.err.println("[MAINCLIENT] Error while sending UDP message: " + e.getMessage());
            printer.promptUser();
        }
    }

    public static boolean isValidCommand(String input) {
        for (String pattern : validCommands) {
            if (input.matches(pattern)) return true;
        }
        return false;
    }

    public static void readConfig() throws FileNotFoundException, IOException {
        InputStream input = new FileInputStream(configFile);
        Properties prop = new Properties();
        prop.load(input);
        TCPport = Integer.parseInt(prop.getProperty("TCPport"));
        hostname = prop.getProperty("hostname");
        input.close();
    }
}