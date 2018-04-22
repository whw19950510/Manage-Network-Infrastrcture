
## UDP packet transmission
The UDP packet transmission is transmitted using the UDP DatagramSocket, which represents a socket address binds to the local network port and Inetaddress. The UDP packet is represented and excapsulated as DatagramPacket, the socket doesn't specify the destincation address where the datagramPacket will be sent to. It is specified when you constructed the UDP DatagramPacket; When you always want to send the DatagramPacket to a certain destination, you should call connet(InetAddress ip, port) such that the packet will always be transmitted to this certain host.
