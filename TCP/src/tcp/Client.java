package tcp;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;

/* 
Clarification: 1. The checksum is used whever to check and drop the packets
2. MTU includes the packets header, whenver lost connection it will continuly to resend for several times and finally exit the system
*/
/*
Entity which will is sending data to server side
*/
public class Client {
    private int port;                       // port which client is running on
    private InetAddress remoteIP;           // IP of remote process
    private int serverport;                 // port at which the remote process is running
    String filename;                        // Name of the file to be transferred
    private int mtu;                        // maximum transmission unit, in byte
    private int sws;                        // Sliding window size for buffering, in byte

    Map<Integer, Long>unACK;                // HashMap for recording each packet's time out mechanism
    Map<Integer, Packet>sendBuffer;         // send buffer for unacknowledgement data
    private DatagramSocket sendsocket;

    public static int Maximum_Retransmission = 16;
    private final double alpha = 0.875;     //  params used for RTT estimation
    private final double beta = 0.75;
    private int curbufferSize;
    private int curSequencenumber;
    private int lastSequencenumber;
    private int curTimeout;                 // Estimate the timeout based on RTT time
    private Map<Integer, Integer>duplicateACK;  // statistics of times of ACK packets received

    public Client(int port, String remoteIP, int serverport, String filename, int mtu, int sws) {
        this.port = port;
        this.serverport = serverport;
        this.filename = filename;
        this.mtu = mtu;
        this.sws = sws;
        unACK = new ConcurrentHashMap<Integer, Long>();
        duplicateACK = new HashMap<Integer, Integer>();
        curbufferSize = 0;
        try {
            this.remoteIP = InetAddress.getByName(remoteIP);
            sendsocket = new DatagramSocket(port, InetAddress.getLocalHost());            
            sendsocket.connect(this.remoteIP, serverport);
        } catch(SocketException e) {
            e.printStackTrace();
        } catch(UnknownHostException e) {
            e.printStackTrace();
        }

        Runnable runnable = new Runnable() {
			public void run() {
				// update the timeout value for each ConcurrentHashMap Entry, if timeout resend current packet
				for(Integer cur:unACK.keySet()) {
                    unACK.put(cur, unACK.get(cur) + 1);
                    if(unACK.get(cur) > curTimeout) {
                        sendBuffer.get(cur).setResendTime(sendBuffer.get(cur).getResendTime() + 1);
                        // if the resend time exceed maximum time, lost connections just exit sending
                        if(sendBuffer.get(cur).getResendTime() > Client.Maximum_Retransmission) {
                            System.err.print("Exceed the maximum transmission time\n");
                            System.exit(1);
                        }
                        sendPacket(sendBuffer.get(cur));
                    }
                }
                // Scan for duplicate ACK for entry exceeding 3 times and resend
                for(Integer cur:duplicateACK.keySet()) {
                    if(duplicateACK.get(cur) >= 3) {
                        sendBuffer.get(cur).setResendTime(sendBuffer.get(cur).getResendTime() + 1);                        
                        sendPacket(sendBuffer.get(cur));
                    }
                }

			}
		};
        ScheduledExecutorService manipulateTimeout = Executors.newSingleThreadScheduledExecutor();
        manipulateTimeout.scheduleAtFixedRate(runnable, 0, 1, TimeUnit.NANOSECONDS);
    }

    public void connectionRequest() {
        Packet connectionreq = new Packet();
        connectionreq.setSequencenumber(0);
        connectionreq.setTimestamp(System.nanoTime());
        connectionreq.setSYN();
        connectionreq.setChecksum();
        connectionreq.setLength(0);
        sendPacket(connectionreq);
        lastSequencenumber = 0;              // Record the acknowledged number expected
    }

    public Packet generateDatapacket(long start, int len) {
        byte[] payload = new byte[len];
        Packet datapac = new Packet();
        int datalength = len;
        try {
            File file = new File(this.filename);
            InputStream ins = new FileInputStream(file);
            ins.skip(start);    // skip the first few bytes of the File
            datalength = ins.read(payload, 0, len);     // Actual datalength reads into the buffer
            ins.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        datapac.setSequencenumber((int)start);
        datapac.setChecksum();
        datapac.setLength(datalength);
        datapac.setTimestamp(System.nanoTime());
        datapac.setData(payload);
        return datapac;
    }

    // Receive ACK packet and sending Packet from remote server
    public void runClient() {
        Packet recv = new Packet();
        // Used as buffer for the received packet
        // Each single time this client sends out a packet to the server, will it receive a packet for ACK
        // Send out the first data packet and get the corresponding response
        int datalength = mtu - 24;          // maximum data payload bytes
            // Continue to send out packets until the send Buffer becomes full
            while(curbufferSize < sws) {
                Packet datapac = generateDatapacket(curSequencenumber, datalength);
                sendPacket(datapac);
                sendBuffer.put(curSequencenumber, datapac);
                unACK.put(curSequencenumber, datapac.getTimestamp());
                lastSequencenumber = curSequencenumber;
                curSequencenumber += datapac.getLength();
                curbufferSize += datapac.getLength();
            }
            // Seems like a background thread running????????????????????
            while(true) {
                try {
                    sendsocket.receive(recv.getPacket());
                } catch(IOException e) {
                    e.printStackTrace();
                }
                // Connection establishes response
                if(recv.isSYN()) {
                    Packet connectAck = new Packet();
                    connectAck.setAcknumber(recv.getSequencenumber() + 1);
                    connectAck.setSYN();
                    connectAck.setChecksum();
                    connectAck.setLength(0);

                    sendPacket(connectAck);
                }

                // Receiver side's close request, will close the connection finally
                if(recv.isFIN()) {
                    Packet closeAck = new Packet();
                    closeAck.setAcknumber(closeAck.getSequencenumber() + 1);
                    closeAck.setChecksum();
                    closeAck.setLength(0);
                    closeAck.setTimestamp(System.nanoTime());

                    sendPacket(closeAck);
                    sendsocket.disconnect();
                    sendsocket.close();
                    System.exit(0);
                }
                if(recv.isACK()) {
                    int acknumber = recv.getAckmber();
                    long sendTime = recv.getTimestamp();
                    double RTT = System.nanoTime() + (1 - alpha) * sendTime;
                    // Not acknowledgeing the last sendout packets
                    if(acknumber - lastSequencenumber != 1) {
                        lastSequencenumber = acknumber;
                        if(duplicateACK.containsKey(acknumber) == false) {
                            duplicateACK.put(acknumber, 1);                            
                        } else {
                            duplicateACK.put(acknumber, duplicateACK.get(acknumber) + 1);
                        }
                        if(duplicateACK.get(acknumber) == 3) {
                                Packet resendtarget = sendBuffer.get(acknumber);
                                sendPacket(resendtarget);
                                resendtarget.setResendTime(resendtarget.getResendTime() + 1);
                                resendtarget.setTimestamp(System.nanoTime());
                        }
                    } else {
                        sendBuffer.remove(acknumber - 1);
                        duplicateACK.remove(acknumber - 1);
                    }
                }
            }
        }

    public void printoutInfo(String pactype, double time, String flaglist, int seq, int numbytes, int acknumber) {
        System.out.printf("%s %f %s %d %d %d\n", pactype, time, flaglist, seq, numbytes, acknumber);
    }

    public void sendPacket(Packet cur) {
        try {  
            sendsocket.send(cur.getPacket());            
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

}