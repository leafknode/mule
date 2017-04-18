/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.junit4;

import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.core.api.message.ExceptionPayload;
import org.mule.runtime.core.internal.message.InternalMessage;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * Provides utility methods to work with the legacy message API for testing purposes only
 *
 * @deprecated tests should not access properties and attachments using the old API.
 */
@Deprecated
public class LegacyMessageTestUtils {

  private static final String LEGACY_MESSAGE_API_ERROR = "Error trying to access legacy message API";

  private LegacyMessageTestUtils() {}

  /**
   * Gets an outbound property from the message.
   *
   * @param message message used to obtain the data from. Must be a {@link InternalMessage}
   * @param name the name or key of the property. This must be non-null.
   * @return the property value or null if the property does not exist in the specified scope
   * @throws {@link IllegalStateException} if there is any problem accessing the legacy message API using reflection
   */
  public static <T extends Serializable> T getOutboundProperty(Message message, String name) {
    try {
      Method method = message.getClass().getMethod("getOutboundProperty", String.class);
      method.setAccessible(true);
      return (T) method.invoke(message, name);
    } catch (Exception e) {
      throw new IllegalStateException(LEGACY_MESSAGE_API_ERROR, e);
    }
  }

  /**
   * Gets an outbound property data type from the message.
   *
   * @param message message used to obtain the data from. Must be a {@link InternalMessage}
   * @param name the name or key of the property. This must be non-null.
   * @return the property data type or null if the property does not exist in the specified scope
   * @throws {@link IllegalStateException} if there is any problem accessing the legacy message API using reflection
   */
  public static DataType getOutboundPropertyDataType(Message message, String name) {
    try {
      Method method = message.getClass().getMethod("getOutboundPropertyDataType", String.class);
      method.setAccessible(true);
      return (DataType) method.invoke(message, name);
    } catch (Exception e) {
      throw new IllegalStateException(LEGACY_MESSAGE_API_ERROR, e);
    }
  }

  /**
   * Gets an inbound property from the message.
   *
   * @param message message used to obtain the data from. Must be a {@link InternalMessage}
   * @param name the name or key of the property. This must be non-null.
   * @return the property value or null if the property does not exist in the specified scope
   * @throws {@link IllegalStateException} if there is any problem accessing the legacy message API using reflection
   */
  public static <T extends Serializable> T getInboundProperty(Message message, String name) {
    try {
      Method method = message.getClass().getMethod("getInboundProperty", String.class);
      method.setAccessible(true);
      return (T) method.invoke(message, name);
    } catch (Exception e) {
      throw new IllegalStateException(LEGACY_MESSAGE_API_ERROR, e);
    }
  }

  /**
   * If an error occurred during the processing of this message this will return a ErrorPayload that contains the root exception
   * and Mule error code, plus any other related info
   *
   * @param message message used to obtain the data from. Must be a {@link InternalMessage}
   * @return The exception payload (if any) attached to this message
   * @throws {@link IllegalStateException} if there is any problem accessing the legacy message API using reflection
   */
  public static ExceptionPayload getExceptionPayload(Message message) {
    try {
      Method method = message.getClass().getMethod("getExceptionPayload");
      method.setAccessible(true);
      return (ExceptionPayload) method.invoke(message);
    } catch (Exception e) {
      throw new IllegalStateException(LEGACY_MESSAGE_API_ERROR, e);
    }
  }
}
