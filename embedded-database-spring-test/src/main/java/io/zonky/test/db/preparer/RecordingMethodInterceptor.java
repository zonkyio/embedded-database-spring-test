package io.zonky.test.db.preparer;

import com.google.common.base.Equivalence;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AtomicLongMap;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.io.IOUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.CharArrayReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Wrapper;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;
import static org.springframework.beans.BeanUtils.isSimpleValueType;

public class RecordingMethodInterceptor implements MethodInterceptor {

    private static final List<Predicate<Method>> EXCLUDED_METHODS = ImmutableList.of(
            new MethodPredicate(Object.class, "equals", "hashCode", "toString"),
            new MethodPredicate(Wrapper.class, "isWrapperFor"),
            new MethodPredicate(DataSource.class, "getLogWriter", "setLogWriter", "getLoginTimeout", "getParentLogger"),
            new MethodPredicate(Connection.class, "getAutoCommit", "isClosed", "getMetaData", "isReadOnly", "getCatalog", "getTransactionIsolation", "getWarnings", "clearWarnings", "getTypeMap", "getHoldability", "isValid", "getClientInfo", "getSchema", "getNetworkTimeout"),
            new MethodPredicate(Statement.class, "getMaxFieldSize", "getMaxRows", "getQueryTimeout", "getWarnings", "clearWarnings", "getResultSet", "getUpdateCount", "getMoreResults", "getFetchDirection", "getFetchSize", "getResultSetConcurrency", "getResultSetType", "getMoreResults", "getGeneratedKeys", "getResultSetHoldability", "isClosed", "isPoolable", "isCloseOnCompletion", "getLargeUpdateCount", "getLargeMaxRows"),
            new MethodPredicate(PreparedStatement.class, "getMetaData", "getParameterMetaData"),
            new MethodPredicate(CallableStatement.class, "getString", "getBoolean", "getByte", "getShort", "getInt", "getLong", "getFloat", "getDouble", "getBigDecimal", "getBytes", "getDate", "getTime", "getTimestamp", "getObject", "getRef", "getBlob", "getClob", "getArray", "getURL", "getRowId", "getNClob", "getSQLXML", "getNString", "getNCharacterStream", "getCharacterStream"),
            new MethodPredicate(ResultSet.class, "wasNull", "getString", "getBoolean", "getByte", "getShort", "getInt", "getLong", "getFloat", "getDouble", "getBigDecimal", "getBytes", "getDate", "getTime", "getTimestamp", "getAsciiStream", "getUnicodeStream", "getBinaryStream", "getWarnings", "clearWarnings", "getCursorName", "getMetaData", "getObject", "findColumn", "getCharacterStream", "isBeforeFirst", "isAfterLast", "isFirst", "isLast", "getRow", "getFetchDirection", "getFetchSize", "getType", "getConcurrency", "rowUpdated", "rowInserted", "rowDeleted", "getRef", "getBlob", "getClob", "getArray", "getURL", "getRowId", "getHoldability", "isClosed", "getNClob", "getSQLXML", "getNString", "getNCharacterStream"));

    private static final String ROOT_REFERENCE = "dataSource";

    private final String thisId;
    private final RecordingContext context;

    public RecordingMethodInterceptor() {
        this.thisId = ROOT_REFERENCE;
        this.context = new RecordingContext();
    }

    private RecordingMethodInterceptor(String thisId, RecordingContext context) {
        this.thisId = thisId;
        this.context = context;
    }

    private Object[] captureArguments(Object[] arguments) throws IOException {
        Object[] captured = new Object[arguments.length];

        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof OutputStream) {
                throw new UnsupportedOperationException("Output streams can not be captured");
            } else if (context.containsArgumentMapping(arguments[i])) {
                captured[i] = new ArgumentReference(context.getArgumentId(arguments[i]));
            } else if (arguments[i] instanceof InputStream) {
                captured[i] = new InputStreamArgumentProvider((InputStream) arguments[i]);
            } else if (arguments[i] instanceof Reader) {
                captured[i] = new ReaderArgumentProvider((Reader) arguments[i]);
            } else {
                captured[i] = captureArgument(arguments[i]);
            }

            if (captured[i] instanceof ArgumentProvider) {
                arguments[i] = ((ArgumentProvider) captured[i]).getArgument();
            }
        }

        return captured;
    }

    private static Object captureArgument(Object argument) {
        if (argument instanceof Date) {
            return ((Date) argument).clone();
        } else if (argument instanceof Calendar) {
            return ((Calendar) argument).clone();
        } else if (argument instanceof Properties) {
            return ((Properties) argument).clone();
        } else if (argument instanceof Map) {
            return ((Map<?, ?>) argument).entrySet().stream()
                    .collect(Collectors.toMap(e -> captureArgument(e.getKey()), e -> captureArgument(e.getValue())));
        } else if (argument instanceof Set) {
            return ((Set<?>) argument).stream()
                    .map(RecordingMethodInterceptor::captureArgument).collect(Collectors.toSet());
        } else if (argument instanceof List) {
            return ((List<?>) argument).stream()
                    .map(RecordingMethodInterceptor::captureArgument).collect(Collectors.toList());
        } else if (argument.getClass().isArray()) {
            int length = Array.getLength(argument);
            Object array = Array.newInstance(argument.getClass().getComponentType(), length);
            System.arraycopy(argument, 0, array, 0, length);

            if (!argument.getClass().getComponentType().isPrimitive()) {
                Object[] objects = (Object[]) array;
                for (int i = 0; i < objects.length; i++) {
                    objects[i] = captureArgument(objects[i]);
                }
            }

            return array;
        } else {
            return argument;
        }
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        String methodName = method.getName();
        Class<?> returnType = method.getReturnType();
        Object[] arguments = captureArguments(invocation.getArguments());

        if (method.getDeclaringClass() == RecordingDataSource.class && methodName.equals("getPreparer")) {
            return new ReplayableDatabasePreparerImpl(context.recordData);
        }

        Object result = invocation.proceed();

        if (isExcludedMethod(method)) {
            return result;
        }

        if (result != null && !isSimpleValueType(returnType) && !returnType.isArray()
                && (returnType.isInterface() || !Modifier.isFinal(result.getClass().getModifiers()))) {

            String returnId = context.generateIdentifier(returnType);
            context.addRecord(new Record(thisId, methodName, arguments, returnId));

            Object proxiedResult = createRecordingProxy(returnId, returnType, result);
            context.registerArgumentMapping(returnId, proxiedResult);
            return proxiedResult;
        } else {
            context.addRecord(new Record(thisId, methodName, arguments, null));
            return result;
        }
    }

    private Object createRecordingProxy(String identifier, Class<?> returnType, Object result) {
        ProxyFactory proxyFactory = new ProxyFactory(result);
        proxyFactory.addAdvice(new RecordingMethodInterceptor(identifier, context));

        if (returnType.isInterface()) {
            proxyFactory.addInterface(returnType);
        } else {
            proxyFactory.setProxyTargetClass(true);
        }

        return proxyFactory.getProxy();
    }

    private static boolean isExcludedMethod(Method method) {
        for (Predicate<Method> exclusionPredicate : EXCLUDED_METHODS) {
            if (exclusionPredicate.test(method)) {
                return true;
            }
        }
        return false;
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

    private static class RecordingContext {

        private final AtomicLongMap<String> sequences;
        private final BlockingQueue<Record> recordData;
        private final ConcurrentMap<Equivalence.Wrapper<Object>, String> argumentMapping;

        public RecordingContext() {
            this.sequences = AtomicLongMap.create();
            this.recordData = new LinkedBlockingQueue<>();
            this.argumentMapping = new ConcurrentHashMap<>();
        }

        public String generateIdentifier(Class<?> type) {
            String typeName = StringUtils.uncapitalize(type.getSimpleName());
            long paramIndex = sequences.incrementAndGet(typeName);
            return typeName + paramIndex;
        }

        public void addRecord(Record record) {
            recordData.add(record);
        }

        public void registerArgumentMapping(String argumentId, Object argumentValue) {
            argumentMapping.put(identity(argumentValue), argumentId);
        }

        public boolean containsArgumentMapping(Object argumentValue) {
            return argumentMapping.containsKey(identity(argumentValue));
        }

        public String getArgumentId(Object argumentValue) {
            return argumentMapping.get(identity(argumentValue));
        }

        private static <T> Equivalence.Wrapper<T> identity(T reference) {
            return Equivalence.identity().wrap(reference);
        }
    }

    public static class ReplayableDatabasePreparerImpl implements ReplayableDatabasePreparer {

        private final List<Record> recordData;

        private ReplayableDatabasePreparerImpl(Iterable<Record> recordData) {
            this.recordData = ImmutableList.copyOf(recordData);
        }

        @Override
        public boolean hasRecords() {
            return !recordData.isEmpty();
        }

        @Override
        public void prepare(DataSource dataSource) {
            Map<String, Object> context = new HashMap<>();
            context.put(ROOT_REFERENCE, dataSource);

            for (Record record : recordData) {
                Object target = context.get(record.thisId);
                Object[] arguments = record.arguments.stream().map(arg -> mapArgument(arg, context)).toArray();
                Object result = ReflectionTestUtils.invokeMethod(target, record.methodName, arguments);
                if (record.resultId != null) {
                    checkState(result != null, "The result does not match the recorded data");
                    context.put(record.resultId, result);
                }
            }
        }

        private static Object mapArgument(Object argument, Map<String, Object> context) {
            if (argument instanceof ArgumentReference) {
                return context.get(((ArgumentReference) argument).getReferenceId());
            } else if (argument instanceof ArgumentProvider) {
                return ((ArgumentProvider) argument).getArgument();
            } else {
                return argument;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReplayableDatabasePreparerImpl that = (ReplayableDatabasePreparerImpl) o;
            return Objects.equals(recordData, that.recordData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recordData);
        }
    }

    private static class Record {

        private final String thisId;
        private final String methodName;
        private final List<Object> arguments;
        private final String resultId;

        private Record(String thisId, String methodName, Object[] arguments, String resultId) {
            this.thisId = thisId;
            this.methodName = methodName;
            this.arguments = ImmutableList.copyOf(arguments);
            this.resultId = resultId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Record record = (Record) o;
            return Objects.equals(thisId, record.thisId) &&
                    Objects.equals(methodName, record.methodName) &&
                    Objects.equals(arguments, record.arguments) &&
                    Objects.equals(resultId, record.resultId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(thisId, methodName, arguments, resultId);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("thisId", thisId)
                    .add("methodName", methodName)
                    .add("arguments", arguments)
                    .add("resultId", resultId)
                    .toString();
        }
    }

    private interface ArgumentProvider {

        Object getArgument();

    }

    private static class ArgumentReference {

        private final String referenceId;

        private ArgumentReference(String referenceId) {
            this.referenceId = referenceId;
        }

        public String getReferenceId() {
            return referenceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArgumentReference that = (ArgumentReference) o;
            return Objects.equals(referenceId, that.referenceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(referenceId);
        }
    }

    private static class InputStreamArgumentProvider implements ArgumentProvider {

        private final byte[] data;

        public InputStreamArgumentProvider(InputStream stream) throws IOException {
            data = IOUtils.toByteArray(stream);
            stream.close();
        }

        @Override
        public Object getArgument() {
            return new ByteArrayInputStream(data);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InputStreamArgumentProvider that = (InputStreamArgumentProvider) o;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }

    private static class ReaderArgumentProvider implements ArgumentProvider {

        private final char[] data;

        public ReaderArgumentProvider(Reader reader) throws IOException {
            data = IOUtils.toCharArray(reader);
            reader.close();
        }

        @Override
        public Object getArgument() {
            return new CharArrayReader(data);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReaderArgumentProvider that = (ReaderArgumentProvider) o;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }
}
