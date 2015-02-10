/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hdl.internal;

import java.util.*;
import java.io.IOException;
import java.lang.NumberFormatException;

import org.openhab.binding.hdl.bus.*;
import org.openhab.binding.hdl.HdlBindingProvider;

import org.apache.commons.lang.StringUtils;
import org.openhab.core.binding.AbstractBinding;
import org.openhab.core.types.Command;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.types.State;
import org.openhab.core.items.Item;
import org.openhab.core.binding.BindingProvider;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Implement this class if you are going create an actively polling service
 * like querying a Website/Device.
 *
 * @author Andrei Sidorov
 * @since 1.0
 */
public class HdlBinding extends AbstractBinding<HdlBindingProvider>
  implements ManagedService,
    IHdlDimmerObserver
{
  private HdlServer server = new HdlServer();
  private String gateway;
  private String listen;

  private static final Logger logger =
    LoggerFactory.getLogger(HdlBinding.class);

  public void activate()
  {
    loadAllBindings();
  }

  public void deactivate()
  {
    server.stop();
  }

  /**
   * @{inheritDoc}
   */
  @Override
  protected void internalReceiveCommand(String itemName, Command command) {
    HdlBindingConfig config = tryGetConfigFor(itemName);

    if (config == null)
      return;

    HdlDimmer dimmer = (HdlDimmer)(server.getDevice(config.address));
    if (dimmer != null) {
      try {
        if (command instanceof OnOffType) {
          dimmer.dimChannel(config.channel, command == OnOffType.ON ? 100 : 0);
        }
        else if (command instanceof PercentType) {
          dimmer.dimChannel(config.channel, ((PercentType)command).intValue());
        }
      }
      catch (IOException e) {
        logger.error("Failed to dim channel: {}", e.getMessage());
      }
    }
  }

  /**
   * @{inheritDoc}
   */
  @Override
  protected void internalReceiveUpdate(String itemName, State newState)
  {
    logger.info("internalReceiveUpdate " + itemName);
  }

  /**
   * @{inheritDoc}
   */
  @Override
  public void updated(Dictionary<String, ?> config) throws ConfigurationException
  {
    if (config != null) {
      gateway = (String)config.get("gateway");
    }

    if (gateway == null) {
      gateway = "255.255.255.255";
    }

    logger.info("gateway is {}", gateway);

    try {
      server.start("0.0.0.0", gateway);
    }
    catch (IOException e) {
      logger.error("Can't start HDL server: {}", e.getMessage());
}
  }

  public void onDimmerStateChanged(HdlDimmer dimmer, int channel, int state)
  {
    logger.debug("Dimmer {}, channel {} state changed to {}", Integer.toHexString(dimmer.getAddress()), channel, state);

    String item = tryGetItemFor(dimmer.getAddress(), channel);

    if (item == null) {
      return;
    }

    State c;
    switch (state) {
      case 0:
        c = OnOffType.OFF;
        break;
      case 100:
        c = OnOffType.ON;
        break;
      default:
        c = new PercentType(state);
    }
    this.eventPublisher.postUpdate(item, c);
  }

  private void loadAllBindings()
  {
    for (HdlBindingProvider provider : this.providers) {
      allBindingsChanged(provider);
    }
  }


  private HdlBindingConfig tryGetConfigFor(String itemName)
  {
    for (HdlBindingProvider provider : this.providers) {
      HdlBindingConfig config = provider.getConfigFor(itemName);
      if (config != null) {
        return config;
      }
    }
    return null;
  }

  private String tryGetItemFor(int address, int channel)
  {
    for (HdlBindingProvider provider : this.providers) {
      String item = provider.getItemFor(address, channel);
      if (item != null) {
        return item;
      }
    }
    return null;
  }

  public void allBindingsChanged(BindingProvider provider)
  {
    logger.debug("All bindings changed");

    for (String itemName : provider.getItemNames()) {
      bindingChanged(provider, itemName);
    }
  }

  public void bindingChanged(BindingProvider provider, String itemName)
  {
    boolean added = provider.providesBindingFor(itemName);
    logger.info("Binding {} {}", itemName, (added ? "added" : "removed"));

    if (added && provider instanceof HdlBindingProvider) {
      HdlBindingConfig config = ((HdlBindingProvider)provider).getConfigFor(itemName);
      if (config == null || server == null) {
        return;
      }

      if (server.getDevice(config.address) == null) {
        HdlDimmer d = new HdlDimmer(config.address, server);
        d.addListener(this);
        server.addDevice(d);
        logger.info("Listening for events from {}", Integer.toHexString(config.address));
      }
    }
  }
}
