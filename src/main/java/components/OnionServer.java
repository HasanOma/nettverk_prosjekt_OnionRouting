package components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CryptoUtil;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

/**
 * Server class that waits for connection from client
 */
public class OnionServer {

    final int PORT_NUM;
    private String IPAddress;

    ServerSocket server;


    // Secret symmetric key
    private SecretKey secretKey;

    private static Logger logger = LoggerFactory.getLogger(OnionClient.class);

    public OnionServer(int port) throws IOException {
        this.IPAddress = InetAddress.getByName(InetAddress.getLocalHost().getHostName()).getHostAddress();

        this.PORT_NUM = port;
        this.server = new ServerSocket(PORT_NUM);

        logger.info("Server starting...");
        logger.info(String.format("Server started at %s:%s", getIPAddress(), getPort()));
    }

    private SecretKey getSecretKey() {
        return secretKey;
    }

    private void setSecretKey(SecretKey sk) {
        this.secretKey = sk;
    }

    public String getIPAddress() {
        return IPAddress;
    }

    public int getPort() {
        return port;
    }


    public void run() throws IOException {
        CryptoUtil.createRSA(port);

        logger.info("Waiting for connection...");
        Socket conn = server.accept();
        logger.info("Connection established");

        DataInputStream reader
                = new DataInputStream(new BufferedInputStream(conn.getInputStream()));
        DataOutputStream writer = new DataOutputStream(conn.getOutputStream());

        while(true) {
            int l = reader.readInt();
            byte[] msgBytes = new byte[l];
            reader.readFully(msgBytes);
            String clientMsg = new String(msgBytes, StandardCharsets.UTF_8);
            logger.info(String.format("Message from Client is: %s", clientMsg));

            if("GivePK!!!".equals(clientMsg)) {
                logger.info(String.format("Received from client: %s", clientMsg));
                logger.info("Sending public key back to client...");

                PublicKey pk = CryptoUtil.loadRSAPublicKey("./src/keys_" + port + "/rsa_pub.pub");
                byte[] stBytes = pk.getEncoded();

                writer.writeInt(stBytes.length);
                writer.write(stBytes);
                writer.flush();

            } else if(getSecretKey() == null) {
                byte[] decrypted = CryptoUtil.decryptRSA(msgBytes, msgBytes.length, CryptoUtil.loadRSAPrivateKey("./src/keys_" + port + "/rsa_pvt.key"));

                SecretKey sk = new SecretKeySpec(decrypted, "AES");
                setSecretKey(sk);

                logger.info("Secret key received from client");

                String response = "Secret key set at server " + getIPAddress() + ":" + getPort();
                byte[] responseBytes = response.getBytes();
                byte[] encrypted = CryptoUtil.encryptAES(responseBytes, responseBytes.length, getSecretKey());

                writer.writeInt(encrypted.length);
                writer.write(encrypted);
                writer.flush();

                try {
                    sendAndReceive(conn);
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }

            }
        }

    }

    /**
     * Takes an already established connection and creates a second one, to receive from one and send on the other
     *
     * @param connection the {@link Socket} that is already created
     * @throws Exception thrown by io stream
     */
    public void sendAndReceive(Socket connection) throws Exception {

        DataInputStream readFromPrev = new DataInputStream(new BufferedInputStream(connection.getInputStream()));
        DataOutputStream writeToPrev = new DataOutputStream((connection.getOutputStream()));

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        int byteLength;
        byte[] bytes;
        byte[] encrypted;
        byte[] decrypted;
        String receivedMessage;
        String sentMessage;


        while(true) {

            byteLength = readFromPrev.readInt();
            bytes = new byte[byteLength];
            readFromPrev.readFully(bytes);

            decrypted = CryptoUtil.decryptAES(bytes, bytes.length, getSecretKey());
            receivedMessage = new String(decrypted, StandardCharsets.UTF_8);

            logger.info(String.format("Received message from client: %s", receivedMessage));

            if("quit".equalsIgnoreCase(receivedMessage)) {
//                String msgToClient = "bye";
//                sentMessage = String.format("%s -love from server", msgToClient);
//                encrypted = CryptoUtil.encryptAES(sentMessage.getBytes(), sentMessage.getBytes().length, getSecretKey());
//                writeToPrev.writeInt(encrypted.length);
//                writeToPrev.write(encrypted);
                logger.warn("Shutting down...");
                readFromPrev.close();
                writeToPrev.close();
                reader.close();
                server.close();
                System.exit(0);
                break;
            } else {
                System.out.println("Write your message to the client: ");
                String msgToClient = reader.readLine();

                encrypted = CryptoUtil.encryptAES(msgToClient.getBytes(), msgToClient.getBytes().length, getSecretKey());
                writeToPrev.writeInt(encrypted.length);
                writeToPrev.write(encrypted);
                logger.info("Message sent: " + msgToClient);
            }

        }

    }


    public static void main(String[] args) throws IOException {
        List<String> argsList = Arrays.asList(args);

        int port;

        if(argsList.contains("-p")) {
            port = Integer.parseInt(argsList.get(argsList.indexOf("-p") + 1));
        } else {
            System.out.println("usage: java OnionServer.java [options]");
            System.out.println("options:\n\t-p <port to use>:\tThe port number the server should use");
            System.out.println("\t\t\tUser must guarantee that there are enough nodes for the path length. Otherwise, there will be unpredictable behavior.");
            return;
        }

        OnionServer server = new OnionServer(port);
        server.run();
    }
}
