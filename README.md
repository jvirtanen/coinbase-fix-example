# Coinbase Pro FIX Example

This is a simple example application that demonstrates how to connect to
[Coinbase Pro][] using the [FIX API][] and [Philadelphia][], an open source
FIX engine for the JVM.

  [Coinbase Pro]: https://pro.coinbase.com
  [FIX API]: https://docs.pro.coinbase.com/#fix-api
  [Philadelphia]: https://github.com/paritytrading/philadelphia

Building and running this application requires Java Development Kit (JDK) 11
or newer and Maven.

## Usage

To build and run the application, follow these steps:

1. Install [stunnel][], for example, using [Homebrew][]:

    ```shell
    brew install stunnel
    ```

2. Download the TLS certificate:

    ```shell
    openssl s_client -showcerts -connect fix.pro.coinbase.com:4198 < /dev/null | \
        openssl x509 -outform PEM > fix.pro.coinbase.com.pem
    ```

3. Build the application:

    ```shell
    mvn package
    ```

4. Create a configuration file, `etc/example.conf`:

    ```shell
    cp etc/example.conf.template etc/example.conf
    ```

5. Fill in the API passphrase, key, and secret in the configuration file,
   `etc/example.conf`.

6. Start stunnel:

    ```shell
    stunnel etc/stunnel.conf
    ```

7. Run the application:

    ```shell
    java -jar coinbase-fix-example.jar etc/example.conf
    ```

The application logs onto Coinbase Pro and immediately logs out.

  [stunnel]: https://www.stunnel.org
  [Homebrew]: https://brew.sh

## License

Copyright 2017 Jussi Virtanen.

Released under the Apache License, Version 2.0. See `LICENSE.txt` for details.
