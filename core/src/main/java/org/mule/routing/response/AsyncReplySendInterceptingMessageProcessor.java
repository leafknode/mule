/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.routing.response;

import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.Pattern;
import org.mule.api.PatternAware;
import org.mule.api.config.MuleProperties;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.transport.ReplyToHandler;
import org.mule.processor.AbstractInterceptingMessageProcessor;
import org.mule.transport.AbstractConnector;

import org.apache.commons.lang.BooleanUtils;

/**
 * Need to be configured at the beginning of the chain
 */
public class AsyncReplySendInterceptingMessageProcessor extends AbstractInterceptingMessageProcessor
    implements PatternAware
{
    protected Pattern pattern;

    public MuleEvent process(MuleEvent event) throws MuleException
    {
        Object replyTo = event.getMessage().getReplyTo();
        ReplyToHandler replyToHandler = getReplyToHandler(event.getMessage(),
            (InboundEndpoint) event.getEndpoint());
        // Do not propagate REPLY_TO beyond the inbound endpoint
        event.getMessage().setReplyTo(null);

        MuleEvent resultEvent = processNext(event);

        // Allow components to stop processing of the ReplyTo property (e.g. CXF)
        if (resultEvent != null
            && !BooleanUtils.toBoolean((String) resultEvent.getProperty(MuleProperties.MULE_REPLY_TO_STOP_PROPERTY)))
        {
            processReplyTo(event, resultEvent, replyToHandler, replyTo);
        }

        return resultEvent;
    }

    protected ReplyToHandler getReplyToHandler(MuleMessage message, InboundEndpoint endpoint)
    {
        Object replyTo = message.getReplyTo();
        ReplyToHandler replyToHandler = null;
        if (replyTo != null)
        {
            replyToHandler = ((AbstractConnector) endpoint.getConnector()).getReplyToHandler();
            // Use the response transformer for the event if one is set
            if (endpoint.getResponseTransformers() != null)
            {
                replyToHandler.setTransformers(endpoint.getResponseTransformers());
            }
        }
        return replyToHandler;
    }

    protected void processReplyTo(MuleEvent event,
                                  MuleEvent result,
                                  ReplyToHandler replyToHandler,
                                  Object replyTo) throws MuleException
    {
        if (result != null && replyToHandler != null)
        {
            String requestor = (String) result.getProperty(MuleProperties.MULE_REPLY_TO_REQUESTOR_PROPERTY);
            if ((requestor != null && !requestor.equals(pattern.getName())) || requestor == null)
            {
                replyToHandler.processReplyTo(event, result.getMessage(), replyTo);
            }
        }
    }

    public void setPattern(Pattern pattern)
    {
        this.pattern = pattern;
    }

}
