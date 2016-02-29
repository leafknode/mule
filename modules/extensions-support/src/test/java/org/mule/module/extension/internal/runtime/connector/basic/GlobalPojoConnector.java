/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.extension.internal.runtime.connector.basic;

import org.mule.extension.annotation.api.Expression;
import org.mule.extension.annotation.api.Extension;
import org.mule.extension.annotation.api.Operations;
import org.mule.extension.annotation.api.Parameter;
import org.mule.extension.api.introspection.ExpressionSupport;

@Extension(name = "Basic", description = "Basic Test connector")
@Operations(VoidOperations.class)
public class GlobalPojoConnector
{

    /**
     * This should generate a Global element for the Owner, but no child element inside the config
     */
    @Parameter
    @Expression(ExpressionSupport.REQUIRED)
    private Owner requiredPojoExpressionRequired;

}