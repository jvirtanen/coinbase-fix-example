/*
 * Copyright 2017 Jussi Virtanen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jvirtanen.coinbase.fix;

import static com.paritytrading.philadelphia.coinbase.CoinbaseTags.*;
import static com.paritytrading.philadelphia.fix42.FIX42Enumerations.*;
import static com.paritytrading.philadelphia.fix42.FIX42MsgTypes.*;
import static com.paritytrading.philadelphia.fix42.FIX42Tags.*;

import com.paritytrading.philadelphia.FIXConfig;
import com.paritytrading.philadelphia.FIXConnection;
import com.paritytrading.philadelphia.FIXConnectionStatusListener;
import com.paritytrading.philadelphia.FIXMessage;
import com.paritytrading.philadelphia.FIXMessageListener;
import com.paritytrading.philadelphia.FIXVersion;
import com.paritytrading.philadelphia.coinbase.Coinbase;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import tlschannel.ClientTlsChannel;

class Example {

    private static boolean receive;

    public static void main(String[] args) {
        if (args.length != 1)
            usage();

        try {
            main(config(args[0]));
        } catch (ConfigException | FileNotFoundException e) {
            error(e);
        } catch (IOException | NoSuchAlgorithmException e) {
            fatal(e);
        }
    }

    public static void main(Config config) throws IOException, NoSuchAlgorithmException {
        var address = config.getString("coinbase.fix.address");
        var port    = config.getInt("coinbase.fix.port");

        var passphrase = config.getString("coinbase.api.passphrase");
        var key        = config.getString("coinbase.api.key");
        var secret     = config.getString("coinbase.api.secret");

        var socketChannel = SocketChannel.open();

        socketChannel.connect(new InetSocketAddress(address, port));

        var sslContext = SSLContext.getDefault();
        var tlsChannel = ClientTlsChannel.newBuilder(socketChannel, sslContext).build();

        var builder = new FIXConfig.Builder()
            .setVersion(FIXVersion.FIX_4_2)
            .setSenderCompID(key)
            .setTargetCompID("Coinbase")
            .setHeartBtInt(30);

        var listener = new FIXMessageListener() {

            @Override
            public void message(FIXMessage message) {
                printf("Message: %s\n", message.getMsgType());
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

        var connection = new FIXConnection(tlsChannel, builder.build(), listener, statusListener, System.currentTimeMillis());

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

    private static Config config(String filename) throws FileNotFoundException {
        var file = new File(filename);
        if (!file.exists() || !file.isFile())
            throw new FileNotFoundException(filename + ": No such file");

        return ConfigFactory.parseFile(file);
    }

    private static void usage() {
        System.err.println("Usage: coinbase-fix-example <configuration-file>");
        System.exit(2);
    }

    private static void error(Throwable throwable) {
        System.err.println("error: " + throwable.getMessage());
        System.exit(1);
    }

    private static void fatal(Throwable throwable) {
        System.err.println("fatal: " + throwable.getMessage());
        System.err.println();
        throwable.printStackTrace(System.err);
        System.err.println();
        System.exit(1);
    }

    private static void printf(String format, Object... args) {
        System.out.printf(Locale.US, format, args);
    }

}
