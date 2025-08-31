# 🌐 Faceboard – Social Network Backend

A robust **Spring Boot backend** for a modern social networking platform.  
This service powers features such as authentication, posts, comments, likes, friendships, real-time chat, notifications, stories, and profile management.  
---
## Live Demo: https://faceboard-frontend.vercel.app/
 

## 🚀 Features

- 🔑 **Authentication & Authorization**
  - Secure login & registration with **JWT tokens**  
  - Role-based access control (Spring Security)  
  - Password encryption with **BCrypt**  

- 📝 **Social Networking Core**
  - Posts with images (**Cloudinary integration**)  
  - Likes, comments, and stories  
  - Friend requests & friendship management  

- 💬 **Messaging & Notifications**
  - Real-time chat with **WebSockets (STOMP over SockJS)**  
  - Online user tracking (`ActiveChatTracker`)  
  - Instant notifications (unread indicator & mark-as-read)  

- 👤 **Profile Management**
  - Editable user profiles (bio, profile picture, social links)  
  - Password reset via email (**MailService**)  

- 🔐 **Security**
  - Spring Security with **JWT filters**  
  - Token-based request validation  
  - Global exception handling  

---

## 🛠️ Tech Stack

| Layer             | Technologies |
|-------------------|--------------|
| **Backend**       | Spring Boot (Java 17+) |
| **Security**      | Spring Security, JWT |
| **Database**      | JPA/Hibernate + (MySQL) |
| **ORM Entities**  | User, Post, Comment, Like, Message, Notification, Friendship, Story |
| **Build Tool**    | Maven |
| **Cloud Storage** | Cloudinary (images) |
| **Real-Time**     | WebSockets (STOMP over SockJS) |
| **Mailing**       | JavaMailSender |
| **DTO & Mapping** | DTOs + Mappers (no manual getters/setters) |
| **Validation**    | Bean Validation (Jakarta Validation) |
| **Testing**       | JUnit |

---

## 📂 Project Structure
```
├── api/controller → REST controllers (Auth, Post, Comment, Friendship, Message, Notification, etc.)
├── api/model → JPA Entities (User, Post, Comment, Like, Message, etc.)
├── api/dto → Data Transfer Objects (DTOs)
├── service → Business logic (UserService, PostService, MessageService, etc.)
├── repository → Spring Data JPA repositories
├── configuration → SecurityConfig, WebSocket, PasswordPolicy, etc.
├── util → JwtUtil, AuthHelper, GlobalExceptionHandler, ActiveChatTracker
└── mapper → DTO Mappers (e.g., PostMapper, UserMapper)
```

---

## 🔐 Security Highlights

- **Password Encryption** – all user passwords are hashed using **BCrypt** before being stored.  
- **JWT Authentication** – each request is validated with a JWT token in the `Authorization` header.  
- **WebSocket Security** – JWT validation integrated with STOMP channels (`JwtChannelInterceptor`).  
- **CORS Configuration** – secure cross-origin requests via `WebConfig`.  

---

## ⚡ Getting Started

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/kbackend.git
   cd kbackend
## Configure application.properties
```
spring.datasource.url=jdbc:postgresql://localhost:5432/socialdb
spring.datasource.username=your_db_user
spring.datasource.password=your_db_password

cloudinary.api_key=xxxx
cloudinary.api_secret=xxxx
cloudinary.cloud_name=xxxx

jwt.secret=yourSuperSecretKey
```
## Run the application
```
mvn spring-boot:run
```
## ✅ Future Improvements
- 🔍 Add search functionality (users, posts, hashtags)
- 🛡️ Admin panel & moderation tools
- 📊 GraphQL endpoints for flexible queries
- ✅ More unit & integration tests
