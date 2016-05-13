import dcnet.DCNETProtocol;

import java.io.UnsupportedEncodingException;
import java.net.SocketException;

public class TestInConsole {

    /**
     * Usage: ./gradlew run -PappArgs=[{message},{directoryIP}]
     * @param args message and ip address of directory node
     */
    public static void main(String[] args) throws UnsupportedEncodingException, SocketException {
        // Parse arguments
        String message = args[0];
        String directoryIp = args[1];

        DCNETProtocol.runProtocol(message, directoryIp, System.out);
    }

}
