/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hdl.bus;

import java.net.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HdlServer implements Runnable
{
  private static final Logger logger =
    LoggerFactory.getLogger(HdlServer.class);

  // The protocol defines 27 header bytes + up to 128 aux data bytes.
  // However, binary representation allows up 256 bytes of aux data, so
  // use 512 for buffer size just in case.
  public static final int MAX_PACKET_SIZE = 512;

  public static final int PORT = 6000;

  Thread serverThread;

  DatagramSocket socket;

  ConcurrentHashMap<Integer, IHdlDevice> devices = new ConcurrentHashMap<Integer, IHdlDevice>();
  ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  InetAddress gatewayAddress;
  InetAddress listenAddress;

  int retryCount = 3;
  int retryTime = 600;
  TimeUnit retryTimeUnit = TimeUnit.MILLISECONDS;

  public synchronized void start(String listenAddress, String gatewayAddress) throws IOException {
    if (socket != null || serverThread != null) {
      throw new IOException("server already started");
    }

    this.gatewayAddress = InetAddress.getByName(gatewayAddress);
    this.listenAddress = InetAddress.getByName(listenAddress);

    socket = new DatagramSocket(null);
    socket.setBroadcast(true);
    socket.bind(new InetSocketAddress(InetAddress.getByName(listenAddress), PORT));

    serverThread = new Thread(this);
    serverThread.start();
  }

  public synchronized void stop() {
    if (socket == null || serverThread == null) {
      return;
    }

    socket.close();
    try {
      if (serverThread.isAlive()) {
        serverThread.join();
      }
    }
    catch (InterruptedException e) { }

    serverThread = null;
    socket = null;
  }

  public void run() {
    byte[] recvBuf = new byte[MAX_PACKET_SIZE];
    DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);

    while (true) {
      try {
        socket.receive(packet);
      }
      catch (SocketException e) {
        logger.warn("Socket error");
        break;
      }
      catch (IOException e) {
        logger.warn("io error");
        continue;
      }

      HdlPacket p = HdlPacket.parse(packet.getData(), packet.getLength());

      if (p != null) {
        IHdlDevice d = devices.get(p.getSourceAddress());

        if (d != null) {
          d.processPacket(p);
        }
      }
    }
  }

  public void addDevice(IHdlDevice device) {
    devices.put(device.getAddress(), device);
  }

  public IHdlDevice getDevice(int address) {
    return devices.get(address);
  }

  public void sendPacket(HdlPacket p) throws IOException {
    DatagramSocket s = socket;

    if (s == null) {
      throw new IOException("server not started");
    }
    p.setReplyAddress(listenAddress);
    p.setSourceAddress(0x01fe);

    byte[] bytes = p.getBytes();

    DatagramPacket packet = new DatagramPacket(bytes, bytes.length, gatewayAddress, PORT);
    s.send(packet);
  }

  public ScheduledFuture sendPacketWithRetry(HdlPacket p) throws IOException {
    return sendPacketWithRetry(p, retryCount, retryTime, retryTimeUnit);
  }

  private ScheduledFuture sendPacketWithRetry(HdlPacket p, int retryCount, int retryTime, TimeUnit timeUnit)
  throws IOException {
    sendPacket(p);
    PacketResender r = new PacketResender(p, this, retryCount);
    ScheduledFuture f = scheduler.scheduleAtFixedRate(r, retryTime, retryTime, timeUnit);
    r.setFuture(f);
    return f;
  }
}

class PacketResender implements Runnable
{
  private int retryCount;
  private HdlServer server;
  private HdlPacket packet;
  private ScheduledFuture future;

  private static final Logger logger =
    LoggerFactory.getLogger(PacketResender.class);

  public PacketResender(HdlPacket p, HdlServer s, int retryCount) {
    this.packet = p;
    this.server = s;
    this.retryCount = retryCount;
  }

  public void setFuture(ScheduledFuture future) {
    this.future = future;
  }

  public void run() {
    if (retryCount == 0) {
      if (future != null) {
        future.cancel(true);
        future = null;
      }
      return;
    }
    retryCount--;
    try {
      server.sendPacket(packet);
    }
    catch (IOException e) {
      future.cancel(true);
      future = null;
    }
    logger.debug("Resending, {} retries left", retryCount);
  }
}
