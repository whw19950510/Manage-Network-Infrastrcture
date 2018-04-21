import java.io.*;
import java.net.DatagramPacket;
import java.util.*;
class Packet {
    // private byte[] pack; 
    DatagramPacket pack;
    public static byte SYN = 0b1000;
    public static byte ACK = 0x01;
    public static byte FIN = 0x10;
    
    Packet() {
        byte[] buf = new byte[28];
        pack = new DatagramPacket(buf, buf.length);
    }

    Packet(DatagramPacket pack) {
        this.pack = pack;
    }
    
    public DatagramPacket getPacket() {
        return pack;
    }

    public int getSequencenumber() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf);
        // int first = ins.read();
        // int sec = ins.read();
        // int third = ins.read();
        // int fou = ins.read();
        // int ans = first + sec * 256 + third * 256 * 256 + fou * 256 * 256 * 256;
        // int ans = (pack[0]&0xFF) << 3*8 + (pack[1]&0xFF) << 2*8 + (pack[2]&0xFF) << 8 + (pack[3]&0xFF);
        int count = 0;
        int seq = 0;
        while(count < 4) {
            try {
                seq += ins.read() * (int)Math.pow(256, count);   
            } catch (IOException e) {
                System.out.print(e.getStackTrace());
            }
            count++;
        }
        return seq;  
    }
    public int getAck() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf, 4,4);
        // int first = ins.read();
        // int sec = ins.read();
        // int third = ins.read();
        // int fou = ins.read();
        // int ack = first + sec * 256 + third * 256 * 256 + fou * 256 * 256 * 256;
        // int ans = (pack[4]&0xFF) << 3*8 + (pack[5]&0xFF) << 2*8 + (pack[6]&0xFF) << 8 + (pack[7]&0xFF);
        int count = 0;
        int ack = 0;
        while(count < 4) {
            try {
                ack += ins.read() * (int)Math.pow(256, count);                
            } catch(IOException e) {
                System.out.print(e.getStackTrace());                
            }
            count++;
        }
        return ack;        
    }
    public long getTime() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf, 8, 8);
        int count = 0;
        long timeStamp = 0;
        while(count < 8) {
            try {
                timeStamp += ins.read() * (int)Math.pow(256, count);                
            } catch(IOException e) {
                System.out.print(e.getStackTrace());
            }
            count++;
        }
        return timeStamp;        
        // int ans = (pack[8]&0xFF) << 3*8 + (pack[9]&0xFF) << 2*8 + (pack[9]&0xFF) << 8 + (pack[10]&0xFF);
    }
    public int getLength() {
        // int ans = (pack[11]&0xFF) << 3*8 + (pack[12]&0xFF) << 2*8 + (pack[13]&0xFF) << 8 + (pack[14]&0xFF);
        int len = 0;
        return len;        
    }
    public boolean isACK() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf, 19, 1);
        int flag = 0;
        try {
            flag = ins.read();            
        } catch(IOException e) {
            System.out.print(e.getStackTrace());
        }
        return (flag & 1) == 1;
    }

    public boolean isFIN() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf, 19, 1);
        int flag = 0;
        try{
            flag = ins.read();
        } catch(IOException e) {
            System.out.print(e.getStackTrace());
        }
        return ((flag & 2) >> 1) == 1;
    }

    public boolean isSYN() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf, 19, 1);
        int flag = 0;
        try {
            flag = ins.read();            
        } catch(IOException e) {
            System.out.print(e.getStackTrace());
        }
        return ((flag & 3) >> 1) == 1;
    }
    public int getChecksum() {
        // int ans = (pack[11]&0xFF) << 3*8 + (pack[12]&0xFF) << 2*8 + (pack[13]&0xFF) << 8 + (pack[14]&0xFF);
        int checkSum = 0;
        int count = 0;
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf, 22, 2);
        while(count < 2) {
            try {
                checkSum += ins.read() * (int)Math.pow(256, count);                
            } catch(IOException e) {
                System.out.print(e.getStackTrace());
            }
            count ++;
        }
        return checkSum;        
    }
    public int getData() {
        int data = 0;
        int count = 0;
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf, 24, 4);
        while(count < 4) {
            try {
                data += ins.read() * (int)Math.pow(256, count);                
            } catch(Exception e) {
                System.out.print(e.getStackTrace());
            }
            count ++;
        }
        // int ans = (pack[24]&0xFF) << 3*8 + (pack[25]&0xFF) << 2*8 + (pack[26]&0xFF) << 8 + (pack[27]&0xFF);
        return data;        
    }

    // Set function  for respective fields

    public void setSeq(int seq) {
        byte[] data = new byte[4];

    }

    public void setAck(int ack) {

    }

    public void setTimestamp() {
        long curtime = System.nanoTime();

    }

    public void setLength(int length) {

    }

    public void setFlag() {

    }

    public void setChecksum() {

    }

    public void setData() {

    }

}