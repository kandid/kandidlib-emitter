/*
 * (C) Copyright 2009-2014, by Dominikus Diesch.
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
 * Objects of this class act as an multiplier for messages to send.
 * @author dominik
 */
public class Emitter<T> {

   /**
    * Use this annotation on an interface to generate code for an {@link Emitter}. In order to
    * to mak eithos work you have to install the necessaray compiler extension.
    * @version $Rev$
    */
   @Target(ElementType.TYPE)
   public static @interface Listener {
   }

   /**
    * The processor to handle {@link Emitter.Listener} annotations.
    * @version $Rev$
    */
   @SupportedSourceVersion(SourceVersion.RELEASE_6)
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
            System.out.println(te);
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
                     break;
                  ExecutableElement ee = (ExecutableElement) e;
                  if (ee.getReturnType().getKind() != TypeKind.VOID) {
                     processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Listener methods must be void", ee);
                     break;
                  }
                  out.write("   public void " + ee.getSimpleName());
                  writeArgList(out, ee, true);
                  out.write(" {\n");
                  out.write("      for (int i = 0; i < _end; i += 2)\n");
                  out.write("         ((" + te.getQualifiedName() + ")_listeners[i])." + ee.getSimpleName());
                  writeArgList(out, ee, false);
                  out.write(";\n   }\n");
               }
               out.write("}");
               out.close();
               System.out.println(out.toString());
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

   @SuppressWarnings("unchecked")
   public T fire() {
      return (T) this;
   }

   public final boolean isFiring() {
      return _isFiring;
   }

   public synchronized void add(T listener) {
   	add(null, listener);
   }

   public synchronized void add(Object key, T listener) {
   	if (key == null)
   		key = listener;
      if (_end + 2 >= _listeners.length) {
         Object[] newListeners = new Object[_listeners.length + _listeners.length / 2];
         System.arraycopy(_listeners, 0, newListeners, 0, _listeners.length);
         _listeners = newListeners;
      }
  		_listeners[_end] = listener;
      _listeners[_end + 1] = key;
      _end += 2;
   }

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

	public synchronized static <T> Emitter<T> makeEmitter(Class<?> interfaze) {
      Class<? extends Emitter<?>> clazz = _classes.get(interfaze);
      if (clazz == null) {
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
	private static <T> Emitter<T> newInstance(Class<?> clazz) throws Exception {
		return (Emitter<T>) clazz.newInstance();
	}

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