import utils.CryptoUtil;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class OnionNode {

    // Network info of node
    private String hostName;
    private String IPAddress;
    private int port;

    // Secret symmetric key
    private SecretKey secretKey;

    private ServerSocket serverSocket;
    private Socket socket;

    private Pattern IPv4 = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

    public OnionNode(int port) throws UnknownHostException {
        this.hostName = InetAddress.getLocalHost().getHostName();
        this.IPAddress = InetAddress.getByName(InetAddress.getLocalHost().getHostName()).getHostAddress();
        this.port = port;

        System.out.println("Node starting...");
        System.out.println("Node started at " + this.getIPAddress() + " on port " + this.getPort());
    }

    public String getHostName() {
        return hostName;
    }

    public String getIPAddress() {
        return IPAddress;
    }

    public int getPort() {
        return port;
    }

    private void setSecretKey(SecretKey key) {
        this.secretKey = key;
    }

    private SecretKey getSecretKey() {
        return this.secretKey;
    }

    /**
     * Sets up a connection between itself and a client or another node
     *
     * @throws Exception
     */
    public void setupConnection() throws Exception {
        serverSocket = new ServerSocket(port);

        Socket connection = serverSocket.accept();
        DataInputStream dis = new DataInputStream(new BufferedInputStream(connection.getInputStream()));

        int byteLength = 0;
        byte[] theBytes;
        String received;

        while(true) {
            byteLength = dis.readInt();
            theBytes = new byte[byteLength];
            dis.readFully(theBytes);

            String tmp = new String(theBytes, StandardCharsets.UTF_8);

            DataOutputStream dos = new DataOutputStream(connection.getOutputStream());


            if("GivePK!!!".equals(tmp)) {
                System.out.println("Received from client: " + tmp + "\n");
                System.out.println("Sending public key back to client\n");

                PublicKey pk = loadRSAPublicKey();


                byte[] stBytes = pk.getEncoded();

                dos.writeInt(stBytes.length);
                dos.write(stBytes);
                System.out.println("Public key was sent\n");
                dos.flush();

            } else if(getSecretKey() == null) { // IPv4.matcher(tmp.substring(0, 11)).matches()
                byte[] decrypted = CryptoUtil.decryptRSA(theBytes, theBytes.length, loadRSAPrivateKey());

                SecretKey sk = new SecretKeySpec(decrypted, "AES");
                setSecretKey(sk);

                System.out.println("New message received from client: " + new String(sk.getEncoded(), StandardCharsets.UTF_8));

//                String response = "Received secret key from client!";
//                dos.writeInt(response.getBytes().length);
//                dos.write(response.getBytes());
//                dos.flush();

            } else {
                byte[] decrypted = CryptoUtil.decryptAES(theBytes, theBytes.length, getSecretKey());

                String st = new String(decrypted, StandardCharsets.UTF_8);
                System.out.println("Message received from client: " + st);

                String response = "Received secret key from client!";
                dos.writeInt(response.getBytes().length);
                dos.write(response.getBytes());
                dos.flush();
            }



//            System.out.println("New message recieved from client: " + tmp);

//            System.out.println(theBytes.length);
//            received = new String(theBytes, StandardCharsets.UTF_8);
//            System.out.println("Recieved from client: " + received + "\n");
//            System.out.println("Sending public key back to client\n");

//            PublicKey pk = loadRSAPublicKey();

//            DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
//            byte[] stBytes = pk.getEncoded();

//            dos.writeInt(stBytes.length);
//            dos.write(stBytes);
//            dos.flush();

//            byteLength = dis.readInt();
//            theBytes = new byte[byteLength];
//            dis.readFully(theBytes);

//            CryptoUtil.decryptRSA(theBytes, theBytes.length, loadRSAPrivateKey());

//            byte[] decrypted = CryptoUtil.decryptRSA(theBytes, theBytes.length, loadRSAPrivateKey());

//            SecretKey sk = new SecretKeySpec(decrypted, "AES");
//            setSecretKey(sk);


//            received = new String(sk.getEncoded(), StandardCharsets.UTF_8);
//            System.out.println("Recieved from client: " + received + "\n");


//            String test = "Dette er en test for å sjekke secret key";

//            CryptoUtil.encryptAES(test.getBytes(), test.length(), getSecretKey());
//            byte[] testEncrypted = CryptoUtil.encryptAES(test.getBytes(), test.getBytes().length, getSecretKey());

//            dos.writeInt(testEncrypted.length);
//            System.out.println("Length of symmetric encrypted message: " + testEncrypted.length);

//            dos.write(testEncrypted);
//            String tmp2 = new String(testEncrypted, StandardCharsets.UTF_8);
//            System.out.println("Symmetric encrypted message: " + tmp2);
//            dos.flush();





            /*if(secretKey != null) {
                break;
            }*/
        }

    }


    public void forwardData(String ip, int port) throws IOException {
        socket = new Socket(ip, port);

    }


    /**
     * Creates a new private-public keypair of type RSA
     *
     * @throws NoSuchAlgorithmException if the generator doesn't recognize the encryption algorithm
     * @throws IOException if the saveRSA method fails to save the keys
     */
    private void createRSA() throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);

        KeyPair kp = kpg.generateKeyPair();

        PublicKey pub = kp.getPublic();
        PrivateKey pvt = kp.getPrivate();
        saveRSA(pub, pvt);
        //cleanUp(new File("./keys"));
    }

    /**
     * Saves a public and a private key to two different files
     *
     * @param pub the public key
     * @param pvt the private key
     * @throws IOException if it fails to save to file
     */
    private void saveRSA(PublicKey pub, PrivateKey pvt) throws IOException {
        String pubOutFile = "rsa_pub.pub";
        String pvtOutFile = "rsa_pvt.key";

        File dir = new File("./src/main/java/keys/");
        boolean dirCreated = dir.mkdir();

        if(dirCreated) {
            System.out.println("Directory created");

            File rsaPub = new File("./src/main/java/keys/" + pubOutFile);
            File rsaPvt = new File("./src/main/java/keys/" + pvtOutFile);

            try(FileOutputStream fosPub = new FileOutputStream(rsaPub)) {
                fosPub.write(pub.getEncoded());
            } catch (FileNotFoundException e) {
                System.out.println(e);
            }

            try(FileOutputStream fosPvt = new FileOutputStream(rsaPvt)) {
                fosPvt.write(pvt.getEncoded());
            } catch (FileNotFoundException e) {
                System.out.println(e);
            }
        }
    }

    private PrivateKey loadRSAPrivateKey() {

        try {
            Path path = Paths.get("./src/main/java/keys/rsa_pvt.key");
            byte[] bytes = Files.readAllBytes(path);

            PKCS8EncodedKeySpec ks = new PKCS8EncodedKeySpec(bytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey pvt = kf.generatePrivate(ks);

            return pvt;
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println(e);
        }

        return null;
    }

    private PublicKey loadRSAPublicKey() {

        try {
            Path path = Paths.get("./src/main/java/keys/rsa_pub.pub");
            byte[] bytes = Files.readAllBytes(path);

            X509EncodedKeySpec ks = new X509EncodedKeySpec(bytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey pub = kf.generatePublic(ks);

            return pub;
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println(e);
        }

        return null;
    }

    private void cleanUp(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                if (!Files.isSymbolicLink(f.toPath())) {
                    cleanUp(f);
                }
            }
        }
        file.delete();
    }

    public static void main(String[] args) throws Exception {
        List<String> argsList = Arrays.asList(args);

        if(argsList.contains("-p")) {
            int port = Integer.parseInt(argsList.get(argsList.indexOf("-p") + 1));
            OnionNode node = new OnionNode(port);
            node.createRSA();
            node.setupConnection();
            node.cleanUp(new File("./src/main/java/keys"));



            /*System.out.println("Public key format: \n" + node.getPublicKey() + "\n\n");
            System.out.println("Private key format: \n" + node.getPrivateKey() + "\n\n");*/
        }

    }
}
