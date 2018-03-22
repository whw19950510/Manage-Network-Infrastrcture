package edu.wisc.cs.sdn.vnet.rt;

import javax.xml.stream.events.DTD;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;

import java.util.HashMap;
import java.util.Map;

public class LocalEntry
{
    private int ipAddr;
    private int nextHopAddr;//gateway
    private int subnetMask;
    private int metric;
    private int live = 30;
    public LocalEntry(int ipAddr, int dstMask, int nextHopAddr, int metric) {
        this.ipAddr = ipAddr;
        this.subnetMask = dstMask;
        this.nextHopAddr = nextHopAddr;
        this.metric = metric;
        this.live = 30;
    }
    public void update(int ipAddr, int dstMask, int nextHopAddr, int metric) {
        this.live = 30;
        this.ipAddr = ipAddr;
        this.subnetMask = dstMask;
        this.nextHopAddr = nextHopAddr;
        this.metric = metric;
    }
    public int getLive() {
        return this.live;
    }
    public void setLive() {
        this.live--;
    }
    public int getIpAddr() {
        return this.ipAddr;
    }
    public int getNexthop() {
        return this.nextHopAddr;
    }
    public int getSubnetMask() {
        return this.subnetMask;
    }
    public int getMetric() {
        return metric;
    }
}