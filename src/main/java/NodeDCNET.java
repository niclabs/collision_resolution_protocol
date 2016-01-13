import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

class NodeDCNET implements ZThread.IAttachedRunnable {

    private final int dcNetSize = 3;
    private final String networkIp;
    private final String name;

    int outputMessage = 0;

    public NodeDCNET(String networkIp, String name) {
        this.networkIp = networkIp;
        this.name = name;
    }

    public static void main(String[] args) throws IOException {
        new NodeDCNET("127.0.0.1", "Node " + args[0]).createNode();
    }

    // Receiver Thread
    @Override
    public void run(Object[] args, ZContext context, ZMQ.Socket pipe) {
        String network_ip = (String) args[0];
        ZMQ.Socket receiver = context.createSocket(ZMQ.SUB);

        // Where to find other nodes working
        for (int port = 9000; port < 9000 + dcNetSize; port++) {
            receiver.connect("tcp://" + network_ip + ":" + port);
        }

        // Subscribe to whatever the other nodes say
        receiver.subscribe("".getBytes());

        // Write to the other nodes
        while (!Thread.currentThread().isInterrupted()) {

        }

        receiver.close();

    }

    // Sender Thread
    public void createNode() throws IOException {
        ZContext context = new ZContext();

        // Run method to find all possible connections in the network
        ZThread.fork(context, new NodeDCNET(this.networkIp, this.name), networkIp);

        // Create socket to receive connections from other nodes
        ZMQ.Socket sender = context.createSocket(ZMQ.PUB);
        sender.setLinger(5000);
        sender.setHWM(0);

        // Explore in all the ports to bind my connection
        for (int i = 0; i < dcNetSize; i++) {
            try {
                sender.bind("tcp://*:900" + i);
            } catch (Exception e) {
                continue;
            }
            System.out.println(name + " binded on port 900" + i);
            break;
        }

        //  Socket to receive signals
        ZMQ.Socket syncservice = context.createSocket(ZMQ.REP);


        // Write to the other nodes
        while (!Thread.currentThread().isInterrupted()) {

        }

        sender.close();
        context.destroy();

    }

}