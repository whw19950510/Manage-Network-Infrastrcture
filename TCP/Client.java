import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;


/*
Entity which will is sending data to server side
*/
class Client {
    private int port;               // port which client is running on
    private InetAddress remoteIP;   // IP of remote process
    private int serverport;         // port at which the remote process is running
    String filename;                // Name of the file to be transferred
    private int mtu;                // maximum transmission unit, in byte
    private int sws;                // Sliding window size for buffering, in byte

    Map<Integer, Long>unACK;        // HashMap for recording each packet's time out mechanism
    // Map<Integer, Integer>sendFre;
    Map<Integer, Packet>sendBuffer;        // send buffer for unacknowledgement data
    private DatagramSocket sendsocket;

    public static int Maximum_Retransmission = 16;
    private int lastSeqnumber;
    private int curTimeout;

    public Client(int port, String remoteIP, int serverport, String filename, int mtu, int sws) {
        this.port = port;
        this.serverport = serverport;
        this.filename = filename;
        this.mtu = mtu;
        this.sws = sws;
        unACK = new ConcurrentHashMap<Integer, Long>();

        try {
            this.remoteIP = InetAddress.getByName(remoteIP);
            sendsocket = new DatagramSocket(port, InetAddress.getLocalHost());            
            sendsocket.connect(this.remoteIP, serverport);
        } catch(Exception e) {
            System.out.print(e.getStackTrace());
            System.exit(1);
        }

        Runnable runnable = new Runnable() {
			public void run() {
				// update the timeout value for each ConcurrentHashMap Entry, if timeout resend current packet
				for(Integer cur:unACK.keySet()) {
                    unACK.put(cur, unACK.get(cur) + 1);
                    if(unACK.get(cur) > curTimeout) {
                        sendBuffer.get(cur).setResendTime(sendBuffer.get(cur).getResendTime() + 1);
                        if(sendBuffer.get(cur).getResendTime() > Client.Maximum_Retransmission) {
                            System.err.print("Exceed the maximum transmission time\n");
                            System.exit(1);
                        }
                        sendsocket.send(sendBuffer.get(cur).getPacket());
                    }
                }
			}
		};
        ScheduledExecutorService manipulateTimeout = Executors.newSingleThreadScheduledExecutor();
        manipulateTimeout.scheduleAtFixedRate(runnable, 0, 1, TimeUnit.NANOSECONDS);
    }

    public void connectionRequest() {
        try {
            Packet connectionreq = new Packet();
            connectionreq.setSequencenumber(0);
            connectionreq.setTimestamp(System.nanoTime());
            connectionreq.setSYN();
            connectionreq.setChecksum();
            connectionreq.setLength(0);
            sendsocket.send(connectionreq.getPacket());
            lastSeqnumber = 0;              // Record the acknowledged number expected
        } catch (Exception e) {
            System.out.println(e.getStackTrace());
        } finally {
            sendsocket.disconnect();
            sendsocket.close();
        }
    }

    public byte[] readFile(long start, int len) {
        byte[] datapacket = new byte[len];
        try {
            File file = new File(this.filename);
            InputStream ins = new FileInputStream(file);
            ins.skip(start);    // skip the first few bytes of the File
            ins.read(datapacket, 0, len);
            ins.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return datapacket;
    }

    // Receive ACK packet and sending Packet from remote server
    public void runClient() {
        Packet recv = new Packet();
        // Used as buffer for the received packet
        // Each single time this client sends out a packet to the server, will it receive a packet for ACK
        // Send out the first data packet and get the corresponding response
        int datalength = mtu - 24;          // maximum data payload bytes
        try {
            // Send the first data packet
            Packet datapac = new Packet();
            datapac.setSequencenumber(0);
            datapac.setChecksum();
            datapac.setTimestamp(System.nanoTime());
            datapac.setLength(datalength);
            byte[] payload = readFile(0, datalength);
            datapac.setData(payload);
            sendsocket.send(datapac.getPacket());

            while(true) {
                sendsocket.receive(recv.getPacket());
                // Connection establishes response
                if(recv.isSYN()) {
                    Packet connectAck = new Packet();
                    connectAck.setAcknumber(recv.getSequencenumber() + 1);
                    connectAck.setSYN();
                    connectAck.setChecksum();
                    connectAck.setLength(0);

                    sendsocket.send(connectAck.getPacket());

                }

                // Receiver side's close request, will close the connection finally
                if(recv.isFIN()) {
                    Packet closeAck = new Packet();
                    closeAck.setAcknumber(closeAck.getSequencenumber() + 1);
                    closeAck.setChecksum();
                    closeAck.setLength(0);

                    sendsocket.send(closeAck.getPacket());
                    sendsocket.disconnect();
                    sendsocket.close();
                    System.exit(0);
                }
                if(recv.isACK()) {
                    int acknumber = recv.getAckmber();
                    // Not acknowledgeing the last sendout packets
                    if(acknumber - lastSeqnumber != 1 || ) {

                    }
                }
            }
        } catch(Exception e) {
            System.out.println(e.getStackTrace());
        } finally {
            sendsocket.disconnect();
            sendsocket.close();
        }
    }

}