# 🗳️ MultiExport - Election Data Export System

MultiExport is a powerful system for exporting election data in multiple formats such as **PDF, Excel, and CSV**, using **Spring Boot**. The project aims to provide a seamless and flexible way to extract election data and generate shareable reports.

---

## 📂 Directory Structure

```
muhamedsaad112-multiexport/
├── mvnw
├── mvnw.cmd
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── election/
│   │   │           ├── ElectionApplication.java
│   │   │           ├── controller/
│   │   │           │   ├── ElectionControllerPdf.java
│   │   │           │   ├── ElectionExportController.java
│   │   │           │   ├── ExportController.java
│   │   │           │   └── PPdfController.java
│   │   │           ├── service/
│   │   │           │   ├── ElectionExportService.java
│   │   │           │   ├── ElectionServicePdf.java
│   │   │           │   ├── ExportService.java
│   │   │           │   ├── PdfService.java
│   │   │           │   └── ElectionServicePdf.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── templates/
│   │           ├── ElectionReport.html
│   │           └── electionReport1.html
│   └── test/
│       └── java/
│           └── com/
│               └── election/
│                   └── ElectionApplicationTests.java
└── .mvn/
    └── wrapper/
        └── maven-wrapper.properties
```

---

## 🚀 Features
✅ Export election data to **PDF, Excel, and CSV** formats  
✅ Fully functional **RESTful API** for automated report generation  
✅ Support for **HTML templates** to create custom PDF reports  
✅ Flexible service to handle data extraction efficiently  
✅ Built with **Spring Boot** and supports seamless integration with other systems  

---

## 🔧 Tech Stack
- **Java 17+**
- **Spring Boot 3+**
- **Spring MVC**
- **Apache POI** (for Excel export)
- **iText PDF** (for PDF generation)
- **Lombok** (to simplify code)
- **Jackson Databind** (for JSON processing)
- **OpenCSV** (for CSV processing)
- **JFreeChart** (for chart generation)
- **Thymeleaf** (for dynamic report templates)
- **Maven** (for dependency management)

---

## ⚙️ Setup & Run

### 📌 Prerequisites
Ensure the following are installed on your system:
- **JDK 17** or later [🔗 Download](https://adoptopenjdk.net/)
- **Maven** [🔗 Download](https://maven.apache.org/download.cgi)
- **Git** (for version control) [🔗 Download](https://git-scm.com/downloads)

### 📌 Clone the Repository
Open **Terminal** or **Command Prompt** and run:

```sh
git clone https://github.com/MuhamedSaad112/MultiExport.git
cd MultiExport
```

### 📌 Run the Project
Run the application using:

```sh
./mvnw spring-boot:run   # (Linux/Mac)
mvnw.cmd spring-boot:run  # (Windows)
```

Or directly with Java:

```sh
java -jar target/multiexport.jar
```

---

## 📡 RESTful API Endpoints

| Functionality          | HTTP Method | Endpoint            |
|------------------------|------------|---------------------|
| Export Creator Excel  | `POST`      | `/api/export/creator/excel` |
| Export Viewer Excel   | `POST`      | `/api/export/viewer/excel`  |
| Export Creator CSV    | `POST`      | `/api/export/creator/csv`   |
| Export Viewer CSV     | `POST`      | `/api/export/viewer/csv`    |
| Generate Election PDF | `POST`      | `/election/generate-pdf`    |
| Generate Charts PDF   | `POST`      | `/pdf/charts`               |

📌 **Note:** All endpoints accept JSON input.

---

## 🛠 Customization
🔹 **Modify application settings**  
Edit **`application.properties`** to change the default file names and configurations.

🔹 **Add new fields**  
- Update **`ElectionExportService.java`** to include new data fields.
- Modify the **HTML templates in `templates/`** to change the report structure.

---

## 🧪 Testing
To ensure the project runs correctly, execute:

```sh
mvn test
```

---

## 👨‍💻 Contributing
We welcome contributions! To contribute:
1. **Fork** the repository.
2. Create a new branch:  
   ```sh
   git checkout -b my-new-feature
   ```
3. Make changes and commit:
   ```sh
   git commit -m "Add new feature"
   ```
4. Push the branch and open a **Pull Request**.

---

## 📜 License
This project is licensed under the **MIT License** – you are free to use and modify it while maintaining proper attribution.

---

## 📞 Support & Contact
If you encounter any issues or have questions, feel free to open an **Issue** in the repository or contact:
📧 **Email:** [muhamedsaad112@gmail.com](mailto:muhamedsaad112@gmail.com)  
🔗 **GitHub:** [MuhamedSaad112](https://github.com/MuhamedSaad112)

🚀 **Enjoy seamless data exporting!** 🗳️📊

