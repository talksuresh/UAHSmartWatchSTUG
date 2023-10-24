package com.example.stug;

import android.content.Context;
import android.util.Log;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

public class MQTTClient {
    public Mqtt3AsyncClient client;

    public boolean mqttClientConnected = false;

    //necessary variables for activity
    private Context appContext;

    //server information
    private final String server = "test.mosquitto.org";

    //useful for debugging
    private final String TAG = "MQTT Connection";

    public MQTTClient(Context appContext) {
        this.appContext = appContext;
        //create client
        this.client = client = MqttClient.builder()
                .useMqttVersion3()
                .identifier("STUGClient")
                .serverHost(server)
                .serverPort(1883)
                .automaticReconnectWithDefaultConfig()//this keeps connection alive to use app after a long time
                .buildAsync();

        this.reconnect(); //custom method to connect
    }

    public void publishMessage(String payload, String topic) {
        if (mqttClientConnected == true) {
            client.publishWith()
                    .topic(topic)
                    .payload(payload.getBytes())
                    .retain(false)
                    .send()
                    .whenComplete((publish, throwable) -> {
                        if (throwable != null) {
                            // handle failure to publish
                            Log.i("Publish", "Failure");
                        } else {
                            Log.i("Publish", "Success");
                        }
                    });
        }
    }

    //possibly useful at some point, disconnects client
    public void disconnect() {
        if (mqttClientConnected == true) {
            client.disconnect();
            mqttClientConnected = false;
        }
    }

    //connect to server
    public void reconnect() {
        client.connect()
                .whenComplete(((mqtt3ConnAck, throwable) -> {
                    if (throwable != null) {
                        Log.i(TAG, "Failure"); //useful for debugging
                    } else {
                        Log.i(TAG, "Success");
                        mqttClientConnected = true;
                    }
                }));
    }
}
