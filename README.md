# Expense Share Application

Expense Share is a backend application that helps groups of users track shared expenses, calculate balances, and intelligently suggest settlements to minimize transactions. The system is designed using clean architecture principles, modern Java frameworks, and a fully automated CI pipeline.

---

## 🚀 Tech Stack

* **Language**: Java
* **Framework**: Micronaut
* **Build Tool**: Gradle
* **Database**: H2 (In-memory)
* **Messaging**: Apache Kafka
* **DB Migration**: Flyway
* **API Documentation**: OpenAPI (Swagger)
* **Testing**: JUnit, Mockito
* **Code Coverage**: JaCoCo
* **Static Code Analysis**: SonarQube
* **Containerization**: Docker
* **CI Pipeline**: Jenkins
* **Version Control**: Git & GitHub

---

## 🧩 Key Features

### 👤 User Management

* Create users
* Fetch user details by ID

### 👥 Group Management

* Create expense-sharing groups
* Add members to groups
* View group details
* Retrieve member balances at a specific point in time

### 💸 Expense Management

* Add expenses to a group
* Automatically update balances for group members

### 🤝 Settlement Management

* Create settlements between users
* Confirm or cancel settlements
* Track settlement status (PENDING, CONFIRMED, CANCELLED)

### 💡 Settlement Suggestions

* Suggest optimal ways to settle expenses
* Supports multiple strategies (Strategy Pattern)
* Default strategy minimizes number of transactions

### 📣 Event-Driven Architecture

* Kafka events published on group creation
* Enables asynchronous processing and extensibility

---

## 🏗️ Design Patterns Used

* **Facade Pattern**
  Simplifies interactions between controllers and complex business logic layers.

* **Strategy Pattern**
  Used to implement multiple settlement algorithms that can be selected dynamically.

---

## 📘 API Documentation

The application exposes RESTful APIs documented using **OpenAPI (Swagger)**.

Once the application is running, access Swagger UI at:

```
http://localhost:8082/swagger-ui
```

---

## 🔌 API Overview

### User APIs

* `POST /api/user` – Create a new user
* `GET /api/users/{id}` – Get user by ID

### Group APIs

* `POST /api/groups` – Create a group
* `GET /api/groups/{groupId}` – Get group details
* `POST /api/groups/{groupId}/members` – Add members
* `GET /api/groups/{groupId}/balances` – Get balances
* `GET /api/groups/{groupId}/settlements` – List settlements
* `POST /api/groups/{groupId}/settlements/suggest` – Suggest settlements

### Expense APIs

* `POST /api/expenses` – Add an expense

### Settlement APIs

* `POST /api/settlements` – Create a settlement
* `POST /api/settlements/{id}/confirm` – Confirm settlement
* `POST /api/settlements/{id}/cancel` – Cancel settlement

---

## 🗄️ Database & Migration

* Uses **H2 in-memory database** for fast development and testing
* **Flyway** manages schema migrations automatically on startup

---

## 🧪 Testing & Quality

* **JUnit & Mockito** for unit testing
* **JaCoCo** for test coverage reports
* **SonarQube** for:

  * Code quality checks
  * Vulnerability detection
  * Coverage evaluation

---

## 🐳 Docker Support

The application is fully containerized.

### Build Docker Image

```bash
docker build -t expense-share-app .
```

### Run Docker Container

```bash
docker run -p 8082:8082 expense-share-app
```

---

## 🔁 CI Pipeline (Jenkins)

The CI pipeline automates:

1. Gradle build
2. Unit testing
3. JaCoCo report generation
4. SonarQube analysis
5. Docker image creation
6. Docker image push to Docker Hub

This ensures code quality, security, and reproducible builds.

---

## ▶️ Running the Application Locally

```bash
./gradlew clean build
./gradlew run
```

Application will start on:

```
http://localhost:8082
```

---

## 📦 Future Enhancements

* Persistent database (PostgreSQL/MySQL)
* Authentication & authorization
* Advanced settlement algorithms
* Kubernetes deployment
* Monitoring & alerting

---

## 👨‍💻 Author

Built as a backend-focused project demonstrating:

* Clean architecture
* Event-driven design
* CI/CD best practices
* Production-ready Java microservice development

---

## 📄 License

This project is for educational and demonstration purposes.
