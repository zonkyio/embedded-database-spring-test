/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zonky.test.db.flyway;

import com.google.common.collect.ImmutableList;
import io.zonky.test.db.util.ReflectionUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.annotation.FlywayTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.springframework.asm.SpringAsmInfo.ASM_VERSION;
import static org.springframework.util.ReflectionUtils.findMethod;

public class OptimizedFlywayTestExecutionListener implements TestExecutionListener, Ordered {

    private static final TestExecutionListener listener = initListener();

    @Override
    public int getOrder() {
        return 3900;
    }

    @Override
    public void beforeTestClass(TestContext testContext) throws Exception {
        listener.beforeTestClass(testContext);
        processPendingFlywayOperations(testContext);
    }

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        listener.prepareTestInstance(testContext);
        processPendingFlywayOperations(testContext);
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        listener.beforeTestMethod(testContext);
        processPendingFlywayOperations(testContext);
    }

    @Override
    public void beforeTestExecution(TestContext testContext) throws Exception {
        listener.beforeTestExecution(testContext);
        processPendingFlywayOperations(testContext);
    }

    @Override
    public void afterTestExecution(TestContext testContext) throws Exception {
        listener.afterTestExecution(testContext);
        processPendingFlywayOperations(testContext);
    }

    @Override
    public void afterTestMethod(TestContext testContext) throws Exception {
        listener.afterTestMethod(testContext);
        processPendingFlywayOperations(testContext);
    }

    @Override
    public void afterTestClass(TestContext testContext) throws Exception {
        listener.afterTestClass(testContext);
        processPendingFlywayOperations(testContext);
    }

    private static void processPendingFlywayOperations(TestContext testContext) {
        if (listener instanceof NoOpTestExecutionListener) {
            return;
        }

        ApplicationContext applicationContext;
        try {
            applicationContext = testContext.getApplicationContext();
        } catch (IllegalStateException e) {
            return;
        }

        if (applicationContext.getBeanNamesForType(FlywayExtension.class, false, false).length > 0) {
            FlywayExtension flywayExtension = applicationContext.getBean(FlywayExtension.class);
            flywayExtension.processPendingOperations();
        }
    }

    private static TestExecutionListener initListener() {
        try {
            if (ClassUtils.isPresent("org.flywaydb.core.Flyway", null)) {

                if (ClassUtils.isPresent("org.flywaydb.test.FlywayTestExecutionListener", null)) {
                    Class<?> listener = ClassUtils.forName("org.flywaydb.test.FlywayTestExecutionListener", null);
                    Method listenerMethod = findMethod(listener, "locationsMigrationHandling", FlywayTest.class, Flyway.class, String.class);
                    if (listenerMethod != null) {
                        Class<?> modifiedListener = replaceMethod(listenerMethod, LocationsMigrationHandlingMethodReplacer::new);
                        return ReflectionUtils.invokeConstructor(modifiedListener);
                    } else {
                        throw new IllegalStateException("org.flywaydb.test.FlywayTestExecutionListener#locationsMigrationHandling method not found");
                    }
                }

                if (ClassUtils.isPresent("org.flywaydb.test.junit.FlywayTestExecutionListener", null)) {
                    return ReflectionUtils.invokeConstructor("org.flywaydb.test.junit.FlywayTestExecutionListener");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new NoOpTestExecutionListener();
    }

    private static class NoOpTestExecutionListener extends AbstractTestExecutionListener {}

    private static Class<?> replaceMethod(Method method, Function<MethodVisitor, MethodVisitor> methodReplacer) throws IOException {
        Class<?> declaringClass = method.getDeclaringClass();
        ClassReader cr = new ClassReader(declaringClass.getResourceAsStream(declaringClass.getSimpleName() + ".class"));
        ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(new ClassTransformer(cw, method, methodReplacer), 0);
        byte[] code = cw.toByteArray();

        return new ClassLoader() {
            Class<?> get() {
                return defineClass(null, code, 0, code.length);
            }
        }.get();
    }

    private static class ClassTransformer extends ClassVisitor {

        private final String methodName;
        private final String methodDescriptor;
        private final Function<MethodVisitor, MethodVisitor> methodReplacer;

        private ClassTransformer(ClassWriter cw, Method method, Function<MethodVisitor, MethodVisitor> methodReplacer) {
            super(ASM_VERSION, cw);
            this.methodName = method.getName();
            this.methodDescriptor = Type.getMethodDescriptor(method);
            this.methodReplacer = methodReplacer;
        }

        // invoked for every method
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (!name.equals(methodName) || !desc.equals(methodDescriptor)) {
                // reproduce the methods we're not interested in, unchanged
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
            // alter the behavior for the specific method
            return methodReplacer.apply(super.visitMethod(access, name, desc, signature, exceptions));
        }
    }

    private static class LocationsMigrationHandlingMethodReplacer extends MethodVisitor {

        private final MethodVisitor writer;

        private LocationsMigrationHandlingMethodReplacer(MethodVisitor writer) {
            // now, we're not passing the writer to the superclass for our radical changes
            super(ASM_VERSION);
            this.writer = writer;
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            writer.visitMaxs(3, 4);
        }

        @Override
        public void visitCode() {
            writer.visitCode();
            writer.visitVarInsn(Opcodes.ALOAD, 1);
            writer.visitVarInsn(Opcodes.ALOAD, 2);
            writer.visitVarInsn(Opcodes.ALOAD, 3);
            writer.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "io/zonky/test/db/flyway/OptimizedFlywayTestExecutionListener",
                    "optimizedLocationsMigrationHandling",
                    "(Lorg/flywaydb/test/annotation/FlywayTest;Lorg/flywaydb/core/Flyway;Ljava/lang/String;)V",
                    false);
            writer.visitInsn(Opcodes.RETURN);
        }

        @Override
        public void visitEnd() {
            writer.visitEnd();
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return writer.visitAnnotation(desc, visible);
        }

        @Override
        public void visitParameter(String name, int access) {
            writer.visitParameter(name, access);
        }
    }

    private static final Logger flywayLogger = LoggerFactory.getLogger("org.flywaydb.test.FlywayTestExecutionListener");

    public static void optimizedLocationsMigrationHandling(FlywayTest annotation, Flyway flyway, String executionInfo) {
        String[] locations = annotation.locationsForMigrate();
        // now migration handling for locations support
        FlywayWrapper wrapper = FlywayWrapper.of(flyway);
        List<String> oldLocations = wrapper.getLocations();
        boolean override = annotation.overrideLocations();
        try {
            List<String> useLocations;
            if (override) {
                useLocations = ImmutableList.copyOf(locations);
            } else {
                // Fill the locations
                useLocations = ImmutableList.<String>builder()
                        .addAll(oldLocations)
                        .addAll(Arrays.asList(locations))
                        .build();
            }
            if (flywayLogger.isDebugEnabled()) {
                flywayLogger.debug(String
                        .format("******** Start migration from locations directories '%s'  for  '%s'.",
                                useLocations, executionInfo));

            }
            wrapper.setLocations(useLocations);
            flyway.migrate();
        } finally {
            // reset the flyway bean to original configuration.
            wrapper.setLocations(oldLocations);
        }
    }
}
