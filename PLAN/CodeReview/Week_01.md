# 🔍 Code Review - Week 01

> **기간**: 2026-09-25 ~ 2026-10-01  
> **프로젝트**: UniClass (Java / Spring Boot 기반)

---

## 📅 2026-09-26 (토) - 1차 스프링 부트 뼈대(Scaffolding) 코드 리뷰

### 1. 리뷰 대상 파일 목록 (총 8개 파일)

| 구분 | 파일 경로 | 역할 |
| :--- | :--- | :--- |
| **빌드/설정** | `build.gradle` | Java 25 & Spring Boot 4.x 의존성 및 빌드 설정 |
| **환경설정** | `src/main/resources/application.yml` | 포트(8080), H2 인메모리 DB, JPA, 20MB 업로드 제한 설정 |
| **메인 애플리케이션** | `src/main/java/.../UniclassApplication.java` | Spring Boot 구동 엔트리포인트 |
| **보안 설정** | `src/main/java/.../global/config/SecurityConfig.java` | SecurityFilterChain 및 BCryptPasswordEncoder 빈 등록 |
| **컨트롤러** | `src/main/java/.../domain/home/controller/HomeController.java` | 메인 홈("/") 대시보드 매핑 컨트롤러 |
| **공통 레이아웃** | `src/main/resources/templates/layout/default.html` | Thymeleaf + Tailwind CSS 반응형 상단바 및 뼈대 레이아웃 |
| **홈 템플릿** | `src/main/resources/templates/index.html` | 메인 랜딩 뷰 (기능 카드, 수업 미리보기) |
| **단위 테스트** | `src/test/java/.../UniclassApplicationTests.java` | 스프링 컨텍스트 로딩 기본 테스트 |

---

### 2. 파일별 상세 분석 및 리뷰

#### ① `build.gradle`
* **분석**:
  * 최신 Java 25 툴체인(`JavaLanguageVersion.of(25)`)과 Gradle 9.7.1 Wrapper 적용.
  * 필수 스타터 패키지(`webmvc`, `thymeleaf`, `data-jpa`, `h2`, `security`, `validation`, `lombok`)가 누락 없이 탑재됨.
* **잘된 점 (Good)**:
  * 최신 자바 버전에 맞추어 툴체인이 정확히 타겟팅되어 로컬 컴파일 성공.
  * Thymeleaf의 Spring Security 태그 라이브러리(`thymeleaf-extras-springsecurity6`)가 미리 포함되어 추후 로그인 여부에 따른 UI 분기 처리가 수월함.
* **개선 및 주의점 (Watchout)**:
  * 향후 실제 배포 단계에서는 `com.h2database:h2` 외에 `org.postgresql:postgresql` 드라이버를 `runtimeOnly`로 추가해줄 필요가 있음.

---

#### ② `application.yml`
* **분석**:
  * 서버 포트 8080 지정, H2 인메모리 모드(`jdbc:h2:mem:uniclassdb;DB_CLOSE_DELAY=-1`) 및 웹 콘솔(`/h2-console`) 활성화.
  * 멀티파트 파일 업로드 제한을 `max-file-size: 20MB`, `max-request-size: 25MB`로 확장.
  * `ddl-auto: update`로 엔티티 변경 시 자동 DDL 반영.
* **잘된 점 (Good)**:
  * 학교 플랫폼 특성상 강의 슬라이드나 과제 ZIP 파일이 크기 때문에, 초기부터 20MB 제한 확장을 선제적으로 적용한 점이 매우 훌륭함.
* **개선 및 주의점 (Watchout)**:
  * **보안 주의**: 운영 환경 배포 시 `ddl-auto: update`는 데이터 유실 또는 의도치 않은 스키마 변경 위험이 있으므로 `validate` 또는 마이그레이션 도구(Flyway)로 전환해야 함.
  * H2 인메모리 방식은 서버를 끄면 데이터가 날아가므로, 개발 중간 단계부터는 파일 기반 H2(`jdbc:h2:file:./data/uniclassdb`)로 변경하는 것을 권장.

---

#### ③ `SecurityConfig.java`
* **분석**:
  * Spring Security 6/7 최신 람다 스타일 DSL로 필터 체인 구성.
  * 모든 경로(`/**`) 및 정적 리소스, H2 콘솔 접근을 허용(`permitAll`).
  * H2 콘솔 iframe 렌더링을 위해 `frameOptions.sameOrigin()` 적용.
* **잘된 점 (Good)**:
  * 뼈대 개발 단계에서 불필요하게 401/403 에러로 개발 흐름이 끊기지 않도록 `permitAll`을 깔끔하게 열어둠.
  * H2 콘솔 사용 시 흔히 발생하는 CSRF 및 X-Frame-Options 차단 문제를 선제적으로 해결함.
* **개선 및 주의점 (Watchout)**:
  * **다음 단계 필수 작업**: Phase 1의 2단계(인증)로 넘어가면 `/**`를 전부 여는 대신, 비로그인 공개 페이지(`/`, `/login`, `/register`, `/css/**`)와 로그인 필수 페이지(`/class/**`, `/assignment/**`)를 엄격하게 분리해야 함.

---

#### ④ `HomeController.java`
* **분석**:
  * `@Controller`로 등록되어 GET `/` 요청 시 `appName`과 `currentSemester`를 Model에 담아 `index.html`을 반환.
* **잘된 점 (Good)**:
  * 심플하고 직관적이며 Spring MVC 표준 패턴을 잘 따름.
* **개선 및 주의점 (Watchout)**:
  * 추후 사용자가 로그인되어 있을 경우, 학생/교수 대시보드로 자동 리다이렉트(`return "redirect:/dashboard"`)시키는 분기 로직이 추가되어야 함.

---

#### ⑤ `layout/default.html` & `index.html` (Thymeleaf + Tailwind CSS)
* **분석**:
  * `layout/default.html`에 CDN 기반 Tailwind CSS와 공통 네비게이션 헤더, 푸터가 구성됨.
  * `th:replace="~{layout/default :: layout(~{::content})}"` 구조로 템플릿 상속 및 프래그먼트 교체 구현.
  * 모바일과 PC 환경에 대응하는 반응형 그리드(`grid-cols-1 md:grid-cols-3`) 적용.
* **잘된 점 (Good)**:
  * 별도의 무거운 프론트엔드 빌드 파이프라인(Node/Webpack) 없이도 완성도 높은 모던 UI 컴포넌트(배너, 카드, 뱃지)를 즉각 렌더링함.
  * 레이아웃이 분리되어 있어 향후 페이지(로그인, 수업 상세, 과제 제출 등)를 추가할 때 헤더/푸터를 재작성할 필요가 없음.
* **개선 및 주의점 (Watchout)**:
  * Tailwind CDN은 개발 단계에서는 최고로 편리하지만, 프로덕션 배포 시에는 로딩 속도 최적화를 위해 Standalone CLI를 통한 경량 CSS 빌드로 마이그레이션하는 것이 좋음.

---

### 3. 총평 및 종합 점수

* **완성도**: ⭐⭐⭐⭐⭐ (5/5)
* **안정성**: 빌드 성공, Tomcat 구동 3.7초, HTTP 200 OK 검증 완료.
* **코드 청결도**: 불필요한 보일러플레이트 없이 최소 필수 요소로만 구성되어 유지보수성이 뛰어남.
* **다음 리뷰 타겟**: Phase 1 - 2단계 `User` 엔티티, `UserRepository`, `UserService` 및 회원가입 폼 구현 코드.
