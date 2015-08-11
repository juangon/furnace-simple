/*
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.forge.furnace.container.simple.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.forge.furnace.Furnace;
import org.jboss.forge.furnace.addons.Addon;
import org.jboss.forge.furnace.container.simple.EventListener;
import org.jboss.forge.furnace.container.simple.Service;
import org.jboss.forge.furnace.container.simple.SingletonService;
import org.jboss.forge.furnace.exception.ContainerException;
import org.jboss.forge.furnace.spi.ExportedInstance;
import org.jboss.forge.furnace.spi.ServiceRegistry;
import org.jboss.forge.furnace.util.Assert;
import org.jboss.forge.furnace.util.ClassLoaders;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class SimpleServiceRegistry implements ServiceRegistry, AutoCloseable
{
   private static final Logger log = Logger.getLogger(SimpleServiceRegistry.class.getName());

   private final Furnace furnace;
   private final Addon addon;
   private final Set<Class<?>> serviceTypes;
   private final Set<Class<?>> singletonServiceTypes;

   private final Map<String, ExportedInstance<?>> instanceCache = new WeakHashMap<>();
   private final Map<String, Set<ExportedInstance<?>>> instancesCache = new WeakHashMap<>();

   public SimpleServiceRegistry(Furnace furnace, Addon addon)
   {
      this.furnace = furnace;
      this.addon = addon;
      Set<Class<?>> allServices = new HashSet<>();
      allServices.addAll(locateServices(addon, Service.class));
      // Maintaining legacy behavior
      allServices.addAll(locateServices(addon, EventListener.class));
      this.serviceTypes = allServices;
      this.singletonServiceTypes = locateServices(addon, SingletonService.class);
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T> Set<ExportedInstance<T>> getExportedInstances(String clazz)
   {
      try
      {
         return getExportedInstances((Class<T>) Class.forName(clazz, false, addon.getClassLoader()));
      }
      catch (ClassNotFoundException e)
      {
         return Collections.emptySet();
      }
   }

   @Override
   @SuppressWarnings({ "unchecked", "rawtypes" })
   public <T> Set<ExportedInstance<T>> getExportedInstances(Class<T> clazz)
   {
      Set<ExportedInstance<T>> result = (Set) instancesCache.get(clazz.getName());

      if (result == null || result.isEmpty())
      {
         result = new HashSet<>();

         for (Class<?> type : serviceTypes)
         {
            if (clazz.isAssignableFrom(type))
            {
               result.add(new SimpleExportedInstanceImpl<>(furnace, addon, (Class<T>) type));
            }
         }

         for (Class<?> type : singletonServiceTypes)
         {
            if (clazz.isAssignableFrom(type))
            {
               result.add(new SimpleSingletonExportedInstanceImpl(furnace, addon, type));
            }
         }
         instancesCache.put(clazz.getName(), (Set) result);
      }
      return result;
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T> ExportedInstance<T> getExportedInstance(String clazz)
   {
      try
      {
         Class<?> type = Class.forName(clazz, false, addon.getClassLoader());
         return getExportedInstance((Class<T>) type);
      }
      catch (ClassNotFoundException e)
      {
         return null;
      }
   }

   @Override
   @SuppressWarnings("unchecked")
   public <T> ExportedInstance<T> getExportedInstance(final Class<T> clazz)
   {
      Assert.notNull(clazz, "Requested Class type may not be null");

      ExportedInstance<T> result = (ExportedInstance<T>) instanceCache.get(clazz.getName());
      if (result == null)
      {
         try
         {
            result = ClassLoaders.executeIn(addon.getClassLoader(), new Callable<ExportedInstance<T>>()
            {
               @Override
               public ExportedInstance<T> call() throws Exception
               {
                  for (Class<?> type : serviceTypes)
                  {
                     if (clazz.isAssignableFrom(type))
                     {
                        return new SimpleExportedInstanceImpl<T>(furnace, addon, (Class<T>) type);
                     }
                  }

                  for (Class<?> type : singletonServiceTypes)
                  {
                     if (clazz.isAssignableFrom(type))
                     {
                        return new SimpleSingletonExportedInstanceImpl<T>(furnace, addon, (Class<T>) type);
                     }
                  }

                  return null;
               }
            });

            if (result != null)
               instanceCache.put(clazz.getName(), result);
         }
         catch (Exception e)
         {
            throw new ContainerException("Could not get service of type [" + clazz + "] from addon ["
                     + addon
                     + "]", e);
         }

      }
      return result;
   }

   @Override
   public Set<Class<?>> getExportedTypes()
   {
      final Set<Class<?>> result = new HashSet<>();
      result.addAll(serviceTypes);
      result.addAll(singletonServiceTypes);
      return Collections.unmodifiableSet(result);
   }

   @Override
   public <T> Set<Class<T>> getExportedTypes(Class<T> type)
   {
      Set<Class<T>> result = new HashSet<>();
      for (Class<?> serviceType : getExportedTypes())
      {
         if (type.isAssignableFrom(serviceType))
         {
            result.add(type);
         }
      }
      return result;
   }

   @Override
   public boolean hasService(Class<?> clazz)
   {
      for (Class<?> service : getExportedTypes())
      {
         if (clazz.isAssignableFrom(service))
         {
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean hasService(String clazz)
   {
      try
      {
         return hasService(Class.forName(clazz, false, addon.getClassLoader()));
      }
      catch (ClassNotFoundException e)
      {
         return false;
      }
   }

   @Override
   public void close()
   {
      this.serviceTypes.clear();
      this.instanceCache.clear();
      this.instancesCache.clear();
      this.singletonServiceTypes.clear();
   }

   @Override
   public String toString()
   {
      return serviceTypes.toString();
   }

   private static Set<Class<?>> locateServices(Addon addon, Class<?> serviceType)
   {
      Set<Class<?>> allServiceTypes = new HashSet<>();
      try
      {
         Enumeration<URL> resources = addon.getClassLoader()
                  .getResources("/META-INF/services/" + serviceType.getName());
         while (resources.hasMoreElements())
         {
            URL resource = resources.nextElement();
            try (InputStream stream = resource.openStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stream)))
            {
               String serviceName;
               while ((serviceName = reader.readLine()) != null)
               {
                  if (ClassLoaders.containsClass(addon.getClassLoader(), serviceName))
                  {
                     Class<?> type = ClassLoaders.loadClass(addon.getClassLoader(), serviceName);
                     if (ClassLoaders.ownsClass(addon.getClassLoader(), type))
                     {
                        allServiceTypes.add(type);
                     }
                  }
                  else
                  {
                     log.log(Level.WARNING,
                              "Service class not enabled due to underlying classloading error. If this is unexpected, "
                                       + "enable DEBUG logging to see the full stack trace: "
                                       + getClassLoadingErrorMessage(addon, serviceName));
                     log.log(Level.FINE,
                              "Service class not enabled due to underlying classloading error.",
                              ClassLoaders.getClassLoadingExceptionFor(addon.getClassLoader(), serviceName));
                  }
               }
            }
         }
      }
      catch (IOException ie)
      {
         log.log(Level.SEVERE, "Error while reading service classes", ie);
      }
      return allServiceTypes;
   }

   private static String getClassLoadingErrorMessage(Addon addon, String serviceType)
   {
      Throwable e = ClassLoaders.getClassLoadingExceptionFor(addon.getClassLoader(), serviceType);
      while (e.getCause() != null && e.getCause() != e)
      {
         e = e.getCause();
      }
      return e.getClass().getName() + ": " + e.getMessage();
   }
}
