kandidlib-emitter
=================

A small Java library providing a type safe model/listener pattern through class generation

The problem
---------
Need to implement a model interface in Java/Swing because none of the default classes fit? Tired to implement all those methods to register listeners and loops to send the messages to all listeners?

If so, kandidlib-emitter can help you. This small library generates classes performing these tasks. The necessary classes will be generated at compile time via an annotation processor. 

Earlier versions of this library had the ability to dynamically generate the needed classes at runtime. Support for this feature has been abandoned since 0.7.0 for several reasons (not allowed for applets, not usable in Android, doubling of parts of the code base). 


The solution
---------

kandidlib-emitter allows you to take any interface containing only void methods and use it to generate a class where you can register listeners implementing this interface and spread events to them by calling this method. No casting needed.

An example shows the principle:
```java
public class EmitterDemo {

   // The interface all listeners must implement
   @Emitter.Listener
   public interface Listener {
      public void bescheid(String text);
   }

   public static void main(String[] args) {
      // Create the Emitter
      Emitter<Listener> emitter = Emitter.makeEmitter(Listener.class);

      // Add a listener as dependent to the emitter
      emitter.add(new Listener() {
         @Override
         public void bescheid(String text) {
            System.out.println("Hello from the listener with " + text);
         }
      });

      // Calls bescheid() for all listeners
      emitter.fire().bescheid("greetings from main");
   }
}
```


Building the jar
---------------

This library uses the [Gradle](http://gradle.org) build system. Since I refuse to add the wrapper to the source code, you need to have it installed. Then
```sh
gradle jar
```
produces the jar containing a manifest which identifies it as an annotation processor and places it in the `libs` subdirectory. This jar is also the one that _must_ be included in distributions of your application since it also contains the runtime classes.

Using the annotation processor
-----------

In most cases it suffices to add the jar to the classpath. For some IDEs it may be necessary to explicitly activate annotation processing.

Once this is done, marking an interface with `@de.kandid.model.Emitter.Listener` makes the emitter available through `Emitter.makeEmitter(Listener.class)` or the generated class may be used directly. The resulting class resides in the same package as the interface and has the same name appended by $Emitter. If the implemented interface is a nested one, the dots are replaced with '$'. Taking the previous example the generated emitter would be named EmitterDemo$Listener$Emitter.

More example uses of this library can be found in the [JUnit tests](test/de/kandid/model/EmitterTest.java).


Improving kandidlib-emitter
-------------
Of course any IDE may be used to work on kandidlib-emitter but support for gradle makes it more convenient.