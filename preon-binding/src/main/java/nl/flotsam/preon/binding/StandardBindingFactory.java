/**
 * Copyright (C) 2009 Wilfred Springer
 *
 * This file is part of Preon.
 *
 * Preon is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2, or (at your option) any later version.
 *
 * Preon is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Preon; see the file COPYING. If not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules, and
 * to copy and distribute the resulting executable under terms of your choice,
 * provided that you also meet, for each linked independent module, the terms
 * and conditions of the license of that module. An independent module is a
 * module which is not derived from or based on this library. If you modify this
 * library, you may extend this exception to your version of the library, but
 * you are not obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */
package nl.flotsam.preon.binding;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import nl.flotsam.limbo.Expression;
import nl.flotsam.limbo.ctx.VariableResolver;
import nl.flotsam.pecia.Documenter;
import nl.flotsam.pecia.ParaContents;
import nl.flotsam.pecia.SimpleContents;
import nl.flotsam.preon.Builder;
import nl.flotsam.preon.Codec;
import nl.flotsam.preon.CodecDescriptor;
import nl.flotsam.preon.DecodingException;
import nl.flotsam.preon.Resolver;
import nl.flotsam.preon.ResolverContext;
import nl.flotsam.preon.buffer.BitBuffer;
import nl.flotsam.preon.buffer.BitBufferException;
import nl.flotsam.preon.reflect.ReflectionUtils;
import nl.flotsam.preon.rendering.CamelCaseRewriter;
import nl.flotsam.preon.rendering.IdentifierRewriter;

/**
 * The {@link BindingFactory} that will simply create a simple {@link Binding}
 * that unconditionally sets and takes the values from the field from which it
 * is constructed.
 * 
 * @author Wilfred Springer
 * 
 */
public class StandardBindingFactory implements BindingFactory {

    /**
     * A unique id for bindings generated by this class.
     */
    private static int id;

    private IdentifierRewriter rewriter = new CamelCaseRewriter();

    public Binding create(AnnotatedElement metadata, Field field,
            Codec<?> codec, ResolverContext context,
            Documenter<ParaContents<?>> containerReference) {
        return new FieldBinding(field, codec, rewriter, containerReference);
    }

    private static class FieldBinding implements Binding {

        private String id = "binding" + StandardBindingFactory.id++;

        private Field field;

        private Codec codec;

        private IdentifierRewriter rewriter;

        private Decorator<Builder> builderDecorator;

        private Decorator<VariableResolver> resolverDecorator;

        private Documenter<ParaContents<?>> containerReference;

        public FieldBinding(Field field, Codec<?> codec,
                IdentifierRewriter rewriter,
                Documenter<ParaContents<?>> containerReference) {
            this.field = field;
            this.codec = codec;
            this.rewriter = rewriter;
            this.containerReference = containerReference;
            Class<?> declaring = field.getDeclaringClass();
            // Class<?>[] members = declaring.getDeclaredClasses();
            // List<Class<?>> types = Arrays.asList(codec.getTypes());
            // boolean contextualize = false;
            // for (Class<?> member : members) {
            // if (!Modifier.isStatic(member.getModifiers()) &&
            // types.contains(member)) {
            // contextualize = true;
            // break;
            // }
            // }
            // if (contextualize) {
            builderDecorator = new ContextualBuilderDecorator(declaring);
            // } else {
            // builderDecorator = new NonDecoratingBuilderDecorator();
            // }
        }

        public void load(Object object, BitBuffer buffer, Resolver resolver,
                Builder builder) throws DecodingException {
            try {
                ReflectionUtils.makeAssessible(field);
                Object value = codec.decode(buffer, resolver, builderDecorator
                        .decorate(builder, object));
                field.set(object, value);
            } catch (IllegalAccessException iae) {
                throw new DecodingException(iae);
            } catch (DecodingException de) {
                // System.err.println("Failed to decode value into "
                // + field.getName() + " of "
                // + field.getDeclaringClass().getSimpleName());
                throw de;
            } catch (BitBufferException bbe) {
                // System.err.println("Failed to decode value into "
                // + field.getName() + " of "
                // + field.getDeclaringClass().getSimpleName());
                throw bbe;
            }
        }

        public <V extends SimpleContents<?>> V describe(V contents) {
            CodecDescriptor codecDescriptor = codec.getCodecDescriptor();
            contents.para().document(codecDescriptor.summary()).end();
            contents.document(codecDescriptor.details("buffer"));
            return contents;
        }

        public Class<?>[] getTypes() {
            return codec.getTypes();
        }

        public Object get(Object context) throws IllegalArgumentException,
                IllegalAccessException {
            return field.get(context);
        }

        public String getName() {
            return field.getName();
        }

        public <T, V extends ParaContents<T>> V writeReference(V contents) {
            System.out.println(containerReference);
            contents.link(getId(), rewriter.rewrite(getName())).text(" of ").document(containerReference);
            return contents;
        }

        public Expression<Integer, Resolver> getSize() {
            return codec.getSize();
        }

        public String getId() {
            return id;
        }

        public Class<?> getType() {
            return codec.getType();
        }

    }

    private interface Decorator<T> {

        T decorate(T object, Object context);

    }

    private static class NonDecoratingBuilderDecorator implements
            Decorator<Builder> {

        public Builder decorate(Builder builder, Object context) {
            return builder;
        }

    }

    private static class ContextualBuilderDecorator implements
            Decorator<Builder> {

        private Class enclosing;

        private List<Class> members;

        public ContextualBuilderDecorator(Class enclosing) {
            this.enclosing = enclosing;
            this.members = new ArrayList<Class>();
            for (Class member : enclosing.getDeclaredClasses()) {
                if (!Modifier.isStatic(member.getModifiers())) {
                    members.add(member);
                }
            }
        }

        public Builder decorate(Builder builder, Object context) {
            return new ContextualBuilder(enclosing, members, builder, context);
        }

        private static class ContextualBuilder implements Builder {

            private Class enclosing;
            private List<Class> members;
            private Builder delegate;
            private Object context;

            public ContextualBuilder(Class enclosing, List<Class> members,
                    Builder delegate, Object context) {
                this.enclosing = enclosing;
                this.members = members;
                this.delegate = delegate;
                this.context = context;
            }

            public <T> T create(Class<T> type) throws InstantiationException,
                    IllegalAccessException {
                if (members.contains(type)) {
                    try {
                        Constructor<T> constructor = type
                                .getDeclaredConstructor(enclosing);
                        constructor.setAccessible(true);
                        return constructor.newInstance(context);
                    } catch (NoSuchMethodException nsme) {
                        throw new InstantiationException(
                                "Missing valid default constructor.");
                    } catch (IllegalArgumentException e) {
                        throw new InstantiationException(
                                "Enclosing instance not accepted as argument.");
                    } catch (InvocationTargetException e) {
                        throw new InstantiationException(
                                "Failed to call constructor.");
                    }
                } else {
                    return delegate.create(type);
                }
            }

        }

    }

}
