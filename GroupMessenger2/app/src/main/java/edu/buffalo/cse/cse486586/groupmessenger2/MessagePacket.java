package edu.buffalo.cse.cse486586.groupmessenger2;

public class MessagePacket implements Comparable<MessagePacket> {

    public String portMappedId;
    public String msg;
    public int finalSequenceNumber;
    public boolean isDelivered;

    public MessagePacket() {}

    public MessagePacket(String portMappedId, String msg, int b,  boolean isDelivered) {
        portMappedId = portMappedId;
        msg = msg;
        finalSequenceNumber = b;
        isDelivered = isDelivered;
    }

    public void setPortMappedId(String portMappedId) {
        this.portMappedId = portMappedId;
    }

    public void setDelivered(boolean delivered) {
        isDelivered = delivered;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public void setFinalSequenceNumber(int finalSequenceNumber) {
        this.finalSequenceNumber = finalSequenceNumber;
    }

    public String getMsg() {
        return msg;
    }

    public int getFinalSequenceNumber() {
        return finalSequenceNumber;
    }

    public String getPortMappedId() {
        return portMappedId;
    }

    public boolean isDelivered() {
        return isDelivered;
    }

    @Override
    public int compareTo(MessagePacket another) {
        if (this.finalSequenceNumber > another.finalSequenceNumber) {
            return -1;
        }
        return 1;
    }
}


