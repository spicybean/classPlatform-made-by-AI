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
