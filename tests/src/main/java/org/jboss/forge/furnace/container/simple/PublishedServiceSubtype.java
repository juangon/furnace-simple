/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.forge.furnace.container.simple;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class PublishedServiceSubtype extends PublishedService
{
   @Override
   public String getMessage()
   {
      return "I am PublishedServiceSubtype.";
   }

   @Override
   public ClassLoader getClassLoader()
   {
      return getClass().getClassLoader();
   }
}
