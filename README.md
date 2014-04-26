kandidlib-emitter
=================

A small Java library providing a type safe model/listener pattern through class generation

The problem
---------
Need to implement a model interface in Java/Swing because none of the dafault classes fit? Tired to implement all those methods to register listeners and loops to send the messages to all listeners?

If so, kandidlib-emitter can help you. This small library generates classes performing these tasks. At your preference will be either generated at compile time via an annotation processor or at runtime with a little help from the [asm](http://asm.ow2.org/) or both.


The solution
---------

kandidlib-emitter allows you to take any interface containing only void methods and use it to generate a class where you can register listeners implementing this interface and spread events to them by calling this method. No casting needed.

An example shows the principle:
```java
public class EmitterDemo {

   // The interface all listeners must implement
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

How to use it
----------
As mentioned before kandidlib-emitter may be used in two ways, either as an annotation processor in order to generate the necessary classes at compile time or do dynamic generation at runtime.

In both cases you can use the `makeEmitter(Class i)` method to instantiate an Emitter. 


### Using runtime generation
* add kandidlib-emitter to your classpath
* add asm-4.2 to your classpath
* instantiate an Emitter with Emitter.makeEmitter(Listener.class)
This approach is simpler to set up but you must take care if you plan to use an Emitter in an Applet since under normal circumstances this environment prohibits code generation at runtime. Another drawback might be the missing 

### Using as an annotation processor
* build the annotation processor jar (see below)
* register the jar as an annotation processor
* mark your interfaces with `@de.kandid.model.Emitter.Listener`
* instantiate them with either Emitter.makeEmitter(Listener.class)
-or-
* use the generated class directly

Following this approach requires you to build the kandidlib-emitter.jar and register it as an annotation processor. Usually it should be enough to simply add the jar to the classpath of your compile step.

Doing so enables you to mark interfaces with the annotation  and the processor generates the Emitter class. The resulting class resides in the same package as the interface and has the same name appended with $Emitter. If the implemented interface is a nested one, the dots are replaced with '§'. Taking the previous example the generated emitter would be named EmitterDemo$Listener$Emitter.


Building the kandidlib-emitter.jar
---------------------------
This library uses the [Gradle](http://gradle.org)-1.10 build system. Since I refuse to add the wrapper to the source code, you need to have it installed. Then
```sh
gradle jar
```
produces the jar containing a manifest which identifies it as an annotation processor. This jar is also the one that must be included in distributions of your application since it also contains the runtime classes.

Improving kandidlib-emitter
-------------
Of course any IDE may be used to work on kandidlib-emitter but support for gradle makes it more convenient.