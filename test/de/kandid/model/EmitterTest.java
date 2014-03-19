/*
 * (C) Copyright 2009, by Dominikus Diesch.
 *
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation;
 * either version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/*
 * Created on Feb 5, 2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
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

   @Emitter.Listener
   public interface ReturnListener {
      public static class Class implements ReturnListener {
         public int increment() {
            return ++_count;
         }
         public int _count;
      }
      public int increment();
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
      assertFalse(e.isFiring());
   }

   private static Listener.Class addListener(Emitter<Listener> emitter) {
      Listener.Class tl = new Listener.Class();
      emitter.add(tl);
      return tl;
   }
}