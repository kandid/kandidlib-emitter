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

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_4;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Emitter is a class used to spread method calls to all of its registered listeners.
 * This concept is typically used in model/view patterns which are ubiquitos in the
 * Java Swing library.<p/>
 * Here is a short example: <pre>

public class EmitterDemo {

   // The interface all listeners must implement
   public interface Listener {
      public void bescheid(String text);
   }

   public static void main(String[] args) {
      // Create the Emitter
      Emitter&lt;Listener&gt; emitter = Emitter.makeEmitter(Listener.class);

      // Add two listeners as dependents to the emitter
      emitter.add(new Listener() {
         &#64;Override
         public void bescheid(String text) {
            System.out.println("Hello from 1 with " + text);
         }
      });
      emitter.add(new Listener() {
         &#64;Override
         public void bescheid(String text) {
            System.out.println("Hello from 2 with " + text);
         }
      });

      // Calls bescheid() for both listeners
      emitter.fire().bescheid(" greetings from main");
   }
}
 * </pre><p/>
 * Emitters can be constructed via the {@link #makeEmitter(Class)} method receiving an interface
 * that the returned {@code Emitter} implements. Calling one of those methods sends it to all
 * registered listeners.<p/>
 *
 * There are two approaches to use Emitters:<ol>
 *
 * <li>Generate the Emitter code at runtime.<p/>
 * The advantage is that you can use Emitters within IDEs without the need to configure an
 * annotation processor or setup the source path to the generated sources. The drawback
 * is, you can't use Emitters in Applets since they normally forbid the generation of classes
 * at runtime</li>
 *
 * <li>Generate the Emitter code at compile time.<p/>
 * For this to work you have to register the annotation processor {@link JavacPlugin} and annotate
 * your listener interfaces with {@link Emitter.Listener}. Setting
 * up this correctly rewards you with a compile time check for the listener interfaces to have
 * only void methods.</li>
 * </ol>
 *
 * Either way, {@link #makeEmitter(Class)} can be used in both cases, since it first checks for
 * a precompiled class and then - if not found - generates one.
 *
 * @author dominik
 */
public class Emitter<T> {

   /**
    * Use this annotation on an interface to generate code for an {@link Emitter}. In order to
    * make this work you have to install the necessaray compiler plugin.
    * @version $Rev: 1659 $
    */
   @Target(ElementType.TYPE)
   public static @interface Listener {
   }

   /**
    * The processor to handle {@link Emitter.Listener} annotations. It generates an
    * {@link Emitter} in the same package like the annotated listener interface resides.
    * It will be named {@code <Listener>.Emitter}, with all dots replaced with {@code $}.<p/>
    * <em>Note</em>: Despite its suggestive name this class is <em>not</em> an inner class!<p/>
    * If you place the
    * kandidlib-emitter jar in your classpath while compiling, it should work out of
    * the box.
    * @version $Rev: 1659 $
    */
   @SupportedSourceVersion(SourceVersion.RELEASE_7)
   @SupportedAnnotationTypes("de.kandid.model.Emitter.Listener")
   public static class JavacPlugin extends AbstractProcessor {

      @SuppressWarnings("unchecked")
      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
         for (TypeElement te : (Set<TypeElement>) roundEnv.getElementsAnnotatedWith(Emitter.Listener.class)) {
            if (te.getKind() != ElementKind.INTERFACE) {
               processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Only interfaces can be used as listeners", te);
               break;
            }
            try {
               String name = makeEmitterName(te);
               String packageName = name.substring(0, name.lastIndexOf('.'));
               Writer out = new StringWriter();
               out.write("package " + packageName + ";\n");
               List<? extends TypeMirror> interfaces = te.getInterfaces();
               String superClass = interfaces.size() == 0 ? "de.kandid.model.Emitter" : makeEmitterName((TypeElement) processingEnv.getTypeUtils().asElement(interfaces.get(0)));
               out.write("public class " + name.substring(name.lastIndexOf('.') + 1) + " extends " + superClass + " implements " + te.getQualifiedName() + " {\n");
               for (Element e : te.getEnclosedElements()) {
                  if (e.getKind() != ElementKind.METHOD)
                     continue;
                  ExecutableElement ee = (ExecutableElement) e;
                  if (ee.getReturnType().getKind() != TypeKind.VOID) {
                     processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Listener methods must be void", ee);
                     break;
                  }
                  out.write("   public void " + ee.getSimpleName());
                  writeArgList(out, ee, true);
                  out.write(" {\n");
                  out.write("      boolean isFiring = _isFiring;\n");
                  out.write("      _isFiring = true;\n");
                  out.write("      for (int i = 0; i < _end; i += 2)\n");
                  out.write("         ((" + te.getQualifiedName() + ")_listeners[i])." + ee.getSimpleName());
                  writeArgList(out, ee, false);
                  out.write(";\n");
                  out.write("      _isFiring = isFiring;");
                  out.write(" }\n");
               }
               out.write("}");
               out.close();
               Writer classWriter = processingEnv.getFiler().createSourceFile(name, te).openWriter();
               classWriter.write(out.toString());
               classWriter.close();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
         return false;
      }

      private static void writeArgList(Writer out, ExecutableElement ee, boolean withTypes) throws IOException {
         boolean needComma = false;
         out.write("(");
         for (VariableElement arg : ee.getParameters()) {
            if (needComma)
               out.write(",");
            if (withTypes)
               out.write(arg.asType().toString() + " ");
            out.write(arg.getSimpleName().toString());
            needComma = true;
         }
         out.write(")");
      }

      public static String makeEmitterName(TypeElement te) {
         String className = te.getSimpleName().toString();
         Element pakkage = te.getEnclosingElement();
         while (pakkage.getKind() != ElementKind.PACKAGE) {
            className = pakkage.getSimpleName().toString() + '$' + className;
            pakkage = pakkage.getEnclosingElement();
         }
         return ((PackageElement) pakkage).getQualifiedName() + "." + className + "$Emitter";
      }

   }

   private static class Loader extends ClassLoader {
      Loader() {
         super(Emitter.class.getClassLoader());
      }

      @SuppressWarnings("unchecked")
		public Class<? extends Emitter<?>> loadClass(ClassWriter cw, String name) {
         byte[] bytes = cw.toByteArray();
         return (Class<? extends Emitter<?>>) defineClass(name, bytes, 0, bytes.length);
      }
   }

   protected Emitter() {
      _listeners = new Object[8];
   }

   /**
    * Although this method is only a convenience method that casts this {@code Emitter} to the
    * implementing interface, it expresses the idea behind Emitters: firing events to all listeners.
    * @return the Emitter casted to the implementing interface
    */
   @SuppressWarnings("unchecked")
   public T fire() {
      return (T) this;
   }

   /**
    * Returns whether this {@code Emitter} is currently firing. This may be important to
    * prevent unwanted recursion.
    * @return {@code true} if this {@code Emitter} is already firing; {@code false} otherwise
    */
   public final boolean isFiring() {
      return _isFiring;
   }

   /**
    * Adds a listener to this {@code Emitter} with the listener itself as the key. It is
    * implemented by calling {@link #add(Object, Object)}.
    * @param listener the listener of type {@code T} to add
    */
   public synchronized void add(T listener) {
   	add(listener, listener);
   }

   /**
    * Registers a listener to this {@code Emitter} with the explicitly specified {@code key}.
    * The listener is always added at the end of the list, so it gets called last when firing
    * an event. Up to now the behaviour is undefined when registering several listeners with
    * the same key.
    * @param key  the identifying key of the listener
    * @param listener  the listener to add
    */
   public synchronized void add(Object key, T listener) {
      if (_end + 2 >= _listeners.length) {
         Object[] newListeners = new Object[_listeners.length + _listeners.length / 2];
         System.arraycopy(_listeners, 0, newListeners, 0, _listeners.length);
         _listeners = newListeners;
      }
  		_listeners[_end] = listener;
      _listeners[_end + 1] = key;
      _end += 2;
   }

   /**
    * Removes a listener from the list. The listener will be identified by the key that have
    * been passed while adding.
    * @param key the key of the listener to remove
    */
   public synchronized void remove(Object key) {
      for (int i = 0; i < _end; i += 2) {
         if (_listeners[i + 1] == key) {
            _end -= 2;
            for (; i < _end; ++i)
               _listeners[i] = _listeners[i + 2];
            break;
         }
      }
   }

   /**
    * Creates an emitter object for the given interface. All methods of the interface need to be
    * of type {@code void}.
    * @param interfaze the interface to create an {@code Emitter} for
    * @return the {@code Emitter}
    */
	public synchronized static <T> Emitter<T> makeEmitter(Class<?> interfaze) {
      Class<? extends Emitter<?>> clazz = _classes.get(interfaze);
      if (clazz == null) {
      	clazz = checkPrecompiled(interfaze);
      	if (clazz == null)
      		clazz = makeClass(interfaze);
         _classes.put(interfaze, clazz);
      }
      try {
         return newInstance(clazz);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

	@SuppressWarnings("unchecked")
	private static <T> Class<Emitter<T>> checkPrecompiled(Class<T> interfaze) {
		try {
			String name = interfaze.getName() + "$Emitter";
			Class<Emitter<T>> clazz = (Class<Emitter<T>>) interfaze.getClassLoader().loadClass(name);
			return clazz;
		} catch (Exception unused) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> Emitter<T> newInstance(Class<?> clazz) throws Exception {
		return (Emitter<T>) clazz.newInstance();
	}

	/**
	 * Assembles a class that implements the given interface by generating the byte code.
	 * @param interfaze the interface to implement
	 * @return the class
	 */
   private static Class<? extends Emitter<?>> makeClass(Class<?> interfaze) {
      String nameClass = _nameEmitter + '$' + interfaze.getName().replace('.', '$');
      String nameInterface = Type.getInternalName(interfaze);
      // ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
      ClassWriter cw = new ClassWriter(0);
      cw.visit(V1_4, ACC_PUBLIC + ACC_SUPER, nameClass, null, _nameEmitter, new String[]{name(interfaze)});

      // Generate default construcotor
      MethodVisitor cv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      cv.visitVarInsn(ALOAD, 0);
      cv.visitMethodInsn(INVOKESPECIAL, _nameEmitter, "<init>", "()V");
      cv.visitInsn(RETURN);
      cv.visitMaxs(1, 1);

      // Generate methods
      Method[] methods = interfaze.getMethods();
      for (int i = 0; i < methods.length; ++i) {
         final Method m = methods[i];
         if (m.getReturnType() != void.class)
            throw new IllegalArgumentException("Method " + m.toGenericString() + " must not return a value");
         final String descMethod = Type.getMethodDescriptor(m);
         final MethodVisitor mw = cw.visitMethod(ACC_PUBLIC + ACC_SYNCHRONIZED, m.getName(), descMethod, null, null);
         final Type[] argTypes = Type.getArgumentTypes(m);

         // for (int i = 0; i < _end; i += 2)
         //    ((Listener) _listeners[i]).send(...);

         int localStart = 1; // give one for "this"
         for (Type at : argTypes)
         	localStart += at.getSize();
         Label entry = new Label();
         Label exit = new Label();
         mw.visitLabel(entry);

         // _isFiring = true;
         mw.visitVarInsn(ALOAD, 0);
         mw.visitInsn(Opcodes.ICONST_1);
         mw.visitFieldInsn(Opcodes.PUTFIELD, nameClass, "_isFiring", Type.BOOLEAN_TYPE.getDescriptor());

         // setup local variables: i, _listeners, _end
         mw.visitLocalVariable("i", Type.INT_TYPE.getDescriptor(), null, entry, exit, localStart);
         mw.visitInsn(Opcodes.ICONST_0);
         mw.visitIntInsn(Opcodes.ISTORE, localStart);

         mw.visitLocalVariable("listeners", _descObjectArray, null, entry, exit, localStart + 1);
         mw.visitVarInsn(ALOAD, 0);
         mw.visitFieldInsn(GETFIELD, nameClass, "_listeners", _descObjectArray);
         mw.visitIntInsn(Opcodes.ASTORE, localStart + 1);

         mw.visitLocalVariable("end", Type.INT_TYPE.getDescriptor(), null, entry, exit, localStart + 2);
         mw.visitVarInsn(ALOAD, 0);
         mw.visitFieldInsn(GETFIELD, nameClass, "_end", Type.INT_TYPE.getDescriptor());
         mw.visitIntInsn(Opcodes.ISTORE, localStart + 2);

         final Label condition = new Label();
         mw.visitJumpInsn(GOTO, condition);

         final Label loop = new Label();
         mw.visitLabel(loop);

         //((Listener) _listeners[i]).doSomething()
         mw.visitIntInsn(Opcodes.ALOAD, localStart + 1);
         mw.visitIntInsn(Opcodes.ILOAD, localStart);
         mw.visitInsn(Opcodes.AALOAD);
         mw.visitTypeInsn(CHECKCAST, nameInterface);
         int offs = 1; // give one for "this"
         for (Type at : argTypes) {
            mw.visitVarInsn(at.getOpcode(ILOAD), offs);
         	offs += at.getSize();
         }
         mw.visitMethodInsn(INVOKEINTERFACE, nameInterface, m.getName(), descMethod);

         // i += 2
         mw.visitIincInsn(localStart, 2);

         // if (i < end) goto loop
         mw.visitLabel(condition);
         mw.visitIntInsn(Opcodes.ILOAD, localStart);
         mw.visitIntInsn(Opcodes.ILOAD, localStart + 2);
         mw.visitJumpInsn(Opcodes.IF_ICMPLT, loop);

         // _isFiring = false;
         mw.visitVarInsn(ALOAD, 0);
         mw.visitInsn(Opcodes.ICONST_0);
         mw.visitFieldInsn(Opcodes.PUTFIELD, nameClass, "_isFiring", Type.BOOLEAN_TYPE.getDescriptor());

         mw.visitLabel(exit);
         mw.visitInsn(RETURN);
         mw.visitMaxs(localStart + 2, localStart + 3);
         mw.visitEnd();
      }
      cw.visitEnd();
      return _loader.loadClass(cw, nameClass.replace('/', '.'));
   }

   private static String name(Class<?> clazz) {
      return Type.getInternalName(clazz);
   }

   private static String desc(Class<?> clazz) {
      return Type.getDescriptor(clazz);
   }

   private final static String _nameEmitter = name(Emitter.class);
   private final static String _descObjectArray = desc(Object[].class);

   private final static Loader _loader = new Loader();
   private final static HashMap<Class<?>, Class<? extends Emitter<?>>> _classes = new HashMap<Class<?>, Class<? extends Emitter<?>>>();

   protected Object[] _listeners;
   protected int _end;
   protected boolean _isFiring;
}