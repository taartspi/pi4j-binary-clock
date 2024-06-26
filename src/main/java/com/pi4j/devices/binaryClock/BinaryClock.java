

/*
 *    * Copyright (C) 2012 - 2024 Pi4J
 *  * %%
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * -
 *  #%L
 *  **********************************************************************
 *  ORGANIZATION  :  Pi4J
 *  PROJECT       :  Pi4J :: EXTENSION
 *  FILENAME      :  BinaryClock.java
 *
 *  This file is part of the Pi4J project. More information about
 *  this project can be found here:  https://pi4j.com/
 *  **********************************************************************
 *  %%
 */

package com.pi4j.devices.binaryClock;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.exception.IOException;
import com.pi4j.io.i2c.I2C;
import com.pi4j.util.Console;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.Timer;
import java.util.TimerTask;

/*
pcfDevOne  Byte one, Seconds LED
           Byte two, Minutes LEDs

pcfDevTwo  Byte one, hours LEDs
           Byte two, 0xff filler


 */
public class BinaryClock  extends TimerTask {

    private I2C pcfDevOne = null;
    private I2C pcfDevTwo = null;
    private final int busNum;
    private final int addressOne;  // first PCF8575
    private final int addressTwo;  // second PCF8575

   private final String traceLevel;

    private Logger logger;
    private final Context pi4j;
    private final Console console;


    private byte currentSecond;
    private byte currentMinute;
    private byte currentHour;
private int interval;

    private static final int DEFAULT_ADDRESS_ONE = 0x24;
    private static final int DEFAULT_ADDRESS_TWO = 0x22;

    private static final int DEFAULT_BUS = 0x1;

    /**
     * @param pi4j
     * @param console
     * @param bus
     * @param addressOne
     * @param addressTwo
     * @param traceLevel
     */
    public BinaryClock(Context pi4j, Console console, int bus, int addressOne, int addressTwo, String traceLevel) throws InterruptedException {
        this.traceLevel = traceLevel;
        this.busNum = bus;
        this.addressOne = addressOne;
        this.addressTwo = addressTwo;
        this.console = console;
        this.pi4j = pi4j;
        this.init();

    }

    /**
     * Configure I2C and Input pin for both PCF8575
     */
    public void init() {
        System.setProperty("org.slf4j.simpleLogger.log." + BinaryClock.class.getName(), this.traceLevel);
        this.logger = LoggerFactory.getLogger(BinaryClock.class);



        this.pcfDevOne = this.createI2cDevice(this.addressOne);
        this.pcfDevTwo = this.createI2cDevice(this.addressTwo);


        // Set all pins to  '1'. LED off
        this.configAllPinInput(this.pcfDevOne);

        // Set all pins to '1' LED off
        this.configAllPinInput(this.pcfDevTwo);

        // get the current time and set internal state and the LEDs
        this.initTimes();

        this.logger.trace("<<< Exit: init  device  ");
    }


    private void initTimes() {
        LocalTime thisSec;
        thisSec = LocalTime.now();
        String string_asc = String.format("%1$02d:%2$02d:%3$02d", thisSec.getHour(), thisSec.getMinute(), thisSec.getSecond());
        this.logger.trace("initTimes()  current time  " + string_asc);
        int sec = thisSec.getSecond();
        this.currentSecond = (byte) thisSec.getSecond();
        this.currentMinute = (byte) thisSec.getMinute();
        byte hr = (byte) thisSec.getHour();
        if (hr > 12) {
            this.currentHour = (byte) (hr - 12);
        } else {
            this.currentHour = hr;
        }
        byte[] data = new byte[2];
        data[0] = this.dec2bcd(this.currentSecond);
        data[1] = this.dec2bcd(this.currentMinute);
        this.writeToDev(data, this.pcfDevOne);

        data[0] = this.dec2bcd(this.currentHour);
        data[1] = (byte) 0XFF;
        this.writeToDev(data, this.pcfDevTwo);

        this.logger.trace(string_asc);
    }




    private byte dec2bcd(int num) {
        int ones = 0;
        int tens = 0;
        int temp = 0;

        ones = num % 10;
        temp = num / 10;
        tens = temp << 4;
        return (byte) (tens + ones);
    }


    /**
     * Bump seconds by 1. If past 59 a minute passed so increment the minute count and set second to zero
     */
    private void incrementSeconds() {
        this.currentSecond++;
        if (this.currentSecond > 59) {
            this.currentSecond = 0;
            this.incrementMinutes();
        }else {
            byte[] data = new byte[2];
            data[0] = this.dec2bcd(this.currentSecond);
            data[1] = this.dec2bcd(this.currentMinute);
            this.writeToDev(data, this.pcfDevOne);
        }
    }


    /**
     * Bump minutes by 1. If past 59 an hour passed so increment the hour count and set minutes to zero
     */
    private void incrementMinutes() {
        this.currentMinute++;
        if (this.currentMinute > 59) {
            this.currentMinute = 0;
            this.incrementHour();
        }
        byte[] data = new byte[2];
        data[0] = this.dec2bcd(this.currentSecond);
        data[1] = this.dec2bcd(this.currentMinute);
        this.writeToDev(data, this.pcfDevOne);
    }


    /**
     * Bump Hours by 1. If past 12 noon/midnight passed so set hours to 1
     */
    private void incrementHour() {
        this.currentHour++;
        if (this.currentHour > 12) {
            this.currentHour = 1;
            // Possibly the one second time drifted, so reset the time
            this.initTimes();
        }
        byte[] data = new byte[2];
        data[0] = this.dec2bcd(this.currentHour);
        data[1] = (byte) 0XFF;
        this.writeToDev(data, this.pcfDevTwo);
    }

    /**
     * Config as an INPUT pin, essentially turning the LED off.
     *
     * @param thisDevice
    */
    private void configAllPinInput( I2C thisDevice) {
        this.logger.trace(">>> Enter: configAllPinOutput   Device " + thisDevice);
        byte[] commandByte = new byte[2];
        commandByte[0] = (byte) 0xff;
        commandByte[1] = (byte) 0xff;
        thisDevice.write(commandByte);
        this.logger.trace("<<< Exit: configAllPinOutput  ");

    }

    /**
     * Write byte to actual device I2C interface
     *
     * @param data  Byte array, size 2
     * @param theDevice I2C device
     */
    private void writeToDev(byte[]data, I2C theDevice) {
        this.logger.trace(">>> Enter: writeToDev  data: " + String.format("byte0:%02x ", data[0])+ String.format(" byte1:%02x ", data[1]));
        data[0] = (byte) ~data[0];
        data[1] = (byte) ~data[1];
        this.logger.trace("Flipped bits, result is data: " + String.format("byte0:%02x ", data[0])+ String.format("  byte1:%02x ", data[1]));
        int rc = theDevice.write(data, 2);
        this.logger.trace("Exit: writeToDev  RC : " + rc);
    }


    private I2C createI2cDevice(int thisAddress) {
        this.logger.trace(">>> Enter:createI2cDevice   bus  " + this.busNum + "  address " + thisAddress);

        var address = thisAddress;
        var bus = this.busNum;

        String id = String.format("0X%02x: ", bus);
        String name = String.format("0X%02x: ", address);
        var i2cDeviceConfig = I2C.newConfigBuilder(this.pi4j)
            .bus(bus)
            .device(address)
            .id(id + " " + name)
            .name(name)
            .provider("linuxfs-i2c")
            .build();
        return this.pi4j.create(i2cDeviceConfig);
    }




    public static void main(String[] args) throws InterruptedException, IOException {
        var console = new Console();
        Context pi4j = Pi4J.newAutoContext();
        int busNum = DEFAULT_BUS;
        int addressOne = DEFAULT_ADDRESS_ONE;
        int addressTwo = DEFAULT_ADDRESS_TWO;


        console.title("<-- The Pi4J V2 Project Extension  -->", "Binary Clock");
        String helpString = " parms: BinaryClock  -b hex value bus    -a1 hex address value first PCF (Second/Hour)  " +
            "-a2 hex address  value second PCF (Hours) -t trace     \n" +
            "-t  trace values : \"trace\", \"debug\", \"info\", \"warn\", \"error\" \n " +
            " or \"off\"  Default \"info\" \n";

        String traceLevel = "info";
        for (int i = 0; i < args.length; i++) {
            String o = args[i];
            if (o.contentEquals("-b")) { // bus
                String a = args[i + 1];
                busNum = Integer.parseInt(a.substring(2), 16);
                i++;
            } else if (o.contentEquals("-a1")) { // device address
                String a = args[i + 1];
                i++;
                addressOne = Integer.parseInt(a.substring(2), 16);
            } else if (o.contentEquals("-a2")) { // device address
                String a = args[i + 1];
                i++;
                addressTwo = Integer.parseInt(a.substring(2), 16);
            } else if (o.contentEquals("-t")) {
                String a = args[i + 1];
                i++;
                traceLevel = a;
                if (a.contentEquals("trace") | a.contentEquals("debug") | a.contentEquals("info") | a.contentEquals("warn") | a.contentEquals("error") | a.contentEquals("off")) {
                    console.println("Changing trace level to : " + traceLevel);
                } else {
                    console.println("Changing trace level invalid  : " + traceLevel);
                    System.exit(41);
                }
            } else if (o.contentEquals("-h")) {
                console.println(helpString);
                System.exit(41);
            } else {
                console.println("  !!! Invalid Parm " + o);
                console.println(helpString);
                System.exit(43);
            }
        }


        console.println("----------------------------------------------------------");
        console.println("PI4J PROVIDERS");
        console.println("----------------------------------------------------------");
        pi4j.providers().describe().print(System.out);
        System.out.println("----------------------------------------------------------");

       // BinaryClock dispObj = new BinaryClock(pi4j, console, busNum, addressOne, addressTwo, traceLevel);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    System.out.println("Shutting down...");
                    pi4j.shutdown();
                } catch (Exception e) {
                    System.out.println("Failed to shutdown");
                }
            }
        });

        Timer timer = new Timer();
        timer.schedule(new BinaryClock(pi4j, console, busNum, addressOne, addressTwo, traceLevel), 1000, 1000);

    }


    @Override
    public void run() {
            this.incrementSeconds();
    }
}
