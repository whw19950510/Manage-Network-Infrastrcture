package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.*;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/**
 * @author Huawei Wang
 */
public class Switch extends Device
{	
	Map<MACAddress,SwitchEntry>forwardingTable;
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		forwardingTable = new HashMap<>();
		Runnable runnable = new Runnable() {
			public void run() {
				// task to run goes here
				for(MACAddress curmac:forwardingTable.keySet()) {
					SwitchEntry curEntry = forwardingTable.get(curmac);
					curEntry.decreaseTTLtime();
					if(curEntry.getTTLtime() == 0) {
						forwardingTable.remove(curmac);
					}
				}
			}
		};
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		service.scheduleAtFixedRate(runnable, 0, 1, TimeUnit.SECONDS);
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
		/* TODO: Handle packets */
		// interface variable contained all interface name where this switch have
		// only have name, no macaddress
		MACAddress sourceAddr = etherPacket.getSourceMAC();
		MACAddress destAddr = etherPacket.getDestinationMAC();
		// If not contained anything of this packets's source address
		// should learn by switch itself
		if(forwardingTable.containsKey(sourceAddr) == false) {
			SwitchEntry newEntry = new SwitchEntry(sourceAddr, inIface,15);
			forwardingTable.put(sourceAddr, newEntry);
		} else {
			// reset the TTL for this node entry
			forwardingTable.get(sourceAddr).setTTLtime(15);
		}

		if(forwardingTable.containsKey(destAddr)) {
			SwitchEntry dest = forwardingTable.get(destAddr);
			this.sendPacket(etherPacket,dest.getIface());
		} else {
			for(String cur: this.interfaces.keySet()) {
				Iface onesent = this.interfaces.get(cur);
				if(onesent.getName().equals(inIface.getName()))
					continue;
				// can only compare about the hostname, if the same interface continue
				this.sendPacket(etherPacket,onesent);
			}
		}
		
		/********************************************************************/
	}
}
