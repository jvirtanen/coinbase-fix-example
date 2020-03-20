package org.jvirtanen.coinbase.fix;

import static com.paritytrading.philadelphia.coinbase.CoinbaseTags.*;
import static com.paritytrading.philadelphia.fix42.FIX42Enumerations.*;
import static com.paritytrading.philadelphia.fix42.FIX42MsgTypes.*;
import static com.paritytrading.philadelphia.fix42.FIX42Tags.*;
import static org.jvirtanen.util.Applications.*;

import com.paritytrading.philadelphia.FIXConfig;
import com.paritytrading.philadelphia.FIXConnection;
import com.paritytrading.philadelphia.FIXConnectionStatusListener;
import com.paritytrading.philadelphia.FIXMessage;
import com.paritytrading.philadelphia.FIXMessageListener;
import com.paritytrading.philadelphia.FIXVersion;
import com.paritytrading.philadelphia.coinbase.Coinbase;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Locale;

class Example {

    private static boolean receive;

    public static void main(String[] args) {
        if (args.length != 1)
            usage("coinbase-fix-example <configuration-file>");

        try {
            main(config(args[0]));
        } catch (ConfigException | FileNotFoundException e) {
            error(e);
        } catch (IOException e) {
            fatal(e);
        }
    }

    public static void main(Config config) throws IOException {
        var address = config.getString("coinbase.fix.address");
        var port    = config.getInt("coinbase.fix.port");

        var passphrase = config.getString("coinbase.api.passphrase");
        var key        = config.getString("coinbase.api.key");
        var secret     = config.getString("coinbase.api.secret");

        SocketChannel channel = SocketChannel.open();

        channel.connect(new InetSocketAddress(address, port));

        var builder = new FIXConfig.Builder()
            .setVersion(FIXVersion.FIX_4_2)
            .setSenderCompID(key)
            .setTargetCompID("Coinbase")
            .setHeartBtInt(30);

        var listener = new FIXMessageListener() {

            @Override
            public void message(FIXMessage message) {
                printf("Message: %s\n", message.getMsgType().asString());
            }

        };

        var statusListener = new FIXConnectionStatusListener() {

            @Override
            public void close(FIXConnection connection, String message) {
                printf("Close: %s\n", message);
            }

            @Override
            public void sequenceReset(FIXConnection connection) {
                printf("Received Sequence Reset\n");
            }

            @Override
            public void tooLowMsgSeqNum(FIXConnection connection, long receivedMsgSeqNum,
                    long expectedMsgSeqNum) {
                printf("Received too low MsgSeqNum: received %s, expected %s\n",
                        receivedMsgSeqNum, expectedMsgSeqNum);
            }

            @Override
            public void heartbeatTimeout(FIXConnection connection) {
                printf("Heartbeat timeout\n");
            }

            @Override
            public void reject(FIXConnection connection, FIXMessage message) {
                printf("Received Reject\n");
            }

            @Override
            public void logon(FIXConnection connection, FIXMessage message) throws IOException {
                printf("Received Logon\n");

                connection.sendLogout();

                printf("Sent Logout\n");
            }

            @Override
            public void logout(FIXConnection connection, FIXMessage message) {
                printf("Received Logout\n");

                receive = false;
            }

        };

        var connection = new FIXConnection(channel, builder.build(), listener, statusListener);

        var message = connection.create();

        connection.prepare(message, Logon);

        message.addField(EncryptMethod).setInt(EncryptMethodValues.None);
        message.addField(HeartBtInt).setInt(30);
        message.addField(Password).setString(passphrase);

        Coinbase.sign(message, secret);

        connection.send(message);

        printf("Sent Logon\n");

        receive = true;

        while (receive) {
            if (connection.receive() < 0)
                break;
        }

        connection.close();
    }

    private static void printf(String format, Object... args) {
        System.out.printf(Locale.US, format, args);
    }

}
