package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import java.util.HashMap;
import java.util.Map;

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
	
	/*record using for recording each route entry's life, then remove*/
    public Map<Integer, LocalEntry>record;
	
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
	// Generate request packets
	public RIPv2 generateRiPv2req() {
		RIPv2 req = new RIPv2();
		req.setCommand(RIPv2.COMMAND_REQUEST);
		return req;
	}
	
	/**
	 * Generate unsolicited request packet and request packets
	 * @param req RIP payload
	 * @param conn the router interface which should be sent out
	 */
	public Ethernet encapsulateRipreqToEthernet(RIPv2 req, Iface conn) {
		UDP reqUdp = new UDP();
		Ethernet reqEth = new Ethernet();
		IPv4 reqIp = new IPv4();
		reqEth.setEtherType(Ethernet.TYPE_IPv4);
		reqUdp = (UDP)reqUdp.setPayload(req);
		reqUdp = reqUdp.setSourcePort(UDP.RIP_PORT);
		reqUdp = reqUdp.setDestinationPort(UDP.RIP_PORT);
		// Set src & dst address for IP & Ethernet
		reqIp = (IPv4)reqIp.setPayload(reqUdp);
		reqIp = reqIp.setSourceAddress(conn.getIpAddress());
		reqIp = reqIp.setDestinationAddress("224.0.0.9");
		reqIp.resetChecksum();
		reqIp = reqIp.setProtocol(IPv4.PROTOCOL_UDP);
		reqEth = (Ethernet)reqEth.setPayload(reqIp);
		reqEth = (Ethernet)reqEth.setSourceMACAddress(conn.getMacAddress().toBytes());
		reqEth.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
		return reqEth;
	}
	// Generate response packets
	public RIPv2 generateRiPv2res() {
		RIPv2 res = new RIPv2();
		res.setCommand(RIPv2.COMMAND_RESPONSE);
		// Build RIP using known RouteTable information
		// Using the mask & Interface IP as the mapping key and thus can be used as destination address of another router
		for(Integer cur:this.record.keySet()) {
			LocalEntry touse = record.get(cur);
			int ipAddr = touse.getIpAddr();
			RouteEntry correspond = this.routeTable.lookup(ipAddr);
			int nextHopAddress = correspond.getGatewayAddress();
			int metric = touse.getMetric();
			RIPv2Entry curen = new RIPv2Entry(correspond.getDestinationAddress(), correspond.getMaskAddress(), metric);
			curen.setNextHopAddress(nextHopAddress);//no use
			res.addEntry(curen);
		}
		return res;
	}

	//  When sending a RIP response for a specific RIP request, 
	// the destination IP address and destination Ethernet address 
	// should be the IP address and MAC address of the router interface that sent the request.
	public Ethernet encapsulateRipresToEthernet(RIPv2 req, Iface conn, int reqIpAddr, MACAddress reqMacAddr) {
		UDP reqUdp = new UDP();
		Ethernet reqEth = new Ethernet();
		IPv4 reqIp = new IPv4();
		reqEth.setEtherType(Ethernet.TYPE_IPv4);
		
		reqUdp = (UDP)reqUdp.setPayload(req);
		reqUdp = reqUdp.setSourcePort(UDP.RIP_PORT);
		reqUdp = reqUdp.setDestinationPort(UDP.RIP_PORT);
		// Set src & dst address for IP & Ethernet
		// conn is the Iface to sent out this packet
		reqIp = (IPv4)reqIp.setPayload(reqUdp);
		reqIp.resetChecksum();
		reqIp = reqIp.setProtocol(IPv4.PROTOCOL_UDP);
		reqIp  = reqIp.setSourceAddress(conn.getIpAddress());
		reqIp = reqIp.setDestinationAddress(reqIpAddr);
		reqEth = (Ethernet)reqEth.setPayload(reqIp);
		reqEth = (Ethernet)reqEth.setSourceMACAddress(conn.getMacAddress().toBytes());
		reqEth.setDestinationMACAddress(reqMacAddr.toBytes());
		return reqEth;
	}

	// Run RIP protocals to build route Table
	// 1. Initialize:fill in entries with those directly connected interface
	// 2. send one RIP request out all of the router’s interfaces 
	// 3. send an unsolicited RIP response out all of the router’s interfaces every 10 seconds thereafter.

	public void buildRouteTable() {
		this.record = new ConcurrentHashMap<Integer, LocalEntry>();
		// 1st step: Add entries for directly connected subnets
		for(String cur:this.interfaces.keySet()) {
			Iface conndir = this.interfaces.get(cur);
			int netAddr = (conndir.getIpAddress()&conndir.getSubnetMask());
			this.routeTable.insert(conndir.getIpAddress(), 0, conndir.getSubnetMask(), conndir);
			LocalEntry toinsert = new LocalEntry(netAddr, conndir.getSubnetMask(), 0, 0);
			record.put(netAddr, toinsert);
		}
		// 2nd step: Send out RIP request packets
		for(String cur:this.interfaces.keySet()) {
			Iface conn = this.interfaces.get(cur);
			RIPv2 ripreqpac = generateRiPv2req();
			Ethernet ripreq = encapsulateRipreqToEthernet(ripreqpac, conn);
			sendPacket(ripreq, conn);			
		}

		final Map<String, Iface> rtinterface = this.interfaces;
		// 3rd step: Send out unsolicit(notify each neighbor router) response after finish building the router table
		// for each interface generate rip packets and sends out every 10s
		Runnable runnable = new Runnable() {
			public void run() {
				// task to run goes here
				for(String cur:rtinterface.keySet()) {
					Iface curconn = rtinterface.get(cur);
					RIPv2 riprespac = generateRiPv2res();
					Ethernet ripimp = encapsulateRipreqToEthernet(riprespac, curconn);
					sendPacket(ripimp, curconn);
				}
			}
		};
		ScheduledExecutorService resservice = Executors.newSingleThreadScheduledExecutor();
		resservice.scheduleAtFixedRate(runnable, 0, 10, TimeUnit.SECONDS);

		// Timeout each entry and remove them when needed
		Runnable removeRun = new Runnable() {
			@Override
			public void run() {
				for(Integer curaddr: record.keySet()) {
					LocalEntry curlocalentry = record.get(curaddr);
					curlocalentry.setLive();
					if(curlocalentry.getLive() == 0) {
						RouteEntry mayremove = routeTable.lookup(curaddr);
						int subnetMask = mayremove.getMaskAddress();
						int dstAddr = mayremove.getDestinationAddress();
						if(mayremove.getGatewayAddress() == 0) 
							continue;// for subnet connected never remove
						boolean succ = routeTable.remove(dstAddr, subnetMask);	//???应该是需要相对应的dst addr
						record.remove(curaddr);
					}
				}
			}
		};
		ScheduledExecutorService removeservice = Executors.newSingleThreadScheduledExecutor();
		removeservice.scheduleAtFixedRate(removeRun, 0, 1, TimeUnit.SECONDS);
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
			IPv4 packetHeader = (IPv4)(etherPacket.getPayload());
			int headerBytes = 4*packetHeader.getHeaderLength();
			int RipdestinationAddress = IPv4.toIPv4Address("224.0.0.9");
			// RIP request/unsolicited response, need to deal separatly
			// No need to deal with the unicast response, only deal with broadcast address
			if(packetHeader.getDestinationAddress() == RipdestinationAddress && packetHeader.getProtocol() == IPv4.PROTOCOL_UDP && ((UDP)packetHeader.getPayload()).getSourcePort() == UDP.RIP_PORT && ((UDP)packetHeader.getPayload()).getDestinationPort() == UDP.RIP_PORT) {
				UDP udppac = (UDP)(packetHeader.getPayload());
				RIPv2 rippac = (RIPv2)(udppac.getPayload());
				if(rippac.getCommand() == RIPv2.COMMAND_REQUEST) {
					// Needs to send out corresponding response
					RIPv2 ripres = generateRiPv2res();
					Ethernet pacres = encapsulateRipresToEthernet(ripres, inIface, packetHeader.getSourceAddress(), etherPacket.getSourceMAC());
					sendPacket(pacres, inIface);
				} else if(rippac.getCommand() == RIPv2.COMMAND_RESPONSE) {
					// unsolicited response, update table
					/** The actual Routing protocal **/
					for(RIPv2Entry curEntry : rippac.getEntries()) {
						int dstAddr = curEntry.getAddress();
						int gtwAddr = packetHeader.getSourceAddress();
						int dstMask = curEntry.getSubnetMask();
						int metric = curEntry.getMetric();
						int netAddr = (dstAddr&dstMask);
						int updatemetric = 1 + metric;
						if(updatemetric >= 16) updatemetric = 16;
						if(record.containsKey(netAddr) == false) {
							// Add this new Entry to RouteTable
							LocalEntry target = new LocalEntry(netAddr, dstMask, gtwAddr, updatemetric);

							record.put(netAddr, target);
							routeTable.insert(dstAddr, gtwAddr, dstMask, inIface);
						} else {
							LocalEntry mayupdaEntry = record.get(netAddr);
							RouteEntry target = this.routeTable.lookup(netAddr);
							if(1 + metric < mayupdaEntry.getMetric() || packetHeader.getSourceAddress() == target.getGatewayAddress()) {
								routeTable.update(dstAddr, dstMask, gtwAddr, inIface);
								record.get(netAddr).update(netAddr, dstMask, gtwAddr, updatemetric);
							}
						}
					}
				}
			} else {
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
		}
		
		/********************************************************************/
	}
}
