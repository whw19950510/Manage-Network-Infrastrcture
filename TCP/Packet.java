import java.io.*;
import java.net.DatagramPacket;
import java.util.*;

class Packet {
    DatagramPacket pack;
    public static byte SYN = 0b1000;
    public static byte ACK = 0x01;
    public static byte FIN = 0x10;
    
    private int resendTime;         // The packet may have been resent for several times
    Packet() {
        byte[] buf = new byte[28];
        pack = new DatagramPacket(buf, buf.length);
        resendTime = 0;
    }

    Packet(DatagramPacket pack) {
        this.pack = pack;
        resendTime = 0;
    }

    public DatagramPacket getPacket() {
        return pack;
    }

    public int getResendTime() {
        return resendTime;
    }

    public int getSequencenumber() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf);
        int count = 4;
        int seq = 0;
        try {
            while(count > 0) {
                count --;
                seq += ins.read() * (int)Math.pow(256, 4 - count);  
            }
        } catch (IOException e) {
                System.out.print(e.getStackTrace());
        } finally { 
            try {
                ins.close();
            } catch(IOException a) {
                a.printStackTrace();
            }
        }
        return seq;  
    }
    public int getAckmber() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf);
        int count = 0;
        int  ack = 0;
        try {
            ins.skip(4);
            while(count > 0) {
                count--;
                ack += ins.read() * (int)Math.pow(256, count);                
            }
         } catch(IOException e) {
            System.out.print(e.getStackTrace());                
        } finally {
            try {
                ins.close();
            } catch(IOException a) {
                a.printStackTrace();
            }
        }
        return ack;        
    }
    public long getTimestamp() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf);
        int count = 8;
        long timeStamp = 0;
        try {
            ins.skip(8);
            while(count > 0) {
                count--;            
                timeStamp += ins.read() * (int)Math.pow(256, count);                
            }
        } catch(IOException e) {
            System.out.print(e.getStackTrace());
        } finally {
            try {
                ins.close();
            } catch(IOException a) {
                a.printStackTrace();
            }
        }
        return timeStamp;        
    }
    public int getLength() {
        byte[] buf = pack.getData();
        int len = 0;
        InputStream ins = new ByteArrayInputStream(buf);
        int count = 4;
        try {
            ins.skip(16);
            while(count > 0) {
                count --;
                if(count == 3) {
                    // Last byte need to be erased
                    int temp = (ins.read() >> 3);
                    len += temp;
                } else {
                    len += ins.read() * (int)Math.pow(256, count);
                }
            } 
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            try {
                ins.close();
            } catch(IOException a) {
                a.printStackTrace();
            }
        }
        return len;        
    }
    public boolean isACK() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf);
        int flag = 0;
        try {
            ins.skip(19); 
            flag = ins.read();            
        } catch(IOException e) {
            System.out.print(e.getStackTrace());
        } finally {
            try {
                ins.close();
            } catch(IOException a) {
                a.printStackTrace();
            }
        }
        return (flag & 1) == 1;
    }

    public boolean isFIN() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf);
        int flag = 0;
        try{
            ins.skip(19);      
            flag = ins.read();
        } catch(IOException e) {
            System.out.print(e.getStackTrace());
        } finally {
            try {
                ins.close();
            } catch(IOException a) {
                a.printStackTrace();
            }
        }
        return ((flag & 2) >> 1) == 1;
    }

    public boolean isSYN() {
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf);
        int flag = 0;
        try {
            ins.skip(19);       
            flag = ins.read();            
        } catch(IOException e) {
            System.out.print(e.getStackTrace());
        } finally {
            try {
                ins.close();
            } catch(IOException a) {
                a.printStackTrace();
            }
        }
        return ((flag & 3) >> 1) == 1;
    }
    public int getChecksum() {
        int checkSum = 0;
        int count = 2;
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf);
        try {
            ins.skip(22);
            while(count > 0) {
                count --;
                checkSum += ins.read() * (int)Math.pow(256, count);                
            } 
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            try {
                ins.close();
            } catch(IOException a) {
                a.printStackTrace();
            }
        }
        return checkSum;        
    }
    public int getData() {
        int data = 0;
        int count = 4;
        byte[] buf = pack.getData();
        InputStream ins = new ByteArrayInputStream(buf, 24, 4);
        try {
            ins.skip(24);
            while(count > 0) {
                count --;            
                data += ins.read() * (int)Math.pow(256, count);                
            } 
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            try {
                ins.close();
            } catch(IOException a) {
                a.printStackTrace();
            }
        }
        return data;        
    }

    // Set function  for respective fields

    public void setSequencenumber(int seq) {
        byte[] data = new byte[4];

    }

    public void setAcknumber(int ack) {
        byte[] buf = pack.getData();
        // write 4 bytes for this integer number
        pack.setData(buf);
    }

    public void setTimestamp(long time) {
        long curtime = System.nanoTime();

    }

    public void setLength(int length) {

    }

    public void setSYN() {

    }

    public void setACK() {

    }

    public void setFIN() {

    }
    // Should use the 2 part of sequence number to calculate this checksum field and make 1's complement
    public void setChecksum() {

    }

    public void setData(byte[] data) {

    }

    public void setResendTime(int resendTime) {
        this.resendTime = resendTime;
    }

}