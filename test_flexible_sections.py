import urllib.request
import urllib.parse
import http.cookiejar
import re
import sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8080"

class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def http_error_302(self, req, fp, code, msg, headers):
        return fp

class TestClient:
    def __init__(self):
        self.cj = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cj),
            NoRedirectHandler()
        )
        self.follow_opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cj)
        )

    def get(self, url, follow=False):
        opener = self.follow_opener if follow else self.opener
        req = urllib.request.Request(url, headers={"User-Agent": "SectionE2E"})
        try:
            resp = opener.open(req)
            return resp.status, resp.headers, resp.read().decode('utf-8', errors='replace')
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read().decode('utf-8', errors='replace')

    def post(self, url, data=None, follow=False):
        opener = self.follow_opener if follow else self.opener
        encoded_data = urllib.parse.urlencode(data).encode('utf-8') if data else b""
        req = urllib.request.Request(url, data=encoded_data, headers={
            "User-Agent": "SectionE2E",
            "Content-Type": "application/x-www-form-urlencoded"
        })
        try:
            resp = opener.open(req)
            return resp.status, resp.headers, resp.read().decode('utf-8', errors='replace')
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read().decode('utf-8', errors='replace')

def get_csrf(client, url):
    status, headers, html = client.get(url, follow=True)
    match = re.search(r'name="_csrf"\s+value="([^"]+)"', html)
    if not match:
        match = re.search(r'value="([^"]+)"\s+name="_csrf"', html)
    return match.group(1) if match else None

def login(client, email, password):
    csrf = get_csrf(client, f"{BASE_URL}/login")
    status, headers, _ = client.post(f"{BASE_URL}/login", data={
        "email": email,
        "password": password,
        "_csrf": csrf
    })
    assert status == 302, f"Login failed for {email} (status={status})"
    loc = headers.get('Location', '')
    assert "error=true" not in loc, f"Login error redirect: {loc}"

print("=== [1] 교수 로그인 및 주차별 커리큘럼(materials) 탭 검증 ===")
prof = TestClient()
login(prof, "prof@uniclass.ac.kr", "password123!")
print("✓ 교수 로그인 성공")

status, headers, html = prof.get(f"{BASE_URL}/classes/1?tab=materials", follow=True)
assert status == 200
assert "주차별 커리큘럼 및 강의자료" in html
assert "주차/보강 추가" in html
assert "16W" in html
print("✓ 1~16주차 커리큘럼 탭 및 교수 관리 버튼 노출 확인")

print("\n=== [2] 5주차 원클릭 휴강(HOLIDAY) 전환 검증 ===")
csrf = get_csrf(prof, f"{BASE_URL}/classes/1?tab=materials")
# 5주차 sectionId 찾기
m = re.search(r'/classes/1/sections/(\d+)/toggle-holiday', html)
assert m, "toggle-holiday URL을 찾을 수 없습니다."
sec5_id = m.group(1)

# 휴강 전환 실행
status, headers, html = prof.post(f"{BASE_URL}/classes/1/sections/{sec5_id}/toggle-holiday", data={
    "_csrf": csrf,
    "reason": "추석 황금연휴로 인한 휴강 주간입니다."
}, follow=True)
assert status == 200
assert "휴강" in html
assert "공휴일 / 휴강 주간" in html
assert "추석 황금연휴" in html
print("✓ 5주차 원클릭 휴강 전환 및 상단 휴강 안내 배너 노출 확인")

print("\n=== [3] 주차 상세 설정 (시험 주간 & 날짜 범위 설정) 검증 ===")
csrf = get_csrf(prof, f"{BASE_URL}/classes/1?tab=materials")
status, headers, html = prof.post(f"{BASE_URL}/classes/1/sections/{sec5_id}/update", data={
    "_csrf": csrf,
    "title": "5주차 (중간고사 대비 특강)",
    "sectionType": "EXAM",
    "startDate": "2026-10-05",
    "endDate": "2026-10-09",
    "noticeText": "중간고사 모의평가 및 Q&A를 진행합니다.",
    "isHidden": "false"
}, follow=True)
assert status == 200
assert "5주차 (중간고사 대비 특강)" in html
assert "시험" in html
assert "시험 주간" in html
assert "10.05 ~ 10.09" in html
print("✓ 주차 시험 유형 변경, 날짜 범위(10.05 ~ 10.09) 및 시험 안내 배너 노출 확인")

print("\n=== [4] 17주차 신규 보강 주차 추가 검증 ===")
csrf = get_csrf(prof, f"{BASE_URL}/classes/1?tab=materials")
status, headers, html = prof.post(f"{BASE_URL}/classes/1/sections/new", data={
    "_csrf": csrf,
    "title": "17주차 (학기말 보강 및 프로젝트 발표)",
    "sectionType": "MAKEUP"
}, follow=True)
assert status == 200
assert "17W" in html
assert "17주차 (학기말 보강 및 프로젝트 발표)" in html
assert "보강" in html
print("✓ 17주차 보강 주차 생성 및 뱃지 노출 확인 (16주 한계 돌파)")

print("\n=== [5] 학생 시점 휴강/시험 배너 렌더링 및 UI 검증 ===")
student = TestClient()
login(student, "student@uniclass.ac.kr", "password123!")
print("✓ 학생 로그인 성공")

status, headers, html = student.get(f"{BASE_URL}/classes/1?tab=materials", follow=True)
assert status == 200
assert "5주차 (중간고사 대비 특강)" in html
assert "시험 주간" in html
assert "중간고사 모의평가" in html
assert "17W" in html
# 학생에게는 주차 추가 버튼과 주차 설정 버튼이 보이지 않아야 함
assert '<button type="button" onclick="openAddSectionModal()"' not in html
assert 'openSectionEditModal(this)' not in html
print("✓ 학생 뷰에서 시험/휴강 일정 정확한 노출 및 교수 전용 관리 버튼 격리 확인")

print("\n🎉 유연한 주차/학사 블록 시스템(Flexible Course Block) 모든 E2E 검증 100% 통과 (ALL SUCCESS)!")
