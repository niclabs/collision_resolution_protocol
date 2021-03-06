package participantnode;

import dcnet.Room;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

/**
 *
 */
public class Receiver implements ZThread.IAttachedRunnable {

    /**
     * @param args    room where the receiver thread needs to listen messages
     * @param context context where the zmq sockets need to run
     * @param pipe    zmq socket created to connect both threads
     */
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
            if (round != 1 && round % 2 != 0)
                continue;

            /* COMMITMENTS ON KEYS PART **/
            for (int i = 0; i < roomSize; i++) {
                // Receive commitment on key from a node in the room
                String inputKeyCommitment = receiver.recvStr();
                // Send string to sender thread
                pipe.send(inputKeyCommitment);
            }

            /* COMMITMENTS FOR SINGLE VALUES **/
            for (int i = 0; i < roomSize; i++) {
                // Received commitments on single values from a node in the room
                String inputCommitmentsOnSingleValues = receiver.recvStr().trim();
                // Send string (json) to sender thread
                pipe.send(inputCommitmentsOnSingleValues);
            }

            /* COMMITMENTS AND POK ON MESSAGES **/
            for (int i = 0; i < roomSize; i++) {
                // Receive proof of knowledge from a node in the room as a String (json)
                String inputMessagePOKAndCommitment = receiver.recvStr().trim();
                // Send String (json) to sender thread
                pipe.send(inputMessagePOKAndCommitment);
            }

            /* OUTPUT MESSAGE SENDING **/
            if (round == 1) {
                for (int i = 0; i < roomSize; i++) {
                    // Receive message from a node in the room
                    String inputOutputMessagePOKAndOutputMessage = receiver.recvStr().trim();
                    // Send String (json) to the sender thread
                    pipe.send(inputOutputMessagePOKAndOutputMessage);
                }
            } else if ((round/2) % 2 == 0 || round == 2) {
                for (int i = 0; i < roomSize; i++) {
                    // Receive message from a node in the room
                    String incomingOutputMessageAndPOKResending = receiver.recvStr().trim();
                    // Send String (json) to the sender thread the message received
                    pipe.send(incomingOutputMessageAndPOKResending);
                }
            } else {
                for (int i = 0; i < roomSize; i++) {
                    // Receive message from a node in the room
                    String incomingOutputMessageAndPOKResending = receiver.recvStr().trim();
                    // Send String (json) to the sender thread the message received
                    pipe.send(incomingOutputMessageAndPOKResending);
                }
            }

        }

        // Close receiver thread
        receiver.close();
        // Let know to the sender that i'm already closed
        pipe.send("");

    }

    /**
     * @param receiver zmq socket that will receive messages
     * @param room     room where the receiver thread is listening messages
     */
    private void connectReceiverThread(ZMQ.Socket receiver, Room room) {
        for (int i = 1; i <= room.getRoomSize(); i++)
            receiver.connect("tcp://" + room.getNodeIpFromIndex(i) + ":9000");
    }

}
