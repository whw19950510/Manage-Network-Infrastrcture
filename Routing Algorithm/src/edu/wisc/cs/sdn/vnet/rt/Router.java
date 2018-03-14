package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import java.nio.ByteBuffer;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		if(etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			return;
		} else {
			IPv4 packetHeader = (IPv4)etherPacket.getPayload();
			int headerBytes = 4*packetHeader.getHeaderLength();
			/* Checksum calculation begin */
			// reset checksum before computing, Compare new checksum with original to make sure no error during transmission process
			short oriChecksum = packetHeader.getChecksum();
			packetHeader.resetChecksum();
			// serialize calculate checksum into byte array
			byte[] headerdata = packetHeader.serialize();
			packetHeader.deserialize(headerdata, 0, headerdata.length);
			
			if(oriChecksum != packetHeader.getChecksum())
				return;
			/* Checksum part end */
			// TTL check, recompute the checksum
			packetHeader = packetHeader.setTtl((byte)(packetHeader.getTtl() - 1));
			if(packetHeader.getTtl() == 0)
				return;
			packetHeader.resetChecksum();
			byte[] sendheader = packetHeader.serialize();
			packetHeader.deserialize(sendheader, 0, sendheader.length);
			
			/** 
			* the packet’s destination IP address exactly matches 
			* one of the interface’s IP addresses, can't be the incoming interface,
			* then you do not need to do any furtherprocessing, 
			* your router should drop the packet.
			*/
			for(String cur:this.interfaces.keySet()) {
				int ifaceIpaddr = this.interfaces.get(cur).getIpAddress();
				if((packetHeader.getDestinationAddress()) == ifaceIpaddr)
					return;
			}
			// destination addr is not router interface
			RouteEntry target = this.routeTable.lookup(packetHeader.getDestinationAddress());
			if(target == null)
				return;
			Iface outInterface = target.getInterface();
			if(outInterface == inIface)
				return;
			MACAddress newSourceMAC = outInterface.getMacAddress();
			ArpEntry macEntry = null;
			// It means that the host is directly connected to the router, no next hop
			if(target.getGatewayAddress() == 0) {
				macEntry = this.arpCache.lookup(packetHeader.getDestinationAddress());//the host is directly conncted to the iface
			} else {
				macEntry = this.arpCache.lookup(target.getGatewayAddress());
			}
			if(macEntry == null)
				return;
			MACAddress newDestMAC = macEntry.getMac();
			etherPacket = etherPacket.setSourceMACAddress(newSourceMAC.toBytes());			
			etherPacket = etherPacket.setDestinationMACAddress(newDestMAC.toBytes());
			sendPacket(etherPacket, outInterface);
		}
		
		/********************************************************************/
	}
}
