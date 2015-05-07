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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
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

/**
 * Emitter is a class used to spread method calls to all of its registered listeners.
 * This concept is typically used in model/view patterns which are ubiquitos in the
 * Java Swing library.<p>
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
 * </pre><p>
 * Emitters can be constructed via the {@link #makeEmitter(Class)} method receiving an interface
 * that the returned {@code Emitter} implements. Calling one of those methods sends it to all
 * registered listeners.<p>
 *
 * There are two approaches to use Emitters:<ol>
 *
 * <li>Generate the Emitter code at runtime.<p>
 * The advantage is that you can use Emitters within IDEs without the need to configure an
 * annotation processor or setup the source path to the generated sources. The drawback
 * is, you can't use Emitters in Applets since they normally forbid the generation of classes
 * at runtime</li>
 *
 * <li>Generate the Emitter code at compile time.<p>
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
	 */
	@Target(ElementType.TYPE)
	public static @interface Listener {
	}

	/**
	 * The processor to handle {@link Emitter.Listener} annotations. It generates an
	 * {@link Emitter} in the same package like the annotated listener interface resides.
	 * It will be named {@code <Listener>.Emitter}, with all dots replaced with {@code $}.<p>
	 * <em>Note</em>: Despite its suggestive name this class is <em>not</em> an inner class!<p>
	 * If you place the
	 * kandidlib-emitter jar in your classpath while compiling, it should work out of
	 * the box.
	 */
	@SupportedSourceVersion(SourceVersion.RELEASE_8)
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
					String superClass = "de.kandid.model.Emitter<" + te.getQualifiedName() + ">";
					out.write("public class " + name.substring(name.lastIndexOf('.') + 1) + " extends " + superClass + " implements " + te.getQualifiedName() + " {\n");
					makeMethods(te, out);
					List<? extends TypeMirror> interfaces = te.getInterfaces();
					for (TypeMirror tm : interfaces) {
						TypeElement ite = (TypeElement) processingEnv.getTypeUtils().asElement(tm);
						makeMethods(ite, out);
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

		private void makeMethods(TypeElement te, Writer out) throws IOException {
			for (Element e : te.getEnclosedElements()) {
				if (e.getKind() != ElementKind.METHOD)
					continue;
				ExecutableElement ee = (ExecutableElement) e;
				if (ee.getReturnType().getKind() != TypeKind.VOID) {
					processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Listener methods must be void", ee);
					break;
				}
				out.write("	public void " + ee.getSimpleName());
				writeArgList(out, ee, true);
				out.write(" {\n");
				out.write("		boolean isFiring = _isFiring;\n");
				out.write("		try {\n");
				out.write("			_isFiring = true;\n");
				out.write("			for (int i = 0; i < _end; i += 2)\n");
				out.write("				((" + te.getQualifiedName() + ")_listeners[i])." + ee.getSimpleName());
				writeArgList(out, ee, false);
				out.write(";\n");
				out.write("		} finally {_isFiring = isFiring;}\n");
				out.write("	}\n");
			}
		}

		private static void writeArgList(Writer out, ExecutableElement ee, boolean withTypes) throws IOException {
			boolean needComma = false;
			out.write("(");
			for (VariableElement arg : ee.getParameters()) {
				if (needComma)
					out.write(",");
				if (withTypes)
					out.write(arg.asType().toString() + " ");
				out.write(arg.getSimpleName().toString() + '_');
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
	 * @param <T> the listener class to build an Emitter for
	 * @param interfaze the interface to create an {@code Emitter} for
	 * @return the {@code Emitter}
	 */
	public synchronized static <T> Emitter<T> makeEmitter(Class<?> interfaze) {
		Class<? extends Emitter<?>> clazz = _classes.get(interfaze);
		if (clazz == null) {
			clazz = checkPrecompiled(interfaze);
			if (clazz == null)
				throw new IllegalArgumentException("No emitter class found for: " + interfaze.getName());
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

	private final static HashMap<Class<?>, Class<? extends Emitter<?>>> _classes = new HashMap<Class<?>, Class<? extends Emitter<?>>>();

	protected Object[] _listeners;
	protected int _end;
	protected boolean _isFiring;
}