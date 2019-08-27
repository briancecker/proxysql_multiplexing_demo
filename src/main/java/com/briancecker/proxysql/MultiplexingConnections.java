package com.briancecker.proxysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MultiplexingConnections {

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    System.out.println("Finished setup");
    final int numParallelThreads = 30;
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
        numParallelThreads,
        30,
        30,
        TimeUnit.SECONDS,
        new LinkedBlockingDeque<>()
    );
    List<Future> futures = new ArrayList<>();
    for (int i = 0; i < numParallelThreads; ++i) {
      final int j = i;
      futures.add(executor.submit(() -> runQueryThread(j)));
    }

    for (Future future : futures) {
      future.get();
    }
  }

  private static boolean runQueryThread(int threadNum) {
    Random rand = new Random();
    Connection connection = getConnection();
    while (true) {
      try {
        PreparedStatement insertStatement = connection.prepareStatement(
            "INSERT INTO some_table (comment) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
        insertStatement.setString(1, "Created from thread " + threadNum);
        insertStatement.executeUpdate();

        ResultSet generatedKeys = insertStatement.getGeneratedKeys();
        if (!generatedKeys.next()) {
          throw new RuntimeException("Failed to retrieve generated keys");
        }
        int id = generatedKeys.getInt(1);

        generatedKeys.close();
        insertStatement.close();

        PreparedStatement selectStatement = connection.prepareStatement(
            "SELECT * from some_table where id = ?");
        selectStatement.setInt(1, id);
        selectStatement.executeQuery();
        selectStatement.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }

      try {
        Thread.sleep((long) (rand.nextDouble() * 200));
      } catch (InterruptedException e) {
        // ignore
      }
    }
  }

  private static Connection getConnection() {
    try {
      return DriverManager.getConnection(
          "jdbc:mysql://127.0.0.1:6033/proxysql_test?user=root&password=root");
    } catch (SQLException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}
