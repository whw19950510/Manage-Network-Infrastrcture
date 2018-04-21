import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;


/*
Entity which will send data to server side
*/
class Client {
    private int port;       // port which client is running on
    private InetAddress remoteIP;   // IP of remote process
    private int serverport; // port at which the remote process is running
    String filename;        // Name of the file to be transferred
    private int mtu;        // maximum transmission unit
    private int sws;        // Sliding window size for buffering

    private DatagramSocket sendsocket;

    public Client(int port, int remoteIP, int serverport, String filename, int mtu, int sws) {
        this.port = port;
        this.remoteIP = InetAddress.getAllByName(remoteIP);
        this.serverport = serverport;
        this.filename = filename;
        this.mtu = mtu;
        this.sws = sws;
        try {
            sendsocket = new DatagramSocket(port, InetAddress.getLocalHost());            
        } catch(SocketException e) {
            System.out.print(e.getStackTrace());
        }
    }

    
}