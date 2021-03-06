/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.socket.api.socket.tcp;

import org.mule.runtime.extension.api.annotation.Extensible;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.dsl.xml.XmlHints;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.display.Placement;

/**
 * Default immutable implementation of the {@code TcpClientSocketProperties} interface.
 *
 * @since 4.0
 */
@Extensible
@XmlHints(allowTopLevelDefinition = true)
public class TcpClientSocketProperties extends AbstractTcpSocketProperties {

  /**
   * Number of milliseconds to wait until an outbound connection to a remote server is successfully created. Defaults to 30
   * seconds.
   */
  @Parameter
  @Optional(defaultValue = "30000")
  @Placement(tab = TIMEOUT_CONFIGURATION)
  private int connectionTimeout = 30000;

  /**
   * Number of milliseconds to wait until an outbound connection to a remote server is successfully created. Defaults to 30
   * seconds.
   */
  public int getConnectionTimeout() {
    return connectionTimeout;
  }
}
