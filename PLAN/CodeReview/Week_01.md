# 🔍 Code Review - Week 01

> **기간**: 2026-09-25 ~ 2026-10-01  
> **프로젝트**: UniClass (Java / Spring Boot 기반)

---

## 📅 2026-09-26 (토) - 1차 스프링 부트 뼈대(Scaffolding) 코드 리뷰

---

### 📁 1. `build.gradle` — Java 25 & Gradle 9.7.1 툴체인 및 의존성 구성

```groovy
plugins {
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'
}
java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.thymeleaf.extras:thymeleaf-extras-springsecurity6'
    compileOnly 'org.projectlombok:lombok'
    runtimeOnly 'com.h2database:h2'
    annotationProcessor 'org.projectlombok:lombok'
}
```

#### 분석
* Java 25 툴체인과 최신 Gradle 9.7.1 Wrapper가 정확히 매핑되어 있음.
* 핵심 스타터 7종이 빠짐없이 등록되어 있으며 Lombok과 H2도 각각 올바른 scope에 선언됨.

#### ✅ 잘된 점
* `thymeleaf-extras-springsecurity6` 선제 탑재 → 추후 로그인 여부에 따른 UI 분기(`sec:authorize="isAuthenticated()"`)를 별도 수정 없이 즉시 사용 가능.
* Lombok을 `compileOnly + annotationProcessor` 조합으로 올바르게 선언하여 런타임 classpath를 오염시키지 않음 (모범 사례).

#### ⚠️ 주의 및 개선점
* 운영 배포 시 `runtimeOnly 'org.postgresql:postgresql'` 추가 필요.
* `spring-boot-h2console`이 별도 선언되어 있으나, Spring Boot 최신 버전에서는 H2 의존성에 콘솔이 이미 포함되어 있으므로 중복 선언 여부 확인 권장.

---

### 📁 2. `application.yml` — H2 DB, 포트 8080, JPA 설정, 20MB 파일 업로드 제한

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:h2:mem:uniclassdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
  h2.console.enabled: true
  jpa:
    hibernate.ddl-auto: update
    show-sql: true
  servlet.multipart:
    max-file-size: 20MB
    max-request-size: 25MB
```

#### 분석
* H2 인메모리 DB를 `DB_CLOSE_DELAY=-1`로 설정하여 커넥션이 끊겨도 데이터가 유지되도록 함 — 단순 `mem:` 보다 안정적인 선택.

#### ✅ 잘된 점
* **업로드 제한 선제 확장**: 기본값(1MB)을 20MB로 처음부터 확장. 첫 강의 슬라이드 업로드 테스트에서 발생하는 에러를 사전 방지.
* `max-request-size: 25MB`를 `max-file-size: 20MB`보다 5MB 여유 있게 설정 → 멀티파트 메타데이터 오버헤드까지 고려한 올바른 방식.

#### ⚠️ 주의 및 개선점
* **`ddl-auto: update` 주의**: 운영 DB에서 스키마 불일치(drift)가 발생할 수 있음. PostgreSQL 전환 시 반드시 `validate`로 변경하고 Flyway 마이그레이션 도입 필요.
* **인메모리 DB 휘발성**: 서버 재시작 시 데이터 전체 초기화. 초기 테스트 이후 `jdbc:h2:file:./data/uniclassdb`로 변경 권장.

---

### 📁 3. `UniclassApplication.java` — 스프링 부트 메인 실행 클래스

```java
@SpringBootApplication
public class UniclassApplication {
    public static void main(String[] args) {
        SpringApplication.run(UniclassApplication.class, args);
    }
}
```

#### 분석
* `@SpringBootApplication` 하나로 `@Configuration`, `@EnableAutoConfiguration`, `@ComponentScan`을 포함하는 표준 엔트리포인트.

#### ✅ 잘된 점
* 군더더기 없는 가장 이상적인 형태. 보일러플레이트가 전혀 없어 유지보수 비용 최소화.

#### ⚠️ 주의 및 개선점
* 향후 엔티티 클래스가 반드시 `com.uniclass` 패키지 하위에 위치해야 함. 다른 최상위 패키지에 있으면 `@ComponentScan`에서 누락되어 JPA가 테이블을 인식하지 못하는 오류 발생.

---

### 📁 4. `SecurityConfig.java` — Spring Security 필터 체인 및 암호화 빈 설정

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/**", "/h2-console/**", ...).permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

#### 분석
* Spring Security 6.x의 **람다 DSL** 방식으로 구성 (최신 권장 방식). 각 설정이 블록으로 분리되어 가독성이 뛰어남.

#### ✅ 잘된 점
* H2 콘솔의 2가지 흔한 에러를 동시에 해결: ① `/h2-console/**` CSRF 예외 처리, ② `sameOrigin()`으로 iframe 렌더링 차단 해제.
* `BCryptPasswordEncoder` 빈을 여기서 관리하여 `UserService` 등에서 의존성 주입으로 바로 사용 가능.

#### ⚠️ 주의 및 개선점
* **다음 단계 반드시 수정**: 회원가입/로그인 기능 추가 시 아래와 같이 경로별 인가 규칙 세분화 필요:
  ```java
  .requestMatchers("/", "/login", "/register", "/css/**").permitAll()
  .requestMatchers("/class/**", "/assignment/**").authenticated()
  .requestMatchers("/admin/**").hasRole("INSTRUCTOR")
  ```
* `/**` 전체 `permitAll`은 개발 단계에서만 사용해야 하며, 운영 코드에 이대로 남으면 보안 취약점.

---

### 📁 5. `HomeController.java` — 메인 대시보드 매핑 컨트롤러

```java
@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("appName", "UniClass");
        model.addAttribute("currentSemester", "2026-1학기");
        return "index";
    }
}
```

#### 분석
* GET `/` 요청 시 `index.html`을 렌더링하는 표준 Spring MVC 패턴.

#### ✅ 잘된 점
* 로직이 전혀 없는 깔끔한 구조. Controller는 뷰 이름과 모델 데이터만 책임지는 역할 분리 원칙을 완벽하게 따름.

#### ⚠️ 주의 및 개선점
* `"2026-1학기"`가 **하드코딩** → 실제 기능 개발 시 `application.yml` 커스텀 속성 또는 DB에서 현재 학기 정보를 읽어오도록 개선 필요.
* 로그인 기능 추가 후, 이미 인증된 사용자가 `/`에 접근했을 때 대시보드로 자동 리다이렉트하는 분기 로직 추가 필요.

---

### 📁 6. `layout/default.html` — Thymeleaf + Tailwind CSS 반응형 공통 레이아웃

```html
<html th:fragment="layout(content)">
<head>
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-slate-50 min-h-screen flex flex-col">
    <header class="bg-white sticky top-0 z-50 shadow-sm">...</header>
    <main>
        <th:block th:replace="${content}" />
    </main>
    <footer>...</footer>
</body>
```

#### 분석
* Thymeleaf 레이아웃 프래그먼트 패턴 활용. 각 페이지는 `th:replace="~{layout/default :: layout(~{::content})}"` 한 줄로 레이아웃 상속.

#### ✅ 잘된 점
* **레이아웃 재사용성**: 앞으로 만들 수십 개의 페이지에서 헤더/푸터를 한 번도 다시 작성할 필요 없음.
* **반응형 대응**: `hidden md:flex`, `sm:px-6 lg:px-8` 등 Tailwind 반응형 접두사를 적절히 활용하여 모바일 + PC 환경 동시 지원.
* `sticky top-0 z-50` 적용으로 스크롤해도 네비게이션이 화면 상단에 고정되어 사용성이 좋음.

#### ⚠️ 주의 및 개선점
* 네비게이션의 로그인/회원가입 버튼이 `<button type="button">`으로 연결 URL 없이 선언됨 → 다음 단계에서 `<a th:href="@{/login}">`으로 교체 필요.
* **프로덕션 주의**: Tailwind CDN은 전체 클래스셋(~30MB)을 로드하므로 운영 환경에서는 Tailwind Standalone CLI로 사용된 클래스만 추출한 경량 CSS 파일로 전환 권장.

---

### 📁 7. `index.html` — 메인 랜딩 뷰 (기능 카드, 수업 목록 미리보기)

```html
<html th:replace="~{layout/default :: layout(~{::content})}">
<div th:fragment="content" class="space-y-8">
    <!-- 히어로 배너 섹션 -->
    <!-- 핵심 기능 3개 카드 (주차별 자료실, 스피드 그레이더, 4자리 출석) -->
    <!-- 내 수업 목록 미리보기 -->
</div>
```

#### 분석
* 레이아웃 상속으로 전체 화면을 헤더 없이 콘텐츠 영역만 작성. 히어로 배너, 기능 소개 카드 3종, 수업 목록 3단 섹션 구조.

#### ✅ 잘된 점
* `th:text="'현재 학기: ' + ${currentSemester}"`로 컨트롤러 모델 데이터를 올바르게 바인딩.
* 샘플 수업 카드(CS101A, AI204B)로 DB 연동 전에도 완성된 UI를 시각적으로 검증 가능.
* "새로운 수업 추가하기" 점선 카드로 빈 상태(Empty State) UX를 선제적으로 구성.

#### ⚠️ 주의 및 개선점
* 수업 카드 데이터가 HTML에 **하드코딩** → 다음 단계에서 `th:each`를 사용해 DB에서 조회한 `List<ClassRoom>`을 반복 렌더링하도록 교체 필요.
* 버튼들이 `<button type="button">`으로 선언되어 아직 동작 없음 → 로그인/회원가입 기능 개발 후 `<a>` 태그로 연결 예정.

---

### 📁 8. `UniclassApplicationTests.java` — 컨텍스트 로딩 단위 테스트

```java
@SpringBootTest
class UniclassApplicationTests {
    @Test
    void contextLoads() {
        // 스프링 컨텍스트가 에러 없이 모든 빈을 로드할 수 있는지 검증
    }
}
```

#### 분석
* `@SpringBootTest`로 전체 애플리케이션 컨텍스트를 로드하는 스모크 테스트. 의존성 오류, 빈 순환 참조, yml 파싱 오류를 한 번에 잡아낼 수 있음.

#### ✅ 잘된 점
* 가장 기본적이면서 가장 중요한 테스트. 뼈대 단계에서 반드시 포함해야 하는 필수 테스트.

#### ⚠️ 주의 및 개선점
* 현재 빈 메서드이므로 Phase 1 개발과 함께 다음 테스트들을 순차적으로 추가해야 함:
  ```java
  // 향후 추가 예정 테스트 예시
  @Test void homeControllerReturnsIndex() { ... }       // HomeController 응답 검증
  @Test void userRepositorySavesAndFinds() { ... }      // User 엔티티 CRUD 검증
  @Test void passwordIsEncodedWithBCrypt() { ... }      // BCrypt 암호화 검증
  ```

---

### 📊 종합 점수

| 항목 | 점수 | 코멘트 |
| :--- | :---: | :--- |
| **구조 및 패키지 설계** | ⭐⭐⭐⭐⭐ | `global/config`, `domain/home/controller` 도메인별 분리가 모범적 |
| **빌드 & 의존성 구성** | ⭐⭐⭐⭐⭐ | 필수 라이브러리 선제 탑재, Lombok scope 분리 등 올바른 선언 |
| **보안 설정** | ⭐⭐⭐⭐☆ | 개발 단계에서 올바름, 인증 기능 추가 시 경로 분리 필수 |
| **UI/UX (템플릿)** | ⭐⭐⭐⭐⭐ | 레이아웃 재사용 구조, 반응형, 직관적인 수업 카드 UX |
| **테스트 커버리지** | ⭐⭐☆☆☆ | 현재 빈 테스트만 존재, 기능 개발과 병행하여 추가 필요 |
| **종합** | **4.4 / 5.0** | 뼈대 단계 완성도 우수. 다음 단계 인증 구현 준비 완료 |

> **다음 코드 리뷰 예정**: Phase 1 - 2단계 `User` 엔티티, `UserRepository`, `UserService`, 회원가입 폼(`/register`) 구현 코드 리뷰

---

## 📅 2026-09-26 (토) - 2차 리뷰: 1차 리뷰 반영 후 변경 코드 재검토

> **리뷰 대상**: 1차 리뷰 지적 사항 반영 후 변경된 3개 파일  
> `application.yml` / `HomeController.java` / `layout/default.html`

---

### 📁 1. `application.yml` — 커스텀 프로퍼티 추가 반영

```yaml
# UniClass Custom Configuration
uniclass:
  current-semester: 2026-1학기
```

#### ✅ 개선 확인 (1차 지적 → 반영 완료)
* 학기명이 자바 코드에서 완전히 제거되고 `application.yml`의 `uniclass.current-semester`로 분리됨.
* 이제 학기가 바뀔 때 자바 코드를 건드리지 않고 yml 파일 한 줄만 수정하면 전체 반영 가능.

#### ⚠️ 추가 발견 사항
* 현재 `uniclass.current-semester`는 **단순 문자열**이라 학기 형식(`2026-2학기`, `2027-1학기`)에 대한 유효성 검증이 없음. 장기적으로는 학기 정보를 DB 테이블로 관리하거나 정규식 검증을 추가하는 것이 더 견고하지만, 현재 단계에서는 이 방식으로 충분함.

---

### 📁 2. `HomeController.java` — `@Value` 주입으로 하드코딩 제거

```java
@Controller
public class HomeController {

    @Value("${uniclass.current-semester:2026-1학기}")
    private String currentSemester;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("appName", "UniClass");
        model.addAttribute("currentSemester", currentSemester);
        return "index";
    }
}
```

#### ✅ 개선 확인 (1차 지적 → 반영 완료)
* `"2026-1학기"` 하드코딩이 `@Value` 주입 방식으로 완전히 교체됨.
* `@Value("${uniclass.current-semester:2026-1학기}")` 형태의 **콜론(`:`) 뒤 기본값 설정**은 yml에 해당 키가 없을 경우 안전하게 fallback하는 좋은 방어적 코딩.

#### ⚠️ 추가 발견 사항 — 미세 개선 권장
* **필드 주입(`@Value` on field) 방식의 한계**: Spring에서는 필드 직접 주입보다 **생성자 주입**을 권장함. 현재는 간단한 문자열이라 큰 문제는 없지만, 향후 Controller에 의존성이 많아질 경우를 위해 아래 패턴으로 리팩토링하는 것이 테스트 친화적이고 더 나은 설계임:
  ```java
  // 권장 패턴: 생성자 주입
  @Controller
  public class HomeController {

      private final String currentSemester;

      public HomeController(@Value("${uniclass.current-semester:2026-1학기}") String currentSemester) {
          this.currentSemester = currentSemester;
      }
      ...
  }
  ```
* 이유: `private final`로 선언하면 불변성이 보장되고, 테스트 코드에서 컨트롤러를 직접 생성해 주입값을 바꿔가며 단위 테스트하기가 훨씬 쉬워짐.

---

### 📁 3. `layout/default.html` — 학기 동적 바인딩 및 링크 교체

```html
<!-- 학기 뱃지: 동적 바인딩 적용 -->
<span th:text="${currentSemester ?: '2026-1학기'}">
    2026-1학기
</span>

<!-- 인증 버튼: button → a 태그로 교체 -->
<a href="/login" class="...">로그인</a>
<a href="/register" class="...">회원가입</a>
```

#### ✅ 개선 확인 (1차 지적 → 반영 완료)
* 학기 뱃지가 `th:text`로 컨트롤러 모델 값과 바인딩되어 이제 동적으로 렌더링됨.
* 의미 없는 `<button>` → URL을 가진 `<a>` 태그로 교체되어 시맨틱 HTML 원칙을 준수함. 브라우저의 링크 기본 동작(새 탭 열기, URL 미리보기 등)도 이제 정상 작동.

#### ⚠️ 추가 발견 사항
* `th:text="${currentSemester ?: '2026-1학기'}"` 에서 Thymeleaf의 Elvis 연산자(`?:`)를 사용한 것은 올바른 방어 코드. 단, 이미 `HomeController`에서도 `@Value`에 fallback이 설정되어 있어 사실상 null이 될 가능성은 거의 없음 — 이중 방어라 오히려 안전.
* `<a href="/login">`과 `<a href="/register">`는 현재 아직 해당 URL에 컨트롤러가 없어 **404 에러**가 발생함. 이 부분은 다음 단계(회원가입/로그인 구현) 완료 시 해소될 예정이며, 지금 단계에서는 의도된 상태.
* Thymeleaf 방식으로 개선하면 `<a th:href="@{/login}">` 패턴이 더 권장됨 — 컨텍스트 경로(Context Path)가 변경되어도 링크가 자동으로 맞춰지기 때문. **다음 수정 권장 사항으로 등록.**

---

### 📊 2차 리뷰 종합

| 항목 | 1차 리뷰 | 2차 리뷰 (반영 후) |
| :--- | :---: | :---: |
| **학기명 하드코딩 제거** | ⚠️ 지적 | ✅ 반영 완료 |
| **인증 버튼 링크화** | ⚠️ 지적 | ✅ 반영 완료 |
| **HomeController 생성자 주입** | — | ⚠️ 신규 권장 사항 |
| **`th:href="@{/login}"` 패턴 적용** | — | ⚠️ 신규 권장 사항 |

**총평**: 1차 리뷰에서 지적한 2가지 사항이 모두 명확하게 반영되었음. 추가로 발견된 2가지 개선 권장 사항(생성자 주입, `th:href` 패턴)은 다음 단계 회원가입/로그인 개발 시 함께 적용하면 깔끔하게 처리될 예정.

---

## 📅 2026-09-26 (토) - 3차 리뷰: Phase 1 - 2단계 사용자 인증 및 권한 코드 리뷰

> **리뷰 대상**: 새로 생성된 9개 파일 (Auth & User Feature)  
> `Role.java` / `User.java` / `UserRepository.java` / `UserRegisterDto.java` / `UserService.java` / `CustomUserDetails.java` / `SecurityConfig.java` (업데이트) / `AuthController.java` / `login.html` / `register.html`

---

### 📁 1. `Role.java` — 역할 체계 Enum

```java
public enum Role {
    ROLE_STUDENT("학생"),
    ROLE_INSTRUCTOR("교수"),
    ROLE_TA("조교"),
    ROLE_ALUMNI("수료/졸업생");

    private final String description;
    ...
}
```

#### ✅ 잘된 점
* Spring Security의 역할 명명 규칙(`ROLE_` 접두사)을 enum 자체에서 따르고 있어, `SimpleGrantedAuthority(user.getRole().name())`로 변환 시 별도 가공 없이 즉시 사용 가능.
* `description` 필드가 있어 UI에서 `role.getDescription()`으로 "학생", "교수" 등 한국어 라벨을 직접 렌더링할 수 있음.

#### ⚠️ 주의 및 개선점
* `ROLE_ALUMNI`(수료/졸업생)는 현재 회원가입 화면에서 선택 불가능하도록 의도적으로 숨겨져 있음 — 추후 관리자 기능에서만 ALUMNI로 전환할 수 있도록 정책 문서화 필요.

---

### 📁 2. `User.java` — JPA 엔티티

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 200)
    private String password;

    @Column(nullable = false, unique = true, length = 30)
    private String studentNo; // 학번 또는 교번

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
}
```

#### ✅ 잘된 점
* **`@NoArgsConstructor(access = AccessLevel.PROTECTED)`**: JPA 스펙은 기본 생성자를 요구하지만 외부에서 직접 `new User()`로 불완전한 객체를 생성하는 것을 방지하는 올바른 설계 패턴.
* **`@Enumerated(EnumType.STRING)`**: `ORDINAL` 대신 `STRING` 사용 → enum 순서가 바뀌어도 DB 저장값이 깨지지 않음 (매우 중요한 선택).
* **`updatable = false`가 있는 `createdAt`**: 생성 시점 이후 JPA가 절대 UPDATE하지 못하도록 DB 레벨에서 보장.
* **Builder 패턴**: `@Builder`를 `@NoArgsConstructor`와 함께 사용하여 불완전한 객체 생성을 차단하면서도 유연한 생성을 지원.

#### ⚠️ 주의 및 개선점
* **`password` 컬럼 길이 200**: BCrypt 해시는 60자로 고정이므로 200자로 충분하나, 명시적으로 `length = 60`을 쓰면 의도가 더 분명해짐 (마이너 의견).
* **`updatedAt` 필드 부재**: 현재는 생성일시만 있음. 비밀번호 변경 이력 추적이나 프로필 수정 감지를 위해 `updatedAt` 필드 추가를 고려할 것.
* **계정 상태 필드 부재**: 현재 `CustomUserDetails`의 `isEnabled()`, `isAccountNonLocked()` 등이 항상 `true`를 반환함. 향후 계정 정지/탈퇴 처리를 위해 `isActive`, `deletedAt` 필드 도입 권장.

---

### 📁 3. `UserRepository.java` — Spring Data JPA 인터페이스

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByStudentNo(String studentNo);
    boolean existsByEmail(String email);
    boolean existsByStudentNo(String studentNo);
}
```

#### ✅ 잘된 점
* `findBy~`는 `Optional`로 감싸 NPE(NullPointerException) 위험 없이 안전하게 처리.
* 중복 체크용 `existsBy~`를 별도로 사용하여 불필요하게 전체 엔티티를 조회하지 않고 `COUNT` 쿼리로 가볍게 처리.

#### ⚠️ 주의 및 개선점
* 현재 수준에서는 완벽함. 추후 다음 쿼리가 추가될 예정:
  ```java
  List<User> findByRole(Role role);   // 역할별 사용자 목록 조회
  ```

---

### 📁 4. `UserRegisterDto.java` — 회원가입 입력값 검증 DTO

```java
@NotBlank @Email private String email;
@NotBlank @Size(min = 6) private String password;
@NotBlank private String name;
@NotBlank @Pattern(regexp = "^[0-9A-Za-z]{4,20}$") private String studentNo;
@NotNull private Role role;
```

#### ✅ 잘된 점
* **이메일/비밀번호/학번** 3개 필드 모두 서로 다른 적절한 Validation 어노테이션 조합 적용.
* 학번 정규식(`^[0-9A-Za-z]{4,20}$`)으로 허용 범위 내에서 영문/숫자 혼합 지원.

#### ⚠️ 주의 및 개선점
* **비밀번호 최대 길이 없음**: 현재는 `min = 6`만 있어 무제한 길이 입력이 가능함. 극단적으로 긴 비밀번호는 BCrypt 해싱 시간을 공격 벡터로 악용할 수 있으므로 `@Size(min = 6, max = 100)` 권장.
* **비밀번호 확인 필드 없음**: 현재 회원가입 폼에서 비밀번호를 한 번만 입력받음. UX 표준상 `passwordConfirm` 필드를 추가하고 커스텀 Validator로 두 값을 비교하는 것이 일반적.

---

### 📁 5. `UserService.java` — 비즈니스 로직 및 인증 처리

```java
@Service
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

    @Transactional
    public Long register(UserRegisterDto dto) {
        // 중복 체크 후 저장
        ...
        User user = User.builder()
            .email(dto.getEmail().trim().toLowerCase())
            ...
            .build();
        return userRepository.save(user).getId();
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        ...
        return new CustomUserDetails(user);
    }
}
```

#### ✅ 잘된 점
* **`@Transactional(readOnly = true)` + 쓰기 메서드에만 `@Transactional`**: 읽기 작업은 readOnly로 DB 락 경합을 줄이고, 쓰기 작업만 완전한 트랜잭션을 사용하는 모범적인 트랜잭션 전략.
* **`email.trim().toLowerCase()`**: 사용자가 대소문자를 섞거나 앞뒤 공백을 입력해도 정규화되어 저장됨. `loadUserByUsername`에서도 동일하게 적용 → 일관성 보장.
* **생성자 주입**: `UserRepository`와 `PasswordEncoder` 모두 생성자 주입으로 불변성과 테스트 편의성 확보.

#### ⚠️ 주의 및 개선점
* **중복 체크와 저장 사이 레이스 컨디션**: 다중 서버 환경에서 동시에 같은 이메일로 가입 요청이 들어오면 `existsByEmail` 체크를 통과한 후 동시에 `save`가 실행되어 `unique` 제약 위반 에러가 발생할 수 있음. 현재는 단일 서버 환경이므로 큰 문제는 없으나, 향후 `DataIntegrityViolationException` catch 블록 추가 권장.
* **`loadUserByUsername` 로그 노출 주의**: 에러 메시지에 이메일을 그대로 포함(`"가입되지 않은 이메일입니다: " + email`)하면 로그 노출 시 보안 이슈. 운영 전환 시 메시지를 일반화하거나 로그 레벨을 DEBUG로 변경할 것.

---

### 📁 6. `CustomUserDetails.java` — Spring Security 세션 사용자 정보

```java
public class CustomUserDetails implements UserDetails {
    private final Long id;
    private final String email;
    private final String name;
    private final String studentNo;
    private final Role role;
    ...
}
```

#### ✅ 잘된 점
* 기본 Spring Security `User` 객체 대신 커스텀 구현을 통해 세션에 `id`, `name`, `studentNo`, `role` 등 추가 정보를 담을 수 있음. 헤더의 `sec:authentication="principal.name"`이 바로 이 필드를 참조하는 구조.
* 모든 필드가 `private final`로 불변 보장.

#### ⚠️ 주의 및 개선점
* `isAccountNonExpired()`, `isAccountNonLocked()`, `isEnabled()` 3개가 모두 `true` 하드코딩. `User` 엔티티에 `isActive` 등의 상태 필드가 추가되면 반드시 여기서 해당 필드를 반환하도록 연결 필요.
* **직렬화 권장**: 세션 클러스터링 환경에서 `CustomUserDetails`가 세션에 저장되므로 `implements Serializable`과 `serialVersionUID` 선언 권장. 현재 단일 서버 환경에서는 무방.

---

### 📁 7. `SecurityConfig.java` — Spring Security 필터 체인 (업데이트)

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/", "/login", "/register", "/h2-console/**", "/css/**", "/js/**", "/images/**").permitAll()
    .anyRequest().authenticated()
)
.formLogin(form -> form
    .loginPage("/login")
    .usernameParameter("email")
    .defaultSuccessUrl("/", true)
    .failureUrl("/login?error=true")
)
.logout(logout -> logout
    .logoutUrl("/logout")
    .invalidateHttpSession(true)
    .deleteCookies("JSESSIONID")
)
```

#### ✅ 잘된 점
* 이전 `/**` 전체 `permitAll`에서 **최소 공개 경로만 허용**하는 인가 체계로 정확히 전환됨.
* `usernameParameter("email")` 설정으로 Spring Security의 기본 `username` 파라미터 대신 `email` 파라미터를 인식하도록 커스터마이징.
* 로그아웃 시 `invalidateHttpSession(true)` + `deleteCookies("JSESSIONID")` 조합으로 세션 잔류 취약점 방지.

#### ⚠️ 주의 및 개선점
* **`UserDetailsService` 빈 연동 명시 부재**: `SecurityConfig`에 `UserService`를 `AuthenticationManagerBuilder`에 명시적으로 등록하지 않아도 스프링이 자동으로 `UserDetailsService` 구현체를 찾아 연결함. 현재는 `UserService` 하나뿐이라 자동 연결되지만, 구현체가 2개 이상 생기면 `@Primary` 또는 명시적 `AuthenticationProvider` 등록 필요.
* 로그아웃 성공 후 `/?logout=true` 파라미터가 전달되지만, 현재 `HomeController`에서 이 파라미터를 처리하는 코드가 없음 → 로그아웃 성공 메시지를 홈 화면에 보여주려면 처리 로직 추가 필요.

---

### 📁 8. `AuthController.java` — 로그인/회원가입 컨트롤러

```java
@GetMapping("/login")
public String loginPage(@RequestParam(required = false) String error, ...)

@PostMapping("/register")
public String register(@Valid @ModelAttribute("form") UserRegisterDto form,
                       BindingResult bindingResult) {
    if (bindingResult.hasErrors()) return "auth/register";
    try {
        userService.register(form);
        return "redirect:/login?registered=true";
    } catch (IllegalArgumentException e) {
        bindingResult.reject("duplicate", e.getMessage());
        return "auth/register";
    }
}
```

#### ✅ 잘된 점
* `@Valid`와 `BindingResult`를 올바른 순서로 선언(`@Valid DTO` 바로 다음에 `BindingResult`) — 이 순서가 틀리면 검증 에러가 예외로 throw됨.
* `bindingResult.reject("duplicate", e.getMessage())`로 글로벌 에러로 등록하여 필드 에러와 구분되게 처리.
* 생성자 주입 패턴 적용됨.

#### ⚠️ 주의 및 개선점
* **POST 후 GET 리다이렉트(PRG 패턴) 올바르게 적용됨**: 회원가입 성공 시 `redirect:/login?registered=true`로 처리하여 새로고침 시 중복 제출을 방지. 잘 구현됨.
* **로그인 상태에서 `/login`/`/register` 접근 차단 없음**: 이미 로그인한 사용자가 `/login`에 접근하면 로그인 폼이 그대로 노출됨. 추후 `@GetMapping("/login")`에서 `SecurityContextHolder`로 인증 여부를 확인하고 이미 로그인된 경우 `/`로 리다이렉트하는 처리 권장.

---

### 📁 9. `login.html` & `register.html` — 인증 화면 UI

#### ✅ 잘된 점
* **`login.html`**: `autofocus` 속성으로 첫 번째 입력 필드(이메일)에 자동 포커스. `th:href="@{/register}"` 표준 URL 패턴 사용. 에러/성공 메시지를 시각적으로 구분되는 배너로 표시.
* **`register.html`**: 역할 선택 UI를 직관적인 라디오 카드 UI로 구현. CSS `:has()` 선택자(`[&:has(:checked)]`)를 활용한 체크 상태 시각 피드백. 각 필드 아래 즉각적인 에러 메시지 출력(`th:errors`).
* 두 페이지 모두 `th:href="@{...}"` Thymeleaf URL 표준 패턴 사용 (2차 리뷰 권장 사항 완전 반영).

#### ⚠️ 주의 및 개선점
* **`login.html` CSRF 토큰**: `th:action="@{/login}"`을 사용하면 Thymeleaf가 자동으로 `_csrf` 히든 필드를 삽입함. 이는 현재 구현이 맞으나, 반드시 테스트로 정상 동작을 확인해야 함.
* **`register.html` 비밀번호 확인 필드 없음**: `UserRegisterDto` 리뷰에서 언급한 것과 동일. UI 레벨에서도 `passwordConfirm` 입력란이 없어 실수로 잘못 입력해도 바로 알 수 없음.
* **접근성(Accessibility)**: 라디오 버튼 카드의 `<label>` 요소가 암묵적으로 내부 `<input>`과 연결되어 있어 스크린 리더 호환성은 양호. 추후 `aria-required`, `aria-describedby` 등 ARIA 속성 추가 권장.

---

### 📊 3차 리뷰 종합

| 파일 | 완성도 | 핵심 개선 권장 |
| :--- | :---: | :--- |
| `Role.java` | ⭐⭐⭐⭐⭐ | ALUMNI 역할 진입 정책 문서화 필요 |
| `User.java` | ⭐⭐⭐⭐☆ | `updatedAt`, `isActive` 필드 추후 추가 필요 |
| `UserRepository.java` | ⭐⭐⭐⭐⭐ | 완벽한 최소 구성 |
| `UserRegisterDto.java` | ⭐⭐⭐⭐☆ | 비밀번호 최대 길이 + 확인 필드 추가 권장 |
| `UserService.java` | ⭐⭐⭐⭐☆ | 레이스 컨디션 대비 예외 처리 + 로그 보안 개선 |
| `CustomUserDetails.java` | ⭐⭐⭐⭐☆ | `isEnabled()` 등 상태 필드와 연동 필요 |
| `SecurityConfig.java` | ⭐⭐⭐⭐⭐ | 전 단계 대비 명확하게 개선됨 |
| `AuthController.java` | ⭐⭐⭐⭐☆ | 로그인 상태 시 `/login` 접근 리다이렉트 추가 권장 |
| `login.html` / `register.html` | ⭐⭐⭐⭐☆ | 비밀번호 확인 필드 없음, ARIA 속성 권장 |

**최우선 수정 사항 (다음 단계 전 적용 권장)**:
1. `UserRegisterDto` 비밀번호 `max = 100` 추가
2. `SecurityConfig` 로그아웃 메시지 처리 (`/?logout=true` 대응)

**차후 적용 사항 (Phase 2~3에서 처리 예정)**:
1. `User` 엔티티 `updatedAt`, `isActive` 필드 추가
2. `CustomUserDetails` 상태 필드 연동
3. `UserService` `DataIntegrityViolationException` 핸들링

---

## 📅 2026-09-26 (토) - 4차 리뷰: 3차 리뷰 반영 후 변경 코드 재검토

> **리뷰 대상**: 3차 리뷰 최우선 수정 사항 반영 후 변경된 4개 파일  
> `UserRegisterDto.java` / `HomeController.java` / `AuthController.java` / `index.html`

---

### 📁 1. `UserRegisterDto.java` — 비밀번호 최대 길이 추가

```java
// 이전
@Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")

// 이후
@Size(min = 6, max = 100, message = "비밀번호는 6자 이상 100자 이하로 입력해 주세요.")
```

#### ✅ 개선 확인 (3차 지적 → 반영 완료)
* BCrypt 길이 공격(`Long Password DoS`) 방어 목적으로 `max = 100` 추가됨.
* 에러 메시지도 "최소 6자 이상"에서 "6자 이상 100자 이하"로 사용자에게 제한 범위를 명확하게 알려주도록 개선됨.

#### ⚠️ 추가 발견 사항 — 없음
* 현재 단계에서 완벽한 수정. 추가 지적 사항 없음. ✔️

---

### 📁 2. `HomeController.java` — 로그아웃 메시지 처리

```java
@GetMapping("/")
public String index(@RequestParam(value = "logout", required = false) String logout, Model model) {
    model.addAttribute("appName", "UniClass");
    model.addAttribute("currentSemester", currentSemester);
    if (logout != null) {
        model.addAttribute("logoutMessage", "성공적으로 로그아웃되었습니다.");
    }
    return "index";
}
```

#### ✅ 개선 확인 (3차 지적 → 반영 완료)
* `?logout=true` 파라미터를 컨트롤러에서 수신하여 `logoutMessage`를 모델에 담아 화면에 전달.
* `required = false`로 안전하게 처리하여 파라미터 없는 일반 `/` 접근 시에도 에러 없이 정상 동작.

#### ⚠️ 추가 발견 사항
* **Import 순서**: `RequestParam` import가 줄 8에 공백 이후 독립적으로 삽입되어 있음(`import org.springframework.web.bind.annotation.GetMapping;` 뒤에 한 줄 띄고 추가). 기능 오류는 없으나, 코딩 컨벤션상 import는 모두 한 블록으로 모으는 것이 가독성이 좋음 — 마이너 의견.
* 기능 로직 자체는 완벽함. ✔️

---

### 📁 3. `AuthController.java` — 로그인 상태 접근 차단

```java
@GetMapping("/login")
public String loginPage(...) {
    if (isAuthenticated()) { return "redirect:/"; }
    ...
}

@GetMapping("/register")
public String registerPage(Model model) {
    if (isAuthenticated()) { return "redirect:/"; }
    ...
}

@PostMapping("/register")
public String register(...) {
    if (isAuthenticated()) { return "redirect:/"; }
    ...
}

private boolean isAuthenticated() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
}
```

#### ✅ 개선 확인 (3차 지적 → 반영 완료)
* `isAuthenticated()` 헬퍼 메서드로 로직을 중복 없이 깔끔하게 추출하여 3개 메서드 모두에서 재사용. 단일 책임 원칙(SRP) 준수.
* `AnonymousAuthenticationToken` 타입 체크를 포함해 비로그인 사용자(익명 인증 객체)를 정확하게 구분하는 올바른 구현. Spring Security에서 흔히 놓치는 부분을 정확히 처리함.
* `POST /register`에도 체크를 추가하여 직접 curl 등으로 POST 요청을 보내는 경우도 방어.

#### ⚠️ 추가 발견 사항
* 현재 `POST /register`에서 `isAuthenticated()` 체크가 `bindingResult.hasErrors()` 보다 앞에 위치하므로 로그인 상태에서 유효하지 않은 폼을 제출해도 에러 메시지 없이 바로 `/`로 리다이렉트됨 — 이는 의도된 올바른 동작.
* 이 방식은 충분히 좋지만, 장기적으로는 Spring Security 설정 레벨에서 `authorizeHttpRequests`에 `/login`과 `/register` 접근 시 인증된 사용자를 redirect시키는 방식(`authenticated().redirect()`)을 사용하면 컨트롤러 코드를 더 단순하게 유지할 수 있음 — 현재 단계에서는 불필요한 변경이므로 기록만.

---

### 📁 4. `index.html` — 로그아웃 알림 배너

```html
<!-- 로그아웃 알림 배너 -->
<div th:if="${logoutMessage}" class="p-4 rounded-2xl bg-emerald-50 border border-emerald-200 ...">
    <div class="w-8 h-8 rounded-xl bg-emerald-100 text-emerald-600 ...">
        <svg ...> <!-- 체크마크 아이콘 --> </svg>
    </div>
    <div>
        <p class="font-bold text-slate-900" th:text="${logoutMessage}">성공적으로 로그아웃되었습니다.</p>
        <p class="text-xs text-slate-500 mt-0.5">안전하게 세션이 종료되었습니다.</p>
    </div>
</div>
```

#### ✅ 개선 확인 (3차 지적 → 반영 완료)
* `th:if="${logoutMessage}"`로 로그아웃 메시지가 있을 때만 배너가 렌더링되어 일반 홈 접근 시에는 전혀 노출되지 않음.
* 배너 디자인이 `login.html`의 성공 메시지 스타일(emerald 계열)과 일관성 있게 통일됨.
* 폴백 텍스트("성공적으로 로그아웃되었습니다.")가 HTML에 포함되어 Thymeleaf 없이도 정적으로 렌더링될 때 기본값이 표시됨 — Thymeleaf Natural Templating 원칙 준수.

#### ⚠️ 추가 발견 사항
* 현재 배너를 닫을 수 있는 **X 버튼(dismiss 버튼)** 이 없어 새로고침 없이는 배너가 사라지지 않음. `?logout=true` URL 파라미터가 유지되는 한 계속 표시됨. 사용자 경험상 3~5초 후 자동으로 사라지거나 닫기 버튼이 있으면 더 좋음 — 선택적 개선 사항.

---

### 📊 4차 리뷰 종합

| 항목 | 3차 리뷰 지적 | 4차 리뷰 (반영 후) |
| :--- | :---: | :---: |
| **비밀번호 최대 길이 100자** | ⚠️ 최우선 지적 | ✅ 반영 완료 |
| **로그아웃 메시지 처리** | ⚠️ 최우선 지적 | ✅ 반영 완료 |
| **로그인 상태 시 인증 페이지 리다이렉트** | ⚠️ 추가 발견 | ✅ 반영 완료 |
| **배너 자동 닫힘/X버튼** | — | 📝 선택적 개선 권장 |

**총평**: 3차 리뷰에서 최우선으로 지적한 2가지 사항이 모두 올바르게 반영되었으며, 추가로 발견되었던 `AuthController` 로그인 상태 차단까지 깔끔하게 구현됨. `isAuthenticated()` 헬퍼 메서드의 구현 방식이 특히 우수함.

> **현재 상태**: Phase 1 - 2단계(사용자 인증 및 권한) 코드가 리뷰 기준으로 프로덕션 수준에 근접하게 정비됨. 배너 닫힘 버튼은 선택 사항이므로 바로 **Phase 1 - 3단계(수업 공간 및 수강 관리)** 진행 가능.

---

## 📅 2026-09-27 (일) - 5차 UI/UX 고도화, 템플릿 예외 방어 및 디버깅 결과 코드 리뷰

---

### 📁 1. `AuthController.java` (L59-L69) — 비밀번호 일치 검증 순서 및 NPE 방어

```java
if (bindingResult.hasErrors()) {
    return "auth/register";
}

if (form.getPassword() != null && !form.getPassword().equals(form.getPasswordConfirm())) {
    bindingResult.rejectValue("passwordConfirm", "passwordMismatch", "비밀번호와 비밀번호 확인이 일치하지 않습니다.");
    return "auth/register";
}
```

#### ✅ 잘된 점
* `hasErrors()` 먼저 → `!= null` 가드 후 비밀번호 비교 순서가 올바름.
* Null-safe하고 검증 흐름이 명확하며, 서비스 레이어의 범용 예외 대신 `passwordConfirm` 필드 에러(`rejectValue`)로 직접 연결하여 인풋 박스 하단에 직관적으로 에러를 노출함.

---

### 📁 2. `register.html` (L19-L26) & `join.html` (L18-L22) — Thymeleaf 컨텍스트 스코프 버그 수정

```html
<!-- 회원가입 폼 내부에 글로벌 에러 위치 -->
<form th:action="@{/register}" th:object="${form}" method="post" class="space-y-5">
    <div th:if="${#fields.hasGlobalErrors()}" ...>
        ...
    </div>
```

#### ✅ 잘된 점
* `#fields` 헬퍼가 `<form th:object="...">` 외부에서 호출될 때 발생하던 `TemplateProcessingException`(500 에러) 및 그로 인한 비정상 `/login` 리다이렉트 결함을 완벽히 해결함.

---

### 📁 3. `join.html` (L23-L36) — 초대 코드 전용 입력 UX 및 힌트 문구

```html
<input type="text" id="inviteCode" th:field="*{inviteCode}" maxlength="6" autofocus
       oninput="this.value = this.value.toUpperCase().replace(/[^A-Z0-9]/g, '')"
       placeholder="예: CS101A"
       class="w-full text-center tracking-[0.3em] font-mono text-2xl font-bold uppercase ...">
<p class="text-[11px] text-slate-400 mt-2 text-center">대소문자 구분 없이 입력하실 수 있습니다.</p>
```

#### ✅ 잘된 점
* `oninput`으로 즉시 대문자 변환 + 영숫자 필터링. 클라이언트 입력 정제가 직관적이고 효과적임.

#### ⚠️ 개선 권고 사항 (UX 불일치)
* 힌트는 `"대소문자 구분 없이 입력하실 수 있습니다"`인데, `oninput`이 강제 대문자 변환(`toUpperCase()`)을 수행하므로 사용자 관점에서 혼란이 있을 수 있음.
* **수정 권장**: 안내 문구를 `"자동으로 대문자 변환됩니다"` 또는 `"영문 대문자 및 숫자로 자동 변환됩니다"`로 수정 권장.

---

### 📁 4. `detail.html` (L43-L66) — 초대 코드 원클릭 복사 및 클립보드 Fallback

```javascript
navigator.clipboard.writeText(code).then(() => {
    ...
}).catch(() => {
    alert('초대 코드: ' + code);
});
```

#### ✅ 잘된 점
* `navigator.clipboard.writeText`를 활용해 원클릭 복사 및 2초간 `복사됨!` 토스트 피드백 제공.

#### ⚠️ 개선 권고 사항 (HTTPS 환경 fallback)
* `localhost` 환경에서는 Clipboard API가 정상 동작하나, 향후 HTTP 배포 환경이나 클립보드 권한 거부 시 `alert()`로 떨어지는 UX가 다소 투박함.
* **수정 권장**: input 요소 생성 후 `select()` 및 `document.execCommand('copy')` 또는 세련된 토스트 알림을 통한 폴백 처리 고려.

---

### 📁 5. `default.html` (L69-L83) — 모바일 반응형 헤더 최적화

```html
<div class="flex items-center gap-1.5 sm:gap-2 px-2.5 sm:px-3 py-1.5 rounded-xl bg-slate-100 text-xs font-semibold text-slate-700">
    <span class="w-2 h-2 rounded-full bg-emerald-500 flex-shrink-0"></span>
    <span sec:authentication="principal.name">홍길동</span>
    <span class="hidden sm:inline text-slate-400 font-normal">|</span>
    <span class="hidden sm:inline text-blue-600 font-mono" sec:authentication="principal.studentNo">20260001</span>
    ...
</div>
```

#### ✅ 잘된 점
* `hidden sm:inline`을 적용하여 좁은 모바일 화면에서 학번과 구분선을 적절히 숨김 처리. Tailwind 반응형 관용구를 올바르게 활용하여 헤더 레이아웃 깨짐을 방지함.

---

### 📁 6. `index.html` (L36-L101) — 역할별 히어로 배너 분기 및 4색 테마 순환

```html
<section sec:authorize="hasRole('ROLE_INSTRUCTOR')" ...> ... </section>
<section sec:authorize="hasRole('ROLE_STUDENT')" ...> ... </section>
<section sec:authorize="isAnonymous()" ...> ... </section>
```

#### ✅ 잘된 점
* `sec:authorize`로 교수/학생/비로그인 3분기 처리. Spring Security Thymeleaf dialect를 올바르게 활용하여 사용자 역할에 꼭 맞는 CTA를 제시함.
* 수업 카드에 `iterStat.index % 4`를 통한 4색 그라데이션 테마 순환 적용으로 과목 간 시각적 식별력 우수.

#### ⚠️ 개선 권고 사항 (`sec:authentication` 폴백)
* `<span sec:authentication="principal.name">교수</span>님!` 형태에서 인증 객체 이상 시 하드코딩 텍스트가 노출될 수 있음. 로그인 상태에서만 렌더링되므로 큰 문제는 아니지만 참고.

---

### 📁 7. `login.html` (L16-L36) — 알림 배너 닫기 및 자동 페이드아웃

#### ✅ 잘된 점
* 수동 닫기(`X`) 버튼 및 3.5초 자동 페이드아웃 애니메이션(`transition-opacity duration-500`) 적용으로 UX 향상.

#### ⚠️ 개선 권고 사항 (스크립트 위치)
* 페이드아웃 스크립트가 인라인 하단에 분산 배치되어 있으므로, 추후 레이아웃 공통 자바스크립트로 일원화 관리 권장.

---

### 📊 5차 리뷰 종합 평가

| 항목 | 평가 | 세부 내용 |
| :--- | :---: | :--- |
| **코드 구조 및 안정성** | ✅ 우수 | NPE 가드 처리, Thymeleaf 컨텍스트 예외 완벽 해결 |
| **Null 안전성** | ✅ 양호 | `form.getPassword() != null` 방어 로직 적용 완료 |
| **UX 일관성** | ⚠️ 주의 | `join.html` 안내 문구(`대소문자 구분 없이` vs 자동 대문자 변환) 정합성 조정 필요 |
| **보안 및 클라이언트 검증** | ✅ 양호 | CSRF 및 XSS 안전, 클라이언트 입력 실시간 정제 |
| **모바일 반응형 대응** | ✅ 우수 | 상단 바 헤더 줄바꿈 방지 및 카드 그리드 유연성 확보 |

> **수정 우선순위**: `join.html` 안내 문구(`대소문자 구분 없이 입력하실 수 있습니다` ➡️ `자동으로 대문자 변환됩니다`) 수정이 가장 빠르고 효과적인 개선 사항임.

