package io.zonky.test.db.preparer;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import javax.sql.rowset.serial.SerialBlob;
import java.io.ByteArrayInputStream;
import java.io.CharArrayReader;
import java.io.InputStream;
import java.io.Reader;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Time;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RecordingDataSourceTest {

    @Test
    public void testRecording() throws SQLException {
        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(mock(DataSource.class, RETURNS_MOCKS));

        Connection connection = recordingDataSource.getConnection();
        connection.setAutoCommit(true);
        Statement statement = connection.createStatement();
        statement.executeUpdate("create table");
        statement.executeUpdate("insert data");
        statement.executeUpdate("select data");

        statement.close();
        connection.commit();
        connection.close();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        DatabasePreparer preparer = recordingDataSource.getPreparer();
        preparer.prepare(mockDataSource);

        InOrder inOrder = inOrder(mockDataSource, mockConnection, mockStatement);
        inOrder.verify(mockDataSource).getConnection();
        inOrder.verify(mockConnection).setAutoCommit(true);
        inOrder.verify(mockConnection).createStatement();
        inOrder.verify(mockStatement).executeUpdate("create table");
        inOrder.verify(mockStatement).executeUpdate("insert data");
        inOrder.verify(mockStatement).executeUpdate("select data");
        inOrder.verify(mockStatement).close();
        inOrder.verify(mockConnection).commit();
        inOrder.verify(mockConnection).close();
    }

    @Test
    public void testResultSet() throws SQLException {
        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(mock(DataSource.class, RETURNS_MOCKS));

        Connection connection = recordingDataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select data");

        assertThat(resultSet).isNotNull();

        resultSet.next();
        resultSet.getString(1);
        resultSet.getMetaData();
        resultSet.updateString(3, "string");
        resultSet.getBigDecimal(4);
        resultSet.updateBinaryStream(5, IOUtils.toInputStream("input", UTF_8));
        resultSet.getBytes(6);
        resultSet.getBlob(7);
        resultSet.updateBoolean(8, true);
        resultSet.getDate(9);
        resultSet.getCharacterStream(10);
        resultSet.updateTime(11, new Time(0));
        resultSet.getURL(12);
        resultSet.cancelRowUpdates();

        resultSet.close();
        statement.close();
        connection.close();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(any())).thenReturn(mockResultSet);

        DatabasePreparer preparer = recordingDataSource.getPreparer();
        preparer.prepare(mockDataSource);

        InOrder inOrder = inOrder(mockDataSource, mockConnection, mockStatement, mockResultSet);
        inOrder.verify(mockDataSource).getConnection();
        inOrder.verify(mockConnection).createStatement();
        inOrder.verify(mockStatement).executeQuery(anyString());

        inOrder.verify(mockResultSet).next();
        inOrder.verify(mockResultSet, never()).getString(anyInt());
        inOrder.verify(mockResultSet, never()).getMetaData();
        inOrder.verify(mockResultSet).updateString(3, "string");
        inOrder.verify(mockResultSet, never()).getBigDecimal(any());
        inOrder.verify(mockResultSet).updateBinaryStream(eq(5), any());
        inOrder.verify(mockResultSet, never()).getBytes(anyInt());
        inOrder.verify(mockResultSet, never()).getBlob(7);
        inOrder.verify(mockResultSet).updateBoolean(8, true);
        inOrder.verify(mockResultSet, never()).getDate(any());
        inOrder.verify(mockResultSet, never()).getCharacterStream(10);
        inOrder.verify(mockResultSet).updateTime(11, new Time(0));
        inOrder.verify(mockResultSet, never()).getURL(anyInt());
        inOrder.verify(mockResultSet).cancelRowUpdates();
        inOrder.verify(mockResultSet).close();
        inOrder.verify(mockStatement).close();
        inOrder.verify(mockConnection).close();
    }

    @Test
    public void testUnwrapping() throws SQLException {
        DataSource targetDataSource = mock(DataSource.class);
        when(targetDataSource.unwrap(PGSimpleDataSource.class)).thenReturn(new PGSimpleDataSource());

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        PGSimpleDataSource unwrappedDataSource = recordingDataSource.unwrap(PGSimpleDataSource.class);
        unwrappedDataSource.getDescription();

        assertThat(unwrappedDataSource).isNotNull();

        DataSource mockDataSource = mock(DataSource.class);
        PGSimpleDataSource mockUnwrappedDataSource = mock(PGSimpleDataSource.class);
        when(mockDataSource.unwrap(PGSimpleDataSource.class)).thenReturn(mockUnwrappedDataSource);

        DatabasePreparer preparer = recordingDataSource.getPreparer();
        preparer.prepare(mockDataSource);

        InOrder inOrder = inOrder(mockDataSource, mockUnwrappedDataSource);
        inOrder.verify(mockDataSource).unwrap(PGSimpleDataSource.class);
        inOrder.verify(mockUnwrappedDataSource).getDescription();
    }

    @Test
    public void testSnapshot() throws SQLException {
        DataSource targetDataSource = mock(DataSource.class, RETURNS_DEEP_STUBS);

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        Connection connection = recordingDataSource.getConnection();
        Savepoint savepoint = connection.setSavepoint();
        Statement statement = connection.createStatement();
        statement.executeUpdate("create table");
        connection.releaseSavepoint(savepoint);

        statement.close();
        connection.close();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        Savepoint mockSavepoint = mock(Savepoint.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.setSavepoint()).thenReturn(mockSavepoint);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        DatabasePreparer preparer = recordingDataSource.getPreparer();
        preparer.prepare(mockDataSource);

        InOrder inOrder = inOrder(mockDataSource, mockConnection, mockStatement);
        inOrder.verify(mockDataSource).getConnection();
        inOrder.verify(mockConnection).setSavepoint();
        inOrder.verify(mockConnection).createStatement();
        inOrder.verify(mockStatement).executeUpdate("create table");
        inOrder.verify(mockConnection).releaseSavepoint(mockSavepoint);
        inOrder.verify(mockStatement).close();
        inOrder.verify(mockConnection).close();
    }

    @Test
    public void testBlobType() throws SQLException {
        DataSource targetDataSource = mock(DataSource.class, RETURNS_DEEP_STUBS);
        when(targetDataSource.getConnection().createBlob()).thenReturn(new SerialBlob(new byte[4]));

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        Connection connection = recordingDataSource.getConnection();
        Blob blob = connection.createBlob();
        blob.setBytes(1, new byte[] { 0, 1, 2, 3 });
        PreparedStatement statement = connection.prepareStatement("insert data");
        statement.setBlob(1, blob);
        statement.executeUpdate();
        statement.close();
        connection.close();
        blob.free();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(any())).thenReturn(mockStatement);
        when(mockConnection.createBlob()).thenReturn(new SerialBlob(new byte[4]));

        // it is not possible to use Mockito#verify method
        // because the verification must take place immediately
        // when the PreparedStatement#setBlob method is called
        doNothing().when(mockStatement).setBlob(eq(1), argThat((ArgumentMatcher<Blob>) blob1 -> {
            try {
                byte[] bytes = blob1.getBytes(1, 4);
                return Arrays.equals(bytes, new byte[] { 0, 1, 2, 3 });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }));

        DatabasePreparer preparer = recordingDataSource.getPreparer();
        preparer.prepare(mockDataSource);

        verify(mockStatement).setBlob(eq(1), any(Blob.class));
    }

    @Test
    public void testArrayType() throws SQLException {
        DataSource targetDataSource = mock(DataSource.class, RETURNS_DEEP_STUBS);

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        Object[] objects = new Object[0];
        Connection connection = recordingDataSource.getConnection();
        Array array = connection.createArrayOf("type", objects);
        PreparedStatement statement = connection.prepareStatement("insert data");
        statement.setArray(1, array);
        statement.executeUpdate();

        statement.close();
        connection.close();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        Array mockArray = mock(Array.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(any())).thenReturn(mockStatement);
        when(mockConnection.createArrayOf(anyString(), any())).thenReturn(mockArray);

        DatabasePreparer preparer = recordingDataSource.getPreparer();
        preparer.prepare(mockDataSource);

        InOrder inOrder = inOrder(mockDataSource, mockConnection, mockStatement);
        inOrder.verify(mockDataSource).getConnection();
        inOrder.verify(mockConnection).createArrayOf(eq("type"), eq(objects));
        inOrder.verify(mockConnection).prepareStatement("insert data");
        inOrder.verify(mockStatement).setArray(1, mockArray);
        inOrder.verify(mockStatement).executeUpdate();
        inOrder.verify(mockStatement).close();
        inOrder.verify(mockConnection).close();

        verify(mockConnection, never()).createArrayOf(any(), same(objects)); // the array must be a copy of the original array
    }

    @Test
    public void testInputStream() throws SQLException {
        DataSource targetDataSource = mock(DataSource.class, RETURNS_DEEP_STUBS);
        PreparedStatement preparedStatement = targetDataSource.getConnection().prepareStatement(any());
        doAnswer(invocation -> {
            InputStream stream = invocation.getArgument(1, InputStream.class);
            byte[] bytes = IOUtils.readFully(stream, 4);
            checkState(Arrays.equals(bytes, new byte[] { 0, 1, 2, 3 }));
            return null;
        }).when(preparedStatement).setBinaryStream(anyInt(), any());

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        Connection connection = recordingDataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("insert data");
        statement.setBinaryStream(1, new ByteArrayInputStream(new byte[] { 0, 1, 2, 3 }));
        statement.executeUpdate();

        statement.close();
        connection.close();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(any())).thenReturn(mockStatement);

        doAnswer(invocation -> {
            InputStream stream = invocation.getArgument(1, InputStream.class);
            byte[] bytes = IOUtils.readFully(stream, 4);
            checkState(Arrays.equals(bytes, new byte[] { 0, 1, 2, 3 }));
            return null;
        }).when(mockStatement).setBinaryStream(eq(1), any());

        DatabasePreparer preparer = recordingDataSource.getPreparer();
        preparer.prepare(mockDataSource);

        verify(mockStatement).setBinaryStream(anyInt(), any());
    }

    @Test
    public void testReader() throws SQLException {
        DataSource targetDataSource = mock(DataSource.class, RETURNS_DEEP_STUBS);
        PreparedStatement preparedStatement = targetDataSource.getConnection().prepareStatement(any());
        doAnswer(invocation -> {
            Reader reader = invocation.getArgument(1, Reader.class);
            checkState("test".equals(IOUtils.toString(reader)));
            return null;
        }).when(preparedStatement).setCharacterStream(anyInt(), any());

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        Connection connection = recordingDataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("insert data");
        statement.setCharacterStream(1, new CharArrayReader(new char[] {'t', 'e', 's', 't'}));
        statement.executeUpdate();

        statement.close();
        connection.close();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(any())).thenReturn(mockStatement);

        doAnswer(invocation -> {
            Reader reader = invocation.getArgument(1, Reader.class);
            checkState("test".equals(IOUtils.toString(reader)));
            return null;
        }).when(mockStatement).setCharacterStream(eq(1), any());

        DatabasePreparer preparer = recordingDataSource.getPreparer();
        preparer.prepare(mockDataSource);

        verify(mockStatement).setCharacterStream(anyInt(), any());
    }

    @Test
    public void testEquals() throws SQLException {
        RecordingDataSource recordingDataSource1 = RecordingDataSource.wrap(mock(DataSource.class, RETURNS_MOCKS));
        RecordingDataSource recordingDataSource2 = RecordingDataSource.wrap(mock(DataSource.class, RETURNS_MOCKS));

        Connection connection1 = recordingDataSource1.getConnection();
        connection1.setAutoCommit(true);
        Statement statement1 = connection1.createStatement();
        statement1.executeUpdate("create table");
        statement1.executeUpdate("insert data");
        statement1.executeUpdate("select data");
        statement1.close();
        connection1.commit();
        connection1.close();

        Connection connection2 = recordingDataSource2.getConnection();
        connection2.setAutoCommit(true);
        Statement statement2 = connection2.createStatement();
        statement2.executeUpdate("create table");
        statement2.executeUpdate("insert data");
        statement2.executeUpdate("select data");
        statement2.close();
        connection2.commit();
        connection2.close();

        DatabasePreparer databasePreparer1 = recordingDataSource1.getPreparer();
        DatabasePreparer databasePreparer2 = recordingDataSource2.getPreparer();

        assertThat(databasePreparer1).isEqualTo(databasePreparer2);
    }

    @Test
    public void testProxyTargetClass() {
        PGSimpleDataSource targetDataSource = new PGSimpleDataSource();
        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        assertThat(recordingDataSource).isInstanceOf(PGSimpleDataSource.class);
    }
}