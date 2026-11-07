
# SpendWise – Smart Expense Tracker

SpendWise is an Android-based smart expense tracking application designed to help users manage their daily income, expenses, and financial activities through a simple, organized, and user-friendly interface.

The application was developed as the Final Lab Project for **Mobile Application and Development Lab (0613-410)**.

## Developer

**Name:** Md. Hasibul Islam  
**Roll No.:** 46

## Project Status

**Core Features Implemented | Final Verification in Progress**

The main functional modules have been implemented and tested on an Android emulator.

Final security verification, cloud data restoration testing, physical-device testing, and release preparation are still in progress.

## Main Features

### Authentication and Account Management

- User registration and login using Firebase Authentication
- Email and password validation
- Forgot password functionality
- Remember Me and persistent login
- User logout
- Editable profile display name
- Profile picture selection using Camera or Gallery

### Financial Management

- Dashboard with total balance, income, and expense summaries
- Add income and expense transactions
- View transaction history
- Edit and delete transactions
- Transaction categorization
- Income and expense breakdown
- Transaction filtering
- Recent transactions and full transaction history
- Bangladeshi Taka (BDT) currency support

### Reports and Analytics

- Financial summary reports
- Monthly income and expense analysis
- Visual charts
- Category-wise financial breakdowns

### Firebase and Local Data

- Firebase Realtime Database integration
- User-specific cloud transaction synchronization
- Local transaction storage
- Pending upload tracking
- Cloud synchronization status
- Retry support for pending uploads
- SharedPreferences for application settings

### Android Features

- AndroidX Splash Screen
- Dark-themed user interface
- Camera and Gallery integration for profile photos
- Accelerometer-based Shake to Add Transaction shortcut
- Implicit Intent for sharing the application
- Phone Dialer integration
- Toast messages and input validation
- Material Design components

## Application Screens

1. Splash Screen / Session Routing
2. Login Screen
3. Registration Screen
4. Forgot Password Screen
5. Dashboard
6. Add / Edit Transaction
7. Transaction History and Details
8. Income and Expense Breakdown
9. Financial Reports
10. Settings and Profile
11. Cloud Sync Status



## Technology Stack

| Component               | Technology                   |
| ----------------------- | ---------------------------- |
| Programming Language    | Java                         |
| User Interface          | XML                          |
| Development Environment | Android Studio               |
| Authentication          | Firebase Authentication      |
| Cloud Database          | Firebase Realtime Database   |
| Local Preferences       | SharedPreferences            |
| Build System            | Gradle Kotlin DSL            |
| Device Sensor           | Accelerometer                |
| Camera / Gallery        | Android Activity Result APIs |
| Version Control         | Git and GitHub               |


## Minimum Requirements

- Android 7.0 (Nougat) or later
- Minimum SDK: API 24
- Android Studio with Android SDK installed
- Internet connection for Firebase cloud operations

## Installation and Setup

### 1. Clone the Repository

```bash
git clone https://github.com/Hasibul-TE/SpendWise-Smart-Expense-Tracker.git
```

### 2. Open in Android Studio

Open the cloned SpendWise project in Android Studio.

### 3. Sync Gradle

Allow Gradle synchronization to complete and install the required dependencies.

### 4. Configure Firebase

Create or configure a Firebase project with the Android application ID:

```text
com.alard.spendwise
```

Enable the following Firebase services:

- Firebase Authentication (Email/Password)
- Firebase Realtime Database

Place the appropriate `google-services.json` file inside the `app` directory if it is not already available.

Ensure the Firebase Realtime Database URL matches the configured Firebase project.

Configure appropriate authentication-based Firebase Database Security Rules.

### 5. Start an Android Device

Start an Android emulator or connect a compatible physical Android device.

### 6. Run the Application

Select the `app` run configuration and run the project from Android Studio.

## Build and Testing

The application has been successfully compiled, tested through the configured Gradle tasks, and installed on an Android emulator.

### Test Environment

- Emulator: SpendWise_API_34
- Android Version: Android 14
- API Level: 34
- Build Variant: Debug

### Gradle Verification Command

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug installDebug
```

The latest reported build completed successfully.

### Tested Functional Areas

- Authentication screen navigation
- Dashboard calculations and financial summaries
- Income and expense transaction management
- Transaction history and filters
- Financial reports and category breakdowns
- Settings and profile functionality
- Profile picture selection
- Display name editing
- Firebase connection status
- Pending cloud upload status
- Debug APK installation

### Remaining Verification

- Full cloud data restoration testing
- Cross-device synchronization verification
- Final Firebase security audit
- Physical Android device testing
- Signed release APK verification

## Development Progress

- [x] Android Studio project initialization
- [x] Git repository initialization
- [x] GitHub integration
- [x] Dark theme and UI design
- [x] Splash screen and session routing
- [x] Login and registration
- [x] Forgot password
- [x] Firebase Authentication
- [x] Remember Me and logout
- [x] Dashboard and financial summaries
- [x] Income and expense transaction CRUD
- [x] Transaction history and filtering
- [x] Financial reports and charts
- [x] Income and expense breakdown
- [x] Firebase Realtime Database integration
- [x] Cloud synchronization status
- [x] Settings and profile management
- [x] Camera and Gallery profile photo
- [x] Editable display name
- [x] SharedPreferences integration
- [x] Accelerometer sensor integration
- [x] Implicit Intent integration
- [x] Initial emulator testing
- [x] Debug APK build
- [x] GitHub source code update
- [ ] Final security audit
- [ ] Cloud restoration and cross-device testing
- [ ] Physical Android device testing
- [ ] Signed release APK

## Data and Privacy

SpendWise uses Firebase Authentication to manage user accounts.

Cloud transactions are organized under individual Firebase user identifiers.

Profile photos are stored locally on the device and are not included in transaction cloud synchronization.

The application supports local transaction storage and pending cloud upload tracking.

A successful upload status does not independently guarantee that data can be restored on another device.

The application is an academic project undergoing final verification and should not be considered production-ready until the remaining security and data recovery tests are completed.

**Security Note:** Never commit Firebase Admin credentials, private signing keys, passwords, or other sensitive information to the repository.

## Repository

**SpendWise – Smart Expense Tracker**

GitHub Repository:

https://github.com/Hasibul-TE/SpendWise-Smart-Expense-Tracker

## Academic Use

This project was developed for academic and educational purposes as part of the **Mobile Application and Development Lab (0613-410)**.

It demonstrates Android application development using Java, XML, Firebase Authentication, Firebase Realtime Database, local data management, Android sensors, and platform components.
