package session;

import config.Configuration;
import config.Properties;
import config.environment.DataSource;
import config.environment.Environment;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Array;
import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class DatabaseConnectionPool {
    private static final int KEEP_CONNECTION_ALIVE_PERIOD = 600000;
    private static final int TERMINATE_CONNECTION_PERIOD = 3600000;
    private static final int MAX_CONNECTIONS_COUNT = 10;
    private static final int MIN_OPEN_CONNECTIONS_COUNT = 2;


    private static DatabaseConnectionPool instance = null;

    static class TerminatingConnection {
        Connection connection;
        Timer terminatingTimer;

        TerminatingConnection(Connection connection, Timer terminatingTimer) {
            this.connection = connection;
            this.terminatingTimer = terminatingTimer;
        }

        void startTerminatingTimer() {
            terminatingTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    throw new IllegalStateException("Connection wasn't released after 1 hour!");
                }
            }, TERMINATE_CONNECTION_PERIOD);
        }

        void stopTerminatingTimer() {
            terminatingTimer.cancel();
        }
    }

    CircularArrayQueue<TerminatingConnection> connections;
    int maxCount;
    int minOpenConnections;

    int openConnections = 0;

    String url;
    String user;
    String password;

    private DatabaseConnectionPool(String url, String user, String password, int maxCount, int minOpenConnections) throws SQLException {
        this.url = url;
        this.user = user;
        this.password = password;
        this.maxCount = maxCount;
        this.minOpenConnections = minOpenConnections;
        connections = new CircularArrayQueue<>(maxCount);

        for (int i = 0; i < minOpenConnections; i++) {
            Timer timer = new Timer();
            Connection connection = DriverManager.getConnection(url, user, password);
            openConnections++;
            TerminatingConnection terminatingConnection = new TerminatingConnection(connection, new Timer());
            connections.add(terminatingConnection);
            alterConnectionStatus(timer, terminatingConnection);
        }
    }

    private void alterConnectionStatus(Timer timer, TerminatingConnection connection) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                String sql = "SELECT 1";
                try {
                    if (connection.connection.isClosed()) {
                        timer.cancel();
                        return;
                    }

                    if (connections.elementsCount() > minOpenConnections) {
                        boolean removed = connections.remove(connection);
                        if (!removed)
                            return;

                        connection.connection.close();
                        timer.cancel();
                        openConnections--;
                        return;
                    }

                    Statement statement = connection.connection.createStatement();
                    statement.execute(sql);
                } catch (SQLException ignored) {}
            }
        }, KEEP_CONNECTION_ALIVE_PERIOD, KEEP_CONNECTION_ALIVE_PERIOD);
    }

    public static DatabaseConnectionPool getConnectionPool() throws Exception {
        return instance;
    }

    public static void setDataSource(DataSource ds) throws Exception {
        if (instance == null) {
            Class.forName(ds.activeProperties.get("driver"));
            String url = ds.activeProperties.get("url");
            String user = ds.activeProperties.get("username");
            String password = ds.activeProperties.get("password");


            instance = new DatabaseConnectionPool(url, user, password, MAX_CONNECTIONS_COUNT, MIN_OPEN_CONNECTIONS_COUNT);
        }
    }

    public Connection getConnection() throws SQLException {
        if (connections.isEmpty())
            if (openConnections == maxCount) {
                throw new NoSuchElementException("There is no available connection at the moment!");
            }
            else {
                openConnections++;
                Connection connection = DriverManager.getConnection(url, user, password);

                TerminatingConnection terminatingConnection = new TerminatingConnection(connection, new Timer());
                Timer timer = new Timer();
                alterConnectionStatus(timer, terminatingConnection);

                terminatingConnection.startTerminatingTimer();
                connections.add(terminatingConnection);
            }
        TerminatingConnection connToReturn = connections.poll();
        connToReturn.startTerminatingTimer();
        return connToReturn.connection;
    }

    public boolean releaseConnection(Connection connection) throws SQLException {
        Timer timer = new Timer();
        TerminatingConnection conn = new TerminatingConnection(connection, timer);

        if (conn.connection.isClosed() && openConnections < minOpenConnections) {
            Connection connForTerminatingConnection = DriverManager.getConnection(url, user, password);
            conn = new TerminatingConnection(connForTerminatingConnection, new Timer());
            alterConnectionStatus(timer, conn);
        }

        conn.stopTerminatingTimer();
        connections.add(conn);
        return true;
    }
}

class CircularArrayQueue<E> implements Queue<E> {

    int head;
    int tail;
    int size;
    Object[] buffer;

    CircularArrayQueue(int size) {
        head = 0;
        tail = 0;
        this.size = size;
        buffer = new Object[size];
    }

    public boolean isFull() {
        return (head == 0 && tail == size - 1) || head - 1 == tail;
    }

    public void ensureCapcity(int minCapacity) {
        size = minCapacity;
        Object[] newArr = new Object[minCapacity];

        for (int iterations = 0, index = head; iterations < size; iterations++, index++) {
            if (index == size) {
                index = 0;
            }

            if ((index > tail && index < head) || index > buffer.length - 1)
                newArr[index] = null;
            else
                newArr[index] = buffer[index];
        }

        buffer = newArr;
    }

    @Override
    public int size() {
        return size;
    }

    public int elementsCount() {
        return head > tail ? head - tail : tail - head;
    }

    @Override
    public boolean isEmpty() {
        return head == -1 || tail == -1 || head == tail;
    }

    @Override
    public boolean contains(Object o) {
        for (Object e : buffer) {
            if (e.equals(o))
                return true;
        }

        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            int index = head;

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public E next() {
                return (E) buffer[index];
            }
        };
    }

    @Override
    public Object[] toArray() {
        int length = Math.max(size - (tail - head), tail - head);
        Object[] result = new Object[length];

        for (int i = 0, bufferI = head; i < length; i++, bufferI++) {
            if (bufferI == size) {
                bufferI = 0;
            }

            result[i] = buffer[bufferI];
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a == null)
            throw new InvalidParameterException("Parameters cannot be null!");

        int length = Math.max(size - (tail - head), tail - head);

        if (a.length < length) {
            Class clazz = a.getClass().getComponentType();
            a = (T[]) Array.newInstance(clazz, length);
        }

        Object[] newArr = this.toArray();

        for (int i = 0; i < length; i++) {
            a[i] = (T) newArr[i];
        }

        return a;
    }

    @Override
    public boolean add(E e) {
        if (isFull())
            ensureCapcity(size + 1);

        if (tail == size) {
            tail = 0;
            buffer[0] = e;
        } else {
            buffer[tail] = e;
        }
        tail++;

        return true;
    }

    public boolean remove(Object o) {
        int index = indexOf(o);

        if (index == -1)
            return false;

        for (int i = index; i < elementsCount(); i++) {
            int cirIndex = linearIndexToCircular(i);
            int nextCirIndex = linearIndexToCircular(i + 1);
            buffer[cirIndex] = buffer[nextCirIndex];
        }

        if (head == size - 1)
            head = 0;
        else
            head++;

        return true;
    }

    public int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (o.equals(buffer[i]))
                return i;
        }

        return -1;
    }

    private int linearIndexToCircular(int index) {
        return linearIndexToCircular(index, head, size);
    }
    private int linearIndexToCircular(int index, int head, int size) {
        return (index + head) % size;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!this.contains(o))
                return false;
        }

        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        for (E o : c) {
            this.add(o);
        }

        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) { // Impossible queue function
        throw new IllegalStateException("Impossible queue operation");
    }

    @Override
    public boolean retainAll(Collection<?> c) { // Impossible queue function
        throw new IllegalStateException("Impossible queue operation");
    }

    @Override
    public void clear() {
        head = 0;
        tail = 0;
    }

    @Override
    public boolean offer(E e) {
        if (isFull())
            ensureCapcity(size + 1);

        if (tail == size - 1) {
            buffer[0] = e;
        } else {
            buffer[tail + 1] = e;
        }
        tail++;

        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E remove() {
        if (isEmpty())
            throw new NoSuchElementException("Queue is empty!");

        Object removed = buffer[head];

        if (head == size - 1)
            head = 0;
        else
            head++;

        return (E) removed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E poll() {
        if (isEmpty())
            return null;

        Object removed = buffer[head];

        if (head == size - 1)
            head = 0;
        else
            head++;

        return (E) removed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E element() {
        if (isEmpty())
            throw new NoSuchElementException("Queue is empty!");

        return (E) buffer[head];
    }

    @Override
    @SuppressWarnings("unchecked")
    public E peek() {
        if (isEmpty())
            return null;

        return (E) buffer[head];
    }
}
