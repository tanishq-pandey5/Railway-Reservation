import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

public class RailwayReservationSystem {
    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;

    static {
        boolean hasH2 = false;
        try {
            Class.forName("org.h2.Driver");
            hasH2 = true;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {}

        String propUrl = System.getProperty("db.url");
        if (propUrl != null) {
            dbUrl = propUrl;
            dbUser = System.getProperty("db.user", "root");
            dbPassword = System.getProperty("db.password", "1234");
        } else if (hasH2) {
            dbUrl = "jdbc:h2:./railway_simple;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
            dbUser = "sa";
            dbPassword = "";
        } else {
            dbUrl = "jdbc:mysql://localhost:3306/railway_simple";
            dbUser = "root";
            dbPassword = "1234";
        }
    }

    private static void initDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            ensureSchema(conn);
        }
    }

    private static void ensureSchema(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1 FROM trains LIMIT 1");
            return;
        } catch (SQLException ignored) {
            // Need to initialize schema
        }

        java.io.File schemaFile = new java.io.File("schema.sql");
        if (!schemaFile.exists()) return;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(schemaFile));
             Statement stmt = conn.createStatement()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.startsWith("/*")) continue;
                if (trimmed.toUpperCase().startsWith("CREATE DATABASE") || trimmed.toUpperCase().startsWith("USE ")) continue;
                sb.append(line).append("\n");
            }
            for (String sql : sb.toString().split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("Schema initialization note: " + e.getMessage());
        }
    }

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("====================================");
        System.out.println("     SIMPLE RAILWAY RESERVATION");
        System.out.println("====================================");

        try {
            initDatabase();
        } catch (Exception e) {
            System.out.println("Database initialization error: " + e.getMessage());
            return;
        }

        while (true) {
            System.out.println("\n1. View trains");
            System.out.println("2. Book ticket");
            System.out.println("3. PNR status");
            System.out.println("4. Cancel ticket");
            System.out.println("5. Exit");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> viewTrains();
                    case "2" -> bookTicket();
                    case "3" -> pnrStatus();
                    case "4" -> cancelTicket();
                    case "5" -> {
                        System.out.println("Thank you. Goodbye.");
                        return;
                    }
                    default -> System.out.println("Please choose a number from 1 to 5.");
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Input error: " + e.getMessage());
            }
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    private static void viewTrains() throws SQLException {
        String sql = "SELECT * FROM trains ORDER BY train_id";

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {

            System.out.println("\nID | Number | Train Name | Route | Departure | Arrival | Fare | Seats");
            System.out.println("--------------------------------------------------------------------------");

            while (result.next()) {
                System.out.printf("%d | %s | %s | %s -> %s | %s | %s | Rs. %.2f | %d/%d%n",
                        result.getInt("train_id"),
                        result.getString("train_number"),
                        result.getString("train_name"),
                        result.getString("source_station"),
                        result.getString("destination_station"),
                        result.getTime("departure_time"),
                        result.getTime("arrival_time"),
                        result.getBigDecimal("fare"),
                        result.getInt("available_seats"),
                        result.getInt("total_seats"));
            }
        }
    }

    private static void bookTicket() throws SQLException {
        viewTrains();

        int trainId = readInt("Enter train ID: ");
        String passengerName = readText("Passenger name: ");
        int age = readInt("Passenger age: ");
        if (age < 1 || age > 120) {
            throw new IllegalArgumentException("Age must be between 1 and 120.");
        }

        String gender = readText("Gender: ").toUpperCase();
        if (!gender.equals("MALE") && !gender.equals("FEMALE") && !gender.equals("OTHER")) {
            throw new IllegalArgumentException("Gender must be MALE, FEMALE, or OTHER.");
        }

        String dateText = readText("Journey date (yyyy-MM-dd): ");
        LocalDate journeyDate = LocalDate.parse(dateText);
        if (journeyDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Journey date cannot be in the past.");
        }

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                int availableSeats;
                int totalSeats;
                java.math.BigDecimal fare;

                String trainSql = "SELECT total_seats, available_seats, fare FROM trains WHERE train_id=? FOR UPDATE";
                try (PreparedStatement statement = connection.prepareStatement(trainSql)) {
                    statement.setInt(1, trainId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Train not found.");
                        }
                        totalSeats = result.getInt("total_seats");
                        availableSeats = result.getInt("available_seats");
                        fare = result.getBigDecimal("fare");
                    }
                }

                if (availableSeats <= 0) {
                    throw new IllegalArgumentException("No seats are available.");
                }

                int seatNumber = findFreeSeat(connection, trainId, journeyDate, totalSeats);
                String pnr = createUniquePnr(connection);

                String updateSql = "UPDATE trains SET available_seats=available_seats-1 WHERE train_id=?";
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    statement.setInt(1, trainId);
                    statement.executeUpdate();
                }

                String bookingSql = "INSERT INTO bookings " +
                        "(pnr, train_id, journey_date, passenger_name, age, gender, seat_number, total_fare) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(bookingSql)) {
                    statement.setString(1, pnr);
                    statement.setInt(2, trainId);
                    statement.setDate(3, java.sql.Date.valueOf(journeyDate));
                    statement.setString(4, passengerName);
                    statement.setInt(5, age);
                    statement.setString(6, gender);
                    statement.setInt(7, seatNumber);
                    statement.setBigDecimal(8, fare);
                    statement.executeUpdate();
                }

                connection.commit();
                System.out.println("\nBooking successful!");
                System.out.println("PNR: " + pnr);
                System.out.println("Seat number: " + seatNumber);
                System.out.println("Total fare: Rs. " + fare);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static int findFreeSeat(Connection connection, int trainId, LocalDate date, int totalSeats)
            throws SQLException {
        Set<Integer> occupiedSeats = new HashSet<>();
        String sql = "SELECT seat_number FROM bookings " +
                "WHERE train_id=? AND journey_date=? AND status='CONFIRMED'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, trainId);
            statement.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    occupiedSeats.add(result.getInt("seat_number"));
                }
            }
        }

        for (int seat = 1; seat <= totalSeats; seat++) {
            if (!occupiedSeats.contains(seat)) {
                return seat;
            }
        }
        throw new IllegalArgumentException("No seat could be allocated.");
    }

    private static String createUniquePnr(Connection connection) throws SQLException {
        while (true) {
            String pnr = "PNR" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            String sql = "SELECT COUNT(*) FROM bookings WHERE pnr=?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, pnr);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    if (result.getInt(1) == 0) {
                        return pnr;
                    }
                }
            }
        }
    }

    private static void pnrStatus() throws SQLException {
        String pnr = readText("Enter PNR: ").toUpperCase();
        String sql = "SELECT b.*, t.train_number, t.train_name, t.source_station, " +
                "t.destination_station FROM bookings b JOIN trains t ON b.train_id=t.train_id WHERE b.pnr=?";

        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pnr);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    System.out.println("Booking not found.");
                    return;
                }

                System.out.println("\nPNR: " + result.getString("pnr"));
                System.out.println("Status: " + result.getString("status"));
                System.out.println("Train: " + result.getString("train_number") + " - " + result.getString("train_name"));
                System.out.println("Route: " + result.getString("source_station") + " -> " + result.getString("destination_station"));
                System.out.println("Journey date: " + result.getDate("journey_date"));
                System.out.println("Passenger: " + result.getString("passenger_name"));
                System.out.println("Seat: " + result.getInt("seat_number"));
                System.out.println("Fare: Rs. " + result.getBigDecimal("total_fare"));
            }
        }
    }

    private static void cancelTicket() throws SQLException {
        String pnr = readText("Enter PNR to cancel: ").toUpperCase();

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);

            try {
                int bookingId;
                int trainId;
                String status;

                String findSql = "SELECT booking_id, train_id, status FROM bookings WHERE pnr=? FOR UPDATE";
                try (PreparedStatement statement = connection.prepareStatement(findSql)) {
                    statement.setString(1, pnr);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("PNR not found.");
                        }
                        bookingId = result.getInt("booking_id");
                        trainId = result.getInt("train_id");
                        status = result.getString("status");
                    }
                }

                if (!status.equals("CONFIRMED")) {
                    throw new IllegalArgumentException("This ticket is already cancelled.");
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE bookings SET status='CANCELLED' WHERE booking_id=?")) {
                    statement.setInt(1, bookingId);
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE trains SET available_seats=available_seats+1 WHERE train_id=?")) {
                    statement.setInt(1, trainId);
                    statement.executeUpdate();
                }

                connection.commit();
                System.out.println("Ticket cancelled successfully. Demonstration refund: 80%.");
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static String readText(String message) {
        System.out.print(message);
        String value = scanner.nextLine().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("This field cannot be empty.");
        }
        return value;
    }

    private static int readInt(String message) {
        try {
            return Integer.parseInt(readText(message));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid whole number.");
        }
    }
}
