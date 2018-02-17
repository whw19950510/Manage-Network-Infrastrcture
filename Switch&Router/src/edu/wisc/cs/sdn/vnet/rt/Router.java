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
			// reset checksum before computing
			short oriChecksum = packetHeader.getChecksum();
			packetHeader.resetChecksum();
			byte[] header = new byte[headerBytes];
			ByteBuffer bb = ByteBuffer.wrap(header);
			bb.put((byte) (((packetHeader.getVersion() & 0xf) << 4) | (packetHeader.getHeaderLength() & 0xf)));
			bb.put(packetHeader.getDiffServ());
			bb.putShort(packetHeader.getTotalLength());
			bb.putShort(packetHeader.getIdentification());
			bb.putShort((short) (((packetHeader.getFlags() & 0x7) << 13) | (packetHeader.getFragmentOffset() & 0x1fff)));
			bb.put(packetHeader.getTtl());
			bb.put(packetHeader.getProtocol());
			bb.putShort(packetHeader.getChecksum());
			bb.putInt(packetHeader.getSourceAddress());
			bb.putInt(packetHeader.getDestinationAddress());
			if (packetHeader.getOptions() != null)
				bb.put(packetHeader.getOptions());
			if (packetHeader.getChecksum() == 0) {
				bb.rewind();
				int accumulation = 0;
				for (int i = 0; i < packetHeader.getHeaderLength() * 2; ++i) {
					accumulation += 0xffff & bb.getShort();
				}
				accumulation = ((accumulation >> 16) & 0xffff)
						+ (accumulation & 0xffff);
				// packetHeader.checksum = (short) (~accumulation & 0xffff);
				packetHeader = packetHeader.setChecksum((short) (~accumulation & 0xffff));//short contains 16 bits				
				bb.putShort(10, packetHeader.getChecksum());
			}
			if(oriChecksum != packetHeader.getChecksum())
				return;
			/* Checksum part end */
			// TTL check
			packetHeader = packetHeader.setTtl((byte)(packetHeader.getTtl() - 1));
			if(packetHeader.getTtl() == 0)
				return;
			
			/** 
			* the packet’s destination IP address exactly matches 
			* one of the interface’s IP addresses, not necessarily the incoming interface,
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
			MACAddress newSourceMAC = outInterface.getMacAddress();
			ArpEntry macEntry = this.arpCache.lookup(target.getGatewayAddress());
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
