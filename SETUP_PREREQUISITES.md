# SL Talk AI - Setup Prerequisites & Installation Guide

## Overview
This document lists everything your manager needs to install and configure on their system to run the SL Talk AI POC locally after cloning the branch.

---

## ✅ System Requirements Checklist

### 1. **Java Development Kit (JDK)**
- **Required Version**: Java 17 or later
- **Why**: Spring Boot 3.2 requires Java 17 as the minimum version
- **Installation**:
  - Download from: https://www.oracle.com/java/technologies/downloads/#java17 (Oracle JDK)
  - Or: https://adoptium.net/temurin/releases/?version=17 (Eclipse Temurin - recommended for free tier)
  - Or: https://openjdk.org/ (OpenJDK)
- **Verify Installation**:
  ```bash
  java -version
  # Should show: java 17.x.x (or higher)
  ```
- **Environment Variable**: Make sure `JAVA_HOME` is set to your JDK installation path
  - Windows: `set JAVA_HOME=C:\Program Files\Java\jdk-17.x.x`

---

### 2. **Maven**
- **Required Version**: Maven 3.8.0 or later (3.9+ recommended)
- **Why**: Build tool for compiling, testing, and running the Spring Boot application
- **Installation**:
  - Download from: https://maven.apache.org/download.cgi
  - Unzip to a folder (e.g., `C:\maven` or `/opt/maven`)
  - Add to PATH environment variable
- **Verify Installation**:
  ```bash
  mvn -version
  # Should show Maven version and Java version
  ```
- **Environment Variable**: Optionally set `M2_HOME` for consistency

---

### 3. **PostgreSQL (Neon Cloud)**
The project uses **Neon PostgreSQL** (managed cloud database). You have two options:

#### **Option A: Use the Same Cloud Database (Recommended for Demo)**
- **What**: The existing Neon database already has the schema and sample data
- **Cost**: Free tier available at https://neon.tech
- **Setup**: 
  - Use credentials from the `.env` file or get them from your manager
  - Database connection string is configured in `application.properties` and `.env`
  - No local installation required

#### **Option B: Set Up Local PostgreSQL (For Development/Testing)**
- **Installation**:
  - Download from: https://www.postgresql.org/download/
  - Install PostgreSQL 14 or later
  - During installation, set a password for the `postgres` user (remember this!)
  - Verify: `psql --version`
- **Database Setup**:
  - Create a new database:
    ```bash
    psql -U postgres -c "CREATE DATABASE datatalk_db;"
    ```
  - Run the schema scripts in order (see **Database Setup** section below)
  - Update `.env` with local connection details:
    ```properties
    DB_URL=jdbc:postgresql://localhost:5432/datatalk_db?sslmode=disable
    DB_USER=postgres
    DB_PASSWORD=<your-postgres-password>
    ```

**For this POC demo, Option A (cloud) is recommended to avoid local database setup.**

---

### 4. **AI Provider API Key (At Least One)**
The application requires at least ONE AI provider API key to generate SQL from natural language questions.

#### **Primary Provider Options** (choose one):

**A. Groq (Free tier, Recommended)** ⭐
- **Cost**: Free (limits: 30 req/min, 8K tokens/min)
- **Setup**:
  1. Go to: https://console.groq.com
  2. Sign up with email
  3. Create a new API key
  4. Add to `.env`:
     ```properties
     AI_PROVIDER=groq
     GROQ_API_KEY=<your-groq-api-key>
     ```
- **Model**: Uses `openai/gpt-oss-20b` by default

**B. Google Gemini**
- **Cost**: Free tier available
- **Setup**:
  1. Go to: https://ai.google.dev/
  2. Sign in with Google account
  3. Create API key in Google AI Studio
  4. Add to `.env`:
     ```properties
     AI_PROVIDER=gemini
     GEMINI_API_KEY=<your-gemini-api-key>
     ```

**C. NVIDIA API Catalog** ⚡
- **Cost**: Free tier available (requires NVIDIA account)
- **Setup**:
  1. Go to: https://build.nvidia.com/
  2. Create NVIDIA account
  3. Generate API key from API catalog
  4. Add to `.env`:
     ```properties
     AI_PROVIDER=nvidia
     NVIDIA_API_KEY=<your-nvidia-api-key>
     ```

**D. OpenAI (Paid)**
- **Cost**: Pay-as-you-go
- **Setup**:
  1. Go to: https://platform.openai.com/
  2. Create account and add payment method
  3. Generate API key
  4. Add to `.env`:
     ```properties
     AI_PROVIDER=openai
     OPENAI_API_KEY=<your-openai-api-key>
     OPENAI_MODEL=gpt-4o-mini
     ```

**Recommended**: Start with **Groq (free tier)** as the primary provider.

---

### 5. **ngrok (Optional - For Public URL)**
Only needed if your manager wants to access the app via a public HTTPS URL for demos/presentations.

- **Cost**: Free tier available at https://ngrok.com
- **Installation**:
  1. Download from: https://ngrok.com/download
  2. Sign up at: https://dashboard.ngrok.com
  3. Install ngrok: `ngrok config add-authtoken <your-token>`
  4. Set in `.env`:
     ```properties
     NGROK_ENABLED=true
     NGROK_DOMAIN=<optional-fixed-domain>
     ```
- **If not needed**: Set `NGROK_ENABLED=false` in `.env` to disable

---

### 6. **Git (For Cloning & Version Control)**
- **Installation**: https://git-scm.com/download/
- **Verify**: `git --version`

---

## 📋 Summary Table

| Requirement | Version | Mandatory? | Why |
|---|---|---|---|
| **Java JDK** | 17+ | ✅ YES | Spring Boot 3.2 requirement |
| **Maven** | 3.8+ | ✅ YES | Build and run the application |
| **PostgreSQL** | 14+ | ⚠️ OPTIONAL* | Only if using local DB (cloud Neon recommended) |
| **AI API Key** | - | ✅ YES | To generate SQL from questions (Groq free tier recommended) |
| **ngrok** | - | ⚠️ OPTIONAL | Only if public URL needed for demos |
| **Git** | 2.0+ | ✅ YES | To clone the repository |

*If using the managed Neon cloud database (recommended), PostgreSQL local installation is not needed.

---

## 🚀 Quick Setup Steps

Once your manager has installed the prerequisites, here's what they need to do:

### **Step 1: Clone the Repository**
```bash
git clone https://github.com/BhanuupriyaSekar-A/DataTalk-AI.git
cd DataTalk-AI---POC
```

### **Step 2: Create `.env` File**
Create a `.env` file in the project root (or update the existing one) with:

```properties
# Database (use the Neon credentials provided)
DB_URL=jdbc:postgresql://ep-lingering-brook-b3tgnze7-pooler.c-4.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require
DB_USER=user_datatalk
DB_PASSWORD=<ask-your-manager-for-the-password>

# AI Provider (Groq recommended)
AI_PROVIDER=groq
AI_FALLBACK_PROVIDERS=gemini,nvidia
GROQ_API_KEY=<your-groq-api-key>

# Optional: Disable ngrok if not needed
NGROK_ENABLED=false
```

**⚠️ IMPORTANT**: 
- Never commit `.env` to Git (it's already in `.gitignore`)
- Keep API keys and passwords private
- Each developer needs their own `.env` file

### **Step 3: Verify Java & Maven**
```bash
java -version
mvn -version
```

### **Step 4: Build the Application**
```bash
mvn clean install
# This will download dependencies and compile the code
```

### **Step 5: Run the Application**
```bash
mvn spring-boot:run
# Or from IntelliJ IDEA: Run → Run 'DataTalkAiApplication'
```

### **Step 6: Access the Application**
- Open browser to: **http://localhost:8081/sltalk/login**
- Use demo credentials (see [docs/DEMO_USERS_GUIDE.md](docs/DEMO_USERS_GUIDE.md))

---

## 🔐 Demo User Credentials

All demo users share the same password:
- **Password**: `DemoPass@123` (BCrypt hashed in database)

| Username | Role | Description |
|----------|------|---|
| `admin_user` | ADMIN | Full system access |
| `techpay_manager` | BUSINESS | TechPay Solutions manager |
| `retail_manager` | MANAGER | RetailHub Inc manager |
| `techpay_customer` | CUSTOMER | TechPay Solutions customer view |
| `restaurant_owner` | BUSINESS | Restaurant Group manager |
| `gas_operator` | CUSTOMER | Gas Station Network customer view |

---

## 🛠️ Troubleshooting

### **"No AI provider configured"**
- Ensure at least one AI provider API key is set in `.env` or environment variables
- Verify the key is not empty: `echo %GROQ_API_KEY%` (Windows) or `echo $GROQ_API_KEY` (Linux/Mac)

### **Port 8081 Already in Use**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8091
```

### **Database Connection Failed**
- Verify `DB_URL`, `DB_USER`, and `DB_PASSWORD` in `.env`
- Check internet connection (Neon is cloud-based)
- Verify IP whitelisting in Neon console if applicable

### **Maven Build Fails**
- Run: `mvn clean` to remove old artifacts
- Run: `mvn update-snapshots`
- Ensure Java 17+ is installed: `java -version`

### **AI Responses Failing**
- Check API key validity in Neon console or provider dashboard
- Verify API rate limits (Groq free tier: 30 req/min)
- Check logs: `tail -f logs/sltalk-ai.log`

---

## 📚 Additional Documentation

For more details, see the documentation in the `docs/` folder:
- [docs/POC_IMPLEMENTATION_DETAILS.md](docs/POC_IMPLEMENTATION_DETAILS.md) - Full architecture and setup
- [docs/DEMO_USERS_GUIDE.md](docs/DEMO_USERS_GUIDE.md) - Demo user passwords and reset procedure
- [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md) - Database schema and structure
- [docs/POC_PROGRESS.md](docs/POC_PROGRESS.md) - Current status and next steps

---

## ✨ What Works Out-of-the-Box

Once your manager completes the setup above, they can immediately:
- ✅ Login with demo credentials
- ✅ Ask business questions in natural English
- ✅ Get AI-generated SQL queries
- ✅ View results as tables and charts
- ✅ Export reports to PDF and Excel
- ✅ Switch between multiple AI providers automatically if one fails
- ✅ Access 11 predefined business reports

---

## 🎯 Minimum Setup Time

- **Prerequisites installed**: ~30 minutes (Java, Maven, Groq API key)
- **Clone & build**: ~5 minutes
- **First run**: Immediate
- **Total**: ~35-40 minutes

---

**Last Updated**: November 17, 2026
