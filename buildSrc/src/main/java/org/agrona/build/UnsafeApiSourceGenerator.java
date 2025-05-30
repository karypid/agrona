/*
 * Copyright 2014-2025 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.agrona.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * This task generates {@code UnsafeApi.java} source file.
 */
public class UnsafeApiSourceGenerator extends DefaultTask
{
    private static final ClassValue<String> TYPE_NAME = new ClassValue<>()
    {
        protected String computeValue(@NotNull final Class<?> type)
        {
            final TypeVariable<? extends Class<?>>[] typeParameters = type.getTypeParameters();
            String typeName = type.getTypeName();
            final String prefix = "java.lang.";

            if (typeName.startsWith(prefix) && typeName.lastIndexOf('.') == (prefix.length() - 1))
            {
                typeName = typeName.substring(prefix.length());
            }

            if (0 == typeParameters.length)
            {
                return typeName;
            }
            else if (1 == typeParameters.length)
            {
                return typeName + "<?>";
            }
            else
            {
                return typeName + "<?" + ",?".repeat(typeParameters.length - 1) + ">";
            }
        }
    };

    private File outputDirectory;

    /**
     * Gets the output directory where the generated java file should be stored.
     *
     * @return output directory for the generated java file.
     */
    @OutputDirectory
    public File getOutputDirectory()
    {
        return outputDirectory;
    }

    /**
     * Set the output directory where the generated java file should be stored.
     *
     * @param outputDirectory for the generated java file.
     */
    public void setOutputDirectory(final File outputDirectory)
    {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Generate {@code org.agrona.UnsafeApi} source file.
     */
    @SuppressWarnings({ "checkstyle:Regexp", "MethodLength" })
    @TaskAction
    public void run()
    {
        System.out.println(">>> Task executing...");

        final String code = """
            /*
             * Copyright 2014-$year Real Logic Limited.
             *
             * Licensed under the Apache License, Version 2.0 (the "License");
             * you may not use this file except in compliance with the License.
             * You may obtain a copy of the License at
             *
             * https://www.apache.org/licenses/LICENSE-2.0
             *
             * Unless required by applicable law or agreed to in writing, software
             * distributed under the License is distributed on an "AS IS" BASIS,
             * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
             * See the License for the specific language governing permissions and
             * limitations under the License.
             */
            package org.agrona;
            
            /**
             * Entry point for accessing {@code jdk.internal.misc.Unsafe} APIs.
             *
             * @since 2.0.0
             */
            public final class UnsafeApi
            {
                private UnsafeApi()
                {
                }

                private static java.lang.invoke.CallSite bootstrapArrayBaseOffset(
                    final java.lang.invoke.MethodHandles.Lookup lookup,
                    final String methodName,
                    final java.lang.invoke.MethodType methodType) throws Throwable
                {
                    final Class<?> clazz = methodType.parameterType(0);
                    final var method = clazz.getMethod("arrayBaseOffset", Class.class);
                    final var methodHandle = lookup.unreflect(method);
                    if (method.getReturnType() == int.class)
                    {
                        return new java.lang.invoke.ConstantCallSite(methodHandle);
                    }
                    else
                    {
                        final var intReturnType = methodHandle.type().changeReturnType(int.class);
                        final var castToIntMethodHandle =
                            java.lang.invoke.MethodHandles.explicitCastArguments(methodHandle, intReturnType);
                        return new java.lang.invoke.ConstantCallSite(castToIntMethodHandle);
                    }
                }
            $body
            }
            
            """;

        try
        {
            final Class<?> unsafeClass = Class.forName("jdk.internal.misc.Unsafe");

            final StringBuilder buffer = new StringBuilder();
            final String lineSeparator = System.lineSeparator();

            final Method[] methods = Stream.of(unsafeClass.getMethods())
                .filter(method -> method.getDeclaringClass() == unsafeClass &&
                !method.getName().endsWith("0") &&
                !method.getName().equals("getUnsafe") &&
                null == method.getAnnotation(Deprecated.class))
                .sorted(Comparator.comparing(Method::getName).thenComparingInt(Method::getParameterCount))
                .toArray(Method[]::new);

            for (final Method method : methods)
            {
                final Class<?>[] parameterTypes = method.getParameterTypes();
                final Parameter[] parameters = method.getParameters();

                buffer.append(lineSeparator).append("    /**");
                buffer.append(lineSeparator).append("     * See {@code ").append(unsafeClass.getName())
                    .append("#").append(method.getName());
                if (parameterTypes.length > 0)
                {
                    buffer.append('(');
                    for (int i = 0; i < parameters.length; i++)
                    {
                        if (i != 0)
                        {
                            buffer.append(", ");
                        }
                        buffer.append(parameterTypes[i].getTypeName());
                    }
                    buffer.append(')');
                }
                buffer.append("}.");
                for (final Parameter parameter : parameters)
                {
                    buffer.append(lineSeparator).append("     * @param ").append(parameter.getName()).append(' ')
                        .append(parameter.getName());
                }

                if (method.getReturnType() != void.class)
                {
                    buffer.append(lineSeparator).append("     * @return value");
                }
                buffer.append(lineSeparator).append("     */");

                buffer.append(lineSeparator).append("    public static ");
                if (method.getName().equals("arrayBaseOffset"))
                {
                    buffer.append(TYPE_NAME.get(int.class)); // JDK 25 changed to long
                }
                else
                {
                    buffer.append(TYPE_NAME.get(method.getReturnType()));
                }
                buffer.append(' ').append(method.getName()).append("(");

                for (int i = 0; i < parameters.length; i++)
                {
                    if (i > 0)
                    {
                        buffer.append(',');
                    }

                    buffer.append(lineSeparator).append("        final ")
                        .append(TYPE_NAME.get(parameterTypes[i])).append(' ')
                        .append(parameters[i].getName());
                }

                buffer.append(')').append(lineSeparator).append("    {").append(lineSeparator)
                    .append("        throw new UnsupportedOperationException(\"'")
                    .append(method.getName()).append("' not implemented\");")
                    .append(lineSeparator).append("    }").append(lineSeparator);
            }

            Files.writeString(
                outputDirectory.toPath().resolve("org/agrona/UnsafeApi.java"),
                code
                .replace("$year", Integer.toString(LocalDate.now().getYear()))
                .replace("$body", buffer),
                StandardCharsets.US_ASCII,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (final Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
