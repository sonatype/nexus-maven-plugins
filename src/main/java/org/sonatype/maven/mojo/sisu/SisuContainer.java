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
    static
    {
        // disable nasty finalizer thread which would cause class loader leaks
        try
        {
            Class<?> guiceRuntimeClass =
                SisuContainer.class.getClassLoader().loadClass( "com/google/inject/util/GuiceRuntime.class" );
            Method method = guiceRuntimeClass.getDeclaredMethod( "setExecutorClassName", String.class );
            method.invoke( null, "NONE" );
        }
        catch ( Exception e )
        {
            // mute
        }
    }

    private final Injector injector;

    public SisuContainer( Module... modules )
    {
        List<Module> mods = new ArrayList<Module>( modules.length + 1 );
        mods.add( new SpaceModule( new URLClassSpace( getClass().getClassLoader() ) ) );
        Collections.addAll( mods, modules );
        injector = Guice.createInjector( new WireModule( mods ) );
    }

    public <T> T get( Class<T> type )
    {
        return injector.getInstance( type );
    }
}