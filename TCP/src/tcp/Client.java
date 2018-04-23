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
Clarification: 
1. The checksum is used whever to check and drop the packets, must match the checkSum first;
2. MTU includes the packets header, whenver lost connection it will continuly to resend for several times and finally exit the system;
3. If the established ACK is dropped but the next packet is not dropped, then everything is fine. Otherwise, the connection must be reset.
You still can send out data packets as usual;
*/
public class Client {
    private int port;                       // port which client is running on
    private InetAddress remoteIP;           // IP of remote process
    private int serverport;                 // port at which the remote process is running
    String filename;                        // Name of the file to be transferred
    private int mtu;                        // maximum transmission unit, in byte
    private int sws;                        // Sliding window size for buffering, in byte

    Map<Long, Double>unACK;              // HashMap for recording each packet's time out mechanism
    Map<Long, Packet>sendBuffer;         // send buffer for unacknowledgement data
    private DatagramSocket sendsocket;

    public static int Maximum_Retransmission = 16;
    private final double alpha = 0.875;     //  params used for RTT estimation
    private final double beta = 0.75;
    private int curbufferSize;
    private long curSequencenumber;
    private long lastSequencenumber;
    private double curTimeout;                 // Estimate the timeout based on RTT time??????????
    private Map<Long, Integer>duplicateACK;  // statistics of times of ACK packets received

    private volatile boolean isEstablished = false;

    public Client(int port, String remoteIP, int serverport, String filename, int mtu, int sws) {
        this.port = port;
        this.serverport = serverport;
        this.filename = filename;
        this.mtu = mtu;
        this.sws = sws;
        unACK = new ConcurrentHashMap<Long, Double>();
        duplicateACK = new HashMap<Long, Integer>();
        curbufferSize = 0;
        try {
            this.remoteIP = InetAddress.getByName(remoteIP);
            sendsocket = new DatagramSocket(port, InetAddress.getLocalHost());            
            sendsocket.connect(this.remoteIP, serverport);
        } catch(SocketException e) {
            e.printStackTrace();
        } catch(UnknownHostException e) {
            e.printStackTrace();
        } finally {
            sendsocket.disconnect();
            sendsocket.close();
        }
        // Retransmission policy: Timeout of packet / duplicate ACK is 3 times
        Runnable runnable = new Runnable() {
			public void run() {
				// update the timeout value for each ConcurrentHashMap Entry, if timeout resend current packet
				for(Long cur:unACK.keySet()) {
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
        curSequencenumber = 1;              // The sequencenumber is set to 1
    }

    // write the file contents into byte array and encapsulate into Packet
    public Packet generateDatapacket(long start, int len) {
        byte[] payload = new byte[len];
        Packet datapac = new Packet();
        int datalength = len;
        InputStream ins = null;
        try {
            File file = new File(this.filename);
            ins = new FileInputStream(file);
            ins.skip(start);    // skip the first few bytes of the File
            datalength = ins.read(payload, 0, len);     // Actual datalength reads into the buffer
            ins.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                ins.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        datapac.setSequencenumber(start);
        datapac.setChecksum();
        datapac.setLength(datalength);
        datapac.setTimestamp(System.nanoTime());
        datapac.setData(payload);
        return datapac;
    }

    // Receive ACK packet and sending Packet from remote server
    public void runClient() {
        Packet recv = new Packet();
        // sendbuffer used as buffer for the received packet
        // Continuing sending out packets until the sliding window is full & wait for response
        int datalength = mtu - 24;          // maximum data payload bytes
        ScheduledExecutorService sendService = Executors.newSingleThreadScheduledExecutor();
        Runnable sendRunnable = new Runnable() {
            @Override
            public void run() {
                // Continue to send out packets until the send Buffer becomes full                  
                while(curbufferSize < sws) {
                    if(isEstablished == false) 
                        continue;
                    Packet datapac = generateDatapacket(curSequencenumber, datalength);
                    sendPacket(datapac);
                    sendBuffer.put(curSequencenumber, datapac);
                    unACK.put(curSequencenumber, 0.0);
                    lastSequencenumber = curSequencenumber;
                    curSequencenumber += datapac.getLength();
                    curbufferSize += datapac.getLength();
                }
            }
        };
        sendService.scheduleAtFixedRate(sendRunnable, 0, 1, TimeUnit.NANOSECONDS);            
        // Seems like a background thread running????????????????????
        while(true) {
            try {
                sendsocket.receive(recv.getPacket());
            } catch(IOException e) {
                e.printStackTrace();
                continue;
            }
            int orichecksum = recv.getChecksum();
            recv.setChecksum();
            if(recv.getChecksum() != orichecksum)
                continue;
            // Connection establishes response
            if(recv.isSYN()) {
                Packet connectAck = new Packet();
                connectAck.setAcknumber(recv.getSequencenumber() + 1);
                connectAck.setSYN();
                connectAck.setChecksum();
                connectAck.setLength(0);

                sendPacket(connectAck);
                isEstablished = true;
            }
            // Receiver side's close request, will close the connection finally
            else if(recv.isFIN()) {
                Packet closeAck = new Packet();
                closeAck.setAcknumber(closeAck.getSequencenumber() + 1);
                closeAck.setChecksum();
                closeAck.setLength(0);
                closeAck.setACK();
                closeAck.setTimestamp(System.nanoTime());

                sendPacket(closeAck);
                sendsocket.disconnect();
                sendsocket.close();
                isEstablished = false;
                System.exit(0);
            }
            else if(recv.isACK()) {
                long acknumber = recv.getAckmber();
                long sendTime = recv.getTimestamp();
                double RTT = System.nanoTime() + (1 - alpha) * sendTime;
                // Not acknowledgeing the last sendout packets
                // if(acknumber - lastSequencenumber != 1) {
                    if(duplicateACK.containsKey(acknumber) == false) {
                        duplicateACK.put(acknumber, 1);                            
                    } else {
                        duplicateACK.put(acknumber, duplicateACK.get(acknumber) + 1);
                        // fast retransmission
                        if(duplicateACK.get(acknumber) == 3) {
                            Packet resendtarget = sendBuffer.get(acknumber);
                            sendPacket(resendtarget);
                            unACK.put(acknumber, 0.0); 
                            resendtarget.setResendTime(resendtarget.getResendTime() + 1);
                            resendtarget.setTimestamp(System.nanoTime());
                        }
                    }
                // } else {
                //     curbufferSize -= sendBuffer.get(acknumber - 1).getLength();                    
                //     lastSequencenumber = acknumber;
                //     sendBuffer.remove(acknumber - 1);
                //     duplicateACK.remove(acknumber - 1);
                // }
                curbufferSize -= sendBuffer.get(acknumber - 1).getLength();  // ???????-data length/ mtu /28 bytes                  
                unACK.remove(acknumber - 1);
                sendBuffer.remove(acknumber - 1);
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