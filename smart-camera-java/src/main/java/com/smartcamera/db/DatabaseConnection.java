package com.smartcamera.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL
            = "jdbc:mysql://localhost:3306/smart_home_camera";

    private static final String USER
            = "UserName";

    private static final String PASSWORD
            = "User-Pass";

    public static Connection getConnection()
            throws SQLException {

        return DriverManager.getConnection(
                URL, USER, PASSWORD
        );
    }
}
