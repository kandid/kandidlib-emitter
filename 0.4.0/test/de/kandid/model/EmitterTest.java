/*
 * (C) Copyright 2005-2014, by Dominikus Diesch.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.kandid.model;

import junit.framework.TestCase;

/**
 * @author dominik
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class EmitterTest extends TestCase {

   @Emitter.Listener
   public interface Listener {
      public static class Class implements Listener {
         public void increment() {
            ++_count;
         }
         public int _count;
      }

      public void increment();
   }

   @Emitter.Listener
   public interface InheritedListener extends Listener {
      public static class Class extends Listener.Class implements InheritedListener {
         public void incrementOther() {
            ++_otherCount;
         }
         public int _otherCount;
      }

      public void incrementOther();
   }


   @Emitter.Listener
   public interface EmptyListener {
   }

   @Emitter.Listener
   public interface ArgumentListener {
      public static class Class implements ArgumentListener {
         public void add(Integer a, Character b, int c, long d, double e) {
            _a += a.intValue();
            _b = b.charValue();
            _c += c;
            _d += d;
            _e += e;
         }
         public int _a;
         char _b;
         int _c;
         long _d;
         double _e;
      }

      public void add(Integer a, Character b, int c, long d, double e);
   }

   // @Emitter.Listener // Disable this for now since it gives a compile error (as it should!)
   public interface ReturnListener {
      public static class Class implements ReturnListener {
         public int increment() {
            return ++_count;
         }
         public int _count;
      }
      public int increment();
   }

   public void testAnnotationProcessorUse() {
   	Emitter<Listener> emitter = Emitter.makeEmitter(Listener.class);
   	assertEquals(Listener.class.getName() + "$Emitter", emitter.getClass().getName());
   }

   public void testCreation() {
      Emitter<Listener> emitter = Emitter.makeEmitter(Listener.class);
      Listener.Class listener = addListener(emitter);
      emitter.fire().increment();
      assertEquals(1, listener._count);
   }

   public void testInheritedListener() {
      Emitter<InheritedListener> emitter = Emitter.makeEmitter(InheritedListener.class);
      InheritedListener.Class l = new InheritedListener.Class();
      emitter.add(l);
      emitter.fire().increment();
      emitter.fire().incrementOther();
      assertEquals(1, l._count);
      assertEquals(1, l._otherCount);
   }

   public void testManyListeners() {
      Emitter<Listener> emitter = Emitter.makeEmitter(Listener.class);
      Listener.Class[] listeners = new Listener.Class[50];
      for (int i = 0; i < listeners.length; ++i)
         listeners[i] = addListener(emitter);
      emitter.fire().increment();
      for (Listener.Class lc : listeners)
         assertEquals(1, lc._count);

      for (int i = 0; i < listeners.length; i += 2)
         emitter.remove(listeners[i]);
      emitter.fire().increment();
      for (int i = 0; i < listeners.length; ++i)
         assertEquals(1 + i % 2, listeners[i]._count);
   }

   public void testReturnListeners() {
      try {
         Emitter.makeEmitter(ReturnListener.class);
         fail("Need an exception here");
      } catch (IllegalArgumentException e) {
         //expected
      }
   }

   public void testArguments() {
      Emitter<ArgumentListener> e = Emitter.makeEmitter(ArgumentListener.class);
      ArgumentListener.Class listener = new ArgumentListener.Class();
      e.add(listener);
      e.fire().add(new Integer(3), new Character('a'), 2, 5, 1.5);
      assertEquals(3, listener._a);
      assertEquals('a', listener._b);
      assertEquals(2, listener._c);
      assertEquals(5, listener._d);
      assertEquals(1.5, listener._e, 0);
      e.fire().add(new Integer(4), new Character('b'), 3, 7, 1.25);
      assertEquals(7, listener._a);
      assertEquals('b', listener._b);
      assertEquals(12, listener._d);
      assertEquals(2.75, listener._e, 0);
   }

   public void testFiringFlag() {
      final Emitter<Listener> e = Emitter.makeEmitter(Listener.class);
      assertFalse(e.isFiring());
      e.add(new Listener() {
         @Override
         public void increment() {
            assertTrue(e.isFiring());
         }
      });
      e.fire().increment();
      assertFalse(e.isFiring());
   }

   private static Listener.Class addListener(Emitter<Listener> emitter) {
      Listener.Class tl = new Listener.Class();
      emitter.add(tl);
      return tl;
   }
}