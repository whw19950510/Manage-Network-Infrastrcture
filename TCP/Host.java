
import java.net.*;
import java.util.*;
import java.io.*;

public class Host {
    private int mtu;    // maximum transmission unit
    private int port;   // port which is receiving data
    private int sws;    // sliding window size
    private DatagramSocket receiveSocket;

    private int seqExpect;
    private PriorityQueue<Integer> record;
    private Map<Integer, Packet>receiveBuffer;

    private int selfSeq = 0;
    private File recvFile = null;

    private int clientPort;
    private InetAddress clientIP;

    public Host(int port, int MTU, int sws) {
        this.port = port;
        this.mtu = MTU;
        this.sws = sws;

        record = new PriorityQueue<Integer>((a, b) -> a - b);
        receiveBuffer = new HashMap<Integer, Packet>();
        seqExpect = 0;
        selfSeq = 0;
        try {
            receiveSocket = new DatagramSocket(port, InetAddress.getLocalHost()); 
        } catch(UnknownHostException a) {
            a.printStackTrace();
        } catch(SocketException e) {
            System.out.print(e.getStackTrace());
        } 
        recvFile = new File("recv.txt");
    }
    public void runHost() {
            byte[] receiveData = new byte[mtu];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            while(true) {
                try {
                    receiveSocket.receive(receivePacket);  
                } catch(IOException e) {
                    e.printStackTrace();
                }
                Packet dealpack = new Packet(receivePacket);
                // Check for checksum first
                int orichecksum = dealpack.getChecksum();
                dealpack.setChecksum();
                if(dealpack.getChecksum() != orichecksum)
                    continue;
                // Construct the packet to be dealed wtith
                // The packet is an connection creation request
                if(dealpack.isSYN()) {
                    if(dealpack.getSequencenumber() != 0)           // this is the first packet need to be acknowledged
                        continue;
                    Packet connectionACK = new Packet(0);
                    int clientseq = dealpack.getSequencenumber();
                    long clientTime = dealpack.getTimestamp();
                    connectionACK.setAcknumber(clientseq + 1);
                    seqExpect = clientseq + 1;                      // record the next data byte to be received
                    connectionACK.setSYN();
                    connectionACK.setSequencenumber(selfSeq);        // Initial sequence number for Host side
                    selfSeq++;
                    connectionACK.setTimestamp(clientTime);
                    connectionACK.setChecksum();
                    connectionACK.setLength(0);
                    // Send packet to where this packet come from, extract from the receiving packets
                    connectionACK.getPacket().setSocketAddress(new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort()));
                    sendACKpacket(connectionACK);
                } else if(dealpack.isACK()) {
                    // receive the connection establish packet
                    continue;
                } else if(dealpack.isFIN()) {
                    // The packet is for connection close request of client side
                    Packet finack = new Packet(0);
                    int clientSeq = dealpack.getSequencenumber();
                    finack.setACK();
                    finack.setSequencenumber(0);
                    finack.setChecksum();
                    finack.setLength(0);
                    finack.setTimestamp(dealpack.getTimestamp());
                    finack.setSequencenumber(selfSeq);
                    finack.setAcknumber(clientSeq + 1);

                    Packet finserver = new Packet(0);
                    finserver.setSequencenumber(selfSeq);
                    selfSeq++;
                    finserver.setACK();
                    finserver.setChecksum();
                    finserver.setLength(0);
                    finserver.setTimestamp(System.nanoTime());
                    finserver.setAcknumber(clientSeq + 1);
                    sendACKpacket(finack);
                    sendACKpacket(finserver);
                } else {
                    // Datasegment to be received
                    int clientseq = dealpack.getSequencenumber();
                    // Not in sequence packet
                    if(clientseq > seqExpect) {
                        Packet hostack = new Packet(0);                                            
                        record.offer(clientseq);                    // if sws < record.size().....
                        receiveBuffer.put(clientseq, dealpack);
                        hostack.setSequencenumber(0);
                        hostack.setAcknumber(seqExpect);          // Retransmit this ack expected for the continuous packet 
                        hostack.setTimestamp(dealpack.getTimestamp());
                        hostack.setACK();
                        hostack.setLength(0);
                        hostack.setChecksum();
                        sendACKpacket(hostack);
                    } else {  
                        seqExpect += dealpack.getLength();
                        writeToFile(dealpack);                        
                        while (record.size() > 0 && record.peek() <= seqExpect) {
                            int bufferpacseq = record.poll();
                            int templen = receiveBuffer.get(bufferpacseq).getLength();
                            Packet formerBuffer = receiveBuffer.get(bufferpacseq);
                            writeToFile(formerBuffer);    
                            seqExpect += templen;
                            receiveBuffer.remove(bufferpacseq);
                        }
                        Packet hostack = new Packet(0);                                                
                        hostack.setAcknumber(seqExpect);
                        hostack.setTimestamp(dealpack.getTimestamp());
                        hostack.setACK();
                        hostack.setLength(0);
                        hostack.setChecksum();
                        sendACKpacket(hostack);
                    }
                }
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
    
    public void sendACKpacket(Packet ackPacket) {
        try {
            receiveSocket.send(ackPacket.getPacket());
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public void writeToFile(Packet dataseg) {
        OutputStream outstr = null;
        try {
            outstr = new FileOutputStream(recvFile);
            outstr.write(dataseg.getPacket().getData(), 24, dataseg.getLength());
        }  catch(IOException e) {
            e.printStackTrace();
        } finally {
            try {
                outstr.close();                
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }
}
