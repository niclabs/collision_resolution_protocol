import com.google.gson.Gson;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

import java.io.IOException;
import java.util.*;

/*
    This application runs a collision resolution protocol, in order
    to use within a DC-NET.
    The nodes that are created by this class are run all in the same
    computer, running on different ports.
*/

class NodeDCNET implements ZThread.IAttachedRunnable {

    private final int SYNC = 0;
    private final int DCNET = 1;

    private final int dcNetSize;
    private final String networkIp;
    private final String name;
    private final int outputNumericMessage;

    public NodeDCNET(String networkIp, String name, String outputNumericMessage, String dcNetSize) {
        this.networkIp = networkIp;
        this.name = name;
        this.outputNumericMessage = Integer.parseInt(outputNumericMessage);
        this.dcNetSize = Integer.parseInt(dcNetSize);
    }

    public static void main(String[] args) throws IOException {
        // Usage: ./gradlew run -PappArgs=[<message>,<numberOfNodes>]
        new NodeDCNET("127.0.0.1", "Node", args[0], args[1]).createNode();
    }

    // Receiver Thread
    @Override
    public void run(Object[] args, ZContext context, ZMQ.Socket pipe) {

        // Get the Network IP where all the nodes are participating
        String network_ip = (String) args[0];

        // Create the receiver socket that work as a subscriber
        ZMQ.Socket receiver = context.createSocket(ZMQ.SUB);

        // Connect as a subscriber to each of the nodes on the DC-NET room, from port 9001 to (9000 + <dcNetSize>)
        for (int i = 0; i < dcNetSize; i++) {
            receiver.connect("tcp://" + network_ip + ":" + (9001 + i));
        }

        // Subscribe to whatever the nodes say
        receiver.subscribe("".getBytes());

        // Synchronize publishers and subscribers
        // waitForAllPublishers(pipe, receiver);

        int round = 1;

        // Read from other nodes
        while (!Thread.currentThread().isInterrupted()) {

            // Wait for sender thread let know that a new round will begin, if it is a virtual round, i wait two times
            if (round != 1 && round%2 != 0) {
                String inputFromSender = pipe.recvStr();
                round++;
                if (inputFromSender.equals("NO ROUND"))
                    continue;
                if (inputFromSender.equals("FINISHED"))
                    break;
            }

            String inputFromSender = pipe.recvStr();
            if (inputFromSender.equals("NO ROUND")) {
                round++;
                continue;
            }
            if (inputFromSender.equals("FINISHED"))
                break;

            for (int i = 0; i < dcNetSize; i++) {
                // Receive message from a node in the room
                String inputMessage = receiver.recvStr().trim();

                // Format the message that is incoming to "extract" the actual message
                OutputMessage incomingOutputMessage = new Gson().fromJson(inputMessage, OutputMessage.class);
                String clearInputMessage = incomingOutputMessage.getMessage();

                // Send to the sender thread the message received
                pipe.send(clearInputMessage);
            }

            round++;

        }

        // Close receiver thread
        receiver.close();

        // Let know to the sender that i'm already closed
        pipe.send("");

    }

    // Sender Thread
    public void createNode() throws IOException {

        // Create context where to run the receiver and sender threads
        ZContext context = new ZContext();

        // Create the sender socket that works as a publisher
        ZMQ.Socket sender = context.createSocket(ZMQ.PUB);

        // // Explore in all the ports, starting from 9001, until find one available. This port will also used as the index for the node, which range will be: [1, ..., n]
        int nodeIndex = bindSenderPort(sender);

        // We need to connect every pair of nodes in order to synchronize the sending of values at the beginning of each round
        // For this, we need that in every pair of nodes there will be one requestor and one replier
        // In every pair of nodes {i,j} where i<j, node i will work as a requestor and node j will work as a replier
        // Create array of sockets that will work as repliers and requestors
        ZMQ.Socket[] repliers = initializeRepliersArray(nodeIndex, context);
        ZMQ.Socket[] requestors = initializeRequestorsArray(nodeIndex, context);

        // Throw receiver thread which runs the method 'run' described above
        ZMQ.Socket receiverThread = ZThread.fork(context, new NodeDCNET(this.networkIp, this.name, "" + this.outputNumericMessage, "" + this.dcNetSize), networkIp);

        /*System.out.println("waiting to all nodes be connected");
        // Synchronize Publishers and Subscribers
        waitForAllSubscribers(receiverThread, sender, nodeIndex);
        System.out.println("all nodes connected");*/

        // Create OutputMessage object
        OutputMessage outputMessage = new OutputMessage();
        outputMessage.setSenderId("Node_" + nodeIndex);
        outputMessage.setCmd(DCNET);

        // This is the actual message that the node wants to communicate (<m>)
        int outputNumericMessage = this.outputNumericMessage;

        // If <m>!=0 must be appended to #1, forming M = <m>#1. If not, M = 0#0
        if (outputNumericMessage == 0)
            outputMessage.setMessage("0#0");
        else
            outputMessage.setMessage(outputNumericMessage + "#1");

        String outputMessageJson = new Gson().toJson(outputMessage);
        System.out.println(outputMessageJson);

        System.out.println("O_" + nodeIndex + " = " + outputMessage.getMessage());

        // Variable to check that the message was transmitted to the rest of the room (was sent in a round with no collisions)
        boolean messageTransmitted = false;

        // Index to know in what round we are
        int round = 1;

        // Index to know in which round i'm allowed to resend my message
        int nextRoundAllowedToSend = 1;

        // How many messages are being involved in the first collision
        int collisionSize = 0;

        // Store messages sent in previous rounds in order to build the message in the virtual rounds
        Dictionary<Integer, String> messagesSentInPreviousRounds = new Hashtable<>();

        // Count how many messages were sent without collisions. When this number equals the collision size, the first collision was resolved
        int messagesSentWithNoCollisions = 0;

        // Variable to see if the first collision was solved or not
        boolean finished = false;

        // Variable to store the next rounds that are allow to happen (this is use to skip rounds that no node will send a value)
        LinkedList<Integer> nextRoundsToHappen = new LinkedList<>();
        nextRoundsToHappen.addFirst(1);

        // Count how many real rounds are played
        int realRounds = 0;

        // Sleep to overlap slow joiner problem
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Begin the collision resolution protocol
        while (!Thread.currentThread().isInterrupted()) {

            // Synchronize nodes at the beginning of each round
            synchronizeNodes(nodeIndex, repliers, requestors);

            // Let know to the receiver thread that a new real round will begin
            if (finished) {
                receiverThread.send("FINISHED");
                receiverThread.recvStr();
                break;
            }
            else if (!nextRoundsToHappen.peekFirst().equals(round)) {
                round++;
                receiverThread.send("NO ROUND");
                continue;
            }
            else
                receiverThread.send("");



            // PRINTING INFO ABOUT THE ROUND
            System.out.println("ROUND " + round);

            // Variables to store the resulting message of the round
            // C = <sumOfM>#<sumOfT>
            int m, t, sumOfM = 0, sumOfT = 0;

            // REAL ROUND
            if (round == 1 || round%2 == 0) {
                realRounds++;
                System.out.println("REAL ROUND");

                // If my message was already transmitted i just send "0#0"
                if (messageTransmitted) {
                    sender.send(new Gson().toJson(new OutputMessage("Node_" + nodeIndex, DCNET, "0#0")));
                }

                // Sending message M to the rest of the room if i'm allowed to. If not, i send "0#0"
                else if (nextRoundAllowedToSend == round) {
                    sender.send(outputMessageJson);
                }
                else {
                    sender.send(new Gson().toJson(new OutputMessage("Node_" + nodeIndex, DCNET, "0#0")));
                }

                // Receive information from the receiver thread
                // Count how many messages were receive from the receiver thread. When this number equals <dcNetSize> i've received all the messages in this round
                int messagesReceivedInThisRound = 0;
                while (messagesReceivedInThisRound < dcNetSize) {
                    String messageReceivedFromReceiverThread = receiverThread.recvStr();
                    m = Integer.parseInt(messageReceivedFromReceiverThread.split("#")[0]);
                    t = Integer.parseInt(messageReceivedFromReceiverThread.split("#")[1]);
                    sumOfM += m;
                    sumOfT += t;
                    messagesReceivedInThisRound++;
                }

                // Assign the size of the collision produced in the first round
                if (round == 1) {
                    collisionSize = sumOfT;
                    if (collisionSize == 0) {
                        System.out.println("NO MESSAGES WERE SENT");
                        finished = true;
                        continue;
                    }
                }
            }

            // VIRTUAL ROUND
            else {
                System.out.println("VIRTUAL ROUND");

                // Recover messages sent in rounds 2k and k
                String messageSentInRound2K = messagesSentInPreviousRounds.get(round-1);
                String messageSentInRoundK = messagesSentInPreviousRounds.get((round-1)/2);

                // Divide messages received in rounds 2k and k in <sumOfM> and <sumOfT> for each round
                int sumOfMInRound2k = Integer.parseInt(messageSentInRound2K.split("#")[0]);
                int sumOfTInRound2k = Integer.parseInt(messageSentInRound2K.split("#")[1]);
                int sumOfMInRoundK = Integer.parseInt(messageSentInRoundK.split("#")[0]);
                int sumOfTInRoundK = Integer.parseInt(messageSentInRoundK.split("#")[1]);

                // If any of the messages recover for this rounds was 0, it means that this round it doesn't really happens
                if (sumOfMInRound2k == 0 || sumOfMInRoundK == 0) {
                    sumOfM = 0;
                    sumOfT = 0;
                }

                // If not, is a virtual round that needs to happen, so calculate the resulting message
                else {
                    sumOfM = sumOfMInRoundK - sumOfMInRound2k;
                    sumOfT = sumOfTInRoundK - sumOfTInRound2k;
                }
            }

            // Display round message
            System.out.println("C_" + round +  " = " + sumOfM + "#" + sumOfT);

            // Store the resulting message of this round in order to calculate the messages in subsequently virtual rounds
            messagesSentInPreviousRounds.put(round, "" + sumOfM + "#" + sumOfT);

            // NO COLLISION ROUND => <sumOfT> = 1
            if (sumOfT == 1) {
                // Increase the number of messages that went through the protocol
                messagesSentWithNoCollisions++;

                // If the message that went through equals mine, my message was transmitted
                if (sumOfM == outputNumericMessage) {
                    messageTransmitted = true;
                }

                // If the number of messages that went through equals the collision size, the collision was completely resolved
                if (messagesSentWithNoCollisions == collisionSize) {
                    System.out.println("Finished!");
                    finished = true;
                }

                // Remove actual round from next rounds to happen
                nextRoundsToHappen.removeFirst();
            }

            // COLLISION OR NO MESSAGES SENT IN THIS ROUND => <sumOfT> != 1
            else {
                // New collision produced, it means that <sumOfT> > 1
                if (sumOfT != 0) {
                    // Check if my message was involved in the collision, seeing that this round i was allowed to send my message
                    if (nextRoundAllowedToSend == round) {
                        // See if i need to send in the next (2*round) round, checking the average condition
                        if (outputNumericMessage < sumOfM / sumOfT) {
                            nextRoundAllowedToSend = 2 * round;
                        }
                        // If not, i'm "allowed to send" in the (2*round + 1) round, which will be a virtual round
                        else {
                            nextRoundAllowedToSend = 2 * round + 1;
                        }
                    }
                    // Remove actual round and add 2k and 2k+1 rounds
                    rotateAndAddRounds(nextRoundsToHappen, 2*round, 2*round+1);
                }
            }

            // At the end of the round, i increase the round number and continue
            round++;
            System.out.println();

            // Prevent infinite loops
            if (round == Math.pow(2, collisionSize))
                finished = true;

        }

        // Close all the threads and destroy the context
        receiverThread.close();
        sender.close();
        context.destroy();

        System.out.println("REAL ROUNDS PLAYED: " + realRounds);

    }

    private void rotateAndAddRounds(LinkedList<Integer> nextRoundsToHappen, int firstRoundToAdd, int secondRoundToAdd) {
        nextRoundsToHappen.removeFirst();
        nextRoundsToHappen.add(firstRoundToAdd);
        nextRoundsToHappen.add(secondRoundToAdd);
    }

    private void waitForAllSubscribers(ZMQ.Socket receiverThread, ZMQ.Socket sender, int nodeIndex) {
        // Let know the receiver thread what is my nodeIndex
        receiverThread.send("" + nodeIndex);

        // Set timeout on the receiver thread
        receiverThread.setReceiveTimeOut(2000);

        // Store the message received from receiver thread
        String inputSyncMessage;

        while (true) {
            // Send nodeIndex to the rest of the room
            sender.send("" + nodeIndex);

            // While the receiver doesn't inform me i send again my nodeIndex to the rest of the room
            while ((inputSyncMessage = receiverThread.recvStr()) == null)
                sender.send("" + nodeIndex);

            // If the receiver tells me we are ready to go, break the cycle and continue
            if (inputSyncMessage.equals("ready")) {
                sender.send("" + nodeIndex);
                sender.send("go" + nodeIndex);
                for (int i = 0; i < dcNetSize; i++)
                    sender.send("r" + (i+1) + "#" + nodeIndex);
            }

            else if (inputSyncMessage.equals("all ready")) {
                break;
            }

            // If not, it means that the receiver received a message from other node, so i let know to the rest of the room that i'm already connected to them
            else
                // "rX#Y" means that node Y already received message from node X
                sender.send("r" + inputSyncMessage + "#" + nodeIndex);
        }

        // Set timeout to infinity to continue the protocol
        receiverThread.setReceiveTimeOut(-1);
    }

    private void waitForAllPublishers(ZMQ.Socket pipe, ZMQ.Socket receiver) {
        // Receive message from sender that indicates our nodeIndex
        int nodeIndex = Integer.parseInt(pipe.recvStr());

        // Store the message received from receiver thread
        String inputSyncMessage;

        // Set time out of the receiver socket
        receiver.setReceiveTimeOut(2000);

        // Array to store what nodes received my message
        boolean[] nodesConnectedtoMe = new boolean[dcNetSize];

        // Array to store what nodes i'm connected to
        boolean[] nodesConnectedTo = new boolean[dcNetSize];

        // Array to store nodes ready
        boolean[] nodesReady = new boolean[dcNetSize];

        /*for (boolean value : nodesConnectedtoMe)
            value = false;
        for (boolean value : nodesConnectedTo)
            value = false;*/

        // Variable to know if i'm connected to the rest of the room
        boolean connectedToAllRoom = false;

        // Variable to know if all the room is connected to me
        boolean allRoomConnectedToMe = false;

        // Variable to know if all the room is connected between them
        boolean allRoomConnected = false;

        while (true) {
            // Wait until receive any message from any node of the room
            while ((inputSyncMessage = receiver.recvStr()) == null)
                ;

            // If the first character is not an 'r' it means that i can hear a certain node that is transmitting
            if (inputSyncMessage.charAt(0) != 'r' && inputSyncMessage.charAt(0) != 'g') {
                // <inputSyncMessage> = node that I can hear
                // Let know to sender thread that i can hear a certain node stored on <inputSyncMessage>
                pipe.send(inputSyncMessage);

                if (!nodesConnectedTo[Integer.parseInt(inputSyncMessage) - 1]) {
                    nodesConnectedTo[Integer.parseInt(inputSyncMessage) - 1] = true;
                    System.out.println("Node " + nodeIndex + " can hear node " + inputSyncMessage);
                }
            }

            else if (inputSyncMessage.charAt(0) == 'g') {
                int nodeReady = Integer.parseInt(inputSyncMessage.substring(2,3));
                nodesReady[nodeReady - 1] = true;
                for (boolean value : nodesReady) {
                    if (value)
                        allRoomConnected = true;
                    else {
                        allRoomConnected = false;
                        break;
                    }
                }
            }

            // If the first character is an 'r', it means that is a response that a node can hear a certain other node
            // <inputSyncMessage> = 'rX#Y' => node Y can hear node X
            else {
                // Separate values that what node is being heard and what node is the one that is hearing it
                int nodeThatIsHeard = Integer.parseInt(inputSyncMessage.substring(1, 2));
                int nodeThatIsHearing = Integer.parseInt(inputSyncMessage.substring(3, 4));

                // See if other node is being hearing me
                if (nodeThatIsHeard == nodeIndex) {
                    if (!nodesConnectedtoMe[nodeThatIsHearing - 1]) {
                        nodesConnectedtoMe[nodeThatIsHearing - 1] = true;
                        System.out.println("Node " + nodeIndex + " is being heard by node " + nodeThatIsHearing);
                    }
                }
            }

            // To be ready i need that all the nodes are connected to me and i am connected to all the nodes
            // Check that all the nodes are connected to me
            for (boolean value : nodesConnectedtoMe) {
                if (value)
                    connectedToAllRoom = true;
                else {
                    connectedToAllRoom = false;
                    break;
                }
            }

            // Check that i am connected to all the nodes
            for (boolean value : nodesConnectedTo) {
                if (value)
                    allRoomConnectedToMe = true;
                else {
                    allRoomConnectedToMe = false;
                    break;
                }
            }

            // If both values are true, it means that i am ready to go
            if (connectedToAllRoom && allRoomConnectedToMe) {
                pipe.send("ready");
                // ¿Let know to the rest of the room that i'm ready?
            }

            if (allRoomConnected) {
                pipe.send("all ready");
                break;
            }


        }

        receiver.setReceiveTimeOut(-1);

    }

    private ZMQ.Socket[] initializeRequestorsArray(int nodeIndex, ZContext context) {
        ZMQ.Socket[] requestors = null;
        // If my index is the last one, i will only have repliers and none requestor
        if (nodeIndex != dcNetSize) {
            // If my index is <nodeIndex>, i will have to create (<dcNetSize>-<nodeIndex>) requestors
            requestors = new ZMQ.Socket[dcNetSize - nodeIndex];

            // Iterate in requestors array in order to create the socket and bind the port
            for (int i = 0; i < requestors.length; i++) {
                // Create the requestor socket
                requestors[i] = context.createSocket(ZMQ.REQ);

                // Bind the requestor socket to the corresponding port, that is at least 7001
                int portToConnect = ((((nodeIndex + i + 1)*(nodeIndex + i - 2))/2) + 7002) + nodeIndex - 1;
                requestors[i].connect("tcp://*:" + (portToConnect));
            }
        }
        return requestors;
    }

    private ZMQ.Socket[] initializeRepliersArray(int nodeIndex, ZContext context) {
        ZMQ.Socket[] repliers = null;
        // If my index is 1, i will only have requestors and none replier
        if (nodeIndex != 1) {
            // If my index is <nodeIndex>, i will have to create (<nodeIndex>-1) repliers
            repliers = new ZMQ.Socket[nodeIndex-1];

            // Iterate in repliers array in order to create the socket and bind the port
            for (int i = 0; i < repliers.length; i++) {
                // Create the replier socket
                repliers[i] = context.createSocket(ZMQ.REP);

                // Bind the replier socket to the corresponding port
                int firstPortToBind = (7002 + ((nodeIndex*(nodeIndex-3))/2));
                repliers[i].bind("tcp://*:" + (firstPortToBind+i));
            }
        }
        return repliers;
    }

    private int bindSenderPort(ZMQ.Socket sender) {
        int nodeIndex;
        for (nodeIndex = 1; nodeIndex < dcNetSize + 1; nodeIndex++) {
            // Try to bind the sender to the port (9000 + <nodeIndex>).
            // If not, continue with the rest of the ports
            try {
                sender.bind("tcp://*:" + (9000 + nodeIndex));
            } catch (Exception e) {
                continue;
            }
            // Already bound to a port, so break the cycle
            break;
        }
        return nodeIndex;
    }

    private void synchronizeNodes(int nodeIndex, ZMQ.Socket[] repliers, ZMQ.Socket[] requestors) {
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                replier.recv(0);
                replier.send("", 0);
            }

        if (nodeIndex != dcNetSize)
            for (ZMQ.Socket requestor : requestors) {
                requestor.send("".getBytes(), 0);
                requestor.recv(0);
            }
    }

}
