# Railway Reservation System

An automated, cross-platform Railway Reservation System developed in **Java** featuring both a graphical desktop interface (**Java Swing**) and an interactive **Command-Line Interface (CLI)**. The application handles concurrent seat reservations with strict ACID database transactions, pessimistic locking, and dynamic seat allocation.

---

## 🌟 Key Features

- **Dual User Interfaces:**
  - **Desktop GUI:** Built with Java Swing, featuring an asynchronous 4-thread pool for smooth, non-blocking UI interactions.
  - **Terminal CLI:** Interactive menu-driven console application.
- **Train Search & Browsing:** Filter routes by source and destination stations, inspect live seat availability, arrival/departure schedules, and fares.
- **ACID Transaction Management:** Uses row-level pessimistic locking (`SELECT ... FOR UPDATE`) with explicit transaction boundaries (`commit` & `rollback`) to prevent race conditions and overbooking.
- **Dynamic Seat Allocation:** Allocates the lowest available seat number for a specific train and journey date.
- **Unique PNR Engine:** Generates collision-free 11-character uppercase PNRs.
- **PNR Status Inquiry:** Real-time lookup of ticket status, passenger info, seat number, and journey details.
- **Ticket Cancellation & Refunds:** Safely cancels confirmed bookings, restores seat inventory, and calculates refund amounts.
- **Zero-Setup Standalone Mode:** Ships with bundled embedded database support (`h2.jar`) for instant local execution without installing an external database server, while maintaining full support for enterprise MySQL servers.

---

## 🏗️ Architecture & Tech Stack

- **Language:** Java (JDK 17+, compatible with Java 25)
- **GUI Toolkit:** Java Swing & AWT
- **Database Engine:** MySQL / Embedded H2 (MySQL Mode)
- **Database Driver:** JDBC (`mysql-connector-j.jar` & `h2.jar`)
- **Concurrency:** Java `ExecutorService` (Fixed Thread Pool)

---

## 📁 Project Structure

```
Railway-Reservation/
├── RailwayReservationGUI.java    # Desktop Swing graphical interface
├── RailwayReservationSystem.java # Terminal-based command-line interface
├── schema.sql                    # Database initialization script and sample routes
├── mysql-connector-j.jar         # MySQL JDBC driver runtime
├── h2.jar                        # Embedded database runtime (zero-setup mode)
├── .gitignore                    # Git ignore file for bytecode & local data
└── README.md                     # Project documentation
```

---

## 🚀 Quick Start (Zero Setup Required)

The project includes an embedded database engine, allowing you to run it immediately without installing MySQL.

### 1. Compile the Project

**macOS / Linux:**
```bash
javac -cp "h2.jar:mysql-connector-j.jar" RailwayReservationSystem.java RailwayReservationGUI.java
```

**Windows Command Prompt:**
```cmd
javac -cp "h2.jar;mysql-connector-j.jar" RailwayReservationSystem.java RailwayReservationGUI.java
```

---

### 2. Run the Application

#### Option A: Desktop GUI (Recommended)

**macOS / Linux:**
```bash
java -cp ".:h2.jar:mysql-connector-j.jar" RailwayReservationGUI
```

**Windows:**
```cmd
java -cp ".;h2.jar;mysql-connector-j.jar" RailwayReservationGUI
```

#### Option B: Terminal CLI

**macOS / Linux:**
```bash
java -cp ".:h2.jar:mysql-connector-j.jar" RailwayReservationSystem
```

**Windows:**
```cmd
java -cp ".;h2.jar;mysql-connector-j.jar" RailwayReservationSystem
```

---

## 🗄️ Using an External MySQL Server (Optional)

If you prefer to connect to an external MySQL server:

1. **Import the Database Schema:**
   ```bash
   mysql -u root -p < schema.sql
   ```

2. **Run with MySQL Credentials:**
   Pass your MySQL connection URL, user, and password via Java system properties:
   ```bash
   java -Ddb.url="jdbc:mysql://localhost:3306/railway_simple" \
        -Ddb.user="root" \
        -Ddb.password="your_password" \
        -cp ".:h2.jar:mysql-connector-j.jar" RailwayReservationGUI
   ```

---

## 📊 Database Schema

```
+------------------------+           +------------------------------+
|         trains         |           |           bookings           |
+------------------------+           +------------------------------+
| train_id (PK)          |<---------1| booking_id (PK)              |
| train_number (Unique)  |           | pnr (Unique)                 |
| train_name             |           | train_id (FK)                |
| source_station         |           | journey_date                 |
| destination_station    |           | passenger_name               |
| departure_time         |           | age                          |
| arrival_time           |           | gender                       |
| fare                   |           | seat_number                  |
| total_seats            |           | total_fare                   |
| available_seats        |           | status (CONFIRMED/CANCELLED) |
+------------------------+           | booking_time                 |
                                     +------------------------------+
```

---

## 📄 License
This project is open-source and available for educational and academic use.
