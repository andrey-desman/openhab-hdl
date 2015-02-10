/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hdl.bus;

import java.util.LinkedList;

public abstract class GenericHdlDevice implements IHdlDevice
{
  protected int address;
  protected LinkedList<Object> observers = new LinkedList<Object>();
  protected HdlServer server;

  public int getAddress() { return address; }

  public void addListener(Object listener)
  {
    synchronized (observers)
    {
      observers.add(listener);
    }
  }

  public void removeListener(Object listener)
  {
    synchronized (observers)
    {
      observers.remove(listener);
    }
  }
}
