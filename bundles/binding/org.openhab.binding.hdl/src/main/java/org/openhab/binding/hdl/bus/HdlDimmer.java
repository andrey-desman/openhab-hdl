/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hdl.bus;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class HdlDimmer extends GenericHdlDevice
{
  public static final int MAX_CHANNELS = 16;
  public static final int CMD_DIMMER_SET_STATE = 0x0031;
  public static final int CMD_DIMMER_SET_STATE_RESPONSE = 0x0032;
  public static final int CMD_DIMMER_STATE = 0xefff;

  ScheduledFuture [] futures = new ScheduledFuture[MAX_CHANNELS];

  public HdlDimmer(int address, HdlServer server)
  {
    this.address = address;
    this.server = server;
  }

  private void setFuture(int channel, ScheduledFuture future) {
    synchronized (futures) {
      if (futures[channel] != null) {
        futures[channel].cancel(false);
      }
      futures[channel] = future;
    }
  }

  public void processPacket(HdlPacket p)
  {
    switch (p.getCommand()) {
      case CMD_DIMMER_SET_STATE_RESPONSE:
        byte[] data = p.getData();

        if (data.length < 3 || data[1] != (byte)0xF8) {
          return;
        }

        if (data[0] > 0 && data[0] < MAX_CHANNELS) {
          setFuture(data[0], null);
        }

        synchronized (observers) {
          if (observers != null) {
            ListIterator<Object> i = observers.listIterator(0);
            while (i.hasNext()) {
              IHdlDimmerObserver o = (IHdlDimmerObserver)i.next();
              o.onDimmerStateChanged(this, data[0], data[2]);
            }
          }
        }
        break;
      case CMD_DIMMER_STATE:
        break;
      default:
        break;
    }
  }

  public void dimChannel(int channel, int value) throws IOException
  {
    if (channel >= MAX_CHANNELS)
      return;

    HdlPacket p = new HdlPacket();

    p.setTargetAddress(address);
    p.setCommand(CMD_DIMMER_SET_STATE);
    p.setData(new byte[] {(byte)channel, (byte)value, 0, 0});

    ScheduledFuture f = server.sendPacketWithRetry(p);

    setFuture(channel, f);
  }
}
