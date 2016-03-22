import com.google.gson.Gson;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.security.SecureRandom;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

public class SessionParameters {

    ZMQ.Socket[] repliers;
    ZMQ.Socket[] requestors;
    boolean messageTransmitted;
    int round, realRoundsPlayed;
    int nextRoundAllowedToSend;
    int collisionSize;
    Dictionary<Integer, Integer> messagesSentInPreviousRounds;
    int messagesSentWithNoCollisions;
    boolean finished;
    LinkedList<Integer> nextRoundsToHappen;
    List<Integer> messagesReceived;
    boolean realRound;

    public SessionParameters() {
        realRound = true;
        messageTransmitted = false;
        round = 1;
        realRoundsPlayed = 0;
        nextRoundAllowedToSend = 1;
        collisionSize = 0;
        messagesSentInPreviousRounds = new Hashtable<>();
        messagesSentWithNoCollisions = 0;
        finished = false;
        nextRoundsToHappen = new LinkedList<>();
        nextRoundsToHappen.addFirst(1);
        messagesReceived = new LinkedList<>();
    }

    public String zeroMessageJson(ParticipantNode participantNode) {
        return new Gson().toJson(new OutputMessage(participantNode, 1, 0));
    }

    public void runSession(int nodeIndex, OutputMessage outputMessage, Room room, ParticipantNode node, ZMQ.Socket receiverThread, String outputMessageJson) {
        while (!Thread.currentThread().isInterrupted()) {

            // Synchronize nodes at the beginning of each round
            synchronizeNodes(nodeIndex, repliers, requestors, room);

            // Check if the protocol was finished in the last round played
            // If it so, let know to the receiver thread, wait for his response and break the loop
            if (finished) {
                receiverThread.send("FINISHED");
                receiverThread.recvStr();
                break;
            }
            // If not is finished yet, get which round we need to play and send this round to the receiver thread
            else {
                round = nextRoundsToHappen.removeFirst();
                receiverThread.send("" + round);
            }

            // Print round number
            System.out.println("ROUND " + round);

            // Variables to store the resulting message of the round
            int sumOfM, sumOfT, sumOfO = 0;

            // The protocol separates his operation if it's being played a real round or a virtual one (see Reference for more information)
            // REAL ROUND (first and even rounds)
            if (round == 1 || round%2 == 0) {
                // Set variable that we are playing a real round, add one to the count and print it
                realRound = true;
                realRoundsPlayed++;
                System.out.println("REAL ROUND");

                // If my message was already sent in a round with no collisions, i send a zero message
                if (messageTransmitted) {
                    node.getSender().send(this.zeroMessageJson(node));
                }

                // If not, check first if i'm allowed to send my message in this round
                // If so i send my message as the Json string constructed before the round began
                else if (nextRoundAllowedToSend == round) {
                    node.getSender().send(outputMessageJson);
                }
                // If not, i send a zero message
                else {
                    node.getSender().send(this.zeroMessageJson(node));
                }

                // After sending my message, receive information from the receiver thread (all the messages sent in this round by all the nodes in the room)
                // Count how many messages were receive from the receiver thread
                int messagesReceivedInThisRound = 0;
                // When this number equals <dcNetSize> i've received all the messages in this round
                while (messagesReceivedInThisRound < room.getRoomSize()) {
                    // Receive a message
                    String messageReceivedFromReceiverThread = receiverThread.recvStr();
                    // Transform incoming message to an int
                    int incomingOutputMessage = Integer.parseInt(messageReceivedFromReceiverThread);
                    // Sum this incoming message with the rest that i've received in this round in order to construct the resulting message of this round
                    sumOfO += incomingOutputMessage;
                    // Increase the number of messages received
                    messagesReceivedInThisRound++;
                }

            }

            // VIRTUAL ROUND (odd rounds)
            else {
                // Set variable that we are playing a virtual round and print it
                realRound = false;
                System.out.println("VIRTUAL ROUND");

                // Recover messages sent in rounds 2k and k in order to construct the resulting message of this round (see Reference for more information)
                int sumOfOSentInRound2K = messagesSentInPreviousRounds.get(round - 1);
                int sumOfOSentInRoundK = messagesSentInPreviousRounds.get((round-1)/2);

                // Construct the resulting message of this round
                sumOfO = sumOfOSentInRoundK - sumOfOSentInRound2K;
            }

            // Store the resulting message of this round in order to calculate the messages in subsequently virtual rounds
            messagesSentInPreviousRounds.put(round, sumOfO);

            // Divide sumOfO in sumOfM and sumOfT (see Reference for more information)
            sumOfM = sumOfO/(room.getRoomSize() + 1);
            sumOfT = sumOfO - (sumOfM*(room.getRoomSize() + 1));

            // Print resulting message of this round
            System.out.println("C_" + round +  " = (" + sumOfM + "," + sumOfT + ")");

            // If we are playing the first round, assign the size of the collision
            if (round == 1) {
                collisionSize = sumOfT;
                // If the size is 0, it means that no messages were sent during this session, so we finish the protocol
                if (collisionSize == 0) {
                    System.out.println("NO MESSAGES WERE SENT");
                    finished = true;
                    continue;
                }
            }

            // Depending on the resulting message, we have to analyze either there was a collision or not in this round
            // <sumOfT> = 1 => No Collision Round => a message went through clearly, received by the rest of the nodes
            if (sumOfT == 1) {
                // Increase the number of messages that went through the protocol
                messagesSentWithNoCollisions++;

                // Add message received in this round in order to calculate messages in subsequently virtual rounds
                messagesReceived.add(sumOfM);

                // If the message that went through is mine, my message was transmitted
                // We have to set the variable in order to start sending zero messages in subsequently rounds
                if (sumOfM == outputMessage.getMessage())
                    messageTransmitted = true;

                // If the number of messages that went through equals the collision size, the collision was completely resolved
                // Set variable to finalize the protocol in the next round
                if (messagesSentWithNoCollisions == collisionSize)
                    finished = true;
            }

            // <sumOfT> != 1 => Collision produced or no messages sent in this round (last can only occur in probabilistic mode)
            else {
                // In probabilistic mode, two things could happen and they are both solved the same way: (see Reference for more information)
                // 1) No messages were sent in a real round (<sumOfT> = 0)
                // 2) All messages involved in the collision of the "father" round are sent in this round and the same collision is produced
                if (round != 1 && (sumOfT == 0 || sumOfO == messagesSentInPreviousRounds.get(round/2))) {
                    // The no splitting of messages can also happen if two messages sent are the same one
                    // TODO: think in a way to solve this

                    // We have to re-do the "father" round in order to expect that no all nodes involved in the collision re-send their message in the same round
                    // Add the "father" round to happen after this one
                    addRoundToHappenFirst(nextRoundsToHappen, round/2);
                    // Remove the virtual round related to this problematic round
                    removeRoundToHappen(nextRoundsToHappen, round+1);
                    // As we removed the next round from happening, we have to reassign the sending round to the "father" round once more
                    if (nextRoundAllowedToSend == round+1 || nextRoundAllowedToSend == round)
                        nextRoundAllowedToSend = round/2;
                }
                // In either re-sending modes, a "normal" collision can be produced
                // <sumOfT> > 1 => A Collision was produced
                else {
                    // Check if my message was involved in the collision, checking if in this round i was allowed to send my message
                    if (nextRoundAllowedToSend == round) {
                        // Check in which mode of re-sending my message we are
                        // Non probabilistic mode (see Reference for more information)
                        if (room.getNonProbabilisticMode()) {
                            // Calculate average message, if my message is below that value i re-send in the round (2*round)
                            if (outputMessage.getMessage() <= sumOfM / sumOfT) {
                                nextRoundAllowedToSend = 2 * round;
                            }
                            // If not, i re-send my message in the round (2*round + 1)
                            else {
                                nextRoundAllowedToSend = 2 * round + 1;
                            }
                        }
                        // Probabilistic mode (see Reference for more information)
                        else {
                            // Throw a coin to see if a send in the round (2*round) or (2*round + 1)
                            boolean coin = new SecureRandom().nextBoolean();
                            if (coin) {
                                nextRoundAllowedToSend = 2 * round;
                            } else {
                                nextRoundAllowedToSend = 2 * round + 1;
                            }
                        }
                    }
                    // Add (2*round) and (2*round + 1) rounds to future plays
                    addRoundsToHappenNext(nextRoundsToHappen, 2 * round, 2 * round + 1);
                }
            }

            // Print a blank line
            System.out.println();

            // Prevent infinite loops
            /*if (round >= Math.pow(2, collisionSize)*4)
                finished = true;*/

        }
    }

    private void synchronizeNodes(int nodeIndex, ZMQ.Socket[] repliers, ZMQ.Socket[] requestors, Room room) {
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1)
            for (ZMQ.Socket replier : repliers) {
                // The replier wait to receive a message
                replier.recv(0);
                // When the replier receives the message, replies with another message
                replier.send("", 0);
            }
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize())
            for (ZMQ.Socket requestor : requestors) {
                // The requestor sends a message
                requestor.send("".getBytes(), 0);
                // The requestor waits to receive a reply by the correspondent replier
                requestor.recv(0);
            }
    }

    // Remove a round to happen afterwards
    private void removeRoundToHappen(LinkedList<Integer> nextRoundsToHappen, int round) {
        nextRoundsToHappen.removeFirstOccurrence(round);
    }

    // Add a round to happen immediately after the running one
    private void addRoundToHappenFirst(LinkedList<Integer> nextRoundsToHappen, int round) {
        nextRoundsToHappen.addFirst(round);
    }

    // Add two rounds to happen afterwards (they are added at the end of the LinkedList)
    private void addRoundsToHappenNext(LinkedList<Integer> nextRoundsToHappen, int firstRoundToAdd, int secondRoundToAdd) {
        nextRoundsToHappen.add(firstRoundToAdd);
        nextRoundsToHappen.add(secondRoundToAdd);
    }

    // Create all the repliers (the quantity depends on the index of the node) socket necessary to run the protocol (see Reference for more information)
    public void initializeRepliersArray(int nodeIndex, ZContext context) {
        // Create an array of sockets
        ZMQ.Socket[] repliers = null;
        // The "first" node doesn't have any replier sockets
        if (nodeIndex != 1) {
            // Initialize the array with exactly (<nodeIndex> - 1) sockets
            repliers = new ZMQ.Socket[nodeIndex-1];
            for (int i = 0; i < repliers.length; i++) {
                // Create the REP socket
                repliers[i] = context.createSocket(ZMQ.REP);
                // Bind this REP socket to the correspondent port in order to be connected by his correspondent REQ socket of another node
                repliers[i].bind("tcp://*:" + (7000+i));
            }
        }
        // Return the array with the replier sockets
        this.repliers = repliers;
    }

    // Create all the requestors (the quantity depends on the index of the node) socket necessary to run the protocol (see Reference for more information)
    public void initializeRequestorsArray(int nodeIndex, ZContext context, Room room) {
        // Create an array of sockets
        ZMQ.Socket[] requestors = null;
        // The "last" node doesn't have any requestor sockets
        if (nodeIndex != room.getRoomSize()) {
            // Initialize the array with exactly (<n> - <nodeIndex>) sockets
            requestors = new ZMQ.Socket[room.getRoomSize() - nodeIndex];
            for (int i = 0; i < requestors.length; i++) {
                // Create the REQ socket
                requestors[i] = context.createSocket(ZMQ.REQ);
                // Connect this REQ socket to his correspondent REP socket of another node
                requestors[i].connect("tcp://" + room.getNodeIpFromIndex(nodeIndex + i + 1) + ":" + (7000 + nodeIndex - 1));
            }
        }
        // Return the array with the requestor sockets
        this.requestors = requestors;
    }


    public int getRealRoundsPlayed() {
        return realRoundsPlayed;
    }

    public List<Integer> getMessagesReceived() {
        return messagesReceived;
    }
}