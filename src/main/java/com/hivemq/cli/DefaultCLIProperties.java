/*
 * Copyright 2019 HiveMQ and the HiveMQ Community
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
 *
 */
package com.hivemq.cli;


import com.hivemq.cli.converters.EnvVarToByteBufferConverter;
import com.hivemq.cli.converters.FileToByteBufferConverter;
import com.hivemq.cli.converters.FileToCertificateConverter;
import com.hivemq.cli.converters.FileToPrivateKeyConverter;
import com.hivemq.client.mqtt.MqttVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pmw.tinylog.Level;

import javax.inject.Singleton;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Represents the default properties which are used throughout the cli.
 * This class pre defines values for every default property which can be overwritten by using the properties file given
 * in the constructor.
 *
 * The CLI uses the default location of the properties file at `~/.mqtt-cli/config.properties` which will be written if
 * not already present.
 *
 * @author Till Seeberger
 */
@Singleton
public class DefaultCLIProperties {

    private static final String MQTT_VERSION = "mqtt.version";
    private static final String HOST = "mqtt.host";
    private static final String PORT = "mqtt.port";
    private static final String DEBUG_LEVEL_SHELL = "debug.level.shell";
    private static final String CLIENT_ID_PREFIX = "client.id.prefix";
    private static final String CLIENT_ID_LENGTH = "client.id.length";
    private static final String SUBSCRIBE_OUTPUT_FILE = "client.subscribe.output";
    private static final String LOGFILE_PATH = "logfile.path";
    private static final String USERNAME = "auth.username";
    private static final String PASSWORD = "auth.password";
    private static final String PASSWORD_FILE = "auth.password.file";
    private static final String PASSWORD_ENV = "auth.password.env";
    private static final String CLIENT_CERTIFICATE = "auth.client.cert";
    private static final String CLIENT_PRIVATE_KEY = "auth.client.key";
    private static final String SERVER_CERTIFICATE = "auth.server.cafile";

    private Map<String, String> propertyToValue = new HashMap<String, String>() {{
       put(MQTT_VERSION, "5");
       put(HOST, "localhost");
       put(PORT, "1883");
       put(DEBUG_LEVEL_SHELL, "verbose");
       put(CLIENT_ID_PREFIX, "mqtt");
       put(CLIENT_ID_LENGTH, "8");
       put(SUBSCRIBE_OUTPUT_FILE, null);
       put(LOGFILE_PATH, System.getProperty("user.home") + File.separator +
                        ".mqtt-cli" + File.separator +
                        "logs" + File.separator);
       put(USERNAME, null);
       put(PASSWORD, null);
       put(PASSWORD_FILE, null);
       put(PASSWORD_ENV, null);
       put(CLIENT_CERTIFICATE, null);
       put(CLIENT_PRIVATE_KEY, null);
    }};


    private File storePropertiesFile;

    public DefaultCLIProperties(final @NotNull String filePath) {
        storePropertiesFile = new File(filePath);
    }


    void init() throws IOException, IllegalArgumentException {
        if (!storePropertiesFile.exists()) {
            createFile();
        }
        else if (!storePropertiesFile.isFile()) {
            throw new IllegalArgumentException("The given file path does not lead to a valid properties file ('"
                    + storePropertiesFile.getPath() +
                    "')");
        }
        else {
            readFromFile();
        }
    }

    private void readFromFile() throws IOException {
        final Properties fileProperties = new Properties();

        try (final InputStream input = new FileInputStream(storePropertiesFile)) {
            fileProperties.load(input);
        }

        fileProperties.stringPropertyNames()
                .stream()
                .filter(name -> propertyToValue.containsKey(name))
                .distinct()
                .forEach(name -> propertyToValue.put(name, fileProperties.getProperty(name)));
    }

    private void createFile() throws IOException {

        storePropertiesFile.getParentFile().mkdirs();
        assert storePropertiesFile.createNewFile();
        try (final OutputStream output = new FileOutputStream(storePropertiesFile)) {
            final Properties properties = getProperties();
            properties.store(output, null);
        }

    }

    public Properties getProperties() {
        final Properties properties = new Properties();

        propertyToValue.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .distinct()
                .forEach(entry -> properties.put(entry.getKey(), entry.getValue()));

        return properties;
    }

    @Nullable
    public File getFile() {
        return storePropertiesFile;
    }

    /****************
     * Getter for properties in the concrete data types *
     ***************/

    @NotNull
    public MqttVersion getMqttVersion() {
        final String versionString = propertyToValue.get(MQTT_VERSION);
        switch (versionString) {
            case "5": return MqttVersion.MQTT_5_0;
            case "3": return MqttVersion.MQTT_3_1_1;
        }
        throw new IllegalArgumentException("'" + versionString + "' is not a valid MQTT version");
    }

    @NotNull
    public String getHost() {
        return propertyToValue.get(HOST);
    }

    public int getPort() {
        return Integer.parseInt(propertyToValue.get(PORT));
    }

    @NotNull
    public Level getShellDebugLevel() {
        final String shellDebugLevel = propertyToValue.get(DEBUG_LEVEL_SHELL);
        switch (shellDebugLevel.toLowerCase()) {
            case "trace":
            case "verbose":
                return Level.TRACE;
            case "debug": return Level.DEBUG;
            case "info": return Level.INFO;
        }
        throw new IllegalArgumentException("'" + shellDebugLevel + "' is not a valid debug level");
    }

    @NotNull
    public String getClientPrefix() {
        return propertyToValue.get(CLIENT_ID_PREFIX);
    }

    public int getClientLength() {
        return Integer.parseInt(propertyToValue.get(CLIENT_ID_LENGTH));
    }

    @Nullable
    public String getClientSubscribeOutputFile() {
        return propertyToValue.get(SUBSCRIBE_OUTPUT_FILE);
    }

    @NotNull
    public String getLogfilePath() {
        return propertyToValue.get(LOGFILE_PATH);
    }

    @Nullable
    public String getUsername() {
        return propertyToValue.get(USERNAME);
    }

    @Nullable
    public ByteBuffer getPassword() throws Exception {
        final String passwordText = propertyToValue.get(PASSWORD);
        final String passwordFile = propertyToValue.get(PASSWORD_FILE);
        final String passwordFromEnv = propertyToValue.get(PASSWORD_ENV);
        ByteBuffer password = null;

        if (passwordText != null) {
            password = ByteBuffer.wrap(passwordText.getBytes());
        }

        if (passwordFile != null) {
            password = new FileToByteBufferConverter().convert(passwordFile);
        }

        if (passwordFromEnv != null) {
            password = new EnvVarToByteBufferConverter().convert(passwordFromEnv);
        }

        return password;
    }

    @Nullable
    public X509Certificate getClientCertificate() throws Exception {
        final String clientCertificate = propertyToValue.get(CLIENT_CERTIFICATE);
        if (clientCertificate == null) {
            return null;
        }
        return new FileToCertificateConverter().convert(clientCertificate);
    }

    @Nullable
    public PrivateKey getClientPrivateKey() throws Exception {
        final String clientPrivateKey = propertyToValue.get(CLIENT_PRIVATE_KEY);
        if (clientPrivateKey == null) {
            return null;
        }
        return new FileToPrivateKeyConverter().convert(clientPrivateKey);
    }

    @Nullable
    public X509Certificate getServerCertificate() throws Exception {
        final String serverCertificate = propertyToValue.get(SERVER_CERTIFICATE);
        if (serverCertificate == null) {
            return null;
        }
        return new FileToCertificateConverter().convert(serverCertificate);
    }

}
