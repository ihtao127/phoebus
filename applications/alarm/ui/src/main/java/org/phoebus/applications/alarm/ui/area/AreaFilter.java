/*******************************************************************************
 * Copyright (c) 2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.applications.alarm.ui.area;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.phoebus.applications.alarm.model.AlarmTreeItem;
import org.phoebus.applications.alarm.model.AlarmTreePath;
import org.phoebus.applications.alarm.model.SeverityLevel;

/** Filter for Alarm Areas. Filters out messages not pertaining to the specified level.
 *  Maintains state information for the areas on the specified level.
 *  @author Evan Smith
 */
public class AreaFilter
{
    private final int level;

    // Associate item name with the item severity level.
    private final ConcurrentHashMap<String, SeverityLevel> itemSeverity = new ConcurrentHashMap<>();

    public AreaFilter(final int level)
    {
        // Set level for levelCheck.
        this.level = level;
    }

    // Check the level of the message by examining the number of elements in its path.
    private boolean levelCheck(final String path)
    {
        final String[] path_elements = AlarmTreePath.splitPath(path);
        return (path_elements.length == level);
    }

    // Filter out messages not pertaining to the set level.
    public String filter(final AlarmTreeItem<?> message)
    {
        if (! levelCheck(message.getPathName()))
            return null;

        final String name = message.getName();
        final SeverityLevel severity = message.getState().getSeverity();
        final SeverityLevel result = itemSeverity.get(name);

        // If the item is not in the map or has an outdated severity, put the item.
        if (null == result || ! severity.equals(result))
            itemSeverity.put(name, severity);

        return name;
    }

    // Return a list of all the keys in the itemSeverity map.
    public List<String> getItems()
    {
        return Collections.list(itemSeverity.keys());
    }

    // Return the severity of the item. Return null if the item is not in the map.
    public SeverityLevel getSeverity(final String item_name)
    {
        return itemSeverity.get(item_name);
    }

    // Remove the item. This is safe to call even if item is not in itemSeverity map.
    public void removeItem(final String item_name)
    {
        itemSeverity.remove(item_name);
    }
}
