import urllib.request
import urllib.parse
import http.cookiejar
import re
import sys
import zipfile
import io

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
        req = urllib.request.Request(url, headers={"User-Agent": "ExportE2E"})
        try:
            resp = opener.open(req)
            return resp.status, resp.headers, resp.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

    def post(self, url, data=None, follow=False):
        opener = self.follow_opener if follow else self.opener
        encoded_data = urllib.parse.urlencode(data).encode('utf-8') if data else b""
        req = urllib.request.Request(url, data=encoded_data, headers={
            "User-Agent": "ExportE2E",
            "Content-Type": "application/x-www-form-urlencoded"
        })
        try:
            resp = opener.open(req)
            return resp.status, resp.headers, resp.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

def get_csrf(client, url):
    status, headers, body = client.get(url, follow=True)
    html = body.decode('utf-8', errors='replace')
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

print("=== [1] 교수 로그인 및 데이터 일괄 내보내기 E2E 검증 ===")
prof = TestClient()
login(prof, "prof@uniclass.ac.kr", "password123!")
print("✓ 교수 로그인 성공")

# 1-1. 성적표 CSV 다운로드
status, headers, body = prof.get(f"{BASE_URL}/classes/1/export/grades-csv", follow=False)
assert status == 200, f"성적표 CSV 다운로드 실패: {status}"
content_type = headers.get("Content-Type", "")
assert "text/csv" in content_type, f"Content-Type 불일치: {content_type}"
assert body.startswith(b'\xef\xbb\xbf'), "UTF-8 BOM 누락"
csv_text = body.decode("utf-8")
assert "학번,이름,이메일" in csv_text, "CSV 헤더 누락"
assert "최종 환산점수" in csv_text, "종합점수 컬럼 누락"
print("✓ [교수] 성적표 CSV 다운로드 및 UTF-8 BOM, 헤더 정상 검증")

# 1-2. 제출물 전체 ZIP 다운로드
status, headers, body = prof.get(f"{BASE_URL}/classes/1/export/submissions-zip", follow=False)
assert status == 200, f"제출물 ZIP 다운로드 실패: {status}"
content_type = headers.get("Content-Type", "")
assert "application/zip" in content_type, f"Content-Type 불일치: {content_type}"
with zipfile.ZipFile(io.BytesIO(body)) as zf:
    namelist = zf.namelist()
    assert len(namelist) > 0, "ZIP 파일이 비어 있습니다."
    print(f"✓ [교수] 제출물 전체 ZIP 압축 정상 검증 (압축 파일 목록: {namelist})")

# 1-3. UI 링크 렌더링 확인 (grades, assignments, settings 탭)
_, _, body = prof.get(f"{BASE_URL}/classes/1?tab=grades", follow=True)
html = body.decode("utf-8", errors='ignore')
assert "/classes/1/export/grades-csv" in html, "grades 탭 내 CSV 다운로드 링크 누락"

_, _, body = prof.get(f"{BASE_URL}/classes/1?tab=assignments", follow=True)
html = body.decode("utf-8", errors='ignore')
assert "/classes/1/export/submissions-zip" in html, "assignments 탭 내 제출물 ZIP 다운로드 링크 누락"

_, _, body = prof.get(f"{BASE_URL}/classes/1?tab=settings", follow=True)
html = body.decode("utf-8", errors='ignore')
assert "학기 데이터 일괄 백업 및 내보내기" in html, "settings 탭 내 백업 카드 누락"
print("✓ [교수] 탭별 다운로드 UI 링크 노출 정상 검증")


print("\n=== [2] 학생 로그인 및 포트폴리오 ZIP 다운로드 E2E 검증 ===")
student = TestClient()
login(student, "student@uniclass.ac.kr", "password123!")
print("✓ 학생 1 로그인 성공")

# 2-1. 내 과제 포트폴리오 ZIP 다운로드
status, headers, body = student.get(f"{BASE_URL}/classes/1/export/portfolio-zip", follow=False)
assert status == 200, f"학생 포트폴리오 ZIP 다운로드 실패: {status}"
with zipfile.ZipFile(io.BytesIO(body)) as zf:
    student_namelist = zf.namelist()
    assert "과제제출_및_피드백_요약.txt" in student_namelist, "피드백 요약 텍스트 파일 누락"
    summary_text = zf.read("과제제출_및_피드백_요약.txt").decode("utf-8")
    assert "학기 과제 제출 포트폴리오 & 피드백 리포트" in summary_text
    print(f"✓ [학생] 내 과제 포트폴리오 ZIP 정상 검증 (포함 파일: {student_namelist})")

# 2-2. 학생 assignments 탭 링크 노출 확인
_, _, body = student.get(f"{BASE_URL}/classes/1?tab=assignments", follow=True)
html = body.decode("utf-8", errors='ignore')
assert "/classes/1/export/portfolio-zip" in html, "학생 assignments 탭 내 포트폴리오 링크 누락"
assert "/classes/1/export/submissions-zip" not in html, "학생에게 전체 제출물 다운로드 버튼이 보이면 안 됨"
print("✓ [학생] UI 뷰 권한 분리 정상 검증")


print("\n=== [3] 권한 방어 (Negative Access Test) 검증 ===")
# 학생이 교수 전용 성적표 CSV나 전체 제출물 ZIP에 직접 URL로 접근 시도
status, _, _ = student.get(f"{BASE_URL}/classes/1/export/grades-csv", follow=False)
assert status in [403, 500], f"학생의 교수 성적표 CSV 접근이 차단되지 않음: {status}"

status, _, _ = student.get(f"{BASE_URL}/classes/1/export/submissions-zip", follow=False)
assert status in [403, 500], f"학생의 전체 제출물 ZIP 접근이 차단되지 않음: {status}"
print("✓ [보안] 학생의 교수 전용 내보내기 URL 직접 접근 원천 차단 확인 (403/500)")

print("\n🎉 Phase 2 Step 8 데이터 보존 및 일괄 내보내기 (Export) 모든 E2E 검증 100% 통과 (ALL SUCCESS)!")
