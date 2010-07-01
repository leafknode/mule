/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.routing;

import org.mule.DefaultMuleEvent;
import org.mule.api.MuleEvent;
import org.mule.api.routing.RoutingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <code>LoggingCatchAllStrategy</code> is a simple strategy that only logs any
 * events not caught by the router associated with this strategy. This should
 * <b>not</b> be used in production unless it is acceptable for events to be lost.
 */

public class LoggingCatchAllStrategy extends AbstractCatchAllStrategy
{
    private static final Log logger = LogFactory.getLog(DefaultMuleEvent.class);

    public MuleEvent doCatchMessage(MuleEvent event) throws RoutingException
    {
        logger.warn("Message: " + event.getMessage() + " was not dispatched."
                    + ". No routing path was defined for it.");
        return event;
    }
}
