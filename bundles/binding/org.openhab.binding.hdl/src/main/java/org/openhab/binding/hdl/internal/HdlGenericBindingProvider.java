/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hdl.internal;

import org.openhab.binding.hdl.bus.*;
import org.openhab.binding.hdl.HdlBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.binding.BindingChangeListener;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;

import java.lang.NumberFormatException;
import java.util.*;
import java.util.concurrent.*;


/**
 * This class is responsible for parsing the binding configuration.
 *
 * @author Andrei Sidorov
 * @since 1.0
 */
public class HdlGenericBindingProvider extends AbstractGenericBindingProvider implements HdlBindingProvider
{

  private ConcurrentMap<Integer, String> items = new ConcurrentHashMap<Integer, String>();

  /**
   * {@inheritDoc}
   */
  public String getBindingType() {
    return "hdl";
  }

  /**
   * @{inheritDoc}
   */
  @Override
  public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException
  {
    if (!(item instanceof SwitchItem || item instanceof DimmerItem)) {
      throw new BindingConfigParseException("item '" + item.getName()
          + "' is of type '" + item.getClass().getSimpleName()
          + "', only Switch- and DimmerItems are allowed - please check your *.items configuration");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processBindingConfiguration(String context, Item item, String bindingConfig) throws BindingConfigParseException
  {
    super.processBindingConfiguration(context, item, bindingConfig);

    HdlBindingConfig config = new HdlBindingConfig();

    String[] parts = bindingConfig.split(":");
    if (parts.length != 2) {
      throw new BindingConfigParseException(bindingConfig);
    }

    try {
      config.channel = Integer.parseInt(parts[1]);
      parts = parts[0].split("\\.");

      if (parts.length != 2) {
        throw new BindingConfigParseException(bindingConfig);
      }

      byte subNet = (byte)Integer.parseInt(parts[0]);
      byte devAddr = (byte)Integer.parseInt(parts[1]);

      config.address = ((subNet << 8) & 0xff00) | (devAddr & 0xff);

      int hash = (config.address << 8) | config.channel;

      addBindingConfig(item, config);
      items.put(new Integer(hash), item.getName());

    }
    catch (NumberFormatException e)
    {
      throw new BindingConfigParseException(bindingConfig);
    }
  }

  public HdlBindingConfig getConfigFor(String itemName)
  {
    return (HdlBindingConfig)bindingConfigs.get(itemName);
  }

  public String getItemFor(int address, int channel)
  {
    return items.get((address << 8) | channel);
  }


}
