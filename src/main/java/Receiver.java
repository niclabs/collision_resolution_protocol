import com.google.gson.Gson;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

public class Receiver implements ZThread.IAttachedRunnable {

    @Override
    public void run(Object[] args, ZContext context, ZMQ.Socket pipe) {
        // Create the receiver socket that work as a subscriber
        ZMQ.Socket receiver = context.createSocket(ZMQ.SUB);

        // Set size of the DC-NET room
        Room room = (Room) args[0];
        int roomSize = room.getRoomSize();

        // Connect as a subscriber to each of the nodes on the DC-NET room
        connectReceiverThread(receiver, room);

        // Subscribe to whatever the nodes say
        receiver.subscribe("".getBytes());

        // Read from other nodes while is not being interrupted
        while (!Thread.currentThread().isInterrupted()) {

            // Receive message from the sender thread
            String inputFromSender = pipe.recvStr();

            // Check if the message is a Finished signal
            if (inputFromSender.equals("FINISHED"))
                break;

            // If not is finished, it is the number of the round that the room is playing
            int round = Integer.parseInt(inputFromSender);

            // If the round is virtual, the receiver thread will not receive any message from the room, so we skip it
            if (round != 1 && round%2 != 0) {
                continue;
            }

            // We are in a real round, so we iterate in order to receive all the messages from the other nodes
            for (int i = 0; i < roomSize; i++) {
                // Receive message from a node in the room
                String inputMessage = receiver.recvStr().trim();

                // Format the message that is incoming to "extract" the actual message
                OutputMessage incomingOutputMessage = new Gson().fromJson(inputMessage, OutputMessage.class);
                int numericInputMessage = incomingOutputMessage.getMessageProtocol();

                // Send to the sender thread the message received
                pipe.send("" + numericInputMessage);
            }

        }

        // Close receiver thread
        receiver.close();

        // Let know to the sender that i'm already closed
        pipe.send("");
    }

    private void connectReceiverThread(ZMQ.Socket receiver, Room room) {
        for (int i = 1; i <= room.getRoomSize(); i++)
            receiver.connect("tcp://" + room.getNodeIpFromIndex(i) + ":9000");
    }

}
