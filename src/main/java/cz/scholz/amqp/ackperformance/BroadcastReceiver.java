package cz.scholz.amqp.ackperformance;

import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Broadcast Receiver Receives broadcasts from the persistent broadcast queue
 */
public class BroadcastReceiver {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(BroadcastReceiver.class);
    private final InitialContext context;
    private final int timeoutInMillis;

    public BroadcastReceiver(Options options) throws NamingException {
        this.timeoutInMillis = options.getTimeoutInMillis();
        try {
            Properties properties = new Properties();
            properties.setProperty(Context.INITIAL_CONTEXT_FACTORY,
                    "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
            properties
                    .setProperty(
                            "connectionfactory.connection",
                            String.format(
                                    "amqp://admin:admin@App1/?brokerlist='tcp://%s:%d'&maxprefetch='10000'&sync_client_ack='true'",
                                    options.getHostname(), options.getPort()));
            properties
                    .setProperty(
                            "destination.broadcastAddress",
                            String.format(
                                    "broadcast.%s.TradeConfirmation; { node: { type: queue }, create: never, mode: consume, assert: never }",
                                    options.getAccountName()));
            this.context = new InitialContext(properties);
        } catch (NamingException ex) {
            LOGGER.error("Unable to proceed with broadcast receiver", ex);
            throw ex;
        }
    }

    public void run() throws JMSException, NamingException,
            InterruptedException {
		/*
		 * Step 1: Initializing the context based on the properties file we
		 * prepared
		 */
        Connection connection = null;
        Session session = null;
        MessageConsumer broadcastConsumer = null;
        try {
			/*
			 * Step 2: Preparing the connection and session
			 */
            LOGGER.info("Creating connection");
            connection = ((ConnectionFactory) context.lookup("connection"))
                    .createConnection();
            session = connection.createSession(false,
                    Session.CLIENT_ACKNOWLEDGE);
			/*
			 * Step 3: Creating a broadcast receiver / consumer
			 */
            broadcastConsumer = session.createConsumer((Destination) context
                    .lookup("broadcastAddress"));
			/*
			 * Step 4: Starting the connection
			 */
            connection.start();
            LOGGER.info("Connected");

			/*
			 * Step 5: Receiving broadcast messages using listener for timeout
			 * seconds
			 */
            Message msg;
            long messageCount = 0;
            long limit = 1000000; // report all over 1 miliseconds
            long limit2 = 2000000; // report all over 2 miliseconds
            long limit3 = 5000000; // report all over 5 miliseconds
            long limit4 = 50000000; // report all over 50 miliseconds
            long messageOverLimit = 0;
            long messageOverLimit2 = 0;
            long messageOverLimit3 = 0;
            long messageOverLimit4 = 0;

            while (null != (msg = broadcastConsumer.receive(timeoutInMillis)))
            {
                messageCount++;
                long before = System.nanoTime();
                msg.acknowledge();
                long after = System.nanoTime();
                long diff = after - before;

                if (diff > limit4) {
                    System.out.println(diff);
                    messageOverLimit++;
                    messageOverLimit2++;
                    messageOverLimit3++;
                    messageOverLimit4++;
                }
                else if (diff > limit3) {
                    System.out.println(diff);
                    messageOverLimit++;
                    messageOverLimit2++;
                    messageOverLimit3++;
                }
                else if (diff > limit2) {
                    System.out.println(diff);
                    messageOverLimit++;
                    messageOverLimit2++;
                }
                else if (diff > limit) {
                    System.out.println(diff);
                    messageOverLimit++;
                }
                else {
                    //System.out.println(diff);
                }
            }

            System.out.println("Received " + messageCount + " messages. ");
            System.out.println("In total " + messageOverLimit + " were acknowledged in more than " + limit/1000000 + " miliseconds. That is " + ((1.0*messageOverLimit)/messageCount)*100 + "%");
            System.out.println("In total " + messageOverLimit2 + " were acknowledged in more than " + limit2/1000000 + " miliseconds. That is " + ((1.0*messageOverLimit2)/messageCount)*100 + "%");
            System.out.println("In total " + messageOverLimit3 + " were acknowledged in more than " + limit3/1000000 + " miliseconds. That is " + ((1.0*messageOverLimit3)/messageCount)*100 + "%");
            System.out.println("In total " + messageOverLimit4 + " were acknowledged in more than " + limit4/1000000 + " miliseconds. That is " + ((1.0*messageOverLimit4)/messageCount)*100 + "%");

            LOGGER.info("Finished receiving broadcast messages for {} seconds",
                    this.timeoutInMillis / 1000);
        } catch (JMSException | NamingException e) {
            LOGGER.error("Unable to proceed with broadcast receiver", e);
            throw e;
        } finally {
			/*
			 * Step 6: Closing the connection
			 */
            if (broadcastConsumer != null) {
                LOGGER.info("Closing consumer");
                broadcastConsumer.close();
            }
            if (session != null) {
                LOGGER.info("Closing session");
                session.close();
            }
            if (connection != null) {
                // implicitly closes session and producers/consumers
                LOGGER.info("Closing connection");
                connection.close();
            }
        }
    }

    public static void main(String[] args) throws JMSException,
            NamingException, InterruptedException {

        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        System.setProperty("slf4j.logger.org.apache.qpid", "trace");

        Options options = new Options.OptionsBuilder()
                .accountName("ABCFR_ABCFRALMMACC1")
                .hostname("eclbgc01.xeop.de").port(20707)
                .keystoreFilename("ABCFR_ABCFRALMMACC1.keystore")
                .keystorePassword("123456").truststoreFilename("truststore")
                .truststorePassword("123456")
                .certificateAlias("abcfr_abcfralmmacc1").build();
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver(options);
        broadcastReceiver.run();
    }
}