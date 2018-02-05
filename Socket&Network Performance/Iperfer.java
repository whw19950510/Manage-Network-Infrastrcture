import java.io.*;
import java.net.*;

public class Iperfer {
    public static void main(String[] args) throws IOException{
        if(args.length == 0 || (args[0].equals("-c") == false && args[0].equals("-s") == false)) {
            System.exit(1);
        } 
        if(args[0].equals("-c")) {
            if(args.length != 7 || args[1].equals("-h")==false || args[3].equals("-p")==false || args[5].equals("-t")==false) {
                System.out.println("Error: missing or additional arguments");                
            }
            int port = Integer.parseInt(args[4]);
            double time = Double.parseDouble(args[6]);
            char[] data = new char[1000];
            for(int i = 0; i < 1000; i++) {
                data[i] = '0';
            }
            String str = new String(data);
            if(port < 1024 || port > 65535) {
                System.out.println("Error: port number must be in the range 1024 to 65535");                                
            }
            try {
                Socket cursoc = new Socket(args[2],port);
                PrintWriter out = new PrintWriter(cursoc.getOutputStream(), true);
                long datacount = 0;
                final long startTime = System.nanoTime();
                while((System.nanoTime() - startTime)/1000000000.0 < time) {
                    datacount += str.getBytes().length;
                    out.print(str);
                    out.flush();
                }
                double actualTime = (System.nanoTime() - startTime)/1000000000.0;
                out.close();
                cursoc.close();
                double rate = datacount/(1000000*actualTime);
                System.out.println("sent="+ datacount/1000 +" KB rate="+ rate +" Mbps");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if(args[0].equals("-s")) {
            if(args.length != 3 || args[1].equals("-p") == false) {
                System.out.println("Error: missing or additional arguments");                                
            }
            int port = Integer.parseInt(args[2]);
            if(port < 1024 || port > 65535) {
                System.out.println("Error: port number must be in the range 1024 to 65535");                                
            }
            ServerSocket serverSoc = new ServerSocket(port);
            while(true) {
                try {
                    long datarecv = 0;
                    double rate = 0;
                    double time = 0;
                    long red = -1;
                    char[] buffer = new char[1000]; // a read buffer of 5KiB
                    byte[] redData;
                    System.out.println("Listening...");
                    Socket clientSocket = serverSoc.accept();
                    System.out.println("Connected client");
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()),1000);
                    long startTime = System.nanoTime();
                    while((red = in.read(buffer)) != -1) {
                        datarecv += red;
                    }
                    double actualTime = (System.nanoTime() - startTime) / 1000000000.0;
                    rate = datarecv/(1000000*actualTime);
                    System.out.println("received="+ datarecv/1000 +" KB rate="+ rate +" Mbps"); 
                    serverSoc.close();
                    System.exit(0);
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}