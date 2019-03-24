package edu.buffalo.cse.cse486586.groupmessenger2;
import android.util.Log;
import java.util.Comparator;

public class CustomComparator implements Comparator<MessagePacket> {

    @Override
    public int compare(MessagePacket lhs, MessagePacket rhs) {
        if (lhs.getFinalSequenceNumber() > rhs.getFinalSequenceNumber()) {
            return 1;
        }
        else if (lhs.getFinalSequenceNumber() < rhs.getFinalSequenceNumber()) {
            return -1;
        } else if (lhs.getFinalSequenceNumber() == rhs.getFinalSequenceNumber())  {
            if (Integer.parseInt(lhs.getPortMappedId()) > Integer.parseInt(rhs.getPortMappedId())) {
                return 1;
            } else if (Integer.parseInt(lhs.getPortMappedId()) < Integer.parseInt(rhs.getPortMappedId())) {
                return -1;
            } else {
                return 0;
            }
        }
        return 0;
    }
}
