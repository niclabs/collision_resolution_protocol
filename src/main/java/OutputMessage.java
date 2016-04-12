import java.math.BigInteger;
import java.util.Random;

/**
 *
 */
class OutputMessage {

    private String ip;
    private int cmd;
    private BigInteger messageBigInteger;
    private BigInteger messageBigIntegerProtocol;

    static private final int RANDOM_PADDING_LENGTH = 10;

    /**
     *
     * @param ip ip address of the sender node
     * @param cmd command of the message
     * @param messageProtocol message written as the protocol describes: <m,1> or <0,0>
     */
    OutputMessage(String ip, int cmd, BigInteger messageProtocol) {
        this.ip = ip;
        this.cmd = cmd;
        this.messageBigIntegerProtocol = messageProtocol;
        this.messageBigInteger = BigInteger.ZERO;

    }

    /**
     *
     */
    OutputMessage() {
        this.cmd = 0;
    }

    /**
     *
     * @return message in BigInteger form
     */
    BigInteger getMessageBigInteger() {
        return messageBigInteger;
    }

    /**
     *
     * @return protocol message in BigInteger form
     */
    BigInteger getMessageBigIntegerProtocol() {
        return messageBigIntegerProtocol;
    }

    /**
     *
     * @param ip ip address of the sender node
     */
    void setSenderNodeIp(String ip) {
        this.ip = ip;
    }

    /**
     *
     * @param message message that the sender wants to communicate
     * @param room room where the message is going to be sent
     */
    void setMessage(String message, Room room) {
        // Generate random characters to prevent infinite protocol when equal messages collide
        String randomString = generateRandomString(RANDOM_PADDING_LENGTH);

        this.messageBigInteger = new BigInteger(randomString.concat(message).getBytes());

        // Set to the OutputMessage object the actual message that the node wants to communicate (<m>)
        // If the message is 0, the node doesn't want to send any message to the room
        if (message.equals("0")) {
            this.messageBigIntegerProtocol = BigInteger.ZERO;
        }
        // If not, the message to send must have the form (<m>,1), that it translates to: <m>*(n+1) + 1 (see Reference for more information)
        else {
            int a = room.getRoomSize()+1;
            this.messageBigIntegerProtocol = messageBigInteger.multiply(BigInteger.valueOf(a)).add(BigInteger.ONE);
        }
    }

    /**
     *
     * @param l length of the random characters that will be append to the message
     * @return random characters append to message
     */
    private String generateRandomString(int l) {
        String strAllowedCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sbRandomString = new StringBuilder(l);
        for(int i = 0 ; i < l; i++){
            //get random integer between 0 and string length
            int randomInt = new Random().nextInt(strAllowedCharacters.length());

            //get char from randomInt index from string and append in StringBuilder
            sbRandomString.append( strAllowedCharacters.charAt(randomInt) );
        }
        return sbRandomString.toString();
    }

    /**
     *
     * @param cmd command
     */
    void setCmd(int cmd) {
        this.cmd = cmd;
    }

    /**
     *
     * @param sumOfM message that went through the protocol which has a random string appended
     * @return message without the randomness
     */
    static String getMessageWithoutRandomness(BigInteger sumOfM) {
        return new String(sumOfM.toByteArray()).substring(RANDOM_PADDING_LENGTH);
    }
}
