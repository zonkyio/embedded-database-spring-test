package io.zonky.test.db.preparer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import io.zonky.test.db.provider.DatabasePreparer;

public class RecordingMethodInterceptor implements MethodInterceptor {

    private static final List<Predicate<Method>> EXCLUDED_METHODS = ImmutableList.of(
            new MethodPredicate(Object.class, "equals", "hashCode", "toString"),
            new MethodPredicate(DataSource.class, "isWrapperFor", "getLogWriter", "setLogWriter", "getLoginTimeout", "getParentLogger"),
            new MethodPredicate(Connection.class, "getAutoCommit", "isClosed", "getMetaData", "isReadOnly", "getCatalog", "getTransactionIsolation", "getWarnings", "clearWarnings", "getTypeMap", "getHoldability", "isValid", "getClientInfo", "getSchema", "getNetworkTimeout"),
            new MethodPredicate(Statement.class, "getMaxFieldSize", "getMaxRows", "getQueryTimeout", "getWarnings", "clearWarnings", "getResultSet", "getUpdateCount", "getMoreResults", "getFetchDirection", "getFetchSize", "getResultSetConcurrency", "getResultSetType", "getMoreResults", "getGeneratedKeys", "getResultSetHoldability", "isClosed", "isPoolable", "isCloseOnCompletion", "getLargeUpdateCount", "getLargeMaxRows"),
            new MethodPredicate(PreparedStatement.class, "getMetaData", "getParameterMetaData"),
            new MethodPredicate(CallableStatement.class, "getString", "getBoolean", "getByte", "getShort", "getInt", "getLong", "getFloat", "getDouble", "getBigDecimal", "getBytes", "getDate", "getTime", "getTimestamp", "getObject", "getRef", "getBlob", "getClob", "getArray", "getURL", "getRowId", "getNClob", "getSQLXML", "getNString", "getNCharacterStream", "getCharacterStream"));

    private static final Set<Class<?>> EXCLUDED_RETURN_TYPES = ImmutableSet.of(ResultSet.class); // TODO: check if this query may modify the database or not

    private final String thisId;
    private final RecordingContext context;

    public RecordingMethodInterceptor() {
        this.thisId = "root";
        this.context = new RecordingContext();
    }

    private RecordingMethodInterceptor(String thisId, RecordingContext context) {
        this.thisId = thisId;
        this.context = context;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        String methodName = method.getName();
        Object[] arguments = invocation.getArguments();
        Class<?> returnType = method.getReturnType();

        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof InputStream) {
                InputStream inputStream = (InputStream) arguments[i];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                StreamUtils.copy(inputStream, baos);
                inputStream.close();
                arguments[i] = new TestByteArrayInputStream(baos.toByteArray());
            }
        }

        if (method.getDeclaringClass() == RecordingDataSource.class && method.getReturnType() == DatabasePreparer.class) {
            return new ReplayableDatabasePreparer(context);
        }

        Object result = invocation.proceed();

        if (isExcludedMethod(method)) {
            return result;
        }

        if (result != null && !returnType.isPrimitive() && (returnType.isInterface() || !Modifier.isFinal(returnType.getModifiers())) && !isExcludedReturnType(returnType)) {
            String returnId = String.valueOf(context.sequence.incrementAndGet());
            context.addRecord(new Record(thisId, methodName, arguments, returnId));

            ProxyFactory proxyFactory = new ProxyFactory(result);
            proxyFactory.addAdvice(new RecordingMethodInterceptor(returnId, context));

            if (returnType.isInterface()) {
                proxyFactory.addInterface(returnType);
            } else {
                proxyFactory.setProxyTargetClass(true);
            }

            Object proxyResult = proxyFactory.getProxy();
            context.addArgument(returnId, proxyResult);
            return proxyResult;
        } else {
            context.addRecord(new Record(thisId, methodName, arguments, null));
            return result;
        }
    }

    private static boolean isExcludedMethod(Method method) {
        for (Predicate<Method> exclusionPredicate : EXCLUDED_METHODS) {
            if (exclusionPredicate.test(method)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExcludedReturnType(Class<?> returnType) {
        for (Class<?> excludedReturnType : EXCLUDED_RETURN_TYPES) {
            if (excludedReturnType.isAssignableFrom(returnType)) {
                return true;
            }
        }
        return false;
    }

    private static class RecordingContext implements Iterable<Record> {

        private final AtomicLong sequence = new AtomicLong(0); // TODO: it needs a little polishing

        private final List<Record> recordData;
        // TODO: make argument mapping better
        // TODO: apply argument mapping to equals and hashCode methods
        private final Map<Object, String> argMapping;

        public RecordingContext() {
            this.recordData = new ArrayList<>();
            this.argMapping = new IdentityHashMap<>();
        }

        private RecordingContext(RecordingContext context) {
            this.recordData = new ArrayList<>(context.recordData);
            this.argMapping = new IdentityHashMap<>(context.argMapping);
        }

        public synchronized void addRecord(Record record) {
            recordData.add(record);
        }

        public synchronized void addArgument(String argumentId, Object argumentValue) {
            argMapping.put(argumentValue, argumentId); // TODO: use multimap or another structure because a single argument value may be assigned to multiple argument ids
        }

        public synchronized String getArgumentId(Object argumentValue) {
            return argMapping.get(argumentValue);
        }

        public synchronized boolean containsArgument(Object argumentValue) {
            return argMapping.containsKey(argumentValue);
        }

        @NotNull
        @Override
        public synchronized Iterator<Record> iterator() {
            return recordData.iterator();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RecordingContext records = (RecordingContext) o;
            return Objects.equals(recordData, records.recordData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recordData);
        }
    }

    private static class MethodPredicate implements Predicate<Method> {

        private final Class<?> declaringClass;
        private final Set<String> methodNames;

        private MethodPredicate(Class<?> declaringClass, String... methodNames) {
            this.declaringClass = declaringClass;
            this.methodNames = ImmutableSet.copyOf(methodNames);
        }

        @Override
        public boolean test(Method method) {
            String methodName = method.getName();
            Class<?> declaringClass = method.getDeclaringClass();

            if (this.declaringClass.isAssignableFrom(declaringClass)) {
                return this.methodNames.contains(methodName);
            }

            return false;
        }
    }

    public static class ReplayableDatabasePreparer implements DatabasePreparer {

        private final RecordingContext recordingContext;

        private ReplayableDatabasePreparer(RecordingContext context) {
            this.recordingContext = new RecordingContext(context);
        }

        @Override
        public void prepare(DataSource dataSource) {
            Map<String, Object> context = new HashMap<>();
            context.put("root", dataSource);

            for (Record record : recordingContext) {
                Object target = context.get(record.thisId);
                Object[] arguments = Arrays.stream(record.arguments).map(arg -> {
                    if (recordingContext.containsArgument(arg)) {
                        return context.get(recordingContext.getArgumentId(arg));
                    } else if (arg instanceof TestByteArrayInputStream) {
                        TestByteArrayInputStream stream = (TestByteArrayInputStream) arg;
                        return new ByteArrayInputStream(stream.getByteArray());
                    } else {
                        return arg;
                    }
                }).toArray();
                Object result = ReflectionTestUtils.invokeMethod(target, record.methodName, arguments);
                if (record.resultId != null) {
                    context.put(record.resultId, result); // TODO: result must not be null
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReplayableDatabasePreparer preparer = (ReplayableDatabasePreparer) o;
            return Objects.equals(recordingContext, preparer.recordingContext);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recordingContext);
        }
    }

    private static class Record {

        private final String thisId;
        private final String methodName;
        private final Object[] arguments; // TODO: beware of mutable object
        private final String resultId;

        private Record(String thisId, String methodName, Object[] arguments, String resultId) {
            this.thisId = thisId;
            this.methodName = methodName;
            this.arguments = arguments;
            this.resultId = resultId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Record record = (Record) o;
            return Objects.equals(thisId, record.thisId) &&
                    Objects.equals(methodName, record.methodName) &&
                    Arrays.equals(arguments, record.arguments) &&
                    Objects.equals(resultId, record.resultId);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(thisId, methodName, resultId);
            result = 31 * result + Arrays.hashCode(arguments);
            return result;
        }
    }

    private static class TestByteArrayInputStream extends ByteArrayInputStream {

        public TestByteArrayInputStream(byte[] buf) {
            super(buf);
        }

        public byte[] getByteArray() {
            return buf;
        }
    }
}
