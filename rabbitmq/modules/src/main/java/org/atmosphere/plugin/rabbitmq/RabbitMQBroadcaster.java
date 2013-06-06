/*
 * Copyright 2013 Jean-Francois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.plugin.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.util.AbstractBroadcasterProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Simple {@link org.atmosphere.cpr.Broadcaster} implementation based on RabbitMQ
 *
 * @author Thibault Normand
 * @author Jean-Francois Arcand
 */
public class RabbitMQBroadcaster extends AbstractBroadcasterProxy {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQBroadcaster.class);

    public static final String PARAM_HOST = RabbitMQBroadcaster.class.getName() + ".host";
    public static final String PARAM_USER = RabbitMQBroadcaster.class.getName() + ".user";
    public static final String PARAM_PASS = RabbitMQBroadcaster.class.getName() + ".password";
    public static final String PARAM_EXCHANGE_TYPE = RabbitMQBroadcaster.class.getName() + ".exchange";

    private final String exchangeName;
    private final ConnectionFactory connectionFactory;
    private final Connection connection;
    private final Channel channel;
    private final String exchange;

    private String queueName;
    private String consumerTag;

    public RabbitMQBroadcaster(String id, AtmosphereConfig config) {
        super(id, null, config);

        String s = config.getInitParameter(PARAM_EXCHANGE_TYPE);
        if (s != null) {
            exchange = s;
        } else {
            exchange = "fanout";
        }

        exchangeName = "atmosphere." + exchange + "." + id;
        try {
            logger.debug("Create Connection Factory");
            connectionFactory = new ConnectionFactory();

            logger.debug("Try to acquire a connection ...");
            connection = connectionFactory.newConnection(getBroadcasterConfig().getExecutorService());
            channel = connection.createChannel();

            logger.debug("Topic creation '{}'...", exchangeName);
            channel.exchangeDeclare(exchangeName, exchange);
        } catch (Exception e) {
            String msg = "Unable to configure RabbitMQBroadcaster";
            logger.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    @Override
    public void setID(String id) {
        super.setID(id);
        restartConsumer();
    }

    @Override
    public String getID() {
        String id = super.getID();
        if (id.startsWith("/*")) {
            id = "atmosphere";
        }
        return id;
    }

    @Override
    public void incomingBroadcast() {
        logger.debug("Incoming broadcast");
    }

    @Override
    public void outgoingBroadcast(Object message) {
        try {
            String id = getID();
            if (message instanceof String) {
                logger.debug("Outgoing broadcast : {}", message);

                channel.basicPublish(exchangeName, id,
                        MessageProperties.PERSISTENT_TEXT_PLAIN, message.toString().getBytes());
            } else {
                throw new IOException("Message is not a string, so could not be handled !");
            }

        } catch (IOException e) {
            logger.warn("Failed to send message over RabbitMQ", e);
        }
    }

    void restartConsumer() {
        try {
            String id = getID();

            if (consumerTag != null) {
                logger.debug("Delete consumer {}", consumerTag);
                channel.basicCancel(consumerTag);
                consumerTag = null;
            }

            if (queueName != null) {
                logger.debug("Delete queue {}", queueName);
                channel.queueUnbind(queueName, exchangeName, id);
                channel.queueDelete(queueName);
                queueName = null;
            }

            queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, exchangeName, id);

            logger.info("Create AMQP consumer on queue {}, for routing key {}", queueName, id);

            DefaultConsumer queueConsumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag,
                                           Envelope envelope,
                                           AMQP.BasicProperties properties,
                                           byte[] body)
                        throws IOException {
                    try {
                        broadcastReceivedMessage(new String(body));
                    } catch (Exception ex) {
                        logger.warn("Failed to broadcast message", ex);
                    }
                }
            };

            consumerTag = channel.basicConsume(queueName, true, queueConsumer);
            logger.info("Consumer " + consumerTag + " for queue {}, on routing key {}", queueName, id);

        } catch (Throwable ex) {
            String msg = "Unable to initialize RabbitMQBroadcaster";
            logger.error(msg, ex);
            throw new IllegalStateException(msg, ex);
        }
    }

    /**
     * Close all related JMS factory, connection, etc.
     */
    @Override
    public synchronized void releaseExternalResources() {
        try {
            if (channel != null && channel.isOpen()) {
                if (consumerTag != null) {
                    channel.basicCancel(consumerTag);
                }
                channel.close();
            }
            connection.close();
        } catch (Throwable ex) {
            logger.warn("releaseExternalResources", ex);
        }
    }

}