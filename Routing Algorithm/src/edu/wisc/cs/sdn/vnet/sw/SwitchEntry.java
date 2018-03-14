package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.*;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

class SwitchEntry {
    private MACAddress position;     //Mac address of a node
    private Iface iface;               // interface to reach it
    private int TTLtime;               //Time to leave the table
    // Binding binding;        //Binding in the map
    public SwitchEntry(MACAddress position, Iface iface, int TTLtime) {
        this.position = position;
        this.iface = iface;
        this.TTLtime = TTLtime;
    }
    public int getTTLtime() {
        return this.TTLtime;
    }
    public void decreaseTTLtime() {
        this.TTLtime--;
    }
    public void setTTLtime(int TTLtime) {
        this.TTLtime = TTLtime;
    }
    public Iface getIface() {
        return this.iface;
    }
}