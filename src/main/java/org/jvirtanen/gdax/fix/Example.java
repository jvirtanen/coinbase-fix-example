package org.jvirtanen.gdax.fix;

import static com.paritytrading.philadelphia.fix42.FIX42Enumerations.*;
import static com.paritytrading.philadelphia.fix42.FIX42MsgTypes.*;
import static com.paritytrading.philadelphia.fix42.FIX42Tags.*;
import static com.paritytrading.philadelphia.gdax.GDAXTags.*;
import static org.jvirtanen.util.Applications.*;

import com.paritytrading.philadelphia.FIXConfig;
import com.paritytrading.philadelphia.FIXMessage;
import com.paritytrading.philadelphia.FIXMessageListener;
import com.paritytrading.philadelphia.FIXSession;
import com.paritytrading.philadelphia.FIXStatusListener;
import com.paritytrading.philadelphia.FIXVersion;
import com.paritytrading.philadelphia.gdax.GDAX;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Locale;
import org.jvirtanen.config.Configs;

class Example {

    private static boolean receive;

    public static void main(String[] args) {
        if (args.length != 1)
            usage("gdax-fix-example <configuration-file>");

        try {
            main(config(args[0]));
        } catch (ConfigException | FileNotFoundException e) {
            error(e);
        } catch (IOException e) {
            fatal(e);
        }
    }

    public static void main(Config config) throws IOException {
        InetAddress address = Configs.getInetAddress(config, "gdax.fix.address");
        int         port    = Configs.getPort(config, "gdax.fix.port");

        String passphrase = config.getString("gdax.api.passphrase");
        String key        = config.getString("gdax.api.key");
        String secret     = config.getString("gdax.api.secret");

        SocketChannel channel = SocketChannel.open();

        channel.connect(new InetSocketAddress(address, port));

        FIXConfig.Builder builder = new FIXConfig.Builder()
            .setVersion(FIXVersion.FIX_4_2)
            .setSenderCompID(key)
            .setTargetCompID("Coinbase")
            .setHeartBtInt(30);

        FIXMessageListener listener = new FIXMessageListener() {

            @Override
            public void message(FIXMessage message) {
                printf("Message: %s\n", message.getMsgType().asString());
            }

        };

        FIXStatusListener statusListener = new FIXStatusListener() {

            @Override
            public void close(FIXSession session, String message) {
                printf("Close: %s\n", message);
            }

            @Override
            public void sequenceReset(FIXSession session) {
                printf("Received Sequence Reset\n");
            }

            @Override
            public void tooLowMsgSeqNum(FIXSession session, long receivedMsgSeqNum,
                    long expectedMsgSeqNum) {
                printf("Received too low MsgSeqNum: received %s, expected %s\n",
                        receivedMsgSeqNum, expectedMsgSeqNum);
            }

            @Override
            public void heartbeatTimeout(FIXSession session) {
                printf("Heartbeat timeout\n");
            }

            @Override
            public void reject(FIXSession session, FIXMessage message) {
                printf("Received Reject\n");
            }

            @Override
            public void logon(FIXSession session, FIXMessage message) throws IOException {
                printf("Received Logon\n");

                session.sendLogout();

                printf("Sent Logout\n");
            }

            @Override
            public void logout(FIXSession session, FIXMessage message) {
                printf("Received Logout\n");

                receive = false;
            }

        };

        FIXSession session = new FIXSession(channel, builder.build(),
                listener, statusListener);

        FIXMessage message = session.create();

        session.prepare(message, Logon);

        message.addField(EncryptMethod).setInt(EncryptMethodValues.None);
        message.addField(HeartBtInt).setInt(30);
        message.addField(Password).setString(passphrase);

        GDAX.sign(message, secret);

        session.send(message);

        printf("Sent Logon\n");

        receive = true;

        while (receive) {
            if (session.receive() < 0)
                break;
        }

        session.close();
    }

    private static void printf(String format, Object... args) {
        System.out.printf(Locale.US, format, args);
    }

}
