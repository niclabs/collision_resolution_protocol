import com.google.gson.Gson;

public class OutputMessage {

    ParticipantNode senderNode;
    private int cmd;
    private int message;

    public OutputMessage(ParticipantNode node, int cmd, int message) {
        this.senderNode = node;
        this.cmd = cmd;
        this.message = message;
    }

    public OutputMessage() {
        this.cmd = 0;
        this.message = 0;
    }

    public void setSenderNode(ParticipantNode node) {
        this.senderNode = node;
    }

    public void setMessage(String message, Room room) {
        // Set to the OutputMessage object the actual message that the node wants to communicate (<m>)
        int messageNumber = Integer.parseInt(message);
        // If the message is 0, the node doesn't want to send any message to the room
        if (messageNumber == 0) {
            this.setMessage(0);
        }
        // If not, the message to send must have the form (<m>,1), that it translates to: <m>*(n+1) + 1 (see Reference for more information)
        else {
            this.setMessage(messageNumber*(room.getRoomSize()+1) + 1);
        }
    }

    public void setCmd(int cmd) {
        this.cmd = cmd;
    }

    public int getMessage() {
        return message;
    }

    public void setMessage(int message) {
        this.message = message;
    }



}
