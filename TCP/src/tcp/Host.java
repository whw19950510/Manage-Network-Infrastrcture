package tcp;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class Host {
    private int mtu;    // maximum transmission unit
    private int port;   // port which is receiving data
    private int sws;    // sliding window size
    private DatagramSocket receiveSocket;

    private int seqExpect;
    private Queue<Packet> record;
    private Map<Integer, Packet>receiveBuffer;

    public Host(int port, int MTU, int sws) {
        this.port = port;
        this.mtu = MTU;
        this.sws = sws;
        record = new LinkedList<Packet>();
        receiveBuffer = new HashMap<Integer, Packet>();
        seqExpect = 0;
        try {
            receiveSocket = new DatagramSocket(port, InetAddress.getLocalHost()); 
        } catch(Exception e) {
            System.out.print(e.getStackTrace());
        }
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
                    if(dealpack.getSequencenumber() != 0)           // this is the first packet need to be acknowledged
                        continue;
                    Packet connectionACK = new Packet();
                    int clientseq = dealpack.getSequencenumber();
                    long clientTime = dealpack.getTimestamp();
                    Packet response = new Packet();
                    response.setAcknumber(clientseq + 1);
                    seqExpect = clientseq + 1;              // record the next data byte to be received
                    response.setSYN();
                    response.setSequencenumber(0);        // Initial sequence number for Host side
                    response.setTimestamp(clientTime);
                    response.setChecksum();
                    // Where to send out this SYN packets:??????????????????????
                    connectionACK.getPacket().setAddress(receivePacket.getAddress());
                    connectionACK.getPacket().setPort(90);
                    receiveSocket.send(connectionACK.getPacket());
                } else if(dealpack.isACK()) {
                    continue;
                } else  if(dealpack.isFIN()) {
                    // The packet is for connection close request of client side

                } else {
                    // Datasegment to be received
                    Packet hostack = new Packet();                    
                    int clientseq = dealpack.getSequencenumber();
                    // Not in sequence packet
                    if(clientseq != seqExpect) {
                        record.offer(dealpack);
                        hostack.setAcknumber(seqExpect);          // Retransmit this ack expected for the continuous packet 
                        hostack.setTimestamp(dealpack.getTimestamp());
                        hostack.setACK();
                        hostack.setLength(0);
                    } else {
                        hostack.setAcknumber(seqExpect);
                        hostack.setTimestamp(dealpack.getTimestamp());
                        hostack.setACK();
                        hostack.setLength(0);
                    }   

                }
            }
        } catch(Exception e) {
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