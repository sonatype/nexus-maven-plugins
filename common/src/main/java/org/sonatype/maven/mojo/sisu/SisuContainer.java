/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.maven.mojo.sisu;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sonatype.guice.bean.binders.SpaceModule;
import org.sonatype.guice.bean.binders.WireModule;
import org.sonatype.guice.bean.reflect.URLClassSpace;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

/**
 * Helper to "boot" SISU container from within a Mojo.
 */
public class SisuContainer
{
  static {
    // disable nasty finalizer thread which would cause class loader leaks
    try {
      Class<?> guiceRuntimeClass =
          SisuContainer.class.getClassLoader().loadClass("com/google/inject/util/GuiceRuntime.class");
      Method method = guiceRuntimeClass.getDeclaredMethod("setExecutorClassName", String.class);
      method.invoke(null, "NONE");
    }
    catch (Exception e) {
      // mute
    }
  }

  private final Injector injector;

  public SisuContainer(Module... modules) {
    List<Module> mods = new ArrayList<Module>(modules.length + 1);
    mods.add(new SpaceModule(new URLClassSpace(getClass().getClassLoader())));
    Collections.addAll(mods, modules);
    injector = Guice.createInjector(new WireModule(mods));
  }

  public <T> T get(Class<T> type) {
    return injector.getInstance(type);
  }
}