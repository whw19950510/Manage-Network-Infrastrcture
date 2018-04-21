
import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.util.*;

public class Host {
    private int mtu;    // maximum transmission unit
    private List<Packet> record;
    private int port;   // port which is receiving data
    private int sws;    // sliding window size
    private DatagramSocket receiveSocket;

    public Host(int port, int MTU, int sws) {
        this.port = port;
        this.mtu = MTU;
        this.sws = sws;
        record = new ArrayList<Packet>();

        receiveSocket = new DatagramSocket(port, InetAddress.getLoopbackAddress());
    }
    public void run() {
        try {

            byte[] receiveData = new byte[28];
            System.out.printf("Listening on udp:%s:%d%n",InetAddress.getLocalHost().getHostAddress(), port);     
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            while(true) {
                receiveSocket.receive(receivePacket);
                Packet dealpack = new Packet(receivePacket);
                // Construct the packet to be dealed with

                // Deal with different situation
                // The packet is an connection creation request
                if(dealpack.isSYN()) {
                    Packet connectionACK = new Packet();

                }
                if(dealpack.isACK()) {

                }
                // The packet is for connection close request of client side
                if(dealpack.isFIN()) {

                }
            }
        } catch(IOException e) {
            System.out.println(e.getStackTrace());
            System.exit(1);
        }
        
    }
    // Get & Set methods
    public void setMtu(int MTU) {
        this.mtu = MTU;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public void setSws(int sws) {
        this.sws = sws;
    }
    public int getMtu() {
        return mtu;
    }
    public int getPort() {
        return port;
    }
    
    public void sendACKpacket() {

    }
}